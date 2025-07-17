package com.lapxpert.backend.sanpham.service;

import com.lapxpert.backend.common.service.WebSocketIntegrationService;
import com.lapxpert.backend.sanpham.entity.SanPhamChiTietAuditHistory;
import com.lapxpert.backend.sanpham.event.PriceChangeEvent;
import com.lapxpert.backend.sanpham.repository.SanPhamChiTietAuditHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Service for handling real-time price change notifications.
 *
 * MIGRATION NOTICE: Updated to use WebSocket Integration Service for dedicated WebSocket microservice.
 * This service now publishes messages via Redis Pub/Sub to the WebSocket service for horizontal scaling.
 *
 * Extends existing audit infrastructure with real-time messaging capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceChangeNotificationService {

    private final WebSocketIntegrationService webSocketIntegrationService;
    private final SanPhamChiTietAuditHistoryRepository auditHistoryRepository;

    /**
     * Handle price change events with audit trail and real-time notifications.
     * Uses @TransactionalEventListener to ensure audit and notifications occur after transaction commit.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePriceChange(PriceChangeEvent event) {
        long startTime = System.currentTimeMillis();
        try {
            log.info("Processing post-commit price change for variant {}: {} -> {} (original prices)",
                event.getVariantId(), event.getOldPrice(), event.getNewPrice());

            // Create audit trail entry after transaction commit
            createAuditEntry(event);

            // Send real-time WebSocket notification after audit entry
            sendWebSocketNotification(event);

            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Post-commit price change notification completed for variant {} ({}ms)",
                    event.getVariantId(), executionTime);

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to process post-commit price change notification for variant {} ({}ms): {}",
                event.getVariantId(), executionTime, e.getMessage(), e);
        }
    }

    /**
     * Handle price change rollback scenarios.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handlePriceChangeRollback(PriceChangeEvent event) {
        log.warn("Price change transaction rolled back for variant {}: {} -> {} (original prices)",
                event.getVariantId(), event.getOldPrice(), event.getNewPrice());
        // No audit entry or WebSocket notification should be sent as transaction was rolled back
    }

    /**
     * Create audit trail entry for price change
     */
    private void createAuditEntry(PriceChangeEvent event) {
        try {
            // Create audit entry for regular price change
            if (event.hasRegularPriceChanged()) {
                SanPhamChiTietAuditHistory auditEntry = SanPhamChiTietAuditHistory.priceChangeEntry(
                    event.getVariantId(),
                    event.getOldPrice() != null ? event.getOldPrice().toString() : "null",
                    event.getNewPrice() != null ? event.getNewPrice().toString() : "null",
                    event.getNguoiThucHien(),
                    event.getLyDoThayDoi()
                );
                auditHistoryRepository.save(auditEntry);
                
                log.debug("Created audit entry for regular price change: variant {}", event.getVariantId());
            }

            // Create separate audit entry for promotional price change if different
            if (event.hasPromotionalPriceChanged()) {
                String oldPromoPrice = event.getOldPromotionalPrice() != null ? 
                    event.getOldPromotionalPrice().toString() : "null";
                String newPromoPrice = event.getNewPromotionalPrice() != null ? 
                    event.getNewPromotionalPrice().toString() : "null";
                
                String oldValues = String.format("{\"giaKhuyenMai\":\"%s\"}", oldPromoPrice);
                String newValues = String.format("{\"giaKhuyenMai\":\"%s\"}", newPromoPrice);
                
                SanPhamChiTietAuditHistory promoAuditEntry = SanPhamChiTietAuditHistory.builder()
                        .sanPhamChiTietId(event.getVariantId())
                        .hanhDong("PROMOTIONAL_PRICE_CHANGE")
                        .thoiGianThayDoi(event.getTimestamp())
                        .nguoiThucHien(event.getNguoiThucHien() != null ? event.getNguoiThucHien() : "SYSTEM")
                        .lyDoThayDoi(event.getLyDoThayDoi() != null ? event.getLyDoThayDoi() : "Thay đổi giá khuyến mãi")
                        .giaTriCu(oldValues)
                        .giaTriMoi(newValues)
                        .build();
                
                auditHistoryRepository.save(promoAuditEntry);
                
                log.debug("Created audit entry for promotional price change: variant {}", event.getVariantId());
            }

        } catch (Exception e) {
            log.error("Failed to create audit entry for price change: {}", e.getMessage(), e);
        }
    }

    /**
     * Send real-time WebSocket notification via dedicated WebSocket service
     * Enhanced to use comprehensive price update for ProductVariantDialog compatibility
     * Fixed to avoid circular reference serialization issues
     */
    private void sendWebSocketNotification(PriceChangeEvent event) {
        try {
            // Safely extract values to avoid null pointer exceptions
            String variantId = event.getVariantId() != null ? event.getVariantId().toString() : "unknown";
            // FIXED: Use original prices (giaBan) instead of effective prices (giaKhuyenMai) for price change notifications
            Double oldPrice = event.getOldPrice() != null ? event.getOldPrice().doubleValue() : null;
            Double newPrice = event.getNewPrice() != null ? event.getNewPrice().doubleValue() : 0.0;
            String productName = event.getProductName() != null ? event.getProductName() : "Unknown Product";
            String sku = event.getSku() != null ? event.getSku() : "";
            String reason = event.getLyDoThayDoi() != null ? event.getLyDoThayDoi() : "Cập nhật giá sản phẩm";

            // Send comprehensive price update with original prices for frontend compatibility
            webSocketIntegrationService.sendComprehensivePriceUpdate(
                variantId,
                oldPrice,
                newPrice,
                productName,
                sku,
                reason
            );

            log.debug("Sent comprehensive WebSocket notification via integration service for variant {} ({}₫ -> {}₫)",
                     variantId, oldPrice, newPrice);

        } catch (Exception e) {
            log.error("Failed to send WebSocket notification for price change: {}", e.getMessage(), e);
            // Log additional details for debugging - using original prices for consistency
            log.error("Event details - variantId: {}, oldPrice: {}, newPrice: {}, productName: {}",
                     event.getVariantId(), event.getOldPrice(), event.getNewPrice(), event.getProductName());
        }
    }

    /**
     * Send test price update notification (for testing purposes)
     * Updated to use comprehensive WebSocket integration service
     */
    public void sendTestNotification(Long variantId, String sku, String productName,
                                   Double oldPrice, Double newPrice) {
        try {
            // Send comprehensive test notification
            webSocketIntegrationService.sendComprehensivePriceUpdate(
                variantId.toString(),
                oldPrice,
                newPrice,
                productName,
                sku,
                "🧪 Test: Cập nhật giá thử nghiệm"
            );

            log.info("Sent comprehensive test price notification via integration service for variant {}", variantId);

        } catch (Exception e) {
            log.error("Failed to send test price notification: {}", e.getMessage(), e);
        }
    }
}
