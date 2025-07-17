package com.lapxpert.backend.sanpham.service;

import com.lapxpert.backend.hoadon.dto.HoaDonChiTietDto;
import com.lapxpert.backend.sanpham.entity.SerialNumber;
import com.lapxpert.backend.sanpham.entity.SerialNumberAuditHistory;
import com.lapxpert.backend.sanpham.entity.sanpham.SanPhamChiTiet;
import com.lapxpert.backend.sanpham.enums.TrangThaiSerialNumber;
import com.lapxpert.backend.sanpham.repository.SerialNumberAuditHistoryRepository;
import com.lapxpert.backend.sanpham.repository.SerialNumberRepository;
import com.lapxpert.backend.sanpham.repository.SanPhamChiTietRepository;
import com.lapxpert.backend.common.service.DistributedLockService;
import com.lapxpert.backend.common.service.OptimisticLockingService;
import com.lapxpert.backend.common.event.InventoryUpdateEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Service for managing serial numbers and inventory tracking.
 * Provides comprehensive serial number lifecycle management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SerialNumberService {

    private final SerialNumberRepository serialNumberRepository;
    private final SerialNumberAuditHistoryRepository auditHistoryRepository;
    private final SanPhamChiTietRepository sanPhamChiTietRepository;
    private final DistributedLockService distributedLockService;
    private final OptimisticLockingService optimisticLockingService;
    private final ApplicationEventPublisher eventPublisher;

    // Serial Number CRUD Operations

    /**
     * Create a new serial number
     */
    public SerialNumber createSerialNumber(SerialNumber serialNumber, String user, String reason) {
        // Validate serial number doesn't exist
        if (serialNumberRepository.existsBySerialNumberValue(serialNumber.getSerialNumberValue())) {
            throw new IllegalArgumentException("Serial number already exists: " + serialNumber.getSerialNumberValue());
        }

        // Set default status if not provided
        if (serialNumber.getTrangThai() == null) {
            serialNumber.setTrangThai(TrangThaiSerialNumber.AVAILABLE);
        }

        // Save serial number
        SerialNumber savedSerialNumber = serialNumberRepository.save(serialNumber);

        // Create audit trail
        SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.createEntry(
            savedSerialNumber.getId(),
            buildAuditJson(savedSerialNumber),
            user,
            reason != null ? reason : "Tạo serial number mới"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Created serial number: {} for variant: {}",
                savedSerialNumber.getSerialNumberValue(),
                savedSerialNumber.getSanPhamChiTiet().getId());

        // Publish inventory update event for WebSocket notifications
        try {
            SanPhamChiTiet variant = savedSerialNumber.getSanPhamChiTiet();
            Long variantId = variant.getId();

            int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
            int oldAvailableQuantity = newAvailableQuantity - 1;

            InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                    .variantId(variantId)
                    .sku(variant.getSku())
                    .tenSanPham(variant.getSanPham().getTenSanPham())
                    .soLuongTonKhoCu(oldAvailableQuantity)
                    .soLuongTonKhoMoi(newAvailableQuantity)
                    .loaiThayDoi("CREATED")
                    .nguoiThucHien(user)
                    .lyDoThayDoi("Tạo serial number mới: " + savedSerialNumber.getSerialNumberValue())
                    .timestamp(Instant.now())
                    .build();

            eventPublisher.publishEvent(event);
            log.debug("Published inventory update event for variant {} serial number creation", variantId);
        } catch (Exception e) {
            log.error("Failed to publish inventory update event for serial number creation: {}", e.getMessage(), e);
        }

        return savedSerialNumber;
    }

    /**
     * Update serial number
     */
    public SerialNumber updateSerialNumber(Long id, SerialNumber updatedSerialNumber, String user, String reason) {
        SerialNumber existingSerialNumber = serialNumberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serial number not found"));

        String oldValues = buildAuditJson(existingSerialNumber);

        // Update fields including serial number value
        if (updatedSerialNumber.getSerialNumberValue() != null &&
            !updatedSerialNumber.getSerialNumberValue().equals(existingSerialNumber.getSerialNumberValue())) {
            // Check if new serial number value already exists
            if (serialNumberRepository.existsBySerialNumberValue(updatedSerialNumber.getSerialNumberValue())) {
                throw new IllegalArgumentException("Serial number already exists: " + updatedSerialNumber.getSerialNumberValue());
            }
            existingSerialNumber.setSerialNumberValue(updatedSerialNumber.getSerialNumberValue());
        }

        existingSerialNumber.setBatchNumber(updatedSerialNumber.getBatchNumber());
        existingSerialNumber.setNgaySanXuat(updatedSerialNumber.getNgaySanXuat());
        existingSerialNumber.setNgayHetBaoHanh(updatedSerialNumber.getNgayHetBaoHanh());
        existingSerialNumber.setNhaCungCap(updatedSerialNumber.getNhaCungCap());
        existingSerialNumber.setGhiChu(updatedSerialNumber.getGhiChu());

        SerialNumber savedSerialNumber = serialNumberRepository.save(existingSerialNumber);

        // Create audit trail
        SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.updateEntry(
            savedSerialNumber.getId(),
            oldValues,
            buildAuditJson(savedSerialNumber),
            user,
            reason != null ? reason : "Cập nhật thông tin serial number"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Updated serial number: {} for variant: {}",
                savedSerialNumber.getSerialNumberValue(),
                savedSerialNumber.getSanPhamChiTiet().getId());

        return savedSerialNumber;
    }

    /**
     * Delete serial number (soft delete by marking as DISPOSED)
     */
    public void deleteSerialNumber(Long id, String user, String reason) {
        SerialNumber serialNumber = serialNumberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serial number not found"));

        // Only allow deletion if serial number is AVAILABLE or DAMAGED
        if (serialNumber.getTrangThai() != TrangThaiSerialNumber.AVAILABLE &&
            serialNumber.getTrangThai() != TrangThaiSerialNumber.DAMAGED) {
            throw new IllegalStateException("Cannot delete serial number with status: " + serialNumber.getTrangThai());
        }

        String oldValues = buildAuditJson(serialNumber);

        // Soft delete by marking as DISPOSED
        serialNumber.setTrangThai(TrangThaiSerialNumber.DISPOSED);
        SerialNumber savedSerialNumber = serialNumberRepository.save(serialNumber);

        // Create audit trail
        SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.updateEntry(
            savedSerialNumber.getId(),
            oldValues,
            buildAuditJson(savedSerialNumber),
            user,
            reason != null ? reason : "Xóa serial number"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Deleted (soft) serial number: {} for variant: {}",
                savedSerialNumber.getSerialNumberValue(),
                savedSerialNumber.getSanPhamChiTiet().getId());
    }

    /**
     * Change serial number status
     */
    public SerialNumber changeStatus(Long id, TrangThaiSerialNumber newStatus, String user, String reason) {
        SerialNumber serialNumber = serialNumberRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Serial number not found"));

        TrangThaiSerialNumber oldStatus = serialNumber.getTrangThai();
        
        // Validate status transition
        validateStatusTransition(oldStatus, newStatus);

        serialNumber.setTrangThai(newStatus);
        SerialNumber savedSerialNumber = serialNumberRepository.save(serialNumber);

        // Create audit trail
        SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.statusChangeEntry(
            savedSerialNumber.getId(),
            oldStatus.name(),
            newStatus.name(),
            user,
            reason != null ? reason : "Thay đổi trạng thái serial number"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Changed status of serial number {} from {} to {}", 
                serialNumber.getSerialNumberValue(), oldStatus, newStatus);

        return savedSerialNumber;
    }

    // Inventory Management

    /**
     * Get available quantity for a product variant.
     * This includes both AVAILABLE serial numbers and cart-reserved serial numbers
     * (RESERVED with 'CART' channel) as they are valid for order creation.
     *
     * Cart reservations represent items already selected by users in their cart
     * and should be considered as available inventory during order validation.
     *
     * @param variantId The product variant ID
     * @return Total count of available and cart-reserved serial numbers
     */
    @Transactional(readOnly = true)
    public int getAvailableQuantityByVariant(Long variantId) {
        long startTime = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("Bắt đầu tính toán số lượng tồn kho khả dụng cho variant ID: {}", variantId);
        }

        // Count truly available serial numbers
        long availableCount = serialNumberRepository.countAvailableByVariant(variantId);

        // Count cart-reserved serial numbers (valid for order creation)
        long cartReservedCount = serialNumberRepository.countCartReservedByVariant(variantId);

        // Calculate total available quantity
        int totalAvailable = (int) (availableCount + cartReservedCount);

        // Enhanced structured logging for monitoring and troubleshooting
        if (log.isDebugEnabled()) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Kết quả tính toán tồn kho cho variant {}: " +
                     "sẵn_có={}, giỏ_hàng_đặt_trước={}, tổng_khả_dụng={}, thời_gian_thực_thi={}ms",
                     variantId, availableCount, cartReservedCount, totalAvailable, executionTime);
        }

        // Warning logs for edge cases that may indicate data issues
        if (totalAvailable == 0) {
            log.warn("Không có hàng tồn kho khả dụng cho variant {}: available={}, cart-reserved={}",
                    variantId, availableCount, cartReservedCount);
        } else if (cartReservedCount > availableCount * 2) {
            log.warn("Số lượng giỏ hàng đặt trước cao bất thường cho variant {}: cart-reserved={}, available={}",
                    variantId, cartReservedCount, availableCount);
        }

        // Info logging for monitoring inventory levels
        if (log.isInfoEnabled() && totalAvailable <= 5) {
            log.info("Tồn kho thấp cho variant {}: chỉ còn {} sản phẩm khả dụng (available={}, cart-reserved={})",
                    variantId, totalAvailable, availableCount, cartReservedCount);
        }

        return totalAvailable;
    }

    /**
     * Reserve specific serial numbers with fine-grained locking for high concurrency
     * OPTIMIZATION: Uses individual serial number locks instead of variant-level locks
     * to reduce lock contention in high-traffic scenarios
     */
    public List<SerialNumber> reserveSpecificSerialNumbers(List<Long> serialNumberIds, String channel, String orderId, String user) {
        if (serialNumberIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<SerialNumber> reservedSerialNumbers = new ArrayList<>();
        List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();

        // Process each serial number with its own fine-grained lock
        for (Long serialNumberId : serialNumberIds) {
            String lockKey = distributedLockService.getSerialNumberLockKey(serialNumberId);

            SerialNumber reservedSerial = distributedLockService.executeWithLock(lockKey, () -> {
                return optimisticLockingService.executeWithRetry(() -> {
                    SerialNumber serialNumber = serialNumberRepository.findById(serialNumberId)
                            .orElseThrow(() -> new IllegalArgumentException("Serial number not found: " + serialNumberId));

                    // Validate serial number can be reserved
                    if (!serialNumber.isAvailable() && !isCartReservation(serialNumber)) {
                        throw new IllegalArgumentException(
                            String.format("Serial number %s is not available for reservation (status: %s)",
                                         serialNumber.getSerialNumberValue(), serialNumber.getTrangThai())
                        );
                    }

                    // Reserve the serial number
                    if (serialNumber.isReserved() && isCartReservation(serialNumber)) {
                        // Convert cart reservation to order reservation
                        serialNumber.setDonHangDatTruoc(orderId);
                        serialNumber.setKenhDatTruoc(channel);
                        serialNumber.setThoiGianDatTruoc(Instant.now());
                    } else {
                        serialNumber.reserveWithTracking(channel, orderId);
                    }

                    SerialNumber savedSerial = serialNumberRepository.save(serialNumber);

                    // Prepare audit entry
                    SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.reservationEntry(
                        savedSerial.getId(),
                        channel,
                        orderId,
                        user,
                        "Đặt trước serial number cụ thể cho đơn hàng"
                    );
                    auditEntries.add(auditEntry);

                    log.debug("Reserved specific serial number {} for order {} via channel {}",
                             savedSerial.getSerialNumberValue(), orderId, channel);

                    return savedSerial;
                });
            }, 5, 10); // OPTIMIZATION: Very short lock duration for individual serial numbers

            reservedSerialNumbers.add(reservedSerial);
        }

        // Batch save audit entries outside locks
        if (!auditEntries.isEmpty()) {
            auditHistoryRepository.saveAll(auditEntries);
            log.debug("Saved {} audit entries for specific serial number reservations", auditEntries.size());
        }

        // Group by variant for WebSocket events
        Map<Long, List<SerialNumber>> byVariant = reservedSerialNumbers.stream()
                .collect(Collectors.groupingBy(sn -> sn.getSanPhamChiTiet().getId()));

        // Publish WebSocket events per variant
        for (Map.Entry<Long, List<SerialNumber>> entry : byVariant.entrySet()) {
            Long variantId = entry.getKey();
            List<SerialNumber> variantSerials = entry.getValue();

            try {
                SerialNumber firstSerial = variantSerials.get(0);
                SanPhamChiTiet variant = firstSerial.getSanPhamChiTiet();

                int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
                int oldAvailableQuantity = newAvailableQuantity + variantSerials.size();

                InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                        .variantId(variantId)
                        .sku(variant.getSku())
                        .tenSanPham(variant.getSanPham().getTenSanPham())
                        .soLuongTonKhoCu(oldAvailableQuantity)
                        .soLuongTonKhoMoi(newAvailableQuantity)
                        .loaiThayDoi("RESERVED")
                        .nguoiThucHien(user)
                        .lyDoThayDoi("Đặt trước " + variantSerials.size() + " serial number cụ thể cho đơn hàng " + orderId)
                        .timestamp(Instant.now())
                        .build();

                eventPublisher.publishEvent(event);
                log.debug("Published inventory update event for variant {} specific reservations", variantId);
            } catch (Exception e) {
                log.error("Failed to publish inventory update event for specific reservations: {}", e.getMessage(), e);
            }
        }

        log.info("Reserved {} specific serial numbers for order {} via channel {} with fine-grained locking",
                reservedSerialNumbers.size(), orderId, channel);

        return reservedSerialNumbers;
    }

    /**
     * Reserve serial numbers for an order with distributed locking to prevent race conditions
     * OPTIMIZED: Reduced lock duration, batch operations, deferred event publishing
     */
    public List<SerialNumber> reserveSerialNumbers(Long variantId, int quantity, String channel, String orderId, String user) {
        String lockKey = distributedLockService.getInventoryLockKey(variantId);

        // Data to collect for post-lock operations
        final List<SerialNumber> reservedSerialNumbers = new ArrayList<>();
        final List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();
        final AtomicReference<InventoryUpdateEvent> eventToPublish = new AtomicReference<>();

        // Execute critical section with optimized lock duration
        distributedLockService.executeWithLock(lockKey, () -> {
            return optimisticLockingService.executeWithRetry(() -> {
                List<SerialNumber> availableSerialNumbers = serialNumberRepository.findAvailableByVariant(
                    variantId, PageRequest.of(0, quantity)
                );

                if (availableSerialNumbers.size() < quantity) {
                    throw new IllegalArgumentException(
                        String.format("Không đủ hàng tồn kho. Yêu cầu: %d, Có sẵn: %d",
                                     quantity, availableSerialNumbers.size())
                    );
                }

                // OPTIMIZATION: Batch update serial numbers
                List<SerialNumber> toUpdate = new ArrayList<>();
                for (int i = 0; i < quantity; i++) {
                    SerialNumber serialNumber = availableSerialNumbers.get(i);
                    serialNumber.reserveWithTracking(channel, orderId);
                    toUpdate.add(serialNumber);

                    // Prepare audit entry (don't save yet)
                    SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.reservationEntry(
                        serialNumber.getId(),
                        channel,
                        orderId,
                        user,
                        "Đặt trước serial number cho đơn hàng"
                    );
                    auditEntries.add(auditEntry);

                    log.debug("Prepared reservation for serial number {} for order {} via channel {}",
                             serialNumber.getSerialNumberValue(), orderId, channel);
                }

                // OPTIMIZATION: Batch save all serial numbers
                List<SerialNumber> savedSerialNumbers = serialNumberRepository.saveAll(toUpdate);
                reservedSerialNumbers.addAll(savedSerialNumbers);

                // Prepare WebSocket event (don't publish yet)
                if (!reservedSerialNumbers.isEmpty()) {
                    SerialNumber firstSerial = reservedSerialNumbers.get(0);
                    SanPhamChiTiet variant = firstSerial.getSanPhamChiTiet();

                    int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
                    int oldAvailableQuantity = newAvailableQuantity + quantity;

                    InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                            .variantId(variantId)
                            .sku(variant.getSku())
                            .tenSanPham(variant.getSanPham().getTenSanPham())
                            .soLuongTonKhoCu(oldAvailableQuantity)
                            .soLuongTonKhoMoi(newAvailableQuantity)
                            .loaiThayDoi("RESERVED")
                            .nguoiThucHien(user)
                            .lyDoThayDoi("Đặt trước " + quantity + " sản phẩm cho đơn hàng " + orderId)
                            .timestamp(Instant.now())
                            .build();

                    eventToPublish.set(event);
                }

                log.info("Reserved {} serial numbers for order {} via channel {} with optimized distributed lock",
                        quantity, orderId, channel);

                return null; // Return value not used
            });
        }, 10, 20); // OPTIMIZATION: Reduced timeouts - 10s wait, 20s lease

        // OPTIMIZATION: Perform non-critical operations outside the lock
        try {
            // Batch save audit entries
            if (!auditEntries.isEmpty()) {
                auditHistoryRepository.saveAll(auditEntries);
                log.debug("Saved {} audit entries for reservation batch", auditEntries.size());
            }

            // Publish WebSocket event
            InventoryUpdateEvent event = eventToPublish.get();
            if (event != null) {
                eventPublisher.publishEvent(event);
                log.debug("Published inventory update event for variant {} reservation", variantId);
            }
        } catch (Exception e) {
            log.error("Failed to complete post-lock operations for reservation: {}", e.getMessage(), e);
            // Don't fail the entire operation for non-critical post-processing
        }

        return reservedSerialNumbers;
    }

    /**
     * Confirm sale of reserved serial numbers
     * CRITICAL FIX: Enhanced validation to ensure correct serial number IDs are processed
     * OPTIMIZED: Batch operations for better performance
     */
    public void confirmSale(List<Long> serialNumberIds, String orderId, String user) {
        if (serialNumberIds.isEmpty()) {
            log.debug("No serial number IDs provided for sale confirmation");
            return;
        }

        // CRITICAL FIX: Batch fetch all serial numbers with validation
        List<SerialNumber> serialNumbers = serialNumberRepository.findAllById(serialNumberIds);
        if (serialNumbers.size() != serialNumberIds.size()) {
            List<Long> foundIds = serialNumbers.stream().map(SerialNumber::getId).collect(Collectors.toList());
            List<Long> missingIds = serialNumberIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());
            throw new IllegalArgumentException("Serial numbers not found for sale confirmation: " + missingIds);
        }

        // CRITICAL FIX: Validate that serial numbers are reserved for the correct order
        List<SerialNumber> validSerialNumbers = new ArrayList<>();
        List<SerialNumber> invalidSerialNumbers = new ArrayList<>();
        List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();

        for (SerialNumber serialNumber : serialNumbers) {
            // Validate serial number state and order association
            if (!serialNumber.isReserved()) {
                log.warn("Serial number {} is not reserved, cannot confirm sale (current status: {})",
                        serialNumber.getSerialNumberValue(), serialNumber.getTrangThai());
                invalidSerialNumbers.add(serialNumber);
                continue;
            }

            if (!orderId.equals(serialNumber.getDonHangDatTruoc())) {
                log.warn("Serial number {} is reserved for order {} but trying to confirm sale for order {}",
                        serialNumber.getSerialNumberValue(), serialNumber.getDonHangDatTruoc(), orderId);
                invalidSerialNumbers.add(serialNumber);
                continue;
            }

            // Valid serial number - prepare for sale confirmation
            serialNumber.markAsSold();
            validSerialNumbers.add(serialNumber);

            // Prepare audit trail
            SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.saleEntry(
                serialNumber.getId(),
                orderId,
                user,
                String.format("Xác nhận bán serial number cho đơn hàng %s", orderId)
            );
            auditEntries.add(auditEntry);

            log.debug("Prepared sale confirmation for serial number {} (order: {})",
                     serialNumber.getSerialNumberValue(), orderId);
        }

        // CRITICAL FIX: Fail if any serial numbers are invalid to prevent partial sales
        if (!invalidSerialNumbers.isEmpty()) {
            List<String> invalidSerialValues = invalidSerialNumbers.stream()
                .map(sn -> String.format("%s (status: %s, order: %s)",
                    sn.getSerialNumberValue(), sn.getTrangThai(), sn.getDonHangDatTruoc()))
                .collect(Collectors.toList());
            throw new IllegalArgumentException(
                String.format("Cannot confirm sale - invalid serial numbers found: %s", invalidSerialValues));
        }

        // Batch save operations for valid serial numbers
        serialNumberRepository.saveAll(validSerialNumbers);
        auditHistoryRepository.saveAll(auditEntries);

        log.info("Confirmed sale of {} serial numbers for order {} with enhanced validation",
                validSerialNumbers.size(), orderId);

        // Publish inventory update event for WebSocket notifications
        try {
            if (!serialNumberIds.isEmpty()) {
                // Get the first serial number to extract variant information
                SerialNumber firstSerial = serialNumberRepository.findById(serialNumberIds.get(0))
                        .orElse(null);
                if (firstSerial != null) {
                    SanPhamChiTiet variant = firstSerial.getSanPhamChiTiet();
                    Long variantId = variant.getId();

                    int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
                    int oldAvailableQuantity = newAvailableQuantity + serialNumberIds.size();

                    InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                            .variantId(variantId)
                            .sku(variant.getSku())
                            .tenSanPham(variant.getSanPham().getTenSanPham())
                            .soLuongTonKhoCu(oldAvailableQuantity)
                            .soLuongTonKhoMoi(newAvailableQuantity)
                            .loaiThayDoi("SOLD")
                            .nguoiThucHien(user)
                            .lyDoThayDoi("Xác nhận bán " + serialNumberIds.size() + " sản phẩm cho đơn hàng " + orderId)
                            .timestamp(Instant.now())
                            .build();

                    eventPublisher.publishEvent(event);
                    log.debug("Published inventory update event for variant {} sale confirmation", variantId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to publish inventory update event for sale confirmation: {}", e.getMessage(), e);
        }
    }

    /**
     * Release reservations
     * OPTIMIZED: Batch operations for better performance
     */
    public void releaseReservations(List<Long> serialNumberIds, String user, String reason) {
        if (serialNumberIds.isEmpty()) {
            return;
        }

        // Batch fetch all serial numbers
        List<SerialNumber> serialNumbers = serialNumberRepository.findAllById(serialNumberIds);

        // Batch update serial numbers
        List<SerialNumber> toUpdate = new ArrayList<>();
        List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();

        for (SerialNumber serialNumber : serialNumbers) {
            if (serialNumber.isReserved()) {
                serialNumber.releaseReservation();
                toUpdate.add(serialNumber);

                // Prepare audit trail
                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.releaseEntry(
                    serialNumber.getId(),
                    user,
                    reason != null ? reason : "Hủy đặt trước serial number"
                );
                auditEntries.add(auditEntry);

                log.debug("Released reservation for serial number {}", serialNumber.getSerialNumberValue());
            }
        }

        // Batch save operations
        if (!toUpdate.isEmpty()) {
            serialNumberRepository.saveAll(toUpdate);
            auditHistoryRepository.saveAll(auditEntries);
        }

        log.info("Released reservations for {} serial numbers with batch operations", toUpdate.size());

        // Publish inventory update event for WebSocket notifications
        try {
            if (!serialNumberIds.isEmpty()) {
                // Get the first serial number to extract variant information
                SerialNumber firstSerial = serialNumberRepository.findById(serialNumberIds.get(0))
                        .orElse(null);
                if (firstSerial != null) {
                    SanPhamChiTiet variant = firstSerial.getSanPhamChiTiet();
                    Long variantId = variant.getId();

                    int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
                    int oldAvailableQuantity = newAvailableQuantity - serialNumberIds.size();

                    InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                            .variantId(variantId)
                            .sku(variant.getSku())
                            .tenSanPham(variant.getSanPham().getTenSanPham())
                            .soLuongTonKhoCu(oldAvailableQuantity)
                            .soLuongTonKhoMoi(newAvailableQuantity)
                            .loaiThayDoi("RELEASED")
                            .nguoiThucHien(user)
                            .lyDoThayDoi("Hủy đặt trước " + serialNumberIds.size() + " sản phẩm: " + reason)
                            .timestamp(Instant.now())
                            .build();

                    eventPublisher.publishEvent(event);
                    log.debug("Published inventory update event for variant {} reservation release", variantId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to publish inventory update event for reservation release: {}", e.getMessage(), e);
        }
    }

    /**
     * Get reserved serial number IDs for a specific order
     * Replaces InventoryService.getReservedItemsForOrder()
     */
    @Transactional(readOnly = true)
    public List<Long> getReservedSerialNumberIdsForOrder(String orderId) {
        List<SerialNumber> reservedSerialNumbers = serialNumberRepository.findByDonHangDatTruoc(orderId);
        return reservedSerialNumbers.stream()
                .map(SerialNumber::getId)
                .collect(Collectors.toList());
    }

    /**
     * Get all serial numbers associated with a specific order (both RESERVED and SOLD)
     * Used for double confirmation prevention in order payment flow
     */
    @Transactional(readOnly = true)
    public List<SerialNumber> getSerialNumbersByOrderId(String orderId) {
        return serialNumberRepository.findByDonHangDatTruoc(orderId);
    }

    /**
     * Get sold serial numbers for a specific product variant and quantity
     * Replaces InventoryService.getSoldItems()
     */
    @Transactional(readOnly = true)
    public List<SerialNumber> getSoldSerialNumbers(Long variantId, int quantity) {
        List<SerialNumber> soldSerialNumbers = serialNumberRepository.findBySanPhamChiTietIdAndTrangThai(
            variantId, TrangThaiSerialNumber.SOLD);

        // Return only the requested quantity
        return soldSerialNumbers.stream()
                .limit(quantity)
                .collect(Collectors.toList());
    }

    /**
     * Release sold serial numbers back to available status (for refunds)
     * Replaces InventoryService.releaseFromSold()
     * OPTIMIZED: Batch operations for better performance
     */
    @Transactional
    public void releaseFromSold(List<Long> serialNumberIds, String user, String reason) {
        if (serialNumberIds.isEmpty()) {
            return;
        }

        // Batch fetch all serial numbers
        List<SerialNumber> serialNumbers = serialNumberRepository.findAllById(serialNumberIds);

        // Batch update serial numbers
        List<SerialNumber> toUpdate = new ArrayList<>();
        List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();

        for (SerialNumber serialNumber : serialNumbers) {
            if (serialNumber.isSold() || serialNumber.isReturned()) {
                serialNumber.releaseFromSold();
                toUpdate.add(serialNumber);

                // Prepare audit trail
                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.releaseEntry(
                    serialNumber.getId(),
                    user,
                    reason != null ? reason : "Hoàn trả serial number từ trạng thái đã bán"
                );
                auditEntries.add(auditEntry);

                log.debug("Released serial number {} from sold status", serialNumber.getSerialNumberValue());
            }
        }

        // Batch save operations
        if (!toUpdate.isEmpty()) {
            serialNumberRepository.saveAll(toUpdate);
            auditHistoryRepository.saveAll(auditEntries);
        }

        log.info("Released {} serial numbers from sold status with batch operations", toUpdate.size());

        // Publish inventory update event for WebSocket notifications
        try {
            if (!serialNumberIds.isEmpty()) {
                // Get the first serial number to extract variant information
                SerialNumber firstSerial = serialNumberRepository.findById(serialNumberIds.get(0))
                        .orElse(null);
                if (firstSerial != null) {
                    SanPhamChiTiet variant = firstSerial.getSanPhamChiTiet();
                    Long variantId = variant.getId();

                    int newAvailableQuantity = getAvailableQuantityByVariant(variantId);
                    int oldAvailableQuantity = newAvailableQuantity - serialNumberIds.size();

                    InventoryUpdateEvent event = InventoryUpdateEvent.builder()
                            .variantId(variantId)
                            .sku(variant.getSku())
                            .tenSanPham(variant.getSanPham().getTenSanPham())
                            .soLuongTonKhoCu(oldAvailableQuantity)
                            .soLuongTonKhoMoi(newAvailableQuantity)
                            .loaiThayDoi("RESTOCKED")
                            .nguoiThucHien(user)
                            .lyDoThayDoi("Hoàn trả " + serialNumberIds.size() + " sản phẩm từ trạng thái đã bán: " + reason)
                            .timestamp(Instant.now())
                            .build();

                    eventPublisher.publishEvent(event);
                    log.debug("Published inventory update event for variant {} restock from sold", variantId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to publish inventory update event for restock from sold: {}", e.getMessage(), e);
        }
    }

    /**
     * Get available serial numbers for a specific product variant
     * Replaces InventoryService.getAvailableItems()
     */
    @Transactional(readOnly = true)
    public List<SerialNumber> getAvailableSerialNumbers(Long variantId) {
        return serialNumberRepository.findBySanPhamChiTietIdAndTrangThai(
            variantId, TrangThaiSerialNumber.AVAILABLE);
    }

    /**
     * Update the order ID for reserved serial numbers
     * CRITICAL FIX: Enhanced with atomic batch operations to prevent race conditions
     * Replaces InventoryService.updateReservationOrderId()
     */
    @Transactional
    public void updateReservationOrderId(List<Long> serialNumberIds, String oldOrderId, String newOrderId) {
        if (serialNumberIds.isEmpty()) {
            log.debug("No serial number IDs provided for order ID update");
            return;
        }

        // CRITICAL FIX: Batch fetch all serial numbers to reduce database round trips
        List<SerialNumber> serialNumbers = serialNumberRepository.findAllById(serialNumberIds);

        if (serialNumbers.size() != serialNumberIds.size()) {
            List<Long> foundIds = serialNumbers.stream().map(SerialNumber::getId).collect(Collectors.toList());
            List<Long> missingIds = serialNumberIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());
            throw new IllegalArgumentException("Serial numbers not found: " + missingIds);
        }

        // CRITICAL FIX: Validate all serial numbers before making any changes (atomic validation)
        List<SerialNumber> validSerialNumbers = new ArrayList<>();
        List<SerialNumberAuditHistory> auditEntries = new ArrayList<>();

        for (SerialNumber serialNumber : serialNumbers) {
            if (!serialNumber.isReserved()) {
                log.warn("Serial number {} is not reserved, skipping order ID update",
                        serialNumber.getSerialNumberValue());
                continue;
            }

            if (!oldOrderId.equals(serialNumber.getDonHangDatTruoc())) {
                log.warn("Serial number {} is reserved for order {} but expected {}, skipping update",
                        serialNumber.getSerialNumberValue(), serialNumber.getDonHangDatTruoc(), oldOrderId);
                continue;
            }

            // Update order ID atomically
            serialNumber.setDonHangDatTruoc(newOrderId);
            serialNumber.setThoiGianDatTruoc(Instant.now()); // Update timestamp for tracking
            validSerialNumbers.add(serialNumber);

            // Prepare audit trail entry
            String oldValues = String.format("{\"orderId\":\"%s\",\"timestamp\":\"%s\"}",
                oldOrderId, serialNumber.getThoiGianDatTruoc());
            String newValues = String.format("{\"orderId\":\"%s\",\"timestamp\":\"%s\"}",
                newOrderId, Instant.now());
            SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.updateEntry(
                serialNumber.getId(),
                oldValues,
                newValues,
                "system",
                String.format("Cập nhật order ID từ %s thành %s", oldOrderId, newOrderId)
            );
            auditEntries.add(auditEntry);

            log.debug("Prepared order ID update for serial number {} from {} to {}",
                     serialNumber.getSerialNumberValue(), oldOrderId, newOrderId);
        }

        // CRITICAL FIX: Atomic batch save operations to prevent partial updates
        if (!validSerialNumbers.isEmpty()) {
            serialNumberRepository.saveAll(validSerialNumbers);
            auditHistoryRepository.saveAll(auditEntries);

            log.info("Successfully updated order ID for {} serial numbers from {} to {} with atomic operations",
                    validSerialNumbers.size(), oldOrderId, newOrderId);
        } else {
            log.warn("No valid serial numbers found for order ID update from {} to {}", oldOrderId, newOrderId);
        }
    }

    /**
     * Check if sufficient inventory is available for order items
     * Handles both specific serial numbers and general quantity requests
     * Replaces InventoryService.isInventoryAvailable()
     */
    @Transactional(readOnly = true)
    public boolean isInventoryAvailable(List<HoaDonChiTietDto> orderItems) {
        long startTime = System.currentTimeMillis();

        if (orderItems == null || orderItems.isEmpty()) {
            log.warn("Danh sách sản phẩm đơn hàng null hoặc rỗng, không thể kiểm tra tồn kho");
            return false;
        }

        if (log.isDebugEnabled()) {
            log.debug("Bắt đầu kiểm tra tồn kho cho {} sản phẩm trong đơn hàng", orderItems.size());
        }

        // Track validation statistics for monitoring
        int specificSerialNumberItems = 0;
        int generalQuantityItems = 0;
        int validatedItems = 0;

        for (int i = 0; i < orderItems.size(); i++) {
            HoaDonChiTietDto item = orderItems.get(i);

            // Enhanced validation with Vietnamese error messages
            if (item.getSanPhamChiTietId() == null || item.getSoLuong() == null || item.getSoLuong() <= 0) {
                log.warn("Sản phẩm thứ {} không hợp lệ: variantId={}, soLuong={}",
                        i + 1, item.getSanPhamChiTietId(), item.getSoLuong());
                return false;
            }

            if (log.isDebugEnabled()) {
                log.debug("Kiểm tra sản phẩm {}/{}: variantId={}, soLuong={}, serialNumberId={}",
                         i + 1, orderItems.size(), item.getSanPhamChiTietId(), item.getSoLuong(), item.getSerialNumberId());
            }

            // CRITICAL FIX: Check if specific serial numbers are provided
            if (item.getSerialNumberId() != null) {
                specificSerialNumberItems++;

                if (log.isDebugEnabled()) {
                    log.debug("Đường dẫn kiểm tra: Serial number cụ thể với ID: {}", item.getSerialNumberId());
                }

                // Validate specific serial number
                Optional<SerialNumber> serialNumber = serialNumberRepository.findById(item.getSerialNumberId());
                if (serialNumber.isEmpty()) {
                    log.warn("Không tìm thấy serial number với ID {} cho sản phẩm thứ {}",
                            item.getSerialNumberId(), i + 1);
                    return false;
                }

                SerialNumber sn = serialNumber.get();
                if (log.isDebugEnabled()) {
                    log.debug("Tìm thấy serial number: {} với trạng thái: {} cho variant: {}",
                             sn.getSerialNumberValue(), sn.getTrangThai(), sn.getSanPhamChiTiet().getId());
                }

                // Check if serial number belongs to the correct variant
                if (!sn.getSanPhamChiTiet().getId().equals(item.getSanPhamChiTietId())) {
                    log.warn("Lỗi dữ liệu: Serial number {} thuộc variant {} nhưng đơn hàng yêu cầu variant {}",
                            sn.getSerialNumberValue(), sn.getSanPhamChiTiet().getId(), item.getSanPhamChiTietId());
                    return false;
                }

                // Check if serial number is available or reserved for cart (cart reservations are OK for order creation)
                if (!sn.isAvailable() && !isCartReservation(sn)) {
                    log.warn("Serial number {} không khả dụng cho đơn hàng (trạng thái: {}, kênh: {})",
                            sn.getSerialNumberValue(), sn.getTrangThai(), sn.getKenhDatTruoc());
                    return false;
                }

                if (log.isDebugEnabled()) {
                    log.debug("Serial number {} đã được xác thực thành công (trạng thái: {})",
                             sn.getSerialNumberValue(), sn.getTrangThai());
                }
            } else {
                generalQuantityItems++;

                if (log.isDebugEnabled()) {
                    log.debug("Đường dẫn kiểm tra: Số lượng tổng quát cho variant: {}", item.getSanPhamChiTietId());
                }

                // No specific serial number provided, check general availability
                int availableQuantity = getAvailableQuantityByVariant(item.getSanPhamChiTietId());

                if (log.isDebugEnabled()) {
                    log.debug("Tồn kho khả dụng cho variant {}: {}, yêu cầu: {}",
                             item.getSanPhamChiTietId(), availableQuantity, item.getSoLuong());
                }

                if (availableQuantity < item.getSoLuong()) {
                    log.warn("Không đủ tồn kho cho variant {}: yêu cầu={}, khả_dụng={}",
                            item.getSanPhamChiTietId(), item.getSoLuong(), availableQuantity);
                    return false;
                }

                // Warning for low inventory
                if (availableQuantity <= item.getSoLuong() + 2) {
                    log.warn("Tồn kho thấp sau khi đặt hàng cho variant {}: còn lại {} sau khi trừ {}",
                            item.getSanPhamChiTietId(), availableQuantity - item.getSoLuong(), item.getSoLuong());
                }
            }

            validatedItems++;
        }

        long executionTime = System.currentTimeMillis() - startTime;

        // Structured logging for monitoring and analytics
        if (log.isInfoEnabled()) {
            log.info("Kiểm tra tồn kho hoàn tất: {} sản phẩm đã xác thực, " +
                    "serial_cụ_thể={}, số_lượng_tổng_quát={}, thời_gian={}ms",
                    validatedItems, specificSerialNumberItems, generalQuantityItems, executionTime);
        }

        return true;
    }

    /**
     * Reserve items with tracking for an order with distributed locking to prevent race conditions
     * Handles both specific serial numbers and general quantity requests
     * Replaces InventoryService.reserveItemsWithTracking()
     */
    @Transactional
    public List<Long> reserveItemsWithTracking(List<HoaDonChiTietDto> orderItems, String channel, String orderId, String user) {
        long startTime = System.currentTimeMillis();

        if (log.isInfoEnabled()) {
            log.info("Bắt đầu đặt trước {} sản phẩm cho đơn hàng {} qua kênh {} bởi người dùng {}",
                    orderItems.size(), orderId, channel, user);
        }

        // Group items by variant to minimize lock contention
        Map<Long, List<HoaDonChiTietDto>> itemsByVariant = orderItems.stream()
            .collect(Collectors.groupingBy(HoaDonChiTietDto::getSanPhamChiTietId));

        if (log.isDebugEnabled()) {
            log.debug("Nhóm sản phẩm theo variant: {} variant khác nhau cần xử lý", itemsByVariant.size());
        }

        List<Long> reservedSerialNumberIds = new ArrayList<>();
        int totalVariantsProcessed = 0;

        try {
            // Process each variant group with its own distributed lock
            for (Map.Entry<Long, List<HoaDonChiTietDto>> entry : itemsByVariant.entrySet()) {
                Long variantId = entry.getKey();
                List<HoaDonChiTietDto> variantItems = entry.getValue();
                totalVariantsProcessed++;

                if (log.isDebugEnabled()) {
                    log.debug("Xử lý variant {}/{}: variantId={}, {} sản phẩm",
                             totalVariantsProcessed, itemsByVariant.size(), variantId, variantItems.size());
                }

                String lockKey = distributedLockService.getInventoryLockKey(variantId);

                List<Long> variantReservedIds = distributedLockService.executeWithLock(lockKey, () ->
                    optimisticLockingService.executeWithRetry(() -> {
                        List<Long> variantReservations = new ArrayList<>();

                        // CRITICAL FIX: Separate items with and without specific serial numbers first
                        List<HoaDonChiTietDto> itemsWithSpecificSerialNumbers = variantItems.stream()
                            .filter(item -> item.getSerialNumberId() != null)
                            .collect(Collectors.toList());

                        List<HoaDonChiTietDto> itemsWithoutSpecificSerialNumbers = variantItems.stream()
                            .filter(item -> item.getSerialNumberId() == null)
                            .collect(Collectors.toList());

                        log.debug("Processing variant {}: {} items with specific serial numbers, {} items without",
                                 variantId, itemsWithSpecificSerialNumbers.size(), itemsWithoutSpecificSerialNumbers.size());

                        // Handle items with specific serial numbers first
                        for (HoaDonChiTietDto item : itemsWithSpecificSerialNumbers) {
                            log.debug("Processing item with specific serial number ID: {}", item.getSerialNumberId());

                            Optional<SerialNumber> serialNumberOpt = serialNumberRepository.findById(item.getSerialNumberId());
                            if (serialNumberOpt.isEmpty()) {
                                throw new IllegalArgumentException("Không tìm thấy serial number với ID " + item.getSerialNumberId());
                            }

                            SerialNumber serialNumber = serialNumberOpt.get();
                            log.debug("Found serial number: {} with status: {}",
                                     serialNumber.getSerialNumberValue(), serialNumber.getTrangThai());

                            // Verify serial number belongs to correct variant
                            if (!serialNumber.getSanPhamChiTiet().getId().equals(variantId)) {
                                throw new IllegalArgumentException(String.format(
                                    "Serial number %s belongs to variant %d but order item is for variant %d",
                                    serialNumber.getSerialNumberValue(),
                                    serialNumber.getSanPhamChiTiet().getId(),
                                    variantId));
                            }

                            // Handle different reservation scenarios
                            if (serialNumber.isReserved()) {
                                // Check if it's reserved for a cart session and we're converting to order
                                String currentReservationId = serialNumber.getDonHangDatTruoc();
                                String currentChannel = serialNumber.getKenhDatTruoc();

                                if ("CART".equals(currentChannel) && currentReservationId != null && currentReservationId.startsWith("CART-")) {
                                    // This is a cart reservation being converted to order - update the reservation
                                    log.debug("Converting cart reservation to order reservation for serial number {} (cart: {} -> order: {})",
                                             serialNumber.getSerialNumberValue(), currentReservationId, orderId);

                                    serialNumber.setDonHangDatTruoc(orderId);
                                    serialNumber.setKenhDatTruoc(channel);
                                    serialNumber.setThoiGianDatTruoc(Instant.now());
                                    serialNumberRepository.save(serialNumber);

                                    // Create audit trail for cart-to-order conversion
                                    SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.reservationEntry(
                                        serialNumber.getId(),
                                        channel,
                                        orderId,
                                        user,
                                        String.format("Chuyển đổi từ giỏ hàng (%s) sang đơn hàng", currentReservationId)
                                    );
                                    auditHistoryRepository.save(auditEntry);
                                } else {
                                    // Update existing reservation with actual order ID (for temp order IDs)
                                    log.debug("Updating existing reservation for serial number {} from {} to {}",
                                             serialNumber.getSerialNumberValue(), currentReservationId, orderId);

                                    serialNumber.setDonHangDatTruoc(orderId);
                                    serialNumber.setKenhDatTruoc(channel);
                                    serialNumber.setThoiGianDatTruoc(Instant.now());
                                    serialNumberRepository.save(serialNumber);
                                }
                            } else if (serialNumber.isAvailableForOrder()) {
                                // Reserve the available serial number (includes cart reservations)
                                if (serialNumber.isCartReservation()) {
                                    log.debug("Converting cart reservation to order reservation: {}", serialNumber.getSerialNumberValue());
                                } else {
                                    log.debug("Reserving available serial number: {}", serialNumber.getSerialNumberValue());
                                }

                                serialNumber.reserveWithTracking(channel, orderId);
                                serialNumberRepository.save(serialNumber);

                                // Create audit trail
                                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.reservationEntry(
                                    serialNumber.getId(),
                                    channel,
                                    orderId,
                                    user,
                                    "Đặt trước serial number cho đơn hàng"
                                );
                                auditHistoryRepository.save(auditEntry);
                            } else {
                                throw new IllegalArgumentException("Serial number " + serialNumber.getSerialNumberValue() +
                                                                 " không khả dụng để đặt trước (trạng thái: " + serialNumber.getTrangThai() + ")");
                            }

                            variantReservations.add(serialNumber.getId());
                            log.debug("Successfully processed serial number ID: {}", serialNumber.getId());
                        }

                        // Handle items without specific serial numbers - aggregate quantities to fix inventory issue
                        if (!itemsWithoutSpecificSerialNumbers.isEmpty()) {
                            // Calculate total quantity needed for this variant
                            int totalQuantityNeeded = itemsWithoutSpecificSerialNumbers.stream()
                                .mapToInt(HoaDonChiTietDto::getSoLuong)
                                .sum();

                            log.debug("Reserving {} total serial numbers for variant {} (from {} items without specific serial numbers)",
                                     totalQuantityNeeded, variantId, itemsWithoutSpecificSerialNumbers.size());

                            // CRITICAL FIX: Use new method that includes cart reservations
                            // This fixes the bug where cart reservations were counted as available
                            // but not actually reservable for order creation
                            List<SerialNumber> availableSerialNumbers = serialNumberRepository.findAvailableForOrderByVariant(
                                variantId, PageRequest.of(0, totalQuantityNeeded)
                            );

                            if (availableSerialNumbers.size() < totalQuantityNeeded) {
                                throw new IllegalArgumentException(
                                    String.format("Không đủ hàng tồn kho. Yêu cầu: %d, Có sẵn: %d",
                                                 totalQuantityNeeded, availableSerialNumbers.size())
                                );
                            }

                            // Batch update serial numbers for general quantity requests
                            List<SerialNumber> toUpdate = new ArrayList<>();
                            for (int i = 0; i < totalQuantityNeeded; i++) {
                                SerialNumber serialNumber = availableSerialNumbers.get(i);
                                serialNumber.reserveWithTracking(channel, orderId);
                                toUpdate.add(serialNumber);

                                log.debug("Prepared reservation for serial number {} for order {} via channel {}",
                                         serialNumber.getSerialNumberValue(), orderId, channel);
                            }

                            // Batch save all serial numbers
                            List<SerialNumber> savedSerialNumbers = serialNumberRepository.saveAll(toUpdate);
                            for (SerialNumber serialNumber : savedSerialNumbers) {
                                variantReservations.add(serialNumber.getId());
                            }

                            log.debug("Reserved {} serial numbers for variant {} without nested locking",
                                     totalQuantityNeeded, variantId);
                        }

                        log.debug("Completed processing variant {}: reserved {} serial numbers",
                                 variantId, variantReservations.size());
                        return variantReservations;
                    })
                , 10L, 20L); // OPTIMIZATION: Reduced timeouts - 10s wait, 20s lease

                reservedSerialNumberIds.addAll(variantReservedIds);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            // Enhanced completion logging with Vietnamese terminology
            if (log.isInfoEnabled()) {
                log.info("Hoàn thành đặt trước thành công: {} serial number cho đơn hàng {} qua kênh {}, " +
                        "xử_lý={} variant, thời_gian={}ms",
                        reservedSerialNumberIds.size(), orderId, channel, totalVariantsProcessed, executionTime);
            }

            return reservedSerialNumberIds;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            // Enhanced error logging with Vietnamese terminology
            log.error("Lỗi trong quá trình đặt trước, đang hủy {} serial number đã đặt trước: {}, " +
                     "thời_gian_thất_bại={}ms",
                     reservedSerialNumberIds.size(), e.getMessage(), executionTime);
            releaseReservationsSafely(reservedSerialNumberIds);
            throw e;
        }
    }

    /**
     * CRITICAL FIX: Validate that reserved serial numbers match the expected cart selections
     * This addresses Issue 2: Serial Number Data Corruption by ensuring correct assignments
     */
    @Transactional(readOnly = true)
    public void validateReservedSerialNumbers(List<HoaDonChiTietDto> orderItems, List<Long> reservedSerialNumberIds, String orderId) {
        if (orderItems.isEmpty() || reservedSerialNumberIds.isEmpty()) {
            return;
        }

        // Extract specific serial number IDs from order items
        List<Long> expectedSpecificSerialNumberIds = orderItems.stream()
            .filter(item -> item.getSerialNumberId() != null)
            .map(HoaDonChiTietDto::getSerialNumberId)
            .collect(Collectors.toList());

        // Fetch all reserved serial numbers
        List<SerialNumber> reservedSerialNumbers = serialNumberRepository.findAllById(reservedSerialNumberIds);

        // Validate that all expected specific serial numbers are in the reserved list
        for (Long expectedId : expectedSpecificSerialNumberIds) {
            boolean found = reservedSerialNumbers.stream()
                .anyMatch(sn -> sn.getId().equals(expectedId) && orderId.equals(sn.getDonHangDatTruoc()));

            if (!found) {
                throw new IllegalStateException(String.format(
                    "Expected specific serial number ID %d is not properly reserved for order %s",
                    expectedId, orderId));
            }
        }

        // Validate that all reserved serial numbers belong to the correct variants
        Map<Long, Integer> variantQuantityMap = orderItems.stream()
            .collect(Collectors.groupingBy(
                HoaDonChiTietDto::getSanPhamChiTietId,
                Collectors.summingInt(HoaDonChiTietDto::getSoLuong)
            ));

        Map<Long, Long> reservedByVariant = reservedSerialNumbers.stream()
            .collect(Collectors.groupingBy(
                sn -> sn.getSanPhamChiTiet().getId(),
                Collectors.counting()
            ));

        for (Map.Entry<Long, Integer> entry : variantQuantityMap.entrySet()) {
            Long variantId = entry.getKey();
            Integer expectedQuantity = entry.getValue();
            Long actualReserved = reservedByVariant.getOrDefault(variantId, 0L);

            if (!expectedQuantity.equals(actualReserved.intValue())) {
                throw new IllegalStateException(String.format(
                    "Variant %d: expected %d serial numbers but %d were reserved for order %s",
                    variantId, expectedQuantity, actualReserved, orderId));
            }
        }

        log.debug("Validated {} reserved serial numbers for order {} - all assignments correct",
                 reservedSerialNumberIds.size(), orderId);
    }

    /**
     * Safely release reservations (for error handling)
     * Replaces InventoryService.releaseReservationSafely()
     */
    @Transactional
    public void releaseReservationsSafely(List<Long> serialNumberIds) {
        try {
            releaseReservations(serialNumberIds, "system", "Order creation failed");
        } catch (Exception e) {
            log.warn("Failed to safely release reservations: {}", e.getMessage());
        }
    }

    // Bulk Operations

    /**
     * Generate serial numbers for a product variant
     */
    public List<SerialNumber> generateSerialNumbers(Long variantId, int quantity, String pattern, String user) {
        SanPhamChiTiet variant = sanPhamChiTietRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Product variant not found"));

        List<SerialNumber> generatedSerialNumbers = new ArrayList<>();
        String batchId = "BATCH-" + System.currentTimeMillis();

        for (int i = 1; i <= quantity; i++) {
            String serialNumberValue = generateSerialNumberValue(pattern, i);
            
            // Check if serial number already exists
            if (serialNumberRepository.existsBySerialNumberValue(serialNumberValue)) {
                log.warn("Serial number {} already exists, skipping", serialNumberValue);
                continue;
            }

            SerialNumber serialNumber = SerialNumber.builder()
                    .serialNumberValue(serialNumberValue)
                    .sanPhamChiTiet(variant)
                    .trangThai(TrangThaiSerialNumber.AVAILABLE)
                    .importBatchId(batchId)
                    .build();

            SerialNumber savedSerialNumber = serialNumberRepository.save(serialNumber);
            generatedSerialNumbers.add(savedSerialNumber);

            // Create audit trail
            SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.bulkOperationEntry(
                savedSerialNumber.getId(),
                "GENERATE",
                batchId,
                user,
                "Tạo serial number hàng loạt"
            );
            auditHistoryRepository.save(auditEntry);
        }

        log.info("Generated {} serial numbers for variant {} with batch ID {}", 
                generatedSerialNumbers.size(), variantId, batchId);

        return generatedSerialNumbers;
    }

    // Scheduled Tasks

    /**
     * Clean up expired reservations (runs every 5 minutes)
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupExpiredReservations() {
        Instant expiredBefore = Instant.now().minus(15, ChronoUnit.MINUTES);

        List<SerialNumber> expiredReservations = serialNumberRepository.findExpiredReservations(expiredBefore);

        if (!expiredReservations.isEmpty()) {
            int releasedCount = serialNumberRepository.releaseExpiredReservations(expiredBefore);

            // Create audit entries for released reservations
            for (SerialNumber serialNumber : expiredReservations) {
                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.releaseEntry(
                    serialNumber.getId(),
                    "SYSTEM",
                    "Hết hạn đặt trước tự động"
                );
                auditHistoryRepository.save(auditEntry);
            }

            log.info("Released {} expired reservations", releasedCount);
        }

        // Also clean up temporary order IDs that are older than 30 minutes
        cleanupTemporaryOrderIds();

        // Clean up expired cart reservations
        cleanupExpiredCartReservations();
    }

    /**
     * Clean up reservations with temporary order IDs that are older than 30 minutes.
     * This prevents inventory deadlocks from failed order creation processes.
     */
    @Transactional
    public void cleanupTemporaryOrderIds() {
        Instant expiredBefore = Instant.now().minus(30, ChronoUnit.MINUTES);

        // Find reservations with temporary order IDs that are older than 30 minutes
        List<SerialNumber> tempReservations = serialNumberRepository.findByDonHangDatTruocStartingWith("TEMP-");

        List<SerialNumber> expiredTempReservations = tempReservations.stream()
            .filter(sn -> sn.getThoiGianDatTruoc() != null && sn.getThoiGianDatTruoc().isBefore(expiredBefore))
            .collect(Collectors.toList());

        if (!expiredTempReservations.isEmpty()) {
            for (SerialNumber serialNumber : expiredTempReservations) {
                String tempOrderId = serialNumber.getDonHangDatTruoc();
                serialNumber.releaseReservation();
                serialNumberRepository.save(serialNumber);

                // Create audit entry
                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.releaseEntry(
                    serialNumber.getId(),
                    "SYSTEM",
                    String.format("Cleanup temporary order ID: %s", tempOrderId)
                );
                auditHistoryRepository.save(auditEntry);

                log.debug("Released expired temporary reservation for serial number {} with temp order ID {}",
                         serialNumber.getSerialNumberValue(), tempOrderId);
            }

            log.info("Cleaned up {} expired temporary order reservations", expiredTempReservations.size());
        }
    }

    /**
     * Clean up expired cart reservations that are older than 30 minutes.
     * This prevents inventory deadlocks from abandoned cart sessions.
     */
    @Transactional
    public void cleanupExpiredCartReservations() {
        Instant expiredBefore = Instant.now().minus(30, ChronoUnit.MINUTES);

        // Find reservations with cart order IDs that are older than 30 minutes
        List<SerialNumber> cartReservations = serialNumberRepository.findByDonHangDatTruocStartingWith("CART-");

        List<SerialNumber> expiredCartReservations = cartReservations.stream()
            .filter(sn -> sn.getThoiGianDatTruoc() != null && sn.getThoiGianDatTruoc().isBefore(expiredBefore))
            .collect(Collectors.toList());

        if (!expiredCartReservations.isEmpty()) {
            for (SerialNumber serialNumber : expiredCartReservations) {
                String cartSessionId = serialNumber.getDonHangDatTruoc();
                serialNumber.releaseReservation();
                serialNumberRepository.save(serialNumber);

                // Create audit entry
                SerialNumberAuditHistory auditEntry = SerialNumberAuditHistory.releaseEntry(
                    serialNumber.getId(),
                    "SYSTEM",
                    String.format("Cleanup expired cart session: %s", cartSessionId)
                );
                auditHistoryRepository.save(auditEntry);

                log.debug("Released expired cart reservation for serial number {} with cart session ID {}",
                         serialNumber.getSerialNumberValue(), cartSessionId);
            }

            log.info("Cleaned up {} expired cart reservations", expiredCartReservations.size());
        }
    }

    // Helper Methods

    /**
     * Check if a serial number is reserved for cart (cart reservations are valid for order creation)
     */
    private boolean isCartReservation(SerialNumber sn) {
        return sn.isReserved() && "CART".equals(sn.getKenhDatTruoc());
    }

    private void validateStatusTransition(TrangThaiSerialNumber from, TrangThaiSerialNumber to) {
        // Define valid transitions
        Map<TrangThaiSerialNumber, Set<TrangThaiSerialNumber>> validTransitions = Map.of(
            TrangThaiSerialNumber.AVAILABLE, Set.of(TrangThaiSerialNumber.RESERVED, TrangThaiSerialNumber.DAMAGED, TrangThaiSerialNumber.UNAVAILABLE, TrangThaiSerialNumber.DISPLAY_UNIT),
            TrangThaiSerialNumber.RESERVED, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.SOLD, TrangThaiSerialNumber.DAMAGED),
            TrangThaiSerialNumber.SOLD, Set.of(TrangThaiSerialNumber.RETURNED, TrangThaiSerialNumber.DAMAGED),
            TrangThaiSerialNumber.RETURNED, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.DAMAGED, TrangThaiSerialNumber.DISPOSED),
            TrangThaiSerialNumber.DAMAGED, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.DISPOSED),
            TrangThaiSerialNumber.UNAVAILABLE, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.DAMAGED),
            TrangThaiSerialNumber.DISPLAY_UNIT, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.DAMAGED),
            TrangThaiSerialNumber.QUALITY_CONTROL, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.DAMAGED),
            TrangThaiSerialNumber.IN_TRANSIT, Set.of(TrangThaiSerialNumber.AVAILABLE, TrangThaiSerialNumber.QUALITY_CONTROL)
        );

        Set<TrangThaiSerialNumber> allowedTransitions = validTransitions.get(from);
        if (allowedTransitions == null || !allowedTransitions.contains(to)) {
            throw new IllegalArgumentException(
                String.format("Invalid status transition from %s to %s", from, to)
            );
        }
    }

    private String generateSerialNumberValue(String pattern, int sequence) {
        // Replace placeholders in pattern
        return pattern
                .replace("{SEQ}", String.format("%04d", sequence))
                .replace("{TIMESTAMP}", String.valueOf(System.currentTimeMillis() % 100000));
    }

    private String buildAuditJson(SerialNumber serialNumber) {
        return String.format(
            "{\"serialNumberValue\":\"%s\",\"trangThai\":\"%s\",\"variantId\":%d,\"batchNumber\":\"%s\",\"supplier\":\"%s\"}",
            serialNumber.getSerialNumberValue(),
            serialNumber.getTrangThai(),
            serialNumber.getSanPhamChiTiet().getId(),
            serialNumber.getBatchNumber() != null ? serialNumber.getBatchNumber() : "",
            serialNumber.getNhaCungCap() != null ? serialNumber.getNhaCungCap() : ""
        );
    }
}
