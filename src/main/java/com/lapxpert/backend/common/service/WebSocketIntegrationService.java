package com.lapxpert.backend.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket Integration Service
 * 
 * Provides integration between the main LapXpert application and the dedicated WebSocket service.
 * Publishes messages to Redis Pub/Sub channels that the WebSocket service subscribes to,
 * enabling real-time notifications to be sent to connected clients.
 * 
 * This service acts as the bridge for the main application to send real-time updates
 * without directly managing WebSocket connections, supporting horizontal scaling
 * and separation of concerns.
 * 
 * Usage:
 * - Inject this service in controllers/services that need to send real-time updates
 * - Call appropriate methods to publish price updates, voucher notifications, etc.
 * - Messages are automatically routed to the WebSocket service via Redis Pub/Sub
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketIntegrationService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${websocket.integration.enabled:true}")
    private boolean integrationEnabled;

    // Redis channel constants (must match WebSocket service configuration)
    private static final String GLOBAL_CHANNEL = "lapxpert:websocket:global";
    private static final String PRICE_CHANNEL = "lapxpert:websocket:price";
    private static final String VOUCHER_CHANNEL = "lapxpert:websocket:voucher";
    private static final String CHATBOX_CHANNEL = "lapxpert:websocket:chatbox";

    // Enhanced channels for cache replacement
    private static final String DATA_CHANNEL = "lapxpert:websocket:data";
    private static final String SEARCH_CHANNEL = "lapxpert:websocket:search";
    private static final String CART_CHANNEL = "lapxpert:websocket:cart";
    private static final String CATEGORY_CHANNEL = "lapxpert:websocket:category";
    private static final String CONFIG_CHANNEL = "lapxpert:websocket:config";

    // Additional channels for comprehensive cache replacement
    private static final String RATING_CHANNEL = "lapxpert:websocket:rating";
    private static final String SHIPPING_CHANNEL = "lapxpert:websocket:shipping";
    private static final String SESSION_CHANNEL = "lapxpert:websocket:session";

    // AI Chat channel for real-time AI chat functionality
    private static final String AI_CHAT_CHANNEL = "lapxpert:websocket:ai-chat";

    // Metrics
    private final AtomicLong totalMessagesSent = new AtomicLong(0);
    private final AtomicLong sendErrors = new AtomicLong(0);
    private final AtomicLong queuedMessages = new AtomicLong(0);
    private final AtomicLong transactionRollbacks = new AtomicLong(0);

    // Message sequencing for cache replacement
    private final AtomicLong messageSequenceGenerator = new AtomicLong(0);

    /**
     * Transaction-aware message queue for coordinating WebSocket delivery with database transactions
     * Vietnamese Business Context: Hàng đợi tin nhắn nhận biết giao dịch để điều phối gửi WebSocket với giao dịch cơ sở dữ liệu
     */
    private static final ThreadLocal<List<QueuedWebSocketMessage>> transactionMessageQueue = new ThreadLocal<>();

    /**
     * Queued WebSocket message for transaction coordination
     */
    private static class QueuedWebSocketMessage {
        final String channel;
        final String destination;
        final Object payload;
        final String messageType;
        final Instant queuedAt;

        QueuedWebSocketMessage(String channel, String destination, Object payload, String messageType) {
            this.channel = channel;
            this.destination = destination;
            this.payload = payload;
            this.messageType = messageType;
            this.queuedAt = Instant.now();
        }
    }

    /**
     * Send price update notification
     * Vietnamese topic: /topic/gia-san-pham/{variantId}
     */
    public void sendPriceUpdate(String variantId, Double newPrice, String message) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping price update");
            return;
        }

        try {
            Map<String, Object> priceData = new HashMap<>();
            priceData.put("variantId", variantId);
            priceData.put("newPrice", newPrice);
            priceData.put("giaMoi", newPrice); // Vietnamese field name for compatibility
            priceData.put("message", message != null ? message : "Giá sản phẩm đã được cập nhật");
            priceData.put("timestamp", Instant.now());
            priceData.put("type", "PRICE_UPDATE");

            String destination = "/topic/gia-san-pham/" + variantId;
            publishMessage(PRICE_CHANNEL, destination, priceData, "PRICE_UPDATE");

            // Also send to general price monitoring topic
            publishMessage(PRICE_CHANNEL, "/topic/gia-san-pham/all", priceData, "PRICE_UPDATE");

            log.debug("Sent price update for variant {}: {}", variantId, newPrice);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send price update for variant {}: {}", variantId, e.getMessage(), e);
        }
    }

    /**
     * Send comprehensive price update notification with old and new prices
     * Enhanced version for ProductVariantDialog real-time updates
     * Vietnamese topic: /topic/gia-san-pham/{variantId}
     * Fixed to prevent JSON serialization circular reference issues
     */
    public void sendComprehensivePriceUpdate(String variantId, Double oldPrice, Double newPrice,
                                           String productName, String sku, String reason) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping comprehensive price update");
            return;
        }

        try {
            // Create a clean data map with only primitive values to avoid circular references
            Map<String, Object> priceData = new HashMap<>();

            // Basic price information
            priceData.put("variantId", variantId != null ? variantId : "unknown");
            priceData.put("oldPrice", oldPrice);
            priceData.put("newPrice", newPrice != null ? newPrice : 0.0);
            priceData.put("giaCu", oldPrice); // Vietnamese field name
            priceData.put("giaMoi", newPrice != null ? newPrice : 0.0); // Vietnamese field name

            // Product information (safe string values only)
            priceData.put("productName", productName != null ? productName : "Unknown Product");
            priceData.put("tenSanPham", productName != null ? productName : "Unknown Product"); // Vietnamese field name
            priceData.put("sku", sku != null ? sku : "");
            priceData.put("reason", reason != null ? reason : "Cập nhật giá sản phẩm");
            priceData.put("lyDo", reason != null ? reason : "Cập nhật giá sản phẩm"); // Vietnamese field name

            // Metadata
            priceData.put("timestamp", Instant.now().toString()); // Convert to string to avoid serialization issues
            priceData.put("type", "PRICE_UPDATE");

            // Calculate change amount and percentage (safe calculations)
            if (oldPrice != null && newPrice != null && oldPrice > 0) {
                double changeAmount = newPrice - oldPrice;
                double changePercent = (changeAmount / oldPrice) * 100;
                priceData.put("changeAmount", Math.round(changeAmount * 100.0) / 100.0); // Round to 2 decimal places
                priceData.put("changePercent", Math.round(changePercent * 100.0) / 100.0); // Round to 2 decimal places
                priceData.put("soTienThayDoi", Math.round(changeAmount * 100.0) / 100.0); // Vietnamese field name
                priceData.put("phanTramThayDoi", Math.round(changePercent * 100.0) / 100.0); // Vietnamese field name
            }

            String destination = "/topic/gia-san-pham/" + variantId;
            publishMessage(PRICE_CHANNEL, destination, priceData, "PRICE_UPDATE");

            // Also send to general price monitoring topic
            publishMessage(PRICE_CHANNEL, "/topic/gia-san-pham/all", priceData, "PRICE_UPDATE");

            log.debug("Sent comprehensive price update for variant {}: {} -> {} ({})",
                     variantId, oldPrice, newPrice, productName);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send comprehensive price update for variant {}: {}", variantId, e.getMessage(), e);
            log.error("Price update details - variantId: {}, oldPrice: {}, newPrice: {}, productName: {}",
                     variantId, oldPrice, newPrice, productName);
        }
    }

    /**
     * Send enhanced voucher notification with proper type field for consistent frontend processing
     * Vietnamese topics: /topic/phieu-giam-gia/{voucherId} or /topic/dot-giam-gia/{campaignId}
     */
    public void sendEnhancedVoucherNotification(String voucherId, String voucherType, String message, String notificationType, Object voucherData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping enhanced voucher notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("voucherId", voucherId);
            notification.put("voucherType", voucherType);
            notification.put("message", message);
            notification.put("type", notificationType); // Add proper type field for frontend processing
            notification.put("timestamp", Instant.now());

            if (voucherData != null) {
                notification.put("data", voucherData);
                // Add Vietnamese field names for compatibility
                if (voucherData instanceof com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) {
                    com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto dto =
                        (com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) voucherData;
                    notification.put("maPhieuGiamGia", dto.getMaPhieuGiamGia());
                    notification.put("voucherCode", dto.getMaPhieuGiamGia());
                    notification.put("giaTriGiam", dto.getGiaTriGiam());
                    notification.put("discountValue", dto.getGiaTriGiam());
                }
            }

            String topicPrefix = "PHIEU_GIAM_GIA".equals(voucherType) ? "phieu-giam-gia" : "dot-giam-gia";
            String destination = "/topic/" + topicPrefix + "/" + voucherId;

            publishMessage(VOUCHER_CHANNEL, destination, notification, notificationType);

            // Also send to general voucher monitoring topic with proper type field
            publishMessage(VOUCHER_CHANNEL, "/topic/voucher/all", notification, notificationType);

            log.debug("Sent enhanced voucher notification for {} {}: {} (type: {})", voucherType, voucherId, message, notificationType);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send enhanced voucher notification for {} {}: {}", voucherType, voucherId, e.getMessage(), e);
        }
    }

    /**
     * Send voucher notification
     * Vietnamese topics: /topic/phieu-giam-gia/{voucherId} or /topic/dot-giam-gia/{campaignId}
     */
    public void sendVoucherNotification(String voucherId, String voucherType, String message, Object voucherData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping voucher notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("voucherId", voucherId);
            notification.put("voucherType", voucherType);
            notification.put("message", message);
            notification.put("timestamp", Instant.now());

            if (voucherData != null) {
                notification.put("data", voucherData);
            }

            String topicPrefix = "PHIEU_GIAM_GIA".equals(voucherType) ? "phieu-giam-gia" : "dot-giam-gia";
            String destination = "/topic/" + topicPrefix + "/" + voucherId;

            publishMessage(VOUCHER_CHANNEL, destination, notification, "PHIEU_GIAM_GIA_NOTIFICATION");

            // Also send to general voucher monitoring topic
            publishMessage(VOUCHER_CHANNEL, "/topic/voucher/all", notification, "PHIEU_GIAM_GIA_NOTIFICATION");

            log.debug("Sent voucher notification for {} {}: {}", voucherType, voucherId, message);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send voucher notification for {} {}: {}", voucherType, voucherId, e.getMessage(), e);
        }
    }

    /**
     * Send voucher update notification for applied voucher revalidation
     * Routes to appropriate topic based on update type
     */
    public void sendVoucherUpdateNotification(String voucherId, String updateType, Object voucherData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping voucher update notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", voucherId);
            notification.put("voucherId", voucherId);
            notification.put("updateType", updateType);
            notification.put("timestamp", Instant.now());

            // Determine message type and topic based on update type
            String messageType;
            String topic;

            if ("PHIEU_GIAM_GIA_EXPIRED_SCHEDULED".equals(updateType) || "VOUCHER_EXPIRED_SCHEDULED".equals(updateType) || "VOUCHER_EXPIRED".equals(updateType)) {
                messageType = "PHIEU_GIAM_GIA_EXPIRED";
                topic = "/topic/phieu-giam-gia/expired";
            } else {
                // For discount value changes and other updates
                messageType = "PHIEU_GIAM_GIA_UPDATED";
                topic = "/topic/phieu-giam-gia/updated";
            }

            notification.put("type", messageType);

            if (voucherData != null) {
                notification.put("data", voucherData);
                // Add Vietnamese field names for compatibility
                if (voucherData instanceof com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) {
                    com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto dto =
                        (com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) voucherData;
                    notification.put("maPhieuGiamGia", dto.getMaPhieuGiamGia());
                    notification.put("voucherCode", dto.getMaPhieuGiamGia());
                    notification.put("giaTriGiam", dto.getGiaTriGiam());
                    notification.put("discountValue", dto.getGiaTriGiam());
                }
            }

            // Send to appropriate topic based on update type
            publishMessage(VOUCHER_CHANNEL, topic, notification, messageType);

            // Also send to voucher-specific topic for targeted updates
            String destination = "/topic/phieu-giam-gia/" + voucherId;
            publishMessage(VOUCHER_CHANNEL, destination, notification, messageType);

            log.debug("Sent voucher update notification for {}: {} -> {}", voucherId, updateType, topic);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send voucher update notification for {}: {}", voucherId, e.getMessage(), e);
        }
    }

    /**
     * Send new voucher notification for better alternatives detection
     * Publishes to /topic/phieu-giam-gia/new for frontend integration
     */
    public void sendNewVoucherNotification(String voucherId, String creationType, Object voucherData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping new voucher notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", voucherId);
            notification.put("voucherId", voucherId);
            notification.put("creationType", creationType);
            notification.put("timestamp", Instant.now());
            notification.put("type", "PHIEU_GIAM_GIA_NEW");

            if (voucherData != null) {
                notification.put("data", voucherData);
                // Add Vietnamese field names for compatibility
                if (voucherData instanceof com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) {
                    com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto dto =
                        (com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto) voucherData;
                    notification.put("maPhieuGiamGia", dto.getMaPhieuGiamGia());
                    notification.put("voucherCode", dto.getMaPhieuGiamGia());
                    notification.put("giaTriGiam", dto.getGiaTriGiam());
                    notification.put("discountValue", dto.getGiaTriGiam());
                    notification.put("moTa", dto.getMoTa());
                    notification.put("voucherDescription", dto.getMoTa());
                    notification.put("giaTriDonHangToiThieu", dto.getGiaTriDonHangToiThieu());
                    notification.put("minimumOrderValue", dto.getGiaTriDonHangToiThieu());
                }
            }

            // Send to general new voucher topic that frontend subscribes to
            publishMessage(VOUCHER_CHANNEL, "/topic/phieu-giam-gia/new", notification, "PHIEU_GIAM_GIA_NEW");

            // Also send to voucher-specific topic for targeted updates
            String destination = "/topic/phieu-giam-gia/" + voucherId;
            publishMessage(VOUCHER_CHANNEL, destination, notification, "PHIEU_GIAM_GIA_NEW");

            log.debug("Sent new voucher notification for {}: {}", voucherId, creationType);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send new voucher notification for {}: {}", voucherId, e.getMessage(), e);
        }
    }

    /**
     * Send alternative voucher recommendation notification
     * Publishes to /topic/phieu-giam-gia/alternatives for frontend integration
     */
    public void sendAlternativeRecommendation(String expiredVoucherId, Object recommendationData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping alternative recommendation");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", expiredVoucherId);
            notification.put("expiredVoucherId", expiredVoucherId);
            notification.put("timestamp", Instant.now());
            notification.put("type", "PHIEU_GIAM_GIA_ALTERNATIVES");

            if (recommendationData != null) {
                notification.put("data", recommendationData);

                // Extract and map fields for frontend compatibility
                if (recommendationData instanceof com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.AlternativeVoucherRecommendation) {
                    com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.AlternativeVoucherRecommendation recommendation =
                        (com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.AlternativeVoucherRecommendation) recommendationData;

                    notification.put("expiredVoucherCode", recommendation.getExpiredVoucherCode());
                    notification.put("primaryAlternative", recommendation.getPrimaryAlternative());
                    notification.put("additionalAlternatives", recommendation.getAdditionalAlternatives());
                    notification.put("message", recommendation.getMessage());
                }
            }

            // Send to alternatives topic that frontend subscribes to
            publishMessage(VOUCHER_CHANNEL, "/topic/phieu-giam-gia/alternatives", notification, "PHIEU_GIAM_GIA_ALTERNATIVES");

            // Also send to voucher-specific topic for targeted updates
            String destination = "/topic/phieu-giam-gia/" + expiredVoucherId;
            publishMessage(VOUCHER_CHANNEL, destination, notification, "PHIEU_GIAM_GIA_ALTERNATIVES");

            log.debug("Sent alternative recommendation for expired voucher: {}", expiredVoucherId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send alternative recommendation for {}: {}", expiredVoucherId, e.getMessage(), e);
        }
    }

    /**
     * Send better voucher suggestion notification
     * Publishes to /topic/phieu-giam-gia/better-suggestion for frontend integration
     */
    public void sendBetterVoucherSuggestion(String customerId, Object suggestionData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping better voucher suggestion");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("id", customerId);
            notification.put("customerId", customerId);
            notification.put("timestamp", Instant.now());
            notification.put("type", "PHIEU_GIAM_GIA_BETTER_SUGGESTION");

            if (suggestionData != null) {
                notification.put("data", suggestionData);

                // Extract and map fields for frontend compatibility
                if (suggestionData instanceof com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.BetterVoucherSuggestion) {
                    com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.BetterVoucherSuggestion suggestion =
                        (com.lapxpert.backend.phieugiamgia.service.VoucherMonitoringService.BetterVoucherSuggestion) suggestionData;

                    notification.put("currentVoucherId", suggestion.getCurrentVoucherId());
                    notification.put("currentVoucherCode", suggestion.getCurrentVoucherCode());
                    notification.put("betterVoucher", suggestion.getBetterVoucher());
                    notification.put("currentDiscount", suggestion.getCurrentDiscount());
                    notification.put("betterDiscount", suggestion.getBetterDiscount());
                    notification.put("savingsAmount", suggestion.getSavingsAmount());
                    notification.put("message", suggestion.getMessage());
                }
            }

            // Send to better suggestion topic that frontend subscribes to
            publishMessage(VOUCHER_CHANNEL, "/topic/phieu-giam-gia/better-suggestion", notification, "PHIEU_GIAM_GIA_BETTER_SUGGESTION");

            // Also send to customer-specific topic for targeted updates
            String destination = "/topic/phieu-giam-gia/customer/" + customerId;
            publishMessage(VOUCHER_CHANNEL, destination, notification, "PHIEU_GIAM_GIA_BETTER_SUGGESTION");

            log.debug("Sent better voucher suggestion for customer: {}", customerId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send better voucher suggestion for customer {}: {}", customerId, e.getMessage(), e);
        }
    }

    /**
     * Send inventory update notification
     * Vietnamese topic: /topic/ton-kho/{productId}
     */
    public void sendInventoryUpdate(String productId, Object inventoryData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping inventory update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("productId", productId);
            update.put("data", inventoryData);
            update.put("timestamp", Instant.now());

            String destination = "/topic/ton-kho/" + productId;
            publishMessage(GLOBAL_CHANNEL, destination, update, "INVENTORY_UPDATE");

            // Also send to general inventory monitoring topic
            publishMessage(GLOBAL_CHANNEL, "/topic/ton-kho/updates", update, "INVENTORY_UPDATE");

            log.debug("Sent inventory update for product {}", productId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send inventory update for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Send order status update notification
     * Vietnamese topic: /topic/hoa-don/{orderId}
     */
    public void sendOrderUpdate(String orderId, String status, Object orderData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping order update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("orderId", orderId);
            update.put("status", status);
            update.put("data", orderData);
            update.put("timestamp", Instant.now());

            String destination = "/topic/hoa-don/" + orderId;
            publishMessage(GLOBAL_CHANNEL, destination, update, "ORDER_UPDATE");

            // Also send to general order monitoring topics based on action type
            if ("CREATED".equals(status)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/hoa-don/new", update, "ORDER_CREATED");
            } else if ("UPDATED".equals(status)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/hoa-don/updated", update, "ORDER_UPDATED");
            } else {
                publishMessage(GLOBAL_CHANNEL, "/topic/hoa-don/status-changed", update, "ORDER_STATUS_CHANGED");
            }

            log.debug("Sent order update for order {}: {}", orderId, status);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send order update for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Send system notification
     */
    public void sendSystemNotification(String message, String level) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping system notification");
            return;
        }

        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("message", message);
            notification.put("level", level != null ? level : "INFO");
            notification.put("timestamp", Instant.now());

            String destination = "/topic/system/notifications";
            publishMessage(GLOBAL_CHANNEL, destination, notification, "SYSTEM_NOTIFICATION");

            log.debug("Sent system notification: {} ({})", message, level);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send system notification: {}", e.getMessage(), e);
        }
    }



    /**
     * Send product update notification
     * Vietnamese topic: /topic/san-pham/{productId}
     */
    public void sendProductUpdate(String productId, String action, Object productData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping product update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("productId", productId);
            update.put("action", action);
            update.put("data", productData);
            update.put("timestamp", Instant.now());

            String destination = "/topic/san-pham/" + productId;
            publishMessage(GLOBAL_CHANNEL, destination, update, "PRODUCT_UPDATE");

            // Also send to general product monitoring topics based on action type
            if ("CREATED".equals(action)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/san-pham/new", update, "PRODUCT_CREATED");
            } else if ("UPDATED".equals(action)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/san-pham/updated", update, "PRODUCT_UPDATED");
            }

            log.debug("Sent product update for product {}: {}", productId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send product update for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Send user update notification
     * Vietnamese topic: /topic/nguoi-dung/{userId}
     */
    public void sendUserUpdate(String userId, String action, Object userData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping user update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("userId", userId);
            update.put("action", action);
            update.put("data", userData);
            update.put("timestamp", Instant.now());

            String destination = "/topic/nguoi-dung/" + userId;
            publishMessage(GLOBAL_CHANNEL, destination, update, "USER_UPDATE");

            // Also send to general user monitoring topics based on action type
            if ("CREATED".equals(action)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/nguoi-dung/new", update, "USER_CREATED");
            } else if ("UPDATED".equals(action)) {
                publishMessage(GLOBAL_CHANNEL, "/topic/nguoi-dung/updated", update, "USER_UPDATED");
            }

            log.debug("Sent user update for user {}: {}", userId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send user update for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send discount campaign update notification
     * Vietnamese topic: /topic/dot-giam-gia/{campaignId}
     */
    public void sendDiscountCampaignUpdate(String campaignId, String action, Object campaignData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping discount campaign update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("campaignId", campaignId);
            update.put("action", action);
            update.put("data", campaignData);
            update.put("timestamp", Instant.now());

            String destination = "/topic/dot-giam-gia/" + campaignId;
            publishMessage(VOUCHER_CHANNEL, destination, update, "DISCOUNT_CAMPAIGN_UPDATE");

            // Also send to general discount campaign monitoring topics based on action type
            if ("CREATED".equals(action)) {
                publishMessage(VOUCHER_CHANNEL, "/topic/dot-giam-gia/new", update, "DISCOUNT_CAMPAIGN_CREATED");
            } else if ("UPDATED".equals(action)) {
                publishMessage(VOUCHER_CHANNEL, "/topic/dot-giam-gia/updated", update, "DISCOUNT_CAMPAIGN_UPDATED");
            } else if ("STATUS_CHANGED".equals(action)) {
                publishMessage(VOUCHER_CHANNEL, "/topic/dot-giam-gia/status-changed", update, "DISCOUNT_CAMPAIGN_STATUS_CHANGED");
            }

            log.debug("Sent discount campaign update for campaign {}: {}", campaignId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send discount campaign update for campaign {}: {}", campaignId, e.getMessage(), e);
        }
    }

    /**
     * Send statistics update notification
     * Vietnamese topic: /topic/thong-ke/updated
     */
    public void sendStatisticsUpdate(Object statsData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping statistics update");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("data", statsData);
            update.put("timestamp", Instant.now());

            publishMessage(GLOBAL_CHANNEL, "/topic/thong-ke/updated", update, "STATISTICS_UPDATE");

            log.debug("Sent statistics update");

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send statistics update: {}", e.getMessage(), e);
        }
    }

    /**
     * Send dashboard refresh notification
     * Vietnamese topic: /topic/dashboard/refresh
     */
    public void sendDashboardRefresh(Object dashboardData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping dashboard refresh");
            return;
        }

        try {
            Map<String, Object> update = new HashMap<>();
            update.put("data", dashboardData);
            update.put("timestamp", Instant.now());

            publishMessage(GLOBAL_CHANNEL, "/topic/dashboard/refresh", update, "DASHBOARD_REFRESH");

            log.debug("Sent dashboard refresh");

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send dashboard refresh: {}", e.getMessage(), e);
        }
    }

    /**
     * Send AI chat message notification
     * Vietnamese topic: /topic/ai-chat/{sessionId}
     */
    public void sendAiChatMessage(String sessionId, Object chatMessage) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping AI chat message");
            return;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("data", chatMessage);
            message.put("sessionId", sessionId);
            message.put("timestamp", Instant.now());

            String destination = "/topic/ai-chat/" + sessionId;
            publishMessage(AI_CHAT_CHANNEL, destination, message, "AI_CHAT_MESSAGE");

            log.debug("Sent AI chat message to session: {}", sessionId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send AI chat message for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Send AI chat response notification
     * Vietnamese topic: /topic/ai-chat/{sessionId}/response
     */
    public void sendAiChatResponse(String sessionId, Object chatResponse) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping AI chat response");
            return;
        }

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("data", chatResponse);
            response.put("sessionId", sessionId);
            response.put("timestamp", Instant.now());

            String destination = "/topic/ai-chat/" + sessionId + "/response";
            publishMessage(AI_CHAT_CHANNEL, destination, response, "AI_CHAT_RESPONSE");

            log.debug("Sent AI chat response to session: {}", sessionId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send AI chat response for session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Send AI chat status notification (typing, processing, etc.)
     * Vietnamese topic: /topic/ai-chat/{sessionId}/status
     */
    public void sendAiChatStatus(String sessionId, String status, String message) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping AI chat status");
            return;
        }

        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("status", status);
            statusUpdate.put("message", message);
            statusUpdate.put("sessionId", sessionId);
            statusUpdate.put("timestamp", Instant.now());

            String destination = "/topic/ai-chat/" + sessionId + "/status";
            publishMessage(AI_CHAT_CHANNEL, destination, statusUpdate, "AI_CHAT_STATUS");

            log.debug("Sent AI chat status '{}' to session: {}", status, sessionId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send AI chat status for session {}: {}", sessionId, e.getMessage(), e);
        }
    }







    /**
     * Send low stock alert notification
     * Vietnamese topic: /topic/ton-kho/low-stock
     */
    public void sendLowStockAlert(String productId, Object alertData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping low stock alert");
            return;
        }

        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("productId", productId);
            alert.put("data", alertData);
            alert.put("timestamp", Instant.now());

            publishMessage(GLOBAL_CHANNEL, "/topic/ton-kho/low-stock", alert, "LOW_STOCK_ALERT");

            log.debug("Sent low stock alert for product {}", productId);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send low stock alert for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Send custom message to any destination
     */
    public void sendCustomMessage(String destination, Object payload, String messageType) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping custom message");
            return;
        }

        try {
            String channel = determineChannelByDestination(destination);
            publishMessage(channel, destination, payload, messageType);

            log.debug("Sent custom message to {}: {}", destination, messageType);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send custom message to {}: {}", destination, e.getMessage(), e);
        }
    }

    // ==================== ENHANCED CACHE REPLACEMENT METHODS ====================
    // Vietnamese Business Context: Các phương thức thay thế cache nâng cao

    /**
     * Send data list update notification (replaces cache invalidation)
     * Vietnamese topic: /topic/{dataType}/list-updated
     * @param dataType Vietnamese data type (san-pham, phieu-giam-gia, etc.)
     * @param action Action type (CREATED, UPDATED, DELETED, REFRESHED)
     * @param data Updated data payload
     */
    public void sendDataListUpdate(String dataType, String action, Object data) {
        sendDataListUpdate(dataType, action, data, null);
    }

    /**
     * Send data list update notification with metadata
     * Vietnamese topic: /topic/{dataType}/list-updated
     */
    public void sendDataListUpdate(String dataType, String action, Object data, Map<String, Object> metadata) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping data list update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("dataType", dataType);
            payload.put("action", action);
            payload.put("data", data);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            if (metadata != null) {
                payload.put("metadata", metadata);
            }

            String destination = "/topic/" + dataType + "/list-updated";
            String messageType = dataType.toUpperCase().replace("-", "_") + "_LIST_UPDATE";

            publishMessage(DATA_CHANNEL, destination, payload, messageType);

            // Also send to general data monitoring topic
            publishMessage(DATA_CHANNEL, "/topic/data/list-updates", payload, "DATA_LIST_UPDATE");

            log.debug("Sent data list update for {}: {} (action: {})", dataType, messageType, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send data list update for {}: {}", dataType, e.getMessage(), e);
        }
    }

    /**
     * Send search results invalidation notification (replaces searchResults cache)
     * Vietnamese topic: /topic/tim-kiem/invalidate
     * @param searchQuery Search query that needs invalidation
     * @param searchType Type of search (san-pham, phieu-giam-gia, etc.)
     * @param reason Invalidation reason
     */
    public void sendSearchInvalidation(String searchQuery, String searchType, String reason) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping search invalidation");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("searchQuery", searchQuery);
            payload.put("searchType", searchType);
            payload.put("reason", reason != null ? reason : "Dữ liệu tìm kiếm đã được cập nhật");
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/tim-kiem/invalidate";
            publishMessage(SEARCH_CHANNEL, destination, payload, "SEARCH_INVALIDATION");

            // Also send to specific search type topic
            if (searchType != null) {
                String typeDestination = "/topic/tim-kiem/" + searchType + "/invalidate";
                publishMessage(SEARCH_CHANNEL, typeDestination, payload, "SEARCH_TYPE_INVALIDATION");
            }

            log.debug("Sent search invalidation for query '{}' (type: {})", searchQuery, searchType);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send search invalidation for query '{}': {}", searchQuery, e.getMessage(), e);
        }
    }

    /**
     * Send cart data update notification (replaces cartData cache)
     * Vietnamese topic: /topic/gio-hang/{userId}/updated
     * @param userId User ID whose cart was updated
     * @param action Action type (ITEM_ADDED, ITEM_REMOVED, ITEM_UPDATED, CART_CLEARED)
     * @param cartData Updated cart data
     */
    public void sendCartUpdate(String userId, String action, Object cartData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping cart update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("action", action);
            payload.put("cartData", cartData);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/gio-hang/" + userId + "/updated";
            publishMessage(CART_CHANNEL, destination, payload, "CART_UPDATE");

            // Also send to general cart monitoring topic
            publishMessage(CART_CHANNEL, "/topic/gio-hang/updates", payload, "CART_UPDATE");

            log.debug("Sent cart update for user {}: {}", userId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send cart update for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send category update notification (replaces categories cache)
     * Vietnamese topic: /topic/danh-muc/updated
     * @param categoryType Category type (san-pham, thuong-hieu, etc.)
     * @param action Action type (CREATED, UPDATED, DELETED, REORDERED)
     * @param categoryData Updated category data
     */
    public void sendCategoryUpdate(String categoryType, String action, Object categoryData) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping category update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("categoryType", categoryType);
            payload.put("action", action);
            payload.put("categoryData", categoryData);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/danh-muc/updated";
            publishMessage(CATEGORY_CHANNEL, destination, payload, "CATEGORY_UPDATE");

            // Also send to specific category type topic
            if (categoryType != null) {
                String typeDestination = "/topic/danh-muc/" + categoryType + "/updated";
                publishMessage(CATEGORY_CHANNEL, typeDestination, payload, "CATEGORY_TYPE_UPDATE");
            }

            log.debug("Sent category update for type {}: {}", categoryType, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send category update for type {}: {}", categoryType, e.getMessage(), e);
        }
    }

    /**
     * Send system configuration update notification (replaces systemConfig cache)
     * Vietnamese topic: /topic/cau-hinh/updated
     * @param configKey Configuration key that was updated
     * @param configValue New configuration value
     * @param scope Configuration scope (GLOBAL, MODULE, USER)
     */
    public void sendSystemConfigUpdate(String configKey, Object configValue, String scope) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping system config update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("configKey", configKey);
            payload.put("configValue", configValue);
            payload.put("scope", scope != null ? scope : "GLOBAL");
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/cau-hinh/updated";
            publishMessage(CONFIG_CHANNEL, destination, payload, "SYSTEM_CONFIG_UPDATE");

            // Also send to scope-specific topic
            if (scope != null) {
                String scopeDestination = "/topic/cau-hinh/" + scope.toLowerCase() + "/updated";
                publishMessage(CONFIG_CHANNEL, scopeDestination, payload, "CONFIG_SCOPE_UPDATE");
            }

            log.debug("Sent system config update for key {}: {} (scope: {})", configKey, configValue, scope);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send system config update for key {}: {}", configKey, e.getMessage(), e);
        }
    }

    /**
     * Send popular products update notification (replaces popularProducts cache)
     * Vietnamese topic: /topic/san-pham-pho-bien/updated
     * @param timeframe Timeframe for popularity (DAILY, WEEKLY, MONTHLY)
     * @param popularProducts List of popular products data
     */
    public void sendPopularProductsUpdate(String timeframe, Object popularProducts) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping popular products update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("timeframe", timeframe != null ? timeframe : "DAILY");
            payload.put("popularProducts", popularProducts);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/san-pham-pho-bien/updated";
            publishMessage(DATA_CHANNEL, destination, payload, "POPULAR_PRODUCTS_UPDATE");

            // Also send to timeframe-specific topic
            if (timeframe != null) {
                String timeframeDestination = "/topic/san-pham-pho-bien/" + timeframe.toLowerCase() + "/updated";
                publishMessage(DATA_CHANNEL, timeframeDestination, payload, "POPULAR_PRODUCTS_TIMEFRAME_UPDATE");
            }

            log.debug("Sent popular products update for timeframe: {}", timeframe);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send popular products update for timeframe {}: {}", timeframe, e.getMessage(), e);
        }
    }

    /**
     * Send user session update notification (replaces userSessions cache)
     * Vietnamese topic: /topic/phien-nguoi-dung/{userId}/updated
     * @param userId User ID whose session was updated
     * @param sessionData Updated session data
     * @param action Action type (LOGIN, LOGOUT, REFRESH, EXPIRED)
     */
    public void sendUserSessionUpdate(String userId, Object sessionData, String action) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping user session update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("sessionData", sessionData);
            payload.put("action", action != null ? action : "UPDATED");
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/phien-nguoi-dung/" + userId + "/updated";
            publishMessage(SESSION_CHANNEL, destination, payload, "USER_SESSION_UPDATE");

            // Also send to general session monitoring topic
            publishMessage(SESSION_CHANNEL, "/topic/phien-nguoi-dung/updates", payload, "USER_SESSION_UPDATE");

            log.debug("Sent user session update for user {}: {}", userId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send user session update for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Send product ratings update notification (replaces productRatings cache)
     * Vietnamese topic: /topic/danh-gia/{productId}/updated
     * @param productId Product ID whose ratings were updated
     * @param ratingsData Updated ratings data (average, count, distribution)
     * @param action Action type (NEW_RATING, RATING_UPDATED, RATING_DELETED)
     */
    public void sendProductRatingsUpdate(String productId, Object ratingsData, String action) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping product ratings update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("productId", productId);
            payload.put("ratingsData", ratingsData);
            payload.put("action", action != null ? action : "UPDATED");
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/danh-gia/" + productId + "/updated";
            publishMessage(RATING_CHANNEL, destination, payload, "PRODUCT_RATINGS_UPDATE");

            // Also send to general ratings monitoring topic
            publishMessage(RATING_CHANNEL, "/topic/danh-gia/updates", payload, "PRODUCT_RATINGS_UPDATE");

            log.debug("Sent product ratings update for product {}: {}", productId, action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send product ratings update for product {}: {}", productId, e.getMessage(), e);
        }
    }

    /**
     * Send shipping fees update notification (replaces shippingFees cache)
     * Vietnamese topic: /topic/phi-van-chuyen/updated
     * @param addressData Address data that affects shipping calculation
     * @param shippingData Updated shipping fees data
     * @param reason Update reason (RATE_CHANGE, ADDRESS_UPDATE, POLICY_CHANGE)
     */
    public void sendShippingFeesUpdate(Object addressData, Object shippingData, String reason) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping shipping fees update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("addressData", addressData);
            payload.put("shippingData", shippingData);
            payload.put("reason", reason != null ? reason : "Phí vận chuyển đã được cập nhật");
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/phi-van-chuyen/updated";
            publishMessage(SHIPPING_CHANNEL, destination, payload, "SHIPPING_FEES_UPDATE");

            log.debug("Sent shipping fees update: {}", reason);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send shipping fees update: {}", e.getMessage(), e);
        }
    }

    // ==================== BATCH MESSAGING AND THROTTLING ====================
    // Vietnamese Business Context: Gửi tin nhắn hàng loạt và điều tiết

    /**
     * Send batch data updates for multiple entities
     * Vietnamese Business Context: Gửi cập nhật dữ liệu hàng loạt cho nhiều thực thể
     * @param dataType Data type (san-pham, phieu-giam-gia, etc.)
     * @param batchUpdates List of batch update data
     * @param batchId Unique batch identifier
     */
    public void sendBatchDataUpdate(String dataType, List<Map<String, Object>> batchUpdates, String batchId) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping batch data update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("dataType", dataType);
            payload.put("batchId", batchId != null ? batchId : "batch_" + System.currentTimeMillis());
            payload.put("batchSize", batchUpdates.size());
            payload.put("batchUpdates", batchUpdates);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/" + dataType + "/batch-updated";
            String messageType = dataType.toUpperCase().replace("-", "_") + "_BATCH_UPDATE";

            publishMessage(DATA_CHANNEL, destination, payload, messageType);

            // Also send to general batch monitoring topic
            publishMessage(DATA_CHANNEL, "/topic/data/batch-updates", payload, "DATA_BATCH_UPDATE");

            log.debug("Sent batch data update for {}: {} items (batch: {})", dataType, batchUpdates.size(), payload.get("batchId"));

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send batch data update for {}: {}", dataType, e.getMessage(), e);
        }
    }

    /**
     * Send throttled message with rate limiting
     * Vietnamese Business Context: Gửi tin nhắn được điều tiết với giới hạn tốc độ
     * @param destination Message destination
     * @param payload Message payload
     * @param messageType Message type
     * @param throttleKey Throttle key for rate limiting
     * @param throttleIntervalMs Throttle interval in milliseconds
     */
    public void sendThrottledMessage(String destination, Object payload, String messageType,
                                   String throttleKey, long throttleIntervalMs) {
        // Enhanced throttling implementation with Redis-based distributed throttling
        // Prevents message spam during cache removal migration

        try {
            // Check if message should be throttled using Redis
            String throttleRedisKey = "throttle:" + throttleKey;
            Boolean isThrottled = redisTemplate.hasKey(throttleRedisKey);

            if (Boolean.TRUE.equals(isThrottled)) {
                log.debug("Message throttled for key {}: skipping duplicate message", throttleKey);
                return;
            }

            Map<String, Object> enhancedPayload = new HashMap<>();
            if (payload instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payloadMap = (Map<String, Object>) payload;
                enhancedPayload.putAll(payloadMap);
            } else {
                enhancedPayload.put("data", payload);
            }

            enhancedPayload.put("throttled", true);
            enhancedPayload.put("throttleKey", throttleKey);
            enhancedPayload.put("throttleInterval", throttleIntervalMs);
            enhancedPayload.put("timestamp", Instant.now());
            enhancedPayload.put("sequenceNumber", generateSequenceNumber());

            String channel = determineChannelByDestination(destination);
            publishMessage(channel, destination, enhancedPayload, messageType);

            // Set throttle key in Redis with expiration
            redisTemplate.opsForValue().set(throttleRedisKey, "1",
                java.time.Duration.ofMillis(throttleIntervalMs));

            log.debug("Sent throttled message to {}: {} (throttle: {}ms)", destination, messageType, throttleIntervalMs);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send throttled message to {}: {}", destination, e.getMessage(), e);
        }
    }

    /**
     * Send product list update with enhanced message structure for cache replacement
     * Vietnamese topic: /topic/san-pham/list-updated
     * @param action Action type (CREATE, UPDATE, DELETE, REFRESH)
     * @param products List of product data
     * @param metadata Additional metadata for frontend coordination
     */
    public void sendProductListUpdate(String action, Object products, Map<String, Object> metadata) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping product list update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", action != null ? action : "REFRESH");
            payload.put("products", products);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());
            payload.put("dataType", "san-pham");

            if (metadata != null) {
                payload.put("metadata", metadata);
            }

            String destination = "/topic/san-pham/list-updated";
            publishMessage(DATA_CHANNEL, destination, payload, "PRODUCT_LIST_UPDATE");

            // Also send to general data monitoring topic
            publishMessage(DATA_CHANNEL, "/topic/data/list-updates", payload, "DATA_LIST_UPDATE");

            log.debug("Sent product list update: {} (action: {})", "PRODUCT_LIST_UPDATE", action);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send product list update: {}", e.getMessage(), e);
        }
    }

    /**
     * Send search result update notification for cache replacement
     * Vietnamese topic: /topic/tim-kiem/results-updated
     * @param searchQuery Search query
     * @param searchType Search type (san-pham, phieu-giam-gia, etc.)
     * @param results Updated search results
     */
    public void sendSearchResultUpdate(String searchQuery, String searchType, Object results) {
        if (!integrationEnabled) {
            log.debug("WebSocket integration disabled - skipping search result update");
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("searchQuery", searchQuery);
            payload.put("searchType", searchType);
            payload.put("results", results);
            payload.put("timestamp", Instant.now());
            payload.put("sequenceNumber", generateSequenceNumber());

            String destination = "/topic/tim-kiem/results-updated";
            publishMessage(SEARCH_CHANNEL, destination, payload, "SEARCH_RESULT_UPDATE");

            // Also send to specific search type topic
            if (searchType != null) {
                String typeDestination = "/topic/tim-kiem/" + searchType + "/results-updated";
                publishMessage(SEARCH_CHANNEL, typeDestination, payload, "SEARCH_TYPE_RESULT_UPDATE");
            }

            log.debug("Sent search result update for query '{}' (type: {})", searchQuery, searchType);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to send search result update for query '{}': {}", searchQuery, e.getMessage(), e);
        }
    }

    /**
     * Core method to publish message to Redis Pub/Sub with transaction coordination
     * Vietnamese Business Context: Phương thức cốt lõi để xuất bản tin nhắn đến Redis Pub/Sub với điều phối giao dịch
     */
    private void publishMessage(String channel, String destination, Object payload, String messageType) {
        // Check if we're in a transaction context
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Queue message for after-commit delivery
            queueMessageForTransaction(channel, destination, payload, messageType);
        } else {
            // Send immediately if not in transaction
            sendMessageImmediately(channel, destination, payload, messageType);
        }
    }

    /**
     * Queue message for transaction-aware delivery
     * Vietnamese Business Context: Xếp hàng tin nhắn để gửi nhận biết giao dịch
     */
    private void queueMessageForTransaction(String channel, String destination, Object payload, String messageType) {
        try {
            // Initialize queue for this transaction if needed
            List<QueuedWebSocketMessage> queue = transactionMessageQueue.get();
            if (queue == null) {
                queue = new ArrayList<>();
                transactionMessageQueue.set(queue);

                // Register transaction synchronization for message delivery
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        deliverQueuedMessages();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) {
                            handleTransactionRollback();
                        }
                        // Clean up thread local
                        transactionMessageQueue.remove();
                    }
                });
            }

            // Add message to queue
            queue.add(new QueuedWebSocketMessage(channel, destination, payload, messageType));
            queuedMessages.incrementAndGet();

            log.debug("Queued WebSocket message for after-commit delivery: destination={}", destination);

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Failed to queue message for transaction: destination={}, error={}", destination, e.getMessage(), e);
            // Fallback to immediate delivery
            sendMessageImmediately(channel, destination, payload, messageType);
        }
    }

    /**
     * Send message immediately without transaction coordination
     */
    private void sendMessageImmediately(String channel, String destination, Object payload, String messageType) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("destination", destination);
            message.put("payload", payload);
            message.put("messageType", messageType);
            message.put("sourceService", "MAIN_APPLICATION");
            message.put("timestamp", Instant.now());

            String messageJson = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(channel, messageJson);
            totalMessagesSent.incrementAndGet();

            log.debug("Published message immediately to Redis channel {}: destination={}", channel, destination);

        } catch (JsonProcessingException e) {
            sendErrors.incrementAndGet();
            log.error("Error serializing message for Redis: {}", e.getMessage(), e);
        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Error publishing message to Redis channel {}: {}", channel, e.getMessage(), e);
        }
    }

    /**
     * Deliver all queued messages after transaction commit
     * Vietnamese Business Context: Gửi tất cả tin nhắn đã xếp hàng sau khi giao dịch commit
     */
    private void deliverQueuedMessages() {
        List<QueuedWebSocketMessage> queue = transactionMessageQueue.get();
        if (queue == null || queue.isEmpty()) {
            return;
        }

        try {
            log.debug("Delivering {} queued WebSocket messages after transaction commit", queue.size());

            for (QueuedWebSocketMessage queuedMessage : queue) {
                try {
                    sendMessageImmediately(
                        queuedMessage.channel,
                        queuedMessage.destination,
                        queuedMessage.payload,
                        queuedMessage.messageType
                    );

                    log.debug("Delivered queued message: destination={}, queued_at={}",
                        queuedMessage.destination, queuedMessage.queuedAt);

                } catch (Exception e) {
                    sendErrors.incrementAndGet();
                    log.error("Failed to deliver queued message: destination={}, error={}",
                        queuedMessage.destination, e.getMessage(), e);
                }
            }

            log.info("Successfully delivered {} WebSocket messages after transaction commit", queue.size());

        } catch (Exception e) {
            sendErrors.incrementAndGet();
            log.error("Error delivering queued WebSocket messages: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle transaction rollback by discarding queued messages
     * Vietnamese Business Context: Xử lý rollback giao dịch bằng cách loại bỏ tin nhắn đã xếp hàng
     */
    private void handleTransactionRollback() {
        List<QueuedWebSocketMessage> queue = transactionMessageQueue.get();
        if (queue != null && !queue.isEmpty()) {
            transactionRollbacks.incrementAndGet();
            log.warn("Transaction rolled back - discarding {} queued WebSocket messages", queue.size());

            // Log discarded messages for debugging
            for (QueuedWebSocketMessage message : queue) {
                log.debug("Discarded WebSocket message due to rollback: destination={}, queued_at={}",
                    message.destination, message.queuedAt);
            }
        }
    }

    /**
     * Generate sequence number for message ordering
     * Vietnamese Business Context: Tạo số thứ tự cho việc sắp xếp tin nhắn
     */
    private Long generateSequenceNumber() {
        return messageSequenceGenerator.incrementAndGet();
    }

    /**
     * Determine appropriate Redis channel based on destination
     * Enhanced for cache replacement with dedicated channels
     */
    private String determineChannelByDestination(String destination) {
        if (destination.contains("/gia-san-pham/") || destination.contains("/san-pham/")) {
            return PRICE_CHANNEL;
        } else if (destination.contains("/phieu-giam-gia/") || destination.contains("/dot-giam-gia/") || destination.contains("/voucher/")) {
            return VOUCHER_CHANNEL;
        } else if (destination.contains("/chatbox/")) {
            return CHATBOX_CHANNEL;
        } else if (destination.contains("/tim-kiem/")) {
            return SEARCH_CHANNEL;
        } else if (destination.contains("/gio-hang/")) {
            return CART_CHANNEL;
        } else if (destination.contains("/danh-muc/")) {
            return CATEGORY_CHANNEL;
        } else if (destination.contains("/cau-hinh/")) {
            return CONFIG_CHANNEL;
        } else if (destination.contains("/danh-gia/")) {
            return RATING_CHANNEL;
        } else if (destination.contains("/phi-van-chuyen/")) {
            return SHIPPING_CHANNEL;
        } else if (destination.contains("/phien-nguoi-dung/")) {
            return SESSION_CHANNEL;
        } else if (destination.contains("/ai-chat/") || destination.contains("/tro-ly-ai/")) {
            return AI_CHAT_CHANNEL;
        } else if (destination.contains("/list-updated") || destination.contains("/san-pham-pho-bien/") ||
                   destination.contains("/data/")) {
            return DATA_CHANNEL;
        } else {
            return GLOBAL_CHANNEL;
        }
    }

    /**
     * Get integration metrics with transaction-aware messaging statistics
     */
    public IntegrationMetrics getMetrics() {
        return new IntegrationMetrics(
                totalMessagesSent.get(),
                sendErrors.get(),
                queuedMessages.get(),
                transactionRollbacks.get(),
                integrationEnabled,
                Instant.now()
        );
    }

    /**
     * Check if there are pending messages in the current transaction
     * Vietnamese Business Context: Kiểm tra xem có tin nhắn đang chờ trong giao dịch hiện tại không
     */
    public boolean hasPendingMessages() {
        List<QueuedWebSocketMessage> queue = transactionMessageQueue.get();
        return queue != null && !queue.isEmpty();
    }

    /**
     * Get count of pending messages in current transaction
     */
    public int getPendingMessageCount() {
        List<QueuedWebSocketMessage> queue = transactionMessageQueue.get();
        return queue != null ? queue.size() : 0;
    }

    /**
     * Integration metrics holder with transaction-aware messaging statistics
     */
    public static class IntegrationMetrics {
        private final long totalMessagesSent;
        private final long sendErrors;
        private final long queuedMessages;
        private final long transactionRollbacks;
        private final boolean enabled;
        private final Instant timestamp;

        public IntegrationMetrics(long totalMessagesSent, long sendErrors, long queuedMessages,
                                long transactionRollbacks, boolean enabled, Instant timestamp) {
            this.totalMessagesSent = totalMessagesSent;
            this.sendErrors = sendErrors;
            this.queuedMessages = queuedMessages;
            this.transactionRollbacks = transactionRollbacks;
            this.enabled = enabled;
            this.timestamp = timestamp;
        }

        public long getTotalMessagesSent() { return totalMessagesSent; }
        public long getSendErrors() { return sendErrors; }
        public long getQueuedMessages() { return queuedMessages; }
        public long getTransactionRollbacks() { return transactionRollbacks; }
        public boolean isEnabled() { return enabled; }
        public Instant getTimestamp() { return timestamp; }

        public double getErrorRate() {
            return totalMessagesSent > 0 ? (double) sendErrors / totalMessagesSent : 0.0;
        }

        public double getTransactionSuccessRate() {
            long totalTransactions = queuedMessages + transactionRollbacks;
            return totalTransactions > 0 ? (double) queuedMessages / totalTransactions : 1.0;
        }
    }
}
