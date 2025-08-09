package com.lapxpert.backend.phieugiamgia.service;

import com.lapxpert.backend.common.enums.TrangThaiCampaign;
import com.lapxpert.backend.common.service.VietnamTimeZoneService;
import com.lapxpert.backend.nguoidung.entity.NguoiDung;
import com.lapxpert.backend.nguoidung.service.NguoiDungService;
import com.lapxpert.backend.phieugiamgia.dto.PhieuGiamGiaDto;
import com.lapxpert.backend.phieugiamgia.dto.IntelligentRecommendationResult;
import com.lapxpert.backend.phieugiamgia.dto.VoucherRecommendation;
import com.lapxpert.backend.phieugiamgia.dto.VoucherSuggestionDto;
import com.lapxpert.backend.phieugiamgia.entity.PhieuGiamGia;
import com.lapxpert.backend.phieugiamgia.repository.PhieuGiamGiaRepository;
import com.lapxpert.backend.common.service.WebSocketIntegrationService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Advanced voucher monitoring service with real-time validation and notifications.
 * Extends existing PhieuGiamGiaService patterns with WebSocket integration.
 * 
 * Features:
 * - Real-time voucher expiration monitoring (every 10 minutes)
 * - WebSocket notifications for expired/new vouchers
 * - Alternative voucher recommendations
 * - Integration with existing intelligent recommendation system
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherMonitoringService {
    
    private final PhieuGiamGiaRepository phieuGiamGiaRepository;
    private final PhieuGiamGiaService phieuGiamGiaService;
    private final VietnamTimeZoneService vietnamTimeZoneService;
    private final WebSocketIntegrationService webSocketIntegrationService;
    private final NguoiDungService nguoiDungService;
    private final VoucherSuggestionEngine voucherSuggestionEngine;
    
    // Track last monitoring state to detect changes
    private final Map<Long, TrangThaiCampaign> lastKnownVoucherStates = new ConcurrentHashMap<>();

    // Flag to track if initial state has been populated
    private volatile boolean initialStatePopulated = false;

    /**
     * Initialize voucher state tracking on service startup
     * This prevents all existing vouchers from being flagged as "new" on first run
     */
    @PostConstruct
    public void initializeVoucherStateTracking() {
        try {
            log.info("Initializing voucher state tracking...");

            List<PhieuGiamGia> allVouchers = phieuGiamGiaRepository.findAll();
            for (PhieuGiamGia voucher : allVouchers) {
                // Calculate actual status based on current time
                TrangThaiCampaign actualStatus = PhieuGiamGia.fromDates(
                    voucher.getNgayBatDau(),
                    voucher.getNgayKetThuc()
                );
                lastKnownVoucherStates.put(voucher.getId(), actualStatus);
            }

            initialStatePopulated = true;
            log.info("Initialized voucher state tracking for {} vouchers", allVouchers.size());

        } catch (Exception e) {
            log.error("Failed to initialize voucher state tracking", e);
        }
    }

    /**
     * Scheduled voucher monitoring job - runs every 2 minutes for critical voucher operations
     * Monitors voucher status changes and sends real-time notifications
     * Reduced from 10 minutes to 2 minutes for better responsiveness
     */
    @Scheduled(cron = "0 */2 * * * ?") // Every 2 minutes for critical voucher operations
    @Transactional(readOnly = true)
    public void monitorVoucherChanges() {
        try {
            // Skip monitoring if initial state not populated
            if (!initialStatePopulated) {
                log.debug("Skipping voucher monitoring - initial state not yet populated");
                return;
            }

            log.debug("Starting voucher monitoring cycle");

            // Use Vietnam timezone for business logic consistency
            Instant currentTime = vietnamTimeZoneService.getCurrentVietnamTime().toInstant();

            // Step 1: Find and notify expired vouchers
            List<PhieuGiamGia> recentlyExpiredVouchers = findRecentlyExpiredVouchers(currentTime);
            recentlyExpiredVouchers.forEach(this::notifyVoucherExpiration);

            // Step 2: Find and notify newly activated vouchers
            List<PhieuGiamGia> newlyActivatedVouchers = findNewlyActivatedVouchers(currentTime);
            newlyActivatedVouchers.forEach(this::notifyNewVoucher);

            // Step 3: Update tracking state BEFORE next cycle (moved before alternatives check)
            updateVoucherStateTracking();

            // Step 4: Voucher monitoring cycle completed
            // Note: Better voucher alternatives are handled by:
            // - checkBetterVoucherForCustomer() for customer-specific recommendations
            // - detectBetterVouchers() for order-specific suggestions
            // - sendAlternativeVoucherRecommendations() for expired voucher alternatives

            log.info("Voucher monitoring completed - Expired: {}, New: {}",
                    recentlyExpiredVouchers.size(), newlyActivatedVouchers.size());

        } catch (Exception e) {
            log.error("Error during voucher monitoring cycle", e);
        }
    }
    
    /**
     * Find vouchers that have recently expired (changed from DA_DIEN_RA to KET_THUC)
     */
    private List<PhieuGiamGia> findRecentlyExpiredVouchers(Instant currentTime) {
        // Find vouchers that should be expired but still marked as active
        List<PhieuGiamGia> expiredVouchers = phieuGiamGiaRepository.findExpiredCampaigns(
            currentTime, TrangThaiCampaign.KET_THUC);
        
        // Filter to only include vouchers that were previously active
        return expiredVouchers.stream()
            .filter(voucher -> {
                TrangThaiCampaign lastState = lastKnownVoucherStates.get(voucher.getId());
                return lastState == TrangThaiCampaign.DA_DIEN_RA;
            })
            .toList();
    }
    
    /**
     * Find vouchers that have recently been activated (changed from CHUA_DIEN_RA to DA_DIEN_RA)
     * Fixed logic to prevent existing vouchers from being flagged as new
     */
    private List<PhieuGiamGia> findNewlyActivatedVouchers(Instant currentTime) {
        // Find vouchers that should be active now based on their start/end dates
        List<PhieuGiamGia> currentlyActiveVouchers = phieuGiamGiaRepository.findByTrangThai(TrangThaiCampaign.DA_DIEN_RA);

        // Filter to only include vouchers that have actually transitioned to active state
        return currentlyActiveVouchers.stream()
            .filter(voucher -> {
                TrangThaiCampaign lastState = lastKnownVoucherStates.get(voucher.getId());

                // Only consider as "newly activated" if:
                // 1. We have previous state tracking (not null - prevents false positives on startup)
                // 2. Previous state was CHUA_DIEN_RA (not started)
                // 3. Current state is DA_DIEN_RA (active)
                return lastState != null && lastState == TrangThaiCampaign.CHUA_DIEN_RA;
            })
            .toList();
    }
    
    /**
     * Update internal tracking state for all vouchers
     * Improved to maintain state history and calculate actual status based on dates
     */
    private void updateVoucherStateTracking() {
        try {
            List<PhieuGiamGia> allVouchers = phieuGiamGiaRepository.findAll();

            // Don't clear the map - preserve existing state for comparison
            // Only update states that have actually changed
            int updatedCount = 0;

            for (PhieuGiamGia voucher : allVouchers) {
                // Calculate actual status based on current time and dates
                TrangThaiCampaign actualStatus = PhieuGiamGia.fromDates(
                    voucher.getNgayBatDau(),
                    voucher.getNgayKetThuc()
                );

                TrangThaiCampaign previousState = lastKnownVoucherStates.get(voucher.getId());

                // Update tracking state with actual calculated status
                lastKnownVoucherStates.put(voucher.getId(), actualStatus);

                if (previousState != actualStatus) {
                    updatedCount++;
                    log.debug("Voucher {} state changed: {} -> {}",
                        voucher.getMaPhieuGiamGia(), previousState, actualStatus);
                }
            }

            // Remove tracking for deleted vouchers
            Set<Long> currentVoucherIds = allVouchers.stream()
                .map(PhieuGiamGia::getId)
                .collect(Collectors.toSet());

            lastKnownVoucherStates.entrySet().removeIf(entry ->
                !currentVoucherIds.contains(entry.getKey()));

            log.debug("Updated voucher state tracking - Total: {}, Changed: {}",
                allVouchers.size(), updatedCount);

        } catch (Exception e) {
            log.error("Error updating voucher state tracking", e);
        }
    }
    
    /**
     * Send WebSocket notification for expired voucher
     * Delegates to WebSocketIntegrationService for consistency
     */
    private void notifyVoucherExpiration(PhieuGiamGia voucher) {
        try {
            log.warn("🚨 VOUCHER EXPIRED: {} - Phiếu giảm giá '{}' đã hết hạn",
                voucher.getMaPhieuGiamGia(), voucher.getMaPhieuGiamGia());

            // Delegate to WebSocketIntegrationService for consistent messaging
            webSocketIntegrationService.sendVoucherUpdateNotification(
                voucher.getId().toString(),
                "PHIEU_GIAM_GIA_EXPIRED_SCHEDULED",
                phieuGiamGiaService.toDto(voucher)
            );

            // Also send alternative recommendations
            sendAlternativeVoucherRecommendations(voucher);

            log.info("Sent expiration notification for voucher: {}", voucher.getMaPhieuGiamGia());

        } catch (Exception e) {
            log.error("Failed to send expiration notification for voucher: {}", voucher.getMaPhieuGiamGia(), e);
        }
    }
    
    /**
     * Send WebSocket notification for newly activated voucher
     * Delegates to WebSocketIntegrationService for consistency
     */
    private void notifyNewVoucher(PhieuGiamGia voucher) {
        try {
            log.info("🎉 NEW VOUCHER ACTIVATED: {} - Phiếu giảm giá mới '{}' đã có hiệu lực",
                voucher.getMaPhieuGiamGia(), voucher.getMaPhieuGiamGia());

            // Delegate to WebSocketIntegrationService for consistent messaging
            webSocketIntegrationService.sendNewVoucherNotification(
                voucher.getId().toString(),
                "PHIEU_GIAM_GIA_ACTIVATED_SCHEDULED",
                phieuGiamGiaService.toDto(voucher)
            );

            log.info("Sent new voucher notification for: {}", voucher.getMaPhieuGiamGia());

        } catch (Exception e) {
            log.error("Failed to send new voucher notification for: {}", voucher.getMaPhieuGiamGia(), e);
        }
    }
    
    /**
     * Send alternative voucher recommendations when a voucher expires
     */
    private void sendAlternativeVoucherRecommendations(PhieuGiamGia expiredVoucher) {
        try {
            // Use existing intelligent recommendation system to find alternatives
            // We'll use a sample order total based on the expired voucher's minimum requirement
            BigDecimal sampleOrderTotal = expiredVoucher.getGiaTriDonHangToiThieu() != null 
                ? expiredVoucher.getGiaTriDonHangToiThieu() 
                : BigDecimal.valueOf(1000000); // Default 1M VND
            
            IntelligentRecommendationResult recommendations =
                phieuGiamGiaService.getIntelligentVoucherRecommendations(null, sampleOrderTotal, null);
            
            if (recommendations.isHasRecommendations()) {
                AlternativeVoucherRecommendation alternativeRecommendation = AlternativeVoucherRecommendation.builder()
                    .expiredVoucherId(expiredVoucher.getId())
                    .expiredVoucherCode(expiredVoucher.getMaPhieuGiamGia())
                    .primaryAlternative(mapToAlternativeVoucher(recommendations.getPrimaryRecommendation()))
                    .additionalAlternatives(recommendations.getAlternativeRecommendations().stream()
                        .map(this::mapToAlternativeVoucher)
                        .toList())
                    .message("Tìm thấy " + (1 + recommendations.getAlternativeRecommendations().size()) + 
                            " phiếu giảm giá thay thế cho '" + expiredVoucher.getMaPhieuGiamGia() + "'")
                    .timestamp(getCurrentVietnamTimeString())
                    .build();
                
                // Send alternative recommendations via WebSocketIntegrationService
                webSocketIntegrationService.sendAlternativeRecommendation(
                    expiredVoucher.getId().toString(),
                    alternativeRecommendation
                );

                // Log the alternative recommendations
                log.info("💡 ALTERNATIVE VOUCHERS for {}: Primary: {}, Additional: {}",
                    expiredVoucher.getMaPhieuGiamGia(),
                    alternativeRecommendation.getPrimaryAlternative() != null ?
                        alternativeRecommendation.getPrimaryAlternative().getVoucherCode() : "None",
                    alternativeRecommendation.getAdditionalAlternatives().size());

                log.info("Sent alternative recommendations for expired voucher: {}", expiredVoucher.getMaPhieuGiamGia());
            }
            
        } catch (Exception e) {
            log.error("Failed to send alternative recommendations for voucher: {}", expiredVoucher.getMaPhieuGiamGia(), e);
        }
    }



    /**
     * Check for better voucher for a specific customer and order
     * Reuses the existing getBestVoucher API pattern from detectBetterVouchers in OrderCreate.vue
     */
    public void checkBetterVoucherForCustomer(Long customerId, BigDecimal orderTotal, String currentVoucherCode) {
        try {
            log.debug("Checking better voucher for customer {} with order total {}", customerId, orderTotal);

            // Get the best voucher for this customer and order total
            // This reuses the same logic as the frontend detectBetterVouchers function
            PhieuGiamGiaService.BestVoucherResult bestVoucherResult = phieuGiamGiaService.findBestVoucher(customerId, orderTotal);

            if (bestVoucherResult.isFound()) {
                PhieuGiamGiaDto bestVoucher = bestVoucherResult.getVoucher();
                BigDecimal bestDiscount = bestVoucherResult.getDiscountAmount();

                // Check if we have a current voucher to compare against
                if (currentVoucherCode != null && !currentVoucherCode.isEmpty()) {
                    // Find current voucher details
                    Optional<PhieuGiamGia> currentVoucherOpt = phieuGiamGiaRepository.findByMaPhieuGiamGia(currentVoucherCode);
                    if (currentVoucherOpt.isPresent()) {
                        PhieuGiamGia currentVoucherEntity = currentVoucherOpt.get();
                        PhieuGiamGiaDto currentVoucher = phieuGiamGiaService.toDto(currentVoucherEntity);
                        // Use the public calculateDiscountAmount method
                        BigDecimal currentDiscount = phieuGiamGiaService.calculateDiscountAmount(currentVoucherEntity, orderTotal);

                        // Check if the best voucher is different and better
                        if (!bestVoucher.getMaPhieuGiamGia().equals(currentVoucherCode) &&
                            bestDiscount.compareTo(currentDiscount) > 0) {

                            BigDecimal savingsAmount = bestDiscount.subtract(currentDiscount);

                            // Create better voucher suggestion
                            BetterVoucherSuggestion suggestion = BetterVoucherSuggestion.builder()
                                .currentVoucherId(currentVoucher.getId())
                                .currentVoucherCode(currentVoucherCode)
                                .betterVoucher(bestVoucher)
                                .currentDiscount(currentDiscount)
                                .betterDiscount(bestDiscount)
                                .savingsAmount(savingsAmount)
                                .message("Tìm thấy voucher tốt hơn tiết kiệm thêm " + formatCurrency(savingsAmount))
                                .timestamp(getCurrentVietnamTimeString())
                                .build();

                            // Send better voucher suggestion via WebSocket
                            webSocketIntegrationService.sendBetterVoucherSuggestion(
                                customerId.toString(),
                                suggestion
                            );

                            log.info("💰 BETTER VOUCHER SUGGESTION for customer {}: {} -> {} (save {})",
                                customerId, currentVoucherCode, bestVoucher.getMaPhieuGiamGia(),
                                formatCurrency(savingsAmount));
                        }
                    }
                } else {
                    // No current voucher - suggest the best available voucher
                    BetterVoucherSuggestion suggestion = BetterVoucherSuggestion.builder()
                        .currentVoucherId(null)
                        .currentVoucherCode(null)
                        .betterVoucher(bestVoucher)
                        .currentDiscount(BigDecimal.ZERO)
                        .betterDiscount(bestDiscount)
                        .savingsAmount(bestDiscount)
                        .message("Tìm thấy voucher giúp tiết kiệm " + formatCurrency(bestDiscount))
                        .timestamp(getCurrentVietnamTimeString())
                        .build();

                    // Send better voucher suggestion via WebSocket
                    webSocketIntegrationService.sendBetterVoucherSuggestion(
                        customerId.toString(),
                        suggestion
                    );

                    log.info("💰 VOUCHER SUGGESTION for customer {}: {} (save {})",
                        customerId, bestVoucher.getMaPhieuGiamGia(), formatCurrency(bestDiscount));
                }
            }

        } catch (Exception e) {
            log.error("Error checking better voucher for customer {}: {}", customerId, e.getMessage(), e);
        }
    }



    /**
     * Event-driven voucher monitoring for immediate notifications
     * Called by PhieuGiamGiaService when vouchers are created/updated
     * This provides immediate notifications without waiting for scheduled monitoring
     */
    public void handleVoucherStateChange(PhieuGiamGia voucher, TrangThaiCampaign oldState, TrangThaiCampaign newState) {
        try {
            log.debug("Handling event-driven voucher state change: {} {} -> {}",
                voucher.getMaPhieuGiamGia(), oldState, newState);

            // Update our tracking state immediately
            lastKnownVoucherStates.put(voucher.getId(), newState);

            // Handle specific state transitions
            if (oldState == TrangThaiCampaign.CHUA_DIEN_RA && newState == TrangThaiCampaign.DA_DIEN_RA) {
                // Voucher activated - send new voucher notification
                notifyNewVoucher(voucher);
                log.info("Event-driven new voucher notification sent for: {}", voucher.getMaPhieuGiamGia());
            } else if (oldState == TrangThaiCampaign.DA_DIEN_RA && newState == TrangThaiCampaign.KET_THUC) {
                // Voucher expired - send expiration notification
                notifyVoucherExpiration(voucher);
                log.info("Event-driven expiration notification sent for: {}", voucher.getMaPhieuGiamGia());
            }

        } catch (Exception e) {
            log.error("Error handling event-driven voucher state change for: {}",
                voucher.getMaPhieuGiamGia(), e);
        }
    }

    /**
     * Detect better vouchers for a specific order context
     * Called by order management system when vouchers are applied
     */
    @Transactional(readOnly = true)
    public List<VoucherSuggestionDto> detectBetterVouchers(
            List<String> currentVoucherCodes,
            Long customerId,
            BigDecimal orderTotal) {

        try {
            log.debug("Detecting better vouchers for customer {} with order total {}", customerId, orderTotal);

            List<VoucherSuggestionDto> suggestions = voucherSuggestionEngine.detectBetterVouchers(
                currentVoucherCodes, customerId, orderTotal);

            if (!suggestions.isEmpty()) {
                log.info("Found {} better voucher suggestions for customer {}", suggestions.size(), customerId);

                // Send suggestions via WebSocket (to be enabled later)
                sendVoucherSuggestions(suggestions, customerId);
            }

            return suggestions;

        } catch (Exception e) {
            log.error("Error detecting better vouchers for customer {}", customerId, e);
            return Collections.emptyList();
        }
    }



    /**
     * Send voucher suggestions via WebSocket
     */
    private void sendVoucherSuggestions(List<VoucherSuggestionDto> suggestions, Long customerId) {
        try {
            // Add timestamp to suggestions
            Instant now = Instant.now();
            suggestions.forEach(suggestion -> suggestion.setTimestamp(now));

            VoucherSuggestionsNotification notification = VoucherSuggestionsNotification.builder()
                .customerId(customerId)
                .suggestions(suggestions)
                .totalSuggestions(suggestions.size())
                .maxSavings(suggestions.stream()
                    .map(VoucherSuggestionDto::getSavingsAmount)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO))
                .message("Tìm thấy voucher tốt hơn cho đơn hàng của bạn")
                .timestamp(getCurrentVietnamTimeString())
                .build();

            // Send voucher suggestions via enhanced WebSocketIntegrationService
            webSocketIntegrationService.sendEnhancedVoucherNotification(
                customerId.toString(),
                "PHIEU_GIAM_GIA",
                "Gợi ý voucher tốt hơn",
                "PHIEU_GIAM_GIA_SUGGESTIONS",
                notification
            );

            // Log the suggestions
            log.info("🎯 VOUCHER SUGGESTIONS: Found {} better alternatives for customer {}",
                suggestions.size(), customerId);

            for (VoucherSuggestionDto suggestion : suggestions) {
                log.info("  - {}: {} (Tiết kiệm: {})",
                    suggestion.getVoucherCode(),
                    suggestion.getMessage(),
                    formatCurrency(suggestion.getSavingsAmount()));
            }

        } catch (Exception e) {
            log.error("Failed to send voucher suggestions for customer: {}", customerId, e);
        }
    }
    
    /**
     * Map VoucherRecommendation to AlternativeVoucherInfo
     */
    private AlternativeVoucherInfo mapToAlternativeVoucher(VoucherRecommendation recommendation) {
        if (recommendation == null) return null;
        
        PhieuGiamGiaDto voucher = recommendation.getVoucher();
        return AlternativeVoucherInfo.builder()
            .voucherId(voucher.getId())
            .voucherCode(voucher.getMaPhieuGiamGia())
            .voucherDescription(voucher.getMoTa())
            .discountValue(voucher.getGiaTriGiam())
            .discountType(voucher.getLoaiGiamGia().name())
            .minimumOrderValue(voucher.getGiaTriDonHangToiThieu())
            .remainingQuantity(voucher.getSoLuongBanDau() - voucher.getSoLuongDaDung())
            .expirationTime(formatVietnamTime(voucher.getNgayKetThuc()))
            .recommendationReason(recommendation.getRecommendationReason())
            .effectivenessScore(recommendation.getEffectivenessScore())
            .build();
    }
    
    /**
     * Format Instant to Vietnam timezone string
     */
    private String formatVietnamTime(Instant instant) {
        return instant.atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }
    
    /**
     * Get current Vietnam time as formatted string
     */
    private String getCurrentVietnamTimeString() {
        return vietnamTimeZoneService.getCurrentVietnamTime()
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
    }

    /**
     * Real-time voucher validation for active orders
     * Can be called by order processing to validate vouchers in real-time
     */
    @Transactional(readOnly = true)
    public VoucherValidationNotification validateVoucherRealTime(String voucherCode, Long customerId, BigDecimal orderTotal) {
        try {
            // Use existing validation logic from PhieuGiamGiaService
            NguoiDung customer = null;
            if (customerId != null) {
                customer = nguoiDungService.findByIdOptional(customerId).orElse(null);
            }

            PhieuGiamGiaService.VoucherValidationResult validationResult =
                phieuGiamGiaService.validateVoucher(voucherCode, customer, orderTotal);

            VoucherValidationNotification notification = VoucherValidationNotification.builder()
                .voucherCode(voucherCode)
                .customerId(customerId)
                .orderTotal(orderTotal)
                .isValid(validationResult.isValid())
                .discountAmount(validationResult.getDiscountAmount())
                .errorMessage(validationResult.getErrorMessage())
                .timestamp(getCurrentVietnamTimeString())
                .build();

            // Send real-time validation result via enhanced WebSocketIntegrationService
            if (customerId != null) {
                webSocketIntegrationService.sendEnhancedVoucherNotification(
                    customerId.toString(),
                    "PHIEU_GIAM_GIA",
                    "Kết quả kiểm tra voucher real-time",
                    "PHIEU_GIAM_GIA_VALIDATION",
                    notification
                );
            }

            return notification;

        } catch (Exception e) {
            log.error("Error during real-time voucher validation for code: {}", voucherCode, e);
            return VoucherValidationNotification.builder()
                .voucherCode(voucherCode)
                .customerId(customerId)
                .orderTotal(orderTotal)
                .isValid(false)
                .errorMessage("Lỗi hệ thống khi kiểm tra phiếu giảm giá")
                .timestamp(getCurrentVietnamTimeString())
                .build();
        }
    }

    // ==================== WebSocket Message DTOs ====================
    // Note: Basic WebSocket messaging now delegated to WebSocketIntegrationService
    // Keeping specialized DTOs for complex recommendation data structures

    /**
     * DTO for alternative voucher recommendations
     */
    @lombok.Builder
    @lombok.Data
    public static class AlternativeVoucherRecommendation {
        private Long expiredVoucherId;
        private String expiredVoucherCode;
        private AlternativeVoucherInfo primaryAlternative;
        private List<AlternativeVoucherInfo> additionalAlternatives;
        private String message;
        private String timestamp;
    }

    /**
     * DTO for individual alternative voucher information
     */
    @lombok.Builder
    @lombok.Data
    public static class AlternativeVoucherInfo {
        private Long voucherId;
        private String voucherCode;
        private String voucherDescription;
        private BigDecimal discountValue;
        private String discountType;
        private BigDecimal minimumOrderValue;
        private Integer remainingQuantity;
        private String expirationTime;
        private String recommendationReason;
        private Double effectivenessScore;
    }

    /**
     * DTO for real-time voucher validation notifications
     * Used for specialized validation responses
     */
    @Builder
    @Data
    public static class VoucherValidationNotification {
        private String voucherCode;
        private Long customerId;
        private BigDecimal orderTotal;
        private Boolean isValid;
        private BigDecimal discountAmount;
        private String errorMessage;
        private String timestamp;
    }

    /**
     * DTO for voucher suggestions notifications
     */
    @Builder
    @Data
    public static class VoucherSuggestionsNotification {
        private Long customerId;
        private List<VoucherSuggestionDto> suggestions;
        private Integer totalSuggestions;
        private BigDecimal maxSavings;
        private String message;
        private String timestamp;
    }

    /**
     * DTO for better voucher suggestion notifications
     */
    @Builder
    @Data
    public static class BetterVoucherSuggestion {
        private Long currentVoucherId;
        private String currentVoucherCode;
        private Object betterVoucher; // PhieuGiamGiaDto
        private BigDecimal currentDiscount;
        private BigDecimal betterDiscount;
        private BigDecimal savingsAmount;
        private String message;
        private String timestamp;
    }

    /**
     * Format currency for display
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0 ₫";
        return String.format("%,.0f ₫", amount);
    }
}
