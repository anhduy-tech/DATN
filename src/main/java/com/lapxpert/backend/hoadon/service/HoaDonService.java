package com.lapxpert.backend.hoadon.service;

import com.lapxpert.backend.common.service.BusinessEntityService;
import com.lapxpert.backend.common.service.EmailService;
import com.lapxpert.backend.common.service.OptimisticLockingService;
import com.lapxpert.backend.common.service.WebSocketIntegrationService;
import com.lapxpert.backend.hoadon.dto.HoaDonDto;
import com.lapxpert.backend.hoadon.dto.HoaDonChiTietDto;
import com.lapxpert.backend.hoadon.dto.PaymentSummaryDto;
import com.lapxpert.backend.hoadon.dto.PaymentDetailDto;
import com.lapxpert.backend.hoadon.entity.HoaDon;
import com.lapxpert.backend.hoadon.entity.HoaDonAuditHistory;
import com.lapxpert.backend.common.event.OrderChangeEvent;
import com.lapxpert.backend.hoadon.entity.HoaDonChiTiet;
import com.lapxpert.backend.hoadon.entity.HoaDonThanhToan;
import com.lapxpert.backend.hoadon.entity.HoaDonThanhToanId;
import com.lapxpert.backend.hoadon.entity.ThanhToan;
import com.lapxpert.backend.hoadon.mapper.HoaDonMapper;
import com.lapxpert.backend.hoadon.repository.HoaDonRepository;
import com.lapxpert.backend.hoadon.repository.HoaDonAuditHistoryRepository;
import com.lapxpert.backend.hoadon.repository.HoaDonThanhToanRepository;
import com.lapxpert.backend.hoadon.repository.ThanhToanRepository;
import com.lapxpert.backend.hoadon.enums.LoaiHoaDon;
import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import com.lapxpert.backend.hoadon.enums.TrangThaiDonHang;
import com.lapxpert.backend.hoadon.enums.TrangThaiThanhToan;
import com.lapxpert.backend.hoadon.enums.TrangThaiGiaoDich;
import com.lapxpert.backend.nguoidung.entity.NguoiDung;
import com.lapxpert.backend.nguoidung.entity.DiaChi;
import com.lapxpert.backend.nguoidung.entity.VaiTro;
import com.lapxpert.backend.nguoidung.repository.NguoiDungRepository;
import com.lapxpert.backend.nguoidung.repository.DiaChiRepository;
import com.lapxpert.backend.sanpham.entity.SerialNumber;
import com.lapxpert.backend.sanpham.entity.sanpham.SanPhamChiTiet;
import com.lapxpert.backend.sanpham.repository.SanPhamChiTietRepository;
import com.lapxpert.backend.sanpham.service.SerialNumberService;
import com.lapxpert.backend.sanpham.service.PricingService;
import com.lapxpert.backend.phieugiamgia.service.PhieuGiamGiaService;

import com.lapxpert.backend.shipping.service.ShippingCalculatorService;
import com.lapxpert.backend.shipping.service.GHNService;
import com.lapxpert.backend.shipping.dto.ShippingRequest;
import com.lapxpert.backend.shipping.dto.ShippingFeeResponse;
import com.lapxpert.backend.payment.vnpay.VNPayService;
import com.lapxpert.backend.payment.service.MoMoService;
import com.lapxpert.backend.payment.service.PaymentValidationService;
import com.lapxpert.backend.hoadon.controller.HoaDonController.PaymentComponent;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoaDonService extends BusinessEntityService<HoaDon, Long, HoaDonDto, HoaDonAuditHistory> {

    private final HoaDonRepository hoaDonRepository;
    private final HoaDonAuditHistoryRepository auditHistoryRepository;
    private final HoaDonThanhToanRepository hoaDonThanhToanRepository;
    private final ThanhToanRepository thanhToanRepository;
    private final HoaDonMapper hoaDonMapper;
    private final NguoiDungRepository nguoiDungRepository;
    private final DiaChiRepository diaChiRepository;
    private final SanPhamChiTietRepository sanPhamChiTietRepository;
    private final SerialNumberService serialNumberService;
    private final PricingService pricingService;
    private final PhieuGiamGiaService phieuGiamGiaService;
    private final KiemTraTrangThaiHoaDonService kiemTraTrangThaiService;
    private final MoMoService moMoGatewayService;
    private final PaymentValidationService paymentParameterValidationService;

    private final VNPayService vnPayService;
    private final ShippingCalculatorService shippingCalculatorService;
    private final GHNService ghnService;
    private final ApplicationEventPublisher eventPublisher;
    private final WebSocketIntegrationService webSocketIntegrationService;
    private final OptimisticLockingService optimisticLockingService;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<HoaDonDto> getHoaDonsByTrangThai(String trangThaiStr) {
        List<HoaDon> hoaDons;
        if (trangThaiStr == null || trangThaiStr.trim().isEmpty()) {
            hoaDons = hoaDonRepository.findAll();
        } else {
            try {
                TrangThaiDonHang trangThaiEnum = TrangThaiDonHang.valueOf(trangThaiStr.toUpperCase());
                hoaDons = hoaDonRepository.findByTrangThaiDonHang(trangThaiEnum);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái đơn hàng không hợp lệ: " + trangThaiStr, e);
            }
        }
        return toDtoListWithPaymentMethod(hoaDons);
    }

    @Transactional
    public HoaDonDto createHoaDon(HoaDonDto hoaDonDto, NguoiDung currentUser) {
        System.out.println(hoaDonDto.getNguoiNhanEmail());
        long startTime = System.currentTimeMillis();
        String orderChannel = hoaDonDto.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY ? "POS" : "ONLINE";
        String tempOrderId = "TEMP-" + System.currentTimeMillis();

        // Step 1: Pre-transaction validation
        validateOrderCreationRequest(hoaDonDto, currentUser);

        // Step 2: Reserve inventory ONLY for TAI_QUAY orders
        List<Long> reservedItemIds = new ArrayList<>(); // Initialize with an empty list
        if (hoaDonDto.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
            log.info("TAI_QUAY order detected, proceeding with serial number reservation.");
            reservedItemIds = reserveInventoryWithCoordination(hoaDonDto, orderChannel, tempOrderId);
        } else {
            log.info("ONLINE order detected, skipping automatic serial number reservation at creation time.");
            // For ONLINE orders, we do not reserve inventory upfront.
            // Serial number assignment will be handled later in the fulfillment process.
        }

        try {
            // Step 3: Create order entity with enhanced transaction coordination
            HoaDon hoaDon = createOrderEntityWithCoordination(hoaDonDto, currentUser, tempOrderId);

            // Step 4.7: Map order items from DTO to entity
            mapOrderItemsFromDto(hoaDon, hoaDonDto);

            // Step 5: Process order items and calculate totals
            BigDecimal tongTienHang = processOrderItems(hoaDon, hoaDonDto);

            // Step 6: Validate and apply vouchers
            BigDecimal totalVoucherDiscount = processVouchers(hoaDon, hoaDonDto, tongTienHang);

            // Step 7: Set order totals
            hoaDon.setTongTienHang(tongTienHang);

            // Step 7.1: Calculate shipping fee automatically if not provided manually
            BigDecimal shippingFee = calculateShippingFee(hoaDon, hoaDonDto);
            hoaDon.setPhiVanChuyen(shippingFee);
            hoaDon.setGiaTriGiamGiaVoucher(totalVoucherDiscount);

            BigDecimal tongCong = tongTienHang.add(hoaDon.getPhiVanChuyen()).subtract(totalVoucherDiscount);
            hoaDon.setTongThanhToan(tongCong.max(BigDecimal.ZERO));

            // Step 7: Set order status
            setOrderStatus(hoaDon, hoaDonDto);

            // Step 8: Save order with optimistic locking retry
            HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                    () -> hoaDonRepository.save(hoaDon),
                    "HoaDon",
                    hoaDon.getId()
            );

            // Step 8.1: Update reserved items with actual order ID synchronously within transaction
            // This eliminates the race condition where frontend queries for serial numbers before they are updated
            final Long finalOrderId = savedHoaDon.getId();

            try {
                serialNumberService.updateReservationOrderId(reservedItemIds, tempOrderId, finalOrderId.toString());
                log.debug("Successfully updated serial number reservations synchronously for order: {}", finalOrderId);
            } catch (Exception e) {
                log.error("Failed to update serial number reservations for order {}: {}", finalOrderId, e.getMessage());
                throw new RuntimeException("Không thể cập nhật thông tin serial number cho đơn hàng", e);
            }

            // Step 8.2: Create initial payment record for order (skip for mixed payments)
            if (hoaDonDto.getPhuongThucThanhToan() != null) {
                // Skip creating initial payment record for mixed payment orders
                // Mixed payments will create their own payment records during processing
                if (Boolean.TRUE.equals(hoaDonDto.getIsMixedPayment())) {
                    log.debug("Skipping initial payment record creation for mixed payment order {}", savedHoaDon.getId());
                } else {
                    // Create initial payment record for single payment orders (preserves existing behavior)
                    try {
                        createInitialPaymentRecord(savedHoaDon, hoaDonDto.getPhuongThucThanhToan(), currentUser);
                        log.debug("Initial payment record created successfully for order {} with method {}",
                                savedHoaDon.getId(), hoaDonDto.getPhuongThucThanhToan());
                    } catch (Exception e) {
                        log.error("Failed to create initial payment record for order {}: {}", savedHoaDon.getId(), e.getMessage());
                        throw new RuntimeException("Không thể tạo bản ghi thanh toán cho đơn hàng", e);
                    }
                }
            } else {
                log.warn("No payment method specified for order {}, skipping payment record creation", savedHoaDon.getId());
            }

            // Step 8.5: Create audit entry for order creation
            String newValues = createAuditValues(savedHoaDon);
            HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                    savedHoaDon.getId(),
                    newValues,
                    savedHoaDon.getNguoiTao(),
                    "Tạo hóa đơn mới"
            );
            auditHistoryRepository.save(auditEntry);

            // Step 8.6: Create audit entry for automatic staff assignment if applicable
            if (savedHoaDon.getNhanVien() != null && hoaDonDto.getNhanVienId() == null) {
                // This was an automatic assignment - create simple audit entry
                String assignmentReason = String.format("Tự động gán nhân viên %s cho đơn hàng %s",
                        savedHoaDon.getNhanVien().getHoTen(), savedHoaDon.getLoaiHoaDon().name());
                HoaDonAuditHistory staffAssignmentAudit = HoaDonAuditHistory.createEntry(
                        savedHoaDon.getId(),
                        createAuditValues(savedHoaDon),
                        savedHoaDon.getNguoiTao(),
                        assignmentReason
                );
                auditHistoryRepository.save(staffAssignmentAudit);
            }

            // Step 8.7: Create specific audit entry for TAI_QUAY order status logic
            if (savedHoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                createTaiQuayOrderStatusAuditEntry(savedHoaDon);
            }

            // Step 9: For POS orders with immediate payment, confirm the sale
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY &&
                    hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
                serialNumberService.confirmSale(reservedItemIds, savedHoaDon.getId().toString(), "system");
                log.info("POS order {} completed with immediate payment confirmation", savedHoaDon.getId());
            } else {
                log.info("Order {} created with inventory reserved. Payment pending.", savedHoaDon.getId());
            }

            // Step 10: Apply vouchers to the saved order in a separate transaction
            applyVouchersToOrderSeparateTransaction(savedHoaDon.getId(), hoaDonDto, tongTienHang);

            // Step 11: Log performance metrics
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Order creation completed successfully - Order: {}, Execution time: {}ms, Items: {}, Total: {}",
                    savedHoaDon.getId(), executionTime, savedHoaDon.getHoaDonChiTiets().size(), savedHoaDon.getTongThanhToan());

            // Step 12: Send order confirmation email
            sendOrderConfirmationEmail(savedHoaDon);

            return toDtoWithPaymentMethod(savedHoaDon);

        } catch (Exception e) {
            // Step 10: Release reserved inventory if order creation fails
            log.error("Order creation failed, releasing reserved inventory: {}", e.getMessage());
            try {
                // Find items reserved with the temporary order ID and release them
                List<Long> tempReservedItems = serialNumberService.getReservedSerialNumberIdsForOrder(tempOrderId);
                if (!tempReservedItems.isEmpty()) {
                    serialNumberService.releaseReservationsSafely(tempReservedItems);
                } else {
                    // Fallback to the original list if temp order ID tracking fails
                    serialNumberService.releaseReservationsSafely(reservedItemIds);
                }
            } catch (Exception releaseException) {
                log.error("Failed to release inventory reservations after order creation failure: {}", releaseException.getMessage());
                // Don't throw this exception as it would mask the original error
            }
            throw e;
        }
    }

    /**
     * Sends an order confirmation email to the customer.
     *
     * @param hoaDon The created order.
     */
    private void sendOrderConfirmationEmail(HoaDon hoaDon) {
        if (hoaDon.getKhachHang() != null && hoaDon.getKhachHang().getEmail() != null && !hoaDon.getKhachHang().getEmail().trim().isEmpty()) {
            String customerEmail = hoaDon.getKhachHang().getEmail();
            String subject = "Xác nhận đơn hàng #" + hoaDon.getMaHoaDon() + " của bạn tại LapXpert Store";
            String orderLink = "http://localhost:5173/shop/orders/" + hoaDon.getId();
            String text = String.format("Chào bạn %s, Đơn hàng của bạn với mã số #%s đã được tạo thành công tại LapXpert Store. Bạn có thể kiểm tra chi tiết đơn hàng của mình tại đây: %s Cảm ơn bạn đã mua sắm tại LapXpert Store!Trân trọng, Đội ngũ LapXpert Store",
                    hoaDon.getKhachHang().getHoTen(),
                    hoaDon.getMaHoaDon(),
                    orderLink
            );
            emailService.sendEmail(customerEmail, subject, text);
            log.info("Order confirmation email sent to {}", customerEmail);
        } else if(hoaDon.getNguoiNhanTen() != null && hoaDon.getNguoiNhanEmail() != null && !hoaDon.getNguoiNhanEmail().trim().isEmpty()){
            String customerEmail = hoaDon.getNguoiNhanEmail();
            String subject = "Xác nhận đơn hàng #" + hoaDon.getMaHoaDon() + " của bạn tại LapXpert Store";
            String orderLink = "http://localhost:5173/shop/orders/" + hoaDon.getId();
            String text = String.format("Chào bạn %s, Đơn hàng của bạn với mã số #%s đã được tạo thành công tại LapXpert Store. Bạn có thể kiểm tra chi tiết đơn hàng của mình tại đây: %s Cảm ơn bạn đã mua sắm tại LapXpert Store!Trân trọng, Đội ngũ LapXpert Store",
                    hoaDon.getNguoiNhanTen(),
                    hoaDon.getMaHoaDon(),
                    orderLink
            );
            emailService.sendEmail(customerEmail, subject, text);
            log.info("Order confirmation email sent to {}", customerEmail);
        } else {
            log.warn("Cannot send order confirmation email: customer or email not found for order {}", hoaDon.getId());
        }
    }

    /**
     * Enhanced map order items from DTO to entity with robust validation and error handling.
     * This creates the HoaDonChiTiet entities from the DTO data with comprehensive validation.
     */
    private void mapOrderItemsFromDto(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        log.debug("Mapping order items from DTO for order: {}", hoaDon.getMaHoaDon());

        if (hoaDonDto.getChiTiet() == null || hoaDonDto.getChiTiet().isEmpty()) {
            log.warn("No order items found in DTO for order: {}", hoaDon.getMaHoaDon());
            return;
        }

        List<HoaDonChiTiet> hoaDonChiTiets = new ArrayList<>();
        int itemIndex = 0;

        for (HoaDonChiTietDto chiTietDto : hoaDonDto.getChiTiet()) {
            itemIndex++;

            try {
                // Enhanced validation for each order item
                validateOrderItemDto(chiTietDto, itemIndex);

                // Create HoaDonChiTiet entity with enhanced mapping
                HoaDonChiTiet chiTiet = createOrderItemEntity(hoaDon, chiTietDto, itemIndex);
                hoaDonChiTiets.add(chiTiet);

                log.debug("Successfully mapped order item {} - Product ID: {}, Quantity: {}",
                        itemIndex, chiTietDto.getSanPhamChiTietId(), chiTietDto.getSoLuong());

            } catch (Exception e) {
                log.error("Failed to map order item {} for order {}: {}",
                        itemIndex, hoaDon.getMaHoaDon(), e.getMessage());
                throw new IllegalArgumentException(
                        String.format("Lỗi xử lý sản phẩm thứ %d trong đơn hàng: %s", itemIndex, e.getMessage()), e);
            }
        }

        hoaDon.setHoaDonChiTiets(hoaDonChiTiets);
        log.info("Successfully mapped {} order items for order: {}", hoaDonChiTiets.size(), hoaDon.getMaHoaDon());
    }

    /**
     * Validate order item DTO data with comprehensive checks.
     */
    private void validateOrderItemDto(HoaDonChiTietDto chiTietDto, int itemIndex) {
        if (chiTietDto == null) {
            throw new IllegalArgumentException("Thông tin sản phẩm thứ " + itemIndex + " không được để trống");
        }

        if (chiTietDto.getSanPhamChiTietId() == null) {
            throw new IllegalArgumentException("ID sản phẩm chi tiết thứ " + itemIndex + " không được để trống");
        }

        if (chiTietDto.getSoLuong() == null || chiTietDto.getSoLuong() <= 0) {
            throw new IllegalArgumentException("Số lượng sản phẩm thứ " + itemIndex + " phải lớn hơn 0");
        }

        if (chiTietDto.getSoLuong() > 1000) {
            throw new IllegalArgumentException("Số lượng sản phẩm thứ " + itemIndex + " không được vượt quá 1000");
        }

        // Enhanced price validation for cart price preservation
        if (chiTietDto.getGiaBan() != null) {
            if (chiTietDto.getGiaBan().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Giá bán sản phẩm thứ " + itemIndex + " không được âm");
            }
            if (chiTietDto.getGiaBan().compareTo(BigDecimal.valueOf(1000000000)) > 0) {
                throw new IllegalArgumentException("Giá bán sản phẩm thứ " + itemIndex + " không được vượt quá 1 tỷ VND");
            }
        }
    }

    /**
     * Create HoaDonChiTiet entity with enhanced mapping and validation.
     */
    private HoaDonChiTiet createOrderItemEntity(HoaDon hoaDon, HoaDonChiTietDto chiTietDto, int itemIndex) {
        HoaDonChiTiet chiTiet = new HoaDonChiTiet();

        // Set basic properties
        chiTiet.setHoaDon(hoaDon);
        chiTiet.setSoLuong(chiTietDto.getSoLuong());
        chiTiet.setGiaBan(chiTietDto.getGiaBan() != null ? chiTietDto.getGiaBan() : BigDecimal.ZERO);

        // Set SanPhamChiTiet reference using ID with validation
        if (chiTietDto.getSanPhamChiTietId() != null) {
            SanPhamChiTiet sanPhamChiTiet = new SanPhamChiTiet();
            sanPhamChiTiet.setId(chiTietDto.getSanPhamChiTietId());
            chiTiet.setSanPhamChiTiet(sanPhamChiTiet);
        } else {
            throw new IllegalArgumentException("ID sản phẩm chi tiết thứ " + itemIndex + " không hợp lệ");
        }

        // Initialize audit fields
        chiTiet.setNgayTao(Instant.now());
        chiTiet.setNgayCapNhat(Instant.now());

        return chiTiet;
    }

    /**
     * Process order items and calculate line totals.
     * Items are already reserved by InventoryService.
     */
    private BigDecimal processOrderItems(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        BigDecimal tongTienHang = BigDecimal.ZERO;
        List<HoaDonChiTiet> processedChiTietList = new ArrayList<>();

        if (hoaDon.getHoaDonChiTiets() != null && !hoaDon.getHoaDonChiTiets().isEmpty()) {
            for (HoaDonChiTiet mappedChiTiet : hoaDon.getHoaDonChiTiets()) {
                if (mappedChiTiet.getSanPhamChiTiet() == null || mappedChiTiet.getSanPhamChiTiet().getId() == null) {
                    throw new IllegalArgumentException("Thông tin sản phẩm chi tiết không hợp lệ trong chi tiết hóa đơn.");
                }
                Long sanPhamChiTietId = mappedChiTiet.getSanPhamChiTiet().getId();
                SanPhamChiTiet sanPhamChiTiet = sanPhamChiTietRepository.findById(sanPhamChiTietId)
                        .orElseThrow(() -> new EntityNotFoundException("Sản phẩm chi tiết không tồn tại với ID: " + sanPhamChiTietId));

                mappedChiTiet.setSanPhamChiTiet(sanPhamChiTiet);
                mappedChiTiet.setHoaDon(hoaDon);

                // CRITICAL FIX: Preserve cart prices when provided, fallback to current price calculation
                // This enables mixed-price scenarios where same SKU has different prices from cart
                BigDecimal sellingPrice;
                if (mappedChiTiet.getGiaBan() != null && mappedChiTiet.getGiaBan().compareTo(BigDecimal.ZERO) > 0) {
                    // Use cart price when provided (preserves user's pricing at time of adding to cart)
                    sellingPrice = mappedChiTiet.getGiaBan();
                    log.debug("Preserving cart price {} for product variant ID: {}", sellingPrice, sanPhamChiTietId);
                } else {
                    // Fallback to current price calculation for backward compatibility
                    sellingPrice = pricingService.calculateEffectivePrice(sanPhamChiTiet);
                    mappedChiTiet.setGiaBan(sellingPrice);
                    log.debug("Using calculated current price {} for product variant ID: {}", sellingPrice, sanPhamChiTietId);
                }
                mappedChiTiet.setGiaGoc(sanPhamChiTiet.getGiaBan());

                BigDecimal lineTotal = sellingPrice.multiply(BigDecimal.valueOf(mappedChiTiet.getSoLuong()));
                mappedChiTiet.setThanhTien(lineTotal);

                // Set snapshots for audit trail
                if (sanPhamChiTiet.getSanPham() != null) {
                    mappedChiTiet.setTenSanPhamSnapshot(sanPhamChiTiet.getSanPham().getTenSanPham());
                }
                mappedChiTiet.setSkuSnapshot(sanPhamChiTiet.getSku());
                if (sanPhamChiTiet.getHinhAnh() != null && !sanPhamChiTiet.getHinhAnh().isEmpty()) {
                    mappedChiTiet.setHinhAnhSnapshot(sanPhamChiTiet.getHinhAnh().get(0));
                }

                tongTienHang = tongTienHang.add(lineTotal);
                processedChiTietList.add(mappedChiTiet);
            }
        }
        hoaDon.setHoaDonChiTiets(processedChiTietList);
        return tongTienHang;
    }

    /**
     * Process and validate vouchers for the order.
     * Returns the total discount amount from all valid vouchers.
     */
    private BigDecimal processVouchers(HoaDon hoaDon, HoaDonDto hoaDonDto, BigDecimal orderTotal) {
        if (hoaDonDto.getVoucherCodes() == null || hoaDonDto.getVoucherCodes().isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalDiscount = BigDecimal.ZERO;
        NguoiDung customer = hoaDon.getKhachHang();


        for (String voucherCode : hoaDonDto.getVoucherCodes()) {
            try {
                // Validate voucher
                PhieuGiamGiaService.VoucherValidationResult validationResult =
                        phieuGiamGiaService.validateVoucher(voucherCode, customer, orderTotal);

                if (validationResult.isValid()) {
                    // Apply voucher to order (this will be done after order is saved)
                    totalDiscount = totalDiscount.add(validationResult.getDiscountAmount());
                    log.info("Voucher {} validated successfully for order. Discount: {}",
                            voucherCode, validationResult.getDiscountAmount());
                } else {
                    log.warn("Voucher {} validation failed: {}", voucherCode, validationResult.getErrorMessage());
                    throw new IllegalArgumentException("Voucher validation failed for " + voucherCode + ": " + validationResult.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("Error processing voucher {}: {}", voucherCode, e.getMessage());
                throw new IllegalArgumentException("Error processing voucher " + voucherCode + ": " + e.getMessage());
            }
        }

        return totalDiscount;
    }

    /**
     * Apply vouchers to order in a separate transaction to avoid transient entity issues.
     * Uses ID-based approach to completely avoid entity reference issues.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void applyVouchersToOrderSeparateTransaction(Long orderId, HoaDonDto hoaDonDto, BigDecimal orderTotal) {
        if (hoaDonDto.getVoucherCodes() == null || hoaDonDto.getVoucherCodes().isEmpty()) {
            return;
        }

        // Get customer ID directly without fetching full entities
        Long customerId = hoaDonRepository.findCustomerIdByOrderId(orderId);
        if (customerId == null) {
            log.warn("No customer found for order {}, skipping voucher application", orderId);
            return;
        }

        // Fetch customer separately to avoid transient entity issues
        NguoiDung customer = nguoiDungRepository.findById(customerId)
                .orElse(null);
        if (customer == null) {
            log.warn("Customer {} not found, skipping voucher application", customerId);
            return;
        }

        for (String voucherCode : hoaDonDto.getVoucherCodes()) {
            try {
                // Re-validate voucher using customer entity
                PhieuGiamGiaService.VoucherValidationResult validationResult =
                        phieuGiamGiaService.validateVoucher(voucherCode, customer, orderTotal);

                if (validationResult.isValid()) {
                    // Apply voucher using ID-based approach to avoid entity references
                    phieuGiamGiaService.applyVoucherToOrderById(
                            validationResult.getVoucher().getId(),
                            orderId,
                            validationResult.getDiscountAmount()
                    );
                    log.info("Applied voucher {} to order {} with discount {}",
                            voucherCode, orderId, validationResult.getDiscountAmount());
                }
            } catch (Exception e) {
                log.error("Failed to apply voucher {} to order {}: {}", voucherCode, orderId, e.getMessage());
                // Note: At this point the order is already saved, so we log the error but don't fail the order
            }
        }
    }

    /**
     * Set order status based on order type and payment method.
     * Respects status values provided in DTO, only sets defaults when not provided.
     */
    private void setOrderStatus(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        // Ensure loaiHoaDon is set, defaulting to ONLINE if not specified
        if (hoaDon.getLoaiHoaDon() == null) {
            hoaDon.setLoaiHoaDon(LoaiHoaDon.ONLINE);
        }

        // Validate payment method based on order type
        if (Boolean.TRUE.equals(hoaDonDto.getIsMixedPayment())) {
            // For mixed payments, validation is already done in validateMixedPaymentConfiguration
            log.debug("Skipping payment method validation for mixed payment - already validated");
        } else {
            validatePaymentMethodForOrderType(hoaDon, hoaDonDto);
        }

        // Set order status - use DTO value if provided, otherwise set defaults
        if (hoaDonDto.getTrangThaiDonHang() != null) {
            // Use status provided by frontend (e.g., from OrderCreate.vue logic)
            hoaDon.setTrangThaiDonHang(hoaDonDto.getTrangThaiDonHang());
            log.debug("Using order status from DTO: {}", hoaDonDto.getTrangThaiDonHang());
        } else if (hoaDon.getTrangThaiDonHang() == null) {
            // Set default status only if not provided by DTO
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                // Enhanced TAI_QUAY order status logic based on shipping requirements
                setTaiQuayOrderStatus(hoaDon);
            } else {
                // Online orders start as pending confirmation
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.CHO_XAC_NHAN);
                log.debug("Setting default online order status: CHO_XAC_NHAN");
            }
        }

        // Set payment status based on payment method from DTO
        if (hoaDonDto.getTrangThaiThanhToan() != null) {
            // Use payment status provided by frontend (e.g., calculated based on payment method)
            hoaDon.setTrangThaiThanhToan(hoaDonDto.getTrangThaiThanhToan());
            log.debug("Using payment status from DTO: {} for payment method: {}",
                    hoaDonDto.getTrangThaiThanhToan(), hoaDonDto.getPhuongThucThanhToan());
        } else {
            // Determine payment status based on payment method or mixed payment flag
            TrangThaiThanhToan paymentStatus;
            if (Boolean.TRUE.equals(hoaDonDto.getIsMixedPayment())) {
                // Mixed payments always start as unpaid
                paymentStatus = TrangThaiThanhToan.CHUA_THANH_TOAN;
                log.debug("Setting payment status for mixed payment: {}", paymentStatus);
            } else {
                // Standard payment method
                paymentStatus = determinePaymentStatusFromMethod(hoaDonDto.getPhuongThucThanhToan());
                log.debug("Setting payment status based on payment method {}: {}",
                        hoaDonDto.getPhuongThucThanhToan(), paymentStatus);
            }
            hoaDon.setTrangThaiThanhToan(paymentStatus);
        }
    }

    /**
     * Determine payment status based on payment method.
     * Gateway payments (VNPAY, MOMO) start as unpaid, cash payments are immediately paid.
     * Mixed payments always start as unpaid and are processed separately.
     */
    private TrangThaiThanhToan determinePaymentStatusFromMethod(PhuongThucThanhToan phuongThucThanhToan) {
        if (phuongThucThanhToan == null) {
            log.warn("Payment method is null, defaulting to CHUA_THANH_TOAN");
            return TrangThaiThanhToan.CHUA_THANH_TOAN;
        }

        switch (phuongThucThanhToan) {
            case VNPAY:
            case MOMO:
                // Gateway payments start as unpaid - payment processing happens after order creation
                return TrangThaiThanhToan.CHUA_THANH_TOAN;
            case TIEN_MAT:
                // Cash payments are immediately considered paid
                return TrangThaiThanhToan.DA_THANH_TOAN;
            default:
                log.warn("Unknown payment method: {}, defaulting to CHUA_THANH_TOAN", phuongThucThanhToan);
                return TrangThaiThanhToan.CHUA_THANH_TOAN;
        }
    }

    /**
     * Determine payment status based on payment method string (for mixed payments).
     * Mixed payments always start as unpaid and are processed through the mixed payment endpoint.
     */
    private TrangThaiThanhToan determinePaymentStatusFromMethod(String phuongThucThanhToan) {
        if (phuongThucThanhToan == null) {
            log.warn("Payment method is null, defaulting to CHUA_THANH_TOAN");
            return TrangThaiThanhToan.CHUA_THANH_TOAN;
        }

        // Handle mixed payment scenario
        if ("MIXED".equals(phuongThucThanhToan)) {
            // Mixed payments always start as unpaid and are processed separately
            log.debug("Mixed payment detected, setting status to CHUA_THANH_TOAN");
            return TrangThaiThanhToan.CHUA_THANH_TOAN;
        }

        // Handle standard payment methods
        try {
            PhuongThucThanhToan paymentMethod = PhuongThucThanhToan.valueOf(phuongThucThanhToan);
            return determinePaymentStatusFromMethod(paymentMethod);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown payment method: {}, defaulting to CHUA_THANH_TOAN", phuongThucThanhToan);
            return TrangThaiThanhToan.CHUA_THANH_TOAN;
        }
    }

    /**
     * Set appropriate order status for TAI_QUAY orders based on shipping requirements.
     * TAI_QUAY orders with shipping enabled are set to DA_XAC_NHAN status.
     * TAI_QUAY orders without shipping (pickup) maintain CHO_XAC_NHAN status.
     */
    private void setTaiQuayOrderStatus(HoaDon hoaDon) {
        // Check if order has shipping enabled (delivery address is set)
        boolean hasShipping = hoaDon.getDiaChiGiaoHang() != null;

        if (hasShipping) {
            // TAI_QUAY orders with shipping enabled are automatically confirmed
            hoaDon.setTrangThaiDonHang(TrangThaiDonHang.DA_XAC_NHAN);
            log.info("TAI_QUAY order with shipping automatically set to DA_XAC_NHAN status");
        } else {
            // TAI_QUAY orders without shipping (pickup at store) start as pending confirmation
            hoaDon.setTrangThaiDonHang(TrangThaiDonHang.CHO_XAC_NHAN);
            log.info("TAI_QUAY order for pickup at store set to CHO_XAC_NHAN status");
        }
    }

    /**
     * Create audit entry for TAI_QUAY order status logic with Vietnamese messages.
     */
    private void createTaiQuayOrderStatusAuditEntry(HoaDon hoaDon) {
        boolean hasShipping = hoaDon.getDiaChiGiaoHang() != null;
        String auditMessage;

        if (hasShipping) {
            auditMessage = "Đơn hàng tại quầy có giao hàng - tự động chuyển trạng thái thành DA_XAC_NHAN";
        } else {
            auditMessage = "Đơn hàng tại quầy lấy tại cửa hàng - thiết lập trạng thái CHO_XAC_NHAN";
        }

        HoaDonAuditHistory statusAuditEntry = HoaDonAuditHistory.createEntry(
                hoaDon.getId(),
                createAuditValues(hoaDon),
                hoaDon.getNguoiTao(),
                auditMessage
        );
        auditHistoryRepository.save(statusAuditEntry);

        log.info("Created TAI_QUAY order status audit entry for order {} - {}", hoaDon.getId(), auditMessage);
    }

    /**
     * Validate payment method is appropriate for order type.
     * Updated to support mixed payment scenarios.
     */
    private void validatePaymentMethodForOrderType(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        PhuongThucThanhToan paymentMethod = hoaDonDto.getPhuongThucThanhToan();

        if (paymentMethod == null) {
            throw new IllegalArgumentException("Payment method is required for order creation");
        }

        LoaiHoaDon orderType = hoaDon.getLoaiHoaDon();

        // Validate payment method compatibility with order type
        switch (paymentMethod) {
            case TIEN_MAT:
                // Cash payments are valid for all order types
                if (orderType == LoaiHoaDon.TAI_QUAY) {
                    log.debug("Cash payment validated for POS order");
                } else {
                    log.debug("Cash on delivery payment validated for online order");
                }
                break;
            case VNPAY:
            case MOMO:
                // Gateway payments are valid for all order types
                log.debug("Gateway payment {} validated for {} order", paymentMethod, orderType);
                break;
            default:
                throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
        }

        log.debug("Payment method validation successful: {} for {} order", paymentMethod, orderType);
    }

    /**
     * Validate payment method is appropriate for order type, including mixed payments.
     * Overloaded method to handle string-based payment method validation for mixed payments.
     */
    private void validatePaymentMethodForOrderType(HoaDon hoaDon, String phuongThucThanhToan) {
        if (phuongThucThanhToan == null) {
            throw new IllegalArgumentException("Payment method is required for order creation");
        }

        LoaiHoaDon orderType = hoaDon.getLoaiHoaDon();

        // Handle mixed payment scenario
        if ("MIXED".equals(phuongThucThanhToan)) {
            // Mixed payments are valid for all order types
            log.debug("Mixed payment validated for {} order", orderType);
            return;
        }

        // Handle standard payment methods
        try {
            PhuongThucThanhToan paymentMethod = PhuongThucThanhToan.valueOf(phuongThucThanhToan);

            switch (paymentMethod) {
                case TIEN_MAT:
                    // Cash payments are valid for all order types
                    if (orderType == LoaiHoaDon.TAI_QUAY) {
                        log.debug("Cash payment validated for POS order");
                    } else {
                        log.debug("Cash on delivery payment validated for online order");
                    }
                    break;
                case VNPAY:
                case MOMO:
                    // Gateway payments are valid for all order types
                    log.debug("Gateway payment {} validated for {} order", paymentMethod, orderType);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported payment method: " + paymentMethod);
            }

            log.debug("Payment method validation successful: {} for {} order", paymentMethod, orderType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported payment method: " + phuongThucThanhToan);
        }
    }

    @Transactional(readOnly = true)
    public HoaDonDto getHoaDonById(Long id) {
        HoaDon hoaDon = hoaDonRepository.findByIdWithStaffAndCustomer(id)
                .orElseThrow(() -> new EntityNotFoundException("Hóa đơn không tồn tại với ID: " + id));
        return toDtoWithPaymentMethod(hoaDon);
    }

    /**
     * Get order by ID with security check.
     * Users can only access their own orders, admins can access any order.
     */
    @Transactional(readOnly = true)
    public HoaDonDto getHoaDonByIdSecure(Long id, NguoiDung currentUser) {
        HoaDon hoaDon = hoaDonRepository.findByIdWithStaffAndCustomer(id)
                .orElseThrow(() -> new EntityNotFoundException("Hóa đơn không tồn tại với ID: " + id));

        return toDtoWithPaymentMethod(hoaDon);
    }

    /**
     * Check if a user can access a specific order.
     * Users can access their own orders, admins can access any order.
     */
    public boolean isOrderAccessible(HoaDon hoaDon, NguoiDung currentUser) {
        if (currentUser == null) {
            return false;
        }

        // Admin can access any order
        if (isAdmin(currentUser)) {
            return true;
        }

        // Staff can access orders they are assigned to or any order (depending on business rules)
        if (isStaff(currentUser)) {
            return true; // For now, staff can access all orders
        }

        // Customer can only access their own orders
        return hoaDon.getKhachHang() != null &&
                hoaDon.getKhachHang().getId().equals(currentUser.getId());
    }

    /**
     * Check if user is an admin.
     */
    private boolean isAdmin(NguoiDung user) {
        return user.getVaiTro() != null &&
                (user.getVaiTro().name().equals("ADMIN") || user.getVaiTro().name().equals("MANAGER"));
    }

    /**
     * Check if user is staff.
     */
    private boolean isStaff(NguoiDung user) {
        return user.getVaiTro() != null &&
                user.getVaiTro().name().equals("STAFF");
    }

    @Transactional
    public HoaDonDto updateHoaDon(Long id, HoaDonDto hoaDonDto, NguoiDung currentUser) {
        HoaDon existingHoaDon = hoaDonRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hóa đơn để cập nhật với ID: " + id));

        // Security check: user can only update their own orders or admin can update any
        if (!isOrderAccessible(existingHoaDon, currentUser)) {
            throw new SecurityException("Bạn không có quyền cập nhật hóa đơn này");
        }

        // Prevent certain status updates if the order is in a final state (e.g., completed, cancelled)
        // This logic needs to be very specific based on business rules.
        // Example: if (existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.HOAN_THANH || existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.DA_HUY) {
        //     throw new IllegalStateException("Không thể cập nhật hóa đơn đã hoàn thành hoặc đã hủy.");
        // }

        // Map basic fields from DTO, but preserve critical existing data
        // Note: MapStruct might be too aggressive here if not configured carefully for updates.
        // A more controlled approach might be to manually set fields.

        TrangThaiDonHang oldTrangThaiDonHang = existingHoaDon.getTrangThaiDonHang();
        TrangThaiThanhToan oldTrangThaiThanhToan = existingHoaDon.getTrangThaiThanhToan();

        // Update delivery address if provided
        if (hoaDonDto.getDiaChiGiaoHangId() != null || hoaDonDto.getDiaChiGiaoHang() != null) {
            validateAndSetDeliveryAddress(existingHoaDon, hoaDonDto);
        }

        // Update delivery contact information if provided
        if (hoaDonDto.getNguoiNhanTen() != null || hoaDonDto.getNguoiNhanSdt() != null) {
            setDeliveryContactInfo(existingHoaDon, hoaDonDto);
        }

        existingHoaDon.setPhiVanChuyen(hoaDonDto.getPhiVanChuyen() != null ? hoaDonDto.getPhiVanChuyen() : existingHoaDon.getPhiVanChuyen());
        existingHoaDon.setGiaTriGiamGiaVoucher(hoaDonDto.getGiaTriGiamGiaVoucher() != null ? hoaDonDto.getGiaTriGiamGiaVoucher() : existingHoaDon.getGiaTriGiamGiaVoucher());

        // Update NhanVien if ID is provided in DTO and different from existing
        if (hoaDonDto.getNhanVienId() != null) {
            if (existingHoaDon.getNhanVien() == null || !hoaDonDto.getNhanVienId().equals(existingHoaDon.getNhanVien().getId())) {
                NguoiDung nhanVien = nguoiDungRepository.findById(hoaDonDto.getNhanVienId())
                        .orElseThrow(() -> new EntityNotFoundException("Nhân viên không tồn tại với ID: " + hoaDonDto.getNhanVienId()));
                existingHoaDon.setNhanVien(nhanVien);
            }
        } else {
            existingHoaDon.setNhanVien(null); // Allow unsetting the staff if DTO provides null ID
        }

        // Status updates - should be handled carefully, possibly in a separate method
        // For now, allow direct update from DTO if provided
        if (hoaDonDto.getTrangThaiDonHang() != null) {
            existingHoaDon.setTrangThaiDonHang(hoaDonDto.getTrangThaiDonHang());
        }
        if (hoaDonDto.getTrangThaiThanhToan() != null) {
            existingHoaDon.setTrangThaiThanhToan(hoaDonDto.getTrangThaiThanhToan());
        }
        if (hoaDonDto.getLoaiHoaDon() != null) {
            existingHoaDon.setLoaiHoaDon(hoaDonDto.getLoaiHoaDon());
        }

        // Handle updates to HoaDonChiTiet (add, remove, update quantity)
        if (hoaDonDto.getChiTiet() != null && !hoaDonDto.getChiTiet().isEmpty()) {
            updateOrderLineItems(existingHoaDon, hoaDonDto);
        }

        // Recalculate totals after line item updates
        recalculateOrderTotals(existingHoaDon);

        // Save order with optimistic locking retry for HoaDonChiTiet updates
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(existingHoaDon),
                "HoaDon",
                existingHoaDon.getId()
        );

        // Send email if order status changed
        if (oldTrangThaiDonHang != savedHoaDon.getTrangThaiDonHang()) {
            String reason = "Cập nhật thông tin đơn hàng";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        // Send email if payment status changed
        if (oldTrangThaiThanhToan != savedHoaDon.getTrangThaiThanhToan()) {
            String reason = "Cập nhật trạng thái thanh toán";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    @Transactional(readOnly = true)
    public List<HoaDonDto> getAllHoaDons() {
        List<HoaDon> hoaDons = hoaDonRepository.findAll();
        return toDtoListWithPaymentMethod(hoaDons);
    }

    @Transactional(readOnly = true)
    public List<HoaDonDto> findByNguoiDungEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return List.of();
        }
        List<HoaDon> hoaDons = hoaDonRepository.findByKhachHang_Email(email);
        return toDtoListWithPaymentMethod(hoaDons);
    }

    /**
     * Cancel an order and release reserved inventory.
     * This method should be called when an order is cancelled before payment.
     */
    @Transactional
    public HoaDonDto cancelOrder(Long orderId, String reason) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        return cancelOrderInternal(hoaDon, reason);
    }

    /**
     * Cancel an order with security check.
     */
    @Transactional
    public HoaDonDto cancelOrderSecure(Long orderId, String reason, NguoiDung currentUser) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Security check: user can only cancel their own orders or admin can cancel any
        if (!isOrderAccessible(hoaDon, currentUser)) {
            throw new SecurityException("Bạn không có quyền hủy hóa đơn này");
        }

        return cancelOrderInternal(hoaDon, reason);
    }

    /**
     * Internal method to handle order cancellation logic.
     */
    private HoaDonDto cancelOrderInternal(HoaDon hoaDon, String reason) {
        TrangThaiDonHang oldTrangThaiDonHang = hoaDon.getTrangThaiDonHang();

        // Only allow cancellation of pending orders
        if (hoaDon.getTrangThaiDonHang() != TrangThaiDonHang.CHO_XAC_NHAN ) {
            throw new IllegalStateException("Cannot cancel order in status: " + hoaDon.getTrangThaiDonHang());
        }

        // Release inventory for all items in the order
        // Find items that are actually reserved for this order
        List<Long> itemIdsToRelease = serialNumberService.getReservedSerialNumberIdsForOrder(hoaDon.getId().toString());

        if (!itemIdsToRelease.isEmpty()) {
            // Use safe release method to avoid exceptions for items that aren't actually reserved
            serialNumberService.releaseReservationsSafely(itemIdsToRelease);
            log.info("Released {} reserved items for cancelled order {}", itemIdsToRelease.size(), hoaDon.getId());
        } else {
            log.info("No reserved items found to release for cancelled order {}", hoaDon.getId());
        }

        // Remove vouchers and decrement usage counts
        phieuGiamGiaService.removeVouchersFromOrder(hoaDon.getId());

        // Store old values for audit
        String oldValues = createAuditValues(hoaDon);

        // Update order status with optimistic locking retry
        hoaDon.setTrangThaiDonHang(TrangThaiDonHang.DA_HUY);
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(hoaDon),
                "HoaDon",
                hoaDon.getId()
        );

        // Create audit entry for cancellation
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.cancelEntry(
                savedHoaDon.getId(),
                oldValues,
                savedHoaDon.getNguoiCapNhat(),
                reason != null ? reason : "Hủy hóa đơn"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Order {} cancelled. Reason: {}", hoaDon.getId(), reason);

        // Send email if order status changed
        if (oldTrangThaiDonHang != savedHoaDon.getTrangThaiDonHang()) {
            String emailReason = reason != null ? reason : "Đơn hàng đã bị hủy.";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        emailReason
                );
                log.info("Đã gửi email thông báo hủy đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        emailReason
                );
                log.info("Đã gửi email thông báo hủy đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo hủy đơn hàng cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    /**
     * Confirm payment for an order and finalize the sale.
     * Supports flexible payment methods: TIEN_MAT, COD, and VNPAY.
     */
    @Transactional
    public HoaDonDto confirmPayment(Long orderId, PhuongThucThanhToan phuongThucThanhToan) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        return confirmPaymentInternal(hoaDon, phuongThucThanhToan);
    }

    /**
     * Confirm payment with security check.
     */
    @Transactional
    public HoaDonDto confirmPaymentSecure(Long orderId, PhuongThucThanhToan phuongThucThanhToan, NguoiDung currentUser) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Security check: user can only confirm payment for their own orders or admin can confirm any
        if (!isOrderAccessible(hoaDon, currentUser)) {
            throw new SecurityException("Bạn không có quyền xác nhận thanh toán cho hóa đơn này");
        }

        return confirmPaymentInternal(hoaDon, phuongThucThanhToan);
    }

    /**
     * Confirm a specific payment component in a mixed payment scenario.
     * This method adds the payment record and updates order status appropriately.
     * Used by payment gateway callbacks for mixed payments.
     */
    @Transactional
    public HoaDonDto confirmMixedPaymentComponent(Long orderId, PhuongThucThanhToan phuongThucThanhToan,
                                                  BigDecimal paymentAmount, String transactionRef) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        TrangThaiDonHang oldTrangThaiDonHang = hoaDon.getTrangThaiDonHang();
        TrangThaiThanhToan oldTrangThaiThanhToan = hoaDon.getTrangThaiThanhToan();

        // Validate payment method and order state
        validatePaymentConfirmation(hoaDon, phuongThucThanhToan);

        // Create payment record for this component
        String notes = String.format("Thanh toán %s - Đơn hàng %s", phuongThucThanhToan, hoaDon.getMaHoaDon());
        ThanhToan thanhToan = createPaymentRecord(paymentAmount, phuongThucThanhToan, transactionRef, notes, null);
        thanhToan = thanhToanRepository.save(thanhToan);

        // Link payment to order
        HoaDonThanhToan hoaDonThanhToan = new HoaDonThanhToan();
        HoaDonThanhToanId id = new HoaDonThanhToanId();
        id.setHoaDonId(orderId);
        id.setThanhToanId(thanhToan.getId());
        hoaDonThanhToan.setId(id);
        hoaDonThanhToan.setHoaDon(hoaDon);
        hoaDonThanhToan.setThanhToan(thanhToan);
        hoaDonThanhToan.setSoTienApDung(paymentAmount);

        hoaDonThanhToanRepository.save(hoaDonThanhToan);

        // Update order payment status based on total payments (handles partial/full payment automatically)
        updateOrderPaymentStatus(hoaDon);

        // Create audit entry for this payment component
        String auditMessage = String.format("Mixed payment component confirmed - Method: %s, Amount: %s, Transaction: %s",
                phuongThucThanhToan, paymentAmount, transactionRef);
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                orderId,
                createAuditValues(hoaDon),
                hoaDon.getNguoiTao(),
                auditMessage
        );
        auditHistoryRepository.save(auditEntry);

        // Save order with updated payment status
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(hoaDon),
                "HoaDon",
                hoaDon.getId()
        );

        // If order is now fully paid, handle inventory confirmation
        if (savedHoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            confirmInventorySale(savedHoaDon);

            // Update order status based on order type if fully paid
            if (savedHoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                savedHoaDon.setTrangThaiDonHang(TrangThaiDonHang.HOAN_THANH);
            } else {
                savedHoaDon.setTrangThaiDonHang(TrangThaiDonHang.DANG_XU_LY);
            }

            // Save again with updated order status
            final HoaDon finalHoaDon = savedHoaDon;
            savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                    () -> hoaDonRepository.save(finalHoaDon),
                    "HoaDon",
                    finalHoaDon.getId()
            );
        }

        log.info("Mixed payment component confirmed for order {} - Method: {}, Amount: {}, Status: {}",
                orderId, phuongThucThanhToan, paymentAmount, savedHoaDon.getTrangThaiThanhToan());

        // Send email if order status changed
        if (oldTrangThaiDonHang != savedHoaDon.getTrangThaiDonHang()) {
            String reason = "Cập nhật trạng thái đơn hàng sau thanh toán hỗn hợp";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        // Send email if payment status changed
        if (oldTrangThaiThanhToan != savedHoaDon.getTrangThaiThanhToan()) {
            String reason = "Cập nhật trạng thái thanh toán sau thanh toán hỗn hợp";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    /**
     * Update payment status with security check.
     */
    @Transactional
    public HoaDonDto updatePaymentStatusSecure(Long orderId, TrangThaiThanhToan trangThaiThanhToan, String ghiChu, NguoiDung currentUser) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Security check: user can only update payment status for their own orders or admin can update any
        if (!isOrderAccessible(hoaDon, currentUser)) {
            throw new SecurityException("Bạn không có quyền cập nhật trạng thái thanh toán cho hóa đơn này");
        }

        return updatePaymentStatusInternal(hoaDon, trangThaiThanhToan, ghiChu, currentUser);
    }

    /**
     * Internal method to handle payment status update logic.
     */
    private HoaDonDto updatePaymentStatusInternal(HoaDon hoaDon, TrangThaiThanhToan trangThaiThanhToan, String ghiChu, NguoiDung currentUser) {
        // Store old payment status for audit
        TrangThaiThanhToan oldPaymentStatus = hoaDon.getTrangThaiThanhToan();

        // Validate payment status transition
        validatePaymentStatusTransition(oldPaymentStatus, trangThaiThanhToan);

        // Update payment status
        hoaDon.setTrangThaiThanhToan(trangThaiThanhToan);

        // Handle specific payment status changes
        handlePaymentStatusChange(hoaDon, oldPaymentStatus, trangThaiThanhToan);

        // Save with optimistic locking retry for payment status updates
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(hoaDon),
                "HoaDon",
                hoaDon.getId()
        );

        // Create audit entry for payment status change
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.paymentStatusChangeEntry(
                savedHoaDon.getId(),
                oldPaymentStatus.name(),
                trangThaiThanhToan.name(),
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                ghiChu != null ? ghiChu : "Cập nhật trạng thái thanh toán"
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Payment status updated for order {} from {} to {} by user {}",
                hoaDon.getId(), oldPaymentStatus, trangThaiThanhToan,
                currentUser != null ? currentUser.getEmail() : "SYSTEM");

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    /**
     * Validate payment status transition.
     */
    private void validatePaymentStatusTransition(TrangThaiThanhToan fromStatus, TrangThaiThanhToan toStatus) {
        // Define valid transitions
        switch (fromStatus) {
            case CHUA_THANH_TOAN:
                if (toStatus != TrangThaiThanhToan.THANH_TOAN_MOT_PHAN &&
                        toStatus != TrangThaiThanhToan.DA_THANH_TOAN &&
                        toStatus != TrangThaiThanhToan.THANH_TOAN_LOI) {
                    throw new IllegalArgumentException("Invalid payment status transition from " + fromStatus + " to " + toStatus);
                }
                break;
            case THANH_TOAN_MOT_PHAN:
                if (toStatus != TrangThaiThanhToan.DA_THANH_TOAN &&
                        toStatus != TrangThaiThanhToan.THANH_TOAN_LOI &&
                        toStatus != TrangThaiThanhToan.CHUA_THANH_TOAN) {
                    throw new IllegalArgumentException("Invalid payment status transition from " + fromStatus + " to " + toStatus);
                }
                break;
            case DA_THANH_TOAN:
                if (toStatus != TrangThaiThanhToan.CHO_XU_LY_HOAN_TIEN &&
                        toStatus != TrangThaiThanhToan.DA_HOAN_TIEN) {
                    throw new IllegalArgumentException("Invalid payment status transition from " + fromStatus + " to " + toStatus);
                }
                break;
            case THANH_TOAN_LOI:
                if (toStatus != TrangThaiThanhToan.CHUA_THANH_TOAN &&
                        toStatus != TrangThaiThanhToan.THANH_TOAN_MOT_PHAN &&
                        toStatus != TrangThaiThanhToan.DA_THANH_TOAN) {
                    throw new IllegalArgumentException("Invalid payment status transition from " + fromStatus + " to " + toStatus);
                }
                break;
            case CHO_XU_LY_HOAN_TIEN:
                if (toStatus != TrangThaiThanhToan.DA_HOAN_TIEN &&
                        toStatus != TrangThaiThanhToan.DA_THANH_TOAN) {
                    throw new IllegalArgumentException("Invalid payment status transition from " + fromStatus + " to " + toStatus);
                }
                break;
            case DA_HOAN_TIEN:
                // Generally, refunded orders shouldn't change status
                throw new IllegalArgumentException("Cannot change payment status from refunded state");
        }
    }

    /**
     * Handle specific payment status changes.
     */
    private void handlePaymentStatusChange(HoaDon hoaDon, TrangThaiThanhToan oldStatus, TrangThaiThanhToan newStatus) {
        // Handle inventory implications
        if ((oldStatus == TrangThaiThanhToan.CHUA_THANH_TOAN || oldStatus == TrangThaiThanhToan.THANH_TOAN_MOT_PHAN)
                && newStatus == TrangThaiThanhToan.DA_THANH_TOAN) {
            // Payment confirmed - finalize inventory sale
            confirmInventorySale(hoaDon);
        } else if (newStatus == TrangThaiThanhToan.DA_HOAN_TIEN) {
            // Refund processed - release inventory back to available
            releaseInventoryForRefund(hoaDon);
        } else if (newStatus == TrangThaiThanhToan.THANH_TOAN_MOT_PHAN) {
            // Partial payment - keep inventory reserved but don't finalize sale yet
            log.info("Order {} moved to partial payment status - inventory remains reserved", hoaDon.getId());
        }
    }

    /**
     * Release inventory back to available when refund is processed.
     */
    private void releaseInventoryForRefund(HoaDon hoaDon) {
        List<Long> serialNumberIdsToRelease = new ArrayList<>();

        for (HoaDonChiTiet chiTiet : hoaDon.getHoaDonChiTiets()) {
            // Get sold serial numbers for this product variant
            List<SerialNumber> soldSerialNumbers = serialNumberService.getSoldSerialNumbers(
                    chiTiet.getSanPhamChiTiet().getId(), chiTiet.getSoLuong());

            for (SerialNumber serialNumber : soldSerialNumbers) {
                serialNumberIdsToRelease.add(serialNumber.getId());
            }
        }

        if (!serialNumberIdsToRelease.isEmpty()) {
            serialNumberService.releaseFromSold(serialNumberIdsToRelease, "system", "Hoàn trả đơn hàng");
            log.info("Released {} serial numbers back to inventory for refunded order {}",
                    serialNumberIdsToRelease.size(), hoaDon.getId());
        }
    }

    /**
     * Validate payment confirmation requirements.
     * Migrated from PaymentMethodValidationService to consolidate validation logic.
     */
    private void validatePaymentConfirmation(HoaDon hoaDon, PhuongThucThanhToan phuongThucThanhToan) {
        // Check if payment is already confirmed
        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalArgumentException("Đơn hàng đã được thanh toán");
        }

        // Check if order is cancelled
        if (hoaDon.getTrangThaiDonHang() == TrangThaiDonHang.DA_HUY) {
            throw new IllegalArgumentException("Không thể xác nhận thanh toán cho đơn hàng đã hủy");
        }

        // Cash payment delivery address validation
        if (phuongThucThanhToan == PhuongThucThanhToan.TIEN_MAT) {
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.ONLINE && hoaDon.getDiaChiGiaoHang() == null) {
                throw new IllegalArgumentException("Thanh toán tiền mặt cho đơn hàng online yêu cầu địa chỉ giao hàng");
            }

            // Warning for missing phone number (log as warning, don't throw exception)
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.ONLINE &&
                    (hoaDon.getNguoiNhanSdt() == null || hoaDon.getNguoiNhanSdt().trim().isEmpty())) {
                log.warn("Khuyến nghị có số điện thoại người nhận cho thanh toán tiền mặt khi giao hàng - Order ID: {}", hoaDon.getId());
            }
        }
    }

    /**
     * Internal method to handle payment confirmation logic.
     */
    private HoaDonDto confirmPaymentInternal(HoaDon hoaDon, PhuongThucThanhToan phuongThucThanhToan) {
        TrangThaiDonHang oldTrangThaiDonHang = hoaDon.getTrangThaiDonHang();
        TrangThaiThanhToan oldTrangThaiThanhToan = hoaDon.getTrangThaiThanhToan();

        // Enhanced payment method validation (migrated from PaymentMethodValidationService)
        validatePaymentConfirmation(hoaDon, phuongThucThanhToan);

        // Validate payment method matches order type (legacy validation)
        validatePaymentMethodForConfirmation(hoaDon, phuongThucThanhToan);

        // Update payment status
        hoaDon.setTrangThaiThanhToan(TrangThaiThanhToan.DA_THANH_TOAN);

        // Update order status based on payment method and order type
        if (phuongThucThanhToan == PhuongThucThanhToan.TIEN_MAT) {
            // Cash payments - handle both POS and delivery scenarios
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                // POS cash payments complete immediately
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.HOAN_THANH);
            } else {
                // Online orders with cash payment (former COD) - payment happens at delivery
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.DA_GIAO_HANG);
            }
        } else if (phuongThucThanhToan == PhuongThucThanhToan.VNPAY) {
            // VNPAY payments are processed immediately, move to processing for fulfillment
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                // POS orders with VNPAY can complete immediately if no delivery needed
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.HOAN_THANH);
            } else {
                // Online orders with VNPAY move to processing for shipping
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.DANG_XU_LY);
            }
        } else if (phuongThucThanhToan == PhuongThucThanhToan.MOMO) {
            // MoMo payments are processed immediately, similar to VNPAY
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
                // POS orders with MoMo can complete immediately if no delivery needed
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.HOAN_THANH);
            } else {
                // Online orders with MoMo move to processing for shipping
                hoaDon.setTrangThaiDonHang(TrangThaiDonHang.DANG_XU_LY);
            }
        }

        // Save with optimistic locking retry for payment confirmation
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(hoaDon),
                "HoaDon",
                hoaDon.getId()
        );

        // Confirm the sale in inventory (items are already reserved)
        confirmInventorySale(savedHoaDon);

        log.info("Payment confirmed for order {} using {}. Sale finalized.",
                hoaDon.getId(), phuongThucThanhToan);

        // Send email if order status changed
        if (oldTrangThaiDonHang != savedHoaDon.getTrangThaiDonHang()) {
            String reason = "Xác nhận thanh toán";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiDonHang.name(),
                        savedHoaDon.getTrangThaiDonHang().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        // Send email if payment status changed
        if (oldTrangThaiThanhToan != savedHoaDon.getTrangThaiThanhToan()) {
            String reason = "Xác nhận thanh toán";
            if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getKhachHang().getEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
            } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
                emailService.sendOrderStatusUpdateEmail(
                        savedHoaDon.getNguoiNhanEmail(),
                        savedHoaDon.getId(),
                        savedHoaDon.getMaHoaDon(),
                        oldTrangThaiThanhToan.name(),
                        savedHoaDon.getTrangThaiThanhToan().name(),
                        reason
                );
                log.info("Đã gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
            } else {
                log.warn("Không thể gửi email thông báo thay đổi trạng thái thanh toán cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
            }
        }

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    /**
     * Validate that payment method is appropriate for the order type.
     * Updated to support flexible payment scenarios based on business requirements.
     */
    private void validatePaymentMethodForConfirmation(HoaDon hoaDon, PhuongThucThanhToan phuongThucThanhToan) {
        // TIEN_MAT now supports both POS and delivery scenarios (consolidated from COD)
        if (phuongThucThanhToan == PhuongThucThanhToan.TIEN_MAT) {
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.ONLINE && hoaDon.getDiaChiGiaoHang() == null) {
                throw new IllegalArgumentException("Cash payment for online orders requires delivery address");
            }
            log.debug("Cash payment accepted for {} order", hoaDon.getLoaiHoaDon());
        }

        // VNPAY and other digital payment methods are flexible for both order types
        if (phuongThucThanhToan == PhuongThucThanhToan.VNPAY) {
            log.debug("VNPAY payment accepted for {} order", hoaDon.getLoaiHoaDon());
        }

        // MoMo digital payment method is flexible for both order types
        if (phuongThucThanhToan == PhuongThucThanhToan.MOMO) {
            log.debug("MoMo payment accepted for {} order", hoaDon.getLoaiHoaDon());
        }
    }

    /**
     * Confirm inventory sale for the order.
     * CRITICAL FIX: Handle different serial number assignment logic for TAI_QUAY vs ONLINE orders:
     * - TAI_QUAY orders: Use specific reserved serial numbers (customer selected)
     * - ONLINE orders: Use random available serial numbers (customer doesn't choose)
     *
     * DOUBLE CONFIRMATION PREVENTION: Check if inventory is already confirmed before proceeding
     * to prevent the bug where TAI_QUAY orders get their inventory confirmed twice.
     */
    private void confirmInventorySale(HoaDon hoaDon) {
        // CRITICAL FIX: Check if inventory is already confirmed to prevent double confirmation
        if (isInventoryAlreadyConfirmed(hoaDon)) {
            log.info("Bỏ qua xác nhận bán cho đơn hàng {} - inventory đã được xác nhận trước đó", hoaDon.getId());

            // Create audit entry for skipped confirmation
            HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                    hoaDon.getId(),
                    createAuditValues(hoaDon),
                    "system",
                    "Bỏ qua xác nhận bán - inventory đã được xác nhận trước đó (ngăn chặn xác nhận kép)"
            );
            auditHistoryRepository.save(auditEntry);

            return; // Skip confirmation to prevent double confirmation bug
        }

        if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY) {
            // TAI_QUAY orders: Use specific reserved serial numbers that customer selected
            List<Long> reservedItemIds = serialNumberService.getReservedSerialNumberIdsForOrder(
                    hoaDon.getId().toString());

            if (!reservedItemIds.isEmpty()) {
                serialNumberService.confirmSale(reservedItemIds, hoaDon.getId().toString(), "system");
                log.info("Xác nhận bán {} serial number đã chọn cho đơn hàng tại quầy {}", reservedItemIds.size(), hoaDon.getId());
            } else {
                log.warn("Không tìm thấy serial number đã đặt trước cho đơn hàng tại quầy {} trong quá trình xác nhận bán", hoaDon.getId());
            }
        } else {
            // ONLINE orders: Confirm the serial numbers that were reserved for this order
            List<Long> reservedItemIds = serialNumberService.getReservedSerialNumberIdsForOrder(
                    hoaDon.getId().toString());

            if (!reservedItemIds.isEmpty()) {
                serialNumberService.confirmSale(reservedItemIds, hoaDon.getId().toString(), "system");
                log.info("Xác nhận bán {} serial number đã đặt trước cho đơn hàng online {}", reservedItemIds.size(), hoaDon.getId());
            } else {
                log.warn("Không tìm thấy serial number đã đặt trước cho đơn hàng online {} trong quá trình xác nhận bán", hoaDon.getId());
            }
        }
    }

    /**
     * Check if inventory for the order has already been confirmed (serial numbers marked as SOLD).
     * This prevents double inventory confirmation which causes the serial number corruption bug.
     *
     * @param hoaDon The order to check
     * @return true if any serial numbers for this order are already marked as SOLD
     */
    private boolean isInventoryAlreadyConfirmed(HoaDon hoaDon) {
        try {
            // Get all serial numbers associated with this order (both RESERVED and SOLD)
            List<SerialNumber> orderSerialNumbers = serialNumberService.getSerialNumbersByOrderId(hoaDon.getId().toString());

            if (orderSerialNumbers.isEmpty()) {
                log.debug("Không tìm thấy serial number nào cho đơn hàng {}", hoaDon.getId());
                return false;
            }

            // Check if any serial numbers are already marked as SOLD
            long soldCount = orderSerialNumbers.stream()
                    .filter(SerialNumber::isSold)
                    .count();

            if (soldCount > 0) {
                log.info("Phát hiện {} serial number đã được đánh dấu SOLD cho đơn hàng {} - inventory đã được xác nhận",
                        soldCount, hoaDon.getId());

                // Log details for debugging
                List<String> soldSerialNumbers = orderSerialNumbers.stream()
                        .filter(SerialNumber::isSold)
                        .map(SerialNumber::getSerialNumberValue)
                        .collect(Collectors.toList());
                log.debug("Serial numbers đã SOLD cho đơn hàng {}: {}", hoaDon.getId(), soldSerialNumbers);

                return true;
            }

            log.debug("Tất cả {} serial number cho đơn hàng {} vẫn ở trạng thái RESERVED - chưa được xác nhận",
                    orderSerialNumbers.size(), hoaDon.getId());
            return false;

        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra trạng thái inventory cho đơn hàng {}: {}", hoaDon.getId(), e.getMessage());
            // In case of error, allow confirmation to proceed to avoid blocking legitimate operations
            return false;
        }
    }

    /**
     * Cập nhật trạng thái hóa đơn với kiểm tra và audit trail.
     * Phương thức này thực thi quy tắc kinh doanh và tạo lịch sử audit.
     *
     * @param hoaDonId ID hóa đơn cần cập nhật
     * @param trangThaiMoi Trạng thái mới cần đặt
     * @param lyDo Lý do thay đổi trạng thái
     * @param nguoiDungHienTai Người dùng thực hiện thay đổi
     * @param diaChiIp Địa chỉ IP của người dùng
     * @return DTO hóa đơn đã cập nhật
     */
    @Transactional
    public HoaDonDto capNhatTrangThaiHoaDon(Long hoaDonId, TrangThaiDonHang trangThaiMoi, String lyDo,
                                            NguoiDung nguoiDungHienTai, String diaChiIp) {
        HoaDon hoaDon = hoaDonRepository.findById(hoaDonId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hóa đơn với ID: " + hoaDonId));

        TrangThaiDonHang trangThaiHienTai = hoaDon.getTrangThaiDonHang();

        // Kiểm tra việc chuyển đổi trạng thái
        KiemTraTrangThaiHoaDonService.KetQuaKiemTra ketQuaKiemTra =
                kiemTraTrangThaiService.kiemTraChuyenDoi(trangThaiHienTai, trangThaiMoi, nguoiDungHienTai, false);

        if (!ketQuaKiemTra.isHopLe()) {
            throw new IllegalStateException("Chuyển đổi trạng thái không được phép: " + ketQuaKiemTra.getThongBao());
        }

        // Cập nhật trạng thái
        hoaDon.setTrangThaiDonHang(trangThaiMoi);

        // Lưu hóa đơn đã cập nhật với optimistic locking retry
        HoaDon savedHoaDon = optimisticLockingService.executeWithRetryAndConstraintHandling(
                () -> hoaDonRepository.save(hoaDon),
                "HoaDon",
                hoaDon.getId()
        );

        // Create audit history entry for status change
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.statusChangeEntry(
                savedHoaDon.getId(),
                trangThaiHienTai.name(),
                trangThaiMoi.name(),
                nguoiDungHienTai != null ? nguoiDungHienTai.getEmail() : "SYSTEM",
                lyDo
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Trạng thái hóa đơn {} đã được cập nhật từ {} thành {} bởi người dùng {}",
                savedHoaDon.getId(), trangThaiHienTai, trangThaiMoi,
                nguoiDungHienTai != null ? nguoiDungHienTai.getEmail() : "SYSTEM");

        // Gửi email thông báo thay đổi trạng thái
        if (savedHoaDon.getKhachHang() != null && savedHoaDon.getKhachHang().getEmail() != null && !savedHoaDon.getKhachHang().getEmail().trim().isEmpty()) {
            emailService.sendOrderStatusUpdateEmail(
                    savedHoaDon.getKhachHang().getEmail(),
                    savedHoaDon.getId(),
                    savedHoaDon.getMaHoaDon(),
                    trangThaiHienTai.name(),
                    trangThaiMoi.name(),
                    lyDo
            );
            log.info("Đã gửi email thông báo thay đổi trạng thái cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getKhachHang().getEmail());
        } else if (savedHoaDon.getNguoiNhanEmail() != null && !savedHoaDon.getNguoiNhanEmail().trim().isEmpty()) {
            emailService.sendOrderStatusUpdateEmail(
                    savedHoaDon.getNguoiNhanEmail(),
                    savedHoaDon.getId(),
                    savedHoaDon.getMaHoaDon(),
                    trangThaiHienTai.name(),
                    trangThaiMoi.name(),
                    lyDo
            );
            log.info("Đã gửi email thông báo thay đổi trạng thái cho đơn hàng {} tới {}", savedHoaDon.getId(), savedHoaDon.getNguoiNhanEmail());
        } else {
            log.warn("Không thể gửi email thông báo thay đổi trạng thái cho đơn hàng {}: không tìm thấy email khách hàng hoặc người nhận.", savedHoaDon.getId());
        }

        return toDtoWithPaymentMethod(savedHoaDon);
    }

    /**
     * Lấy các chuyển đổi trạng thái được phép cho một hóa đơn.
     *
     * @param hoaDonId ID hóa đơn
     * @param nguoiDungHienTai Người dùng yêu cầu các chuyển đổi
     * @return Danh sách các chuyển đổi được phép
     */
    @Transactional(readOnly = true)
    public List<TrangThaiDonHang> layCacChuyenDoiTrangThaiChoPhep(Long hoaDonId, NguoiDung nguoiDungHienTai) {
        HoaDon hoaDon = hoaDonRepository.findById(hoaDonId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hóa đơn với ID: " + hoaDonId));

        return kiemTraTrangThaiService.layCacChuyenDoiChoPhep(hoaDon.getTrangThaiDonHang(), nguoiDungHienTai)
                .stream()
                .map(chuyenDoi -> chuyenDoi.getTrangThaiDen())
                .toList();
    }

    /**
     * Validate and set delivery address for the order.
     * Supports both existing address ID and new address creation.
     */
    private void validateAndSetDeliveryAddress(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        // If diaChiGiaoHangId is provided, use existing address
        if (hoaDonDto.getDiaChiGiaoHangId() != null) {
            DiaChi diaChi = diaChiRepository.findById(hoaDonDto.getDiaChiGiaoHangId())
                    .orElseThrow(() -> new EntityNotFoundException("Địa chỉ giao hàng không tồn tại với ID: " + hoaDonDto.getDiaChiGiaoHangId()));

            // Validate that the address belongs to the customer
            if (hoaDon.getKhachHang() != null && !diaChi.getNguoiDung().getId().equals(hoaDon.getKhachHang().getId())) {
                throw new IllegalArgumentException("Địa chỉ giao hàng không thuộc về khách hàng này");
            }

            hoaDon.setDiaChiGiaoHang(diaChi);
        }
        // If diaChiGiaoHang object is provided, create new address or use existing
        else if (hoaDonDto.getDiaChiGiaoHang() != null) {
            DiaChi diaChi;

            if (hoaDonDto.getDiaChiGiaoHang().getId() != null) {
                // Use existing address
                diaChi = diaChiRepository.findById(hoaDonDto.getDiaChiGiaoHang().getId())
                        .orElseThrow(() -> new EntityNotFoundException("Địa chỉ giao hàng không tồn tại với ID: " + hoaDonDto.getDiaChiGiaoHang().getId()));

                // Validate ownership
                if (hoaDon.getKhachHang() != null && !diaChi.getNguoiDung().getId().equals(hoaDon.getKhachHang().getId())) {
                    throw new IllegalArgumentException("Địa chỉ giao hàng không thuộc về khách hàng này");
                }
            } else {


                diaChi = DiaChi.builder()
                        .nguoiDung(hoaDon.getKhachHang())
                        .duong(hoaDonDto.getDiaChiGiaoHang().getDuong())
                        .phuongXa(hoaDonDto.getDiaChiGiaoHang().getPhuongXa())
                        .quanHuyen(hoaDonDto.getDiaChiGiaoHang().getQuanHuyen())
                        .tinhThanh(hoaDonDto.getDiaChiGiaoHang().getTinhThanh())
                        .quocGia(hoaDonDto.getDiaChiGiaoHang().getQuocGia() != null ? hoaDonDto.getDiaChiGiaoHang().getQuocGia() : "Việt Nam")
                        .loaiDiaChi(hoaDonDto.getDiaChiGiaoHang().getLoaiDiaChi())
                        .laMacDinh(false) // New addresses for orders are not default
                        .build();

                diaChi = diaChiRepository.save(diaChi);
            }

            hoaDon.setDiaChiGiaoHang(diaChi);
        }
        // For POS orders, delivery address might not be required
        else if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.ONLINE) {
            throw new IllegalArgumentException("Địa chỉ giao hàng là bắt buộc cho đơn hàng online");
        }

        // Set delivery contact information
        setDeliveryContactInfo(hoaDon, hoaDonDto);
    }

    /**
     * Set delivery contact information for the order.
     * Defaults to customer's information if not provided.
     */
    private void setDeliveryContactInfo(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        // Use provided delivery contact info, or default to customer's info
        if (hoaDonDto.getNguoiNhanTen() != null && !hoaDonDto.getNguoiNhanTen().trim().isEmpty()) {
            hoaDon.setNguoiNhanTen(hoaDonDto.getNguoiNhanTen());
        } else if (hoaDon.getKhachHang() != null) {
            hoaDon.setNguoiNhanTen(hoaDon.getKhachHang().getHoTen());
        }

        if (hoaDonDto.getNguoiNhanSdt() != null && !hoaDonDto.getNguoiNhanSdt().trim().isEmpty()) {
            hoaDon.setNguoiNhanSdt(hoaDonDto.getNguoiNhanSdt());
        } else if (hoaDon.getKhachHang() != null) {
            hoaDon.setNguoiNhanSdt(hoaDon.getKhachHang().getSoDienThoai());
        }
    }

    /**
     * Update order line items with inventory management.
     * This handles adding, removing, and updating quantities of line items.
     */
    /**
     * Helper method to get existing serial numbers for an order
     * Used to distinguish between existing SOLD serial numbers and new items requiring reservation
     */
    private Set<String> getExistingOrderSerialNumbers(Long orderId) {
        try {
            List<SerialNumber> existingSerialNumbers = serialNumberService.getSerialNumbersByOrderId(orderId.toString());
            Set<String> serialNumberValues = existingSerialNumbers.stream()
                    .map(SerialNumber::getSerialNumberValue)
                    .collect(Collectors.toSet());

            log.debug("Found {} existing serial numbers for order {}: {}",
                     serialNumberValues.size(), orderId, serialNumberValues);
            return serialNumberValues;
        } catch (Exception e) {
            log.error("Failed to fetch existing serial numbers for order {}: {}", orderId, e.getMessage());
            // Return empty set to be safe - this will cause all items to be treated as new
            // which is safer than potentially missing existing items
            return new HashSet<>();
        }
    }

    @Transactional
    public void updateOrderLineItems(HoaDon existingHoaDon, HoaDonDto hoaDonDto) {
        // Only allow line item updates for orders that haven't been shipped
        if (existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.DANG_GIAO_HANG ||
                existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.DA_GIAO_HANG ||
                existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.HOAN_THANH ||
                existingHoaDon.getTrangThaiDonHang() == TrangThaiDonHang.DA_HUY) {
            throw new IllegalStateException("Cannot modify line items for orders in status: " + existingHoaDon.getTrangThaiDonHang());
        }

        log.info("Starting order line items update for order {} with {} new items",
                 existingHoaDon.getId(), hoaDonDto.getChiTiet() != null ? hoaDonDto.getChiTiet().size() : 0);

        // Get current line items
        List<HoaDonChiTiet> currentItems = new ArrayList<>(existingHoaDon.getHoaDonChiTiets());
        List<HoaDonChiTietDto> newItems = hoaDonDto.getChiTiet();

        // Track inventory changes
        List<Long> itemsToRelease = new ArrayList<>();

        // Process removals and updates
        for (HoaDonChiTiet currentItem : currentItems) {
            boolean found = false;
            for (HoaDonChiTietDto newItem : newItems) {
                if (newItem.getId() != null && newItem.getId().equals(currentItem.getId())) {
                    found = true;
                    // Update existing item
                    if (!newItem.getSoLuong().equals(currentItem.getSoLuong())) {
                        int quantityDiff = newItem.getSoLuong() - currentItem.getSoLuong();
                        if (quantityDiff > 0) {
                            // Need to reserve more items - check availability first
                            int availableQuantity = serialNumberService.getAvailableQuantityByVariant(currentItem.getSanPhamChiTiet().getId());
                            if (availableQuantity < quantityDiff) {
                                throw new IllegalArgumentException("Insufficient inventory to increase quantity for product: " +
                                        currentItem.getSanPhamChiTiet().getId() + ". Requested: " + quantityDiff + ", Available: " + availableQuantity);
                            }
                            log.info("Validated availability for {} additional items for product {}", quantityDiff, currentItem.getSanPhamChiTiet().getId());
                        } else if (quantityDiff < 0) {
                            // Need to release some items
                            int itemsToReleaseCount = Math.abs(quantityDiff);
                            List<SerialNumber> availableSerialNumbers = serialNumberService.getAvailableSerialNumbers(
                                    currentItem.getSanPhamChiTiet().getId());
                            for (int i = 0; i < Math.min(itemsToReleaseCount, availableSerialNumbers.size()); i++) {
                                itemsToRelease.add(availableSerialNumbers.get(i).getId());
                            }
                        }

                        // Update quantity and recalculate line total
                        currentItem.setSoLuong(newItem.getSoLuong());
                        BigDecimal lineTotal = currentItem.getGiaBan().multiply(BigDecimal.valueOf(newItem.getSoLuong()));
                        currentItem.setThanhTien(lineTotal);
                    }
                    break;
                }
            }

            if (!found) {
                // Item was removed, release its inventory
                List<SerialNumber> availableSerialNumbers = serialNumberService.getAvailableSerialNumbers(
                        currentItem.getSanPhamChiTiet().getId());
                for (int i = 0; i < Math.min(currentItem.getSoLuong(), availableSerialNumbers.size()); i++) {
                    itemsToRelease.add(availableSerialNumbers.get(i).getId());
                }
                existingHoaDon.getHoaDonChiTiets().remove(currentItem);
            }
        }

        // Apply inventory releases
        if (!itemsToRelease.isEmpty()) {
            serialNumberService.releaseReservations(itemsToRelease, "system", "Order update");
            log.info("Released {} items during order update", itemsToRelease.size());
        }

        // Process additions - handle new items without IDs
        List<Long> itemsToReserve = new ArrayList<>();
        for (HoaDonChiTietDto newItem : newItems) {
            if (newItem.getId() == null) {
                // This is a new item to be added
                log.info("Adding new item to order: sanPhamChiTietId={}, soLuong={}",
                        newItem.getSanPhamChiTietId(), newItem.getSoLuong());

                // Validate product variant exists
                SanPhamChiTiet sanPhamChiTiet = sanPhamChiTietRepository.findById(newItem.getSanPhamChiTietId())
                        .orElseThrow(() -> new EntityNotFoundException("Sản phẩm chi tiết không tồn tại với ID: " + newItem.getSanPhamChiTietId()));

                // Check inventory availability
                int availableQuantity = serialNumberService.getAvailableQuantityByVariant(newItem.getSanPhamChiTietId());
                if (availableQuantity < newItem.getSoLuong()) {
                    throw new IllegalArgumentException("Insufficient inventory for product: " +
                            sanPhamChiTiet.getSanPham().getTenSanPham() +
                            ". Available: " + availableQuantity + ", Requested: " + newItem.getSoLuong());
                }

                // Create new HoaDonChiTiet entity
                HoaDonChiTiet newChiTiet = new HoaDonChiTiet();
                newChiTiet.setHoaDon(existingHoaDon);
                newChiTiet.setSanPhamChiTiet(sanPhamChiTiet);
                newChiTiet.setSoLuong(newItem.getSoLuong());

                // Set price from DTO or use current product price
                BigDecimal giaBan = newItem.getGiaBan() != null ? newItem.getGiaBan() :
                        (sanPhamChiTiet.getGiaKhuyenMai() != null && sanPhamChiTiet.getGiaKhuyenMai().compareTo(BigDecimal.ZERO) > 0 ?
                                sanPhamChiTiet.getGiaKhuyenMai() : sanPhamChiTiet.getGiaBan());
                newChiTiet.setGiaBan(giaBan);

                // Set original price (required field)
                newChiTiet.setGiaGoc(sanPhamChiTiet.getGiaBan());

                // Calculate line total
                BigDecimal thanhTien = giaBan.multiply(BigDecimal.valueOf(newItem.getSoLuong()));
                newChiTiet.setThanhTien(thanhTien);

                // Set snapshot fields for audit trail (required fields)
                if (sanPhamChiTiet.getSanPham() != null) {
                    newChiTiet.setTenSanPhamSnapshot(sanPhamChiTiet.getSanPham().getTenSanPham());
                }
                newChiTiet.setSkuSnapshot(sanPhamChiTiet.getSku());
                if (sanPhamChiTiet.getHinhAnh() != null && !sanPhamChiTiet.getHinhAnh().isEmpty()) {
                    newChiTiet.setHinhAnhSnapshot(sanPhamChiTiet.getHinhAnh().get(0));
                }

                // Add to order
                existingHoaDon.getHoaDonChiTiets().add(newChiTiet);

                // Reserve inventory for new items - always reserve available serial numbers
                List<SerialNumber> availableSerialNumbers = serialNumberService.getAvailableSerialNumbers(newItem.getSanPhamChiTietId());
                for (int i = 0; i < Math.min(newItem.getSoLuong(), availableSerialNumbers.size()); i++) {
                    itemsToReserve.add(availableSerialNumbers.get(i).getId());
                }

                log.info("Added new item to order {}: {} x {} = {}",
                        existingHoaDon.getId(), sanPhamChiTiet.getSanPham().getTenSanPham(),
                        newItem.getSoLuong(), thanhTien);
            }
        }

        // Apply inventory reservations for new items
        if (!itemsToReserve.isEmpty()) {
            // CRITICAL FIX: Get existing serial numbers to avoid re-reserving SOLD items
            Set<String> existingSerialNumbers = getExistingOrderSerialNumbers(existingHoaDon.getId());
            log.info("Found {} existing serial numbers for order {}", existingSerialNumbers.size(), existingHoaDon.getId());

            // Create temporary order items for reservation, filtering out existing serial numbers
            List<HoaDonChiTietDto> tempOrderItems = new ArrayList<>();
            int filteredItemsCount = 0;

            for (HoaDonChiTietDto newItem : newItems) {
                if (newItem.getId() == null) {
                    // Check if this item has a serial number that already exists in the order
                    if (newItem.getSerialNumber() != null && existingSerialNumbers.contains(newItem.getSerialNumber())) {
                        log.debug("Skipping reservation for existing serial number: {} (already SOLD/committed to this order)",
                                 newItem.getSerialNumber());
                        filteredItemsCount++;
                    } else {
                        // This is a truly new item that needs reservation
                        tempOrderItems.add(newItem);
                        log.debug("Adding new item for reservation: sanPhamChiTietId={}, serialNumber={}",
                                 newItem.getSanPhamChiTietId(), newItem.getSerialNumber());
                    }
                }
            }

            log.info("Filtered out {} existing items, {} truly new items need reservation",
                     filteredItemsCount, tempOrderItems.size());

            // Reserve items using the existing service method - only for truly new items
            if (!tempOrderItems.isEmpty()) {
                serialNumberService.reserveItemsWithTracking(
                        tempOrderItems,
                        "ORDER_UPDATE",
                        existingHoaDon.getId().toString(),
                        "system"
                );
                log.info("Successfully reserved {} new items for order update", tempOrderItems.size());
            } else {
                log.info("No new items requiring reservation for order update");
            }
        }

        // Recalculate order totals
        recalculateOrderTotals(existingHoaDon);

        log.info("Updated line items for order {} - {} items total", existingHoaDon.getId(), existingHoaDon.getHoaDonChiTiets().size());
    }

    /**
     * Recalculate order totals based on current line items.
     */
    private void recalculateOrderTotals(HoaDon hoaDon) {
        BigDecimal tongTienHang = BigDecimal.ZERO;

        for (HoaDonChiTiet chiTiet : hoaDon.getHoaDonChiTiets()) {
            tongTienHang = tongTienHang.add(chiTiet.getThanhTien());
        }

        hoaDon.setTongTienHang(tongTienHang);

        BigDecimal phiVanChuyen = hoaDon.getPhiVanChuyen() != null ? hoaDon.getPhiVanChuyen() : BigDecimal.ZERO;
        BigDecimal giamGia = hoaDon.getGiaTriGiamGiaVoucher() != null ? hoaDon.getGiaTriGiamGiaVoucher() : BigDecimal.ZERO;

        BigDecimal tongThanhToan = tongTienHang.add(phiVanChuyen).subtract(giamGia);
        hoaDon.setTongThanhToan(tongThanhToan.max(BigDecimal.ZERO));
    }

    /**
     * Create JSON representation of entity for audit trail
     */
    private String createAuditValues(HoaDon entity) {
        return String.format(
                "{\"maHoaDon\":\"%s\",\"loaiHoaDon\":\"%s\",\"trangThaiDonHang\":\"%s\",\"trangThaiThanhToan\":\"%s\",\"tongThanhToan\":\"%s\",\"khachHangId\":\"%s\"}",
                entity.getMaHoaDon(),
                entity.getLoaiHoaDon(),
                entity.getTrangThaiDonHang(),
                entity.getTrangThaiThanhToan(),
                entity.getTongThanhToan(),
                entity.getKhachHang() != null ? entity.getKhachHang().getId() : null
        );
    }

    /**
     * Get audit history for a specific order
     */
    @Transactional(readOnly = true)
    public List<HoaDonAuditHistory> getAuditHistory(Long hoaDonId) {
        return auditHistoryRepository.findByHoaDonIdOrderByThoiGianThayDoiDesc(hoaDonId);
    }

    /**
     * Create VNPay payment URL for a specific order.
     * This method integrates VNPay payment with order management.
     * Updated to support mixed payment scenarios.
     */
    @Transactional
    public String createVNPayPayment(Long orderId, long amount, String orderInfo, String baseUrl, String clientIp) {
        // Validate order exists and is in correct state
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Validate order can be paid (updated for mixed payment support)
        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Order has already been fully paid");
        }

        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            throw new IllegalStateException("Cannot process payment for refunded order");
        }

        // Validate payment parameters using direct validation
        validateVNPayPaymentParameters(orderId, amount, orderInfo, baseUrl);

        // Create VNPay payment URL using direct VNPayService call
        String vnpayUrl = vnPayService.createOrderWithOrderId(amount, orderInfo, baseUrl, orderId.toString(), clientIp);

        // Create audit entry for payment attempt
        String auditMessage = String.format("VNPay payment initiated - Amount: %d, OrderInfo: %s", amount, orderInfo);
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                orderId,
                createAuditValues(hoaDon),
                hoaDon.getNguoiTao(),
                auditMessage
        );
        auditHistoryRepository.save(auditEntry);

        log.info("VNPay payment URL created for order {} with amount {}", orderId, amount);
        return vnpayUrl;
    }

    // ==================== MIXED PAYMENT SCENARIOS SUPPORT ====================

    /**
     * Add a payment to an order, supporting mixed payment scenarios.
     * Automatically calculates payment completion and updates order status.
     * This method maintains backward compatibility for single payment scenarios.
     */
    @Transactional
    public HoaDonDto addPaymentToOrder(Long orderId, BigDecimal paymentAmount, PhuongThucThanhToan paymentMethod,
                                       String transactionRef, String notes, NguoiDung currentUser) {
        // Call the enhanced method with zero cumulative payments for backward compatibility
        return addPaymentToOrder(orderId, paymentAmount, paymentMethod, transactionRef, notes, currentUser, BigDecimal.ZERO);
    }

    /**
     * Add a payment to an order with cumulative payment tracking for mixed payment scenarios.
     * Automatically calculates payment completion and updates order status.
     * This enhanced method supports cumulative payment tracking to address transaction visibility issues.
     *
     * @param orderId The ID of the order to add payment to
     * @param paymentAmount The amount of the payment being added
     * @param paymentMethod The payment method being used
     * @param transactionRef The transaction reference for the payment
     * @param notes Additional notes for the payment
     * @param currentUser The user processing the payment
     * @param cumulativePayments The cumulative amount of payments already processed
     *                          within the current transaction context
     * @return HoaDonDto representing the updated order
     */
    @Transactional
    public HoaDonDto addPaymentToOrder(Long orderId, BigDecimal paymentAmount, PhuongThucThanhToan paymentMethod,
                                       String transactionRef, String notes, NguoiDung currentUser,
                                       BigDecimal cumulativePayments) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Validate payment can be added with cumulative context
        validatePaymentAddition(hoaDon, paymentAmount, cumulativePayments);

        // Create payment record
        ThanhToan thanhToan = createPaymentRecord(paymentAmount, paymentMethod, transactionRef, notes, currentUser);
        thanhToan = thanhToanRepository.save(thanhToan);

        // Link payment to order
        HoaDonThanhToan hoaDonThanhToan = new HoaDonThanhToan();
        HoaDonThanhToanId id = new HoaDonThanhToanId();
        id.setHoaDonId(orderId);
        id.setThanhToanId(thanhToan.getId());
        hoaDonThanhToan.setId(id);
        hoaDonThanhToan.setHoaDon(hoaDon);
        hoaDonThanhToan.setThanhToan(thanhToan);
        hoaDonThanhToan.setSoTienApDung(paymentAmount);

        hoaDonThanhToanRepository.save(hoaDonThanhToan);

        // Update order payment status based on total payments
        updateOrderPaymentStatus(hoaDon);

        // Create audit entry
        String auditMessage = String.format("Payment added - Method: %s, Amount: %s, Transaction: %s, Cumulative: %s",
                paymentMethod, paymentAmount, transactionRef, cumulativePayments);
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                orderId,
                createAuditValues(hoaDon),
                currentUser != null ? currentUser.getEmail() : "SYSTEM",
                auditMessage
        );
        auditHistoryRepository.save(auditEntry);

        log.info("Payment added to order {} - Method: {}, Amount: {}, Cumulative context: {}",
                orderId, paymentMethod, paymentAmount, cumulativePayments);
        return toDtoWithPaymentMethod(hoaDon);
    }

    /**
     * Process mixed payment for an order with multiple payment methods.
     * Handles sequential processing of each payment component and manages payment gateway URLs.
     */
    @Transactional
    public Map<String, Object> processMixedPayment(Long orderId, List<PaymentComponent> payments,
                                                   String baseUrl, String clientIp, NguoiDung currentUser) {
        // Validate order exists and can accept payments
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Validate order can accept mixed payments
        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Đơn hàng đã được thanh toán đầy đủ");
        }

        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            throw new IllegalStateException("Không thể thanh toán cho đơn hàng đã hoàn tiền");
        }

        // Validate total payment amount matches order total
        BigDecimal totalPaymentAmount = payments.stream()
                .map(PaymentComponent::getSoTien)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaymentAmount.compareTo(hoaDon.getTongThanhToan()) != 0) {
            throw new IllegalArgumentException(
                    String.format("Tổng số tiền thanh toán (%s) không khớp với tổng tiền đơn hàng (%s)",
                            totalPaymentAmount, hoaDon.getTongThanhToan())
            );
        }

        Map<String, Object> paymentResults = new HashMap<>();
        List<Map<String, Object>> processedPayments = new ArrayList<>();
        List<String> gatewayUrls = new ArrayList<>();

        // Initialize cumulative payment tracking for transaction context
        BigDecimal cumulativePayments = BigDecimal.ZERO;
        log.debug("Starting mixed payment processing for order {} with {} payment components",
                orderId, payments.size());

        try {
            // Process each payment component sequentially
            for (int i = 0; i < payments.size(); i++) {
                PaymentComponent payment = payments.get(i);

                log.debug("Processing payment component {} of {} - Method: {}, Amount: {}, Cumulative so far: {}",
                        i + 1, payments.size(), payment.getPhuongThucThanhToan(),
                        payment.getSoTien(), cumulativePayments);

                Map<String, Object> paymentResult = processIndividualPayment(
                        hoaDon, payment, i + 1, baseUrl, clientIp, currentUser, cumulativePayments
                );

                processedPayments.add(paymentResult);

                // Update cumulative payments after successful processing
                cumulativePayments = cumulativePayments.add(payment.getSoTien());

                log.debug("Payment component {} processed successfully. Updated cumulative total: {}",
                        i + 1, cumulativePayments);

                // Collect gateway URLs for frontend redirection
                if (paymentResult.containsKey("paymentUrl")) {
                    gatewayUrls.add((String) paymentResult.get("paymentUrl"));
                }
            }

            // Update order payment status after all payments are processed
            updateOrderPaymentStatus(hoaDon);

            // Create comprehensive audit entry for mixed payment
            String auditMessage = String.format("Mixed payment processed - %d payment methods, Total: %s",
                    payments.size(), totalPaymentAmount);
            HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                    orderId,
                    createAuditValues(hoaDon),
                    currentUser != null ? currentUser.getEmail() : "SYSTEM",
                    auditMessage
            );
            auditHistoryRepository.save(auditEntry);

            // Prepare response
            paymentResults.put("processedPayments", processedPayments);
            paymentResults.put("gatewayUrls", gatewayUrls);
            paymentResults.put("totalAmount", totalPaymentAmount);
            paymentResults.put("paymentCount", payments.size());
            paymentResults.put("orderStatus", hoaDon.getTrangThaiThanhToan());

            log.info("Mixed payment processed successfully for order {} - {} payments, Total: {}, Final cumulative: {}",
                    orderId, payments.size(), totalPaymentAmount, cumulativePayments);

            return paymentResults;

        } catch (Exception e) {
            // Log error with cumulative tracking information for debugging
            log.error("Failed to process mixed payment for order {} at cumulative amount {}: {}",
                    orderId, cumulativePayments, e.getMessage(), e);
            throw new RuntimeException("Lỗi xử lý thanh toán hỗn hợp: " + e.getMessage(), e);
        }
    }

    /**
     * Process an individual payment component within a mixed payment scenario.
     * This method acts as the bridge between mixed payment processing and individual payment validation,
     * ensuring the cumulative context is properly propagated to validation logic.
     *
     * @param hoaDon The order being processed
     * @param payment The payment component to process
     * @param paymentIndex The index of this payment in the sequence (1-based)
     * @param baseUrl Base URL for payment gateway redirects
     * @param clientIp Client IP address for payment processing
     * @param currentUser Current user processing the payment
     * @param cumulativePayments The cumulative amount of payments already processed
     *                          within the current transaction context
     * @return Map containing payment processing results
     */
    private Map<String, Object> processIndividualPayment(HoaDon hoaDon, PaymentComponent payment,
                                                         int paymentIndex, String baseUrl,
                                                         String clientIp, NguoiDung currentUser,
                                                         BigDecimal cumulativePayments) {
        Map<String, Object> result = new HashMap<>();

        try {
            PhuongThucThanhToan paymentMethod = payment.getPhuongThucThanhToan();
            BigDecimal amount = payment.getSoTien();
            String notes = payment.getGhiChu() != null ? payment.getGhiChu() :
                    String.format("Thanh toán hỗn hợp - Phần %d/%d", paymentIndex, paymentIndex);

            // Validate payment method compatibility with order type
            validatePaymentMethodCompatibility(hoaDon.getLoaiHoaDon(), paymentMethod);

            switch (paymentMethod) {
                case TIEN_MAT:
                    // Cash payments are processed immediately with cumulative context
                    String cashTransactionRef = "CASH-" + hoaDon.getMaHoaDon() + "-" + paymentIndex;
                    addPaymentToOrder(hoaDon.getId(), amount, paymentMethod, cashTransactionRef, notes, currentUser, cumulativePayments);

                    result.put("paymentMethod", "TIEN_MAT");
                    result.put("amount", amount);
                    result.put("status", "COMPLETED");
                    result.put("transactionRef", cashTransactionRef);
                    result.put("message", "Thanh toán tiền mặt đã được xử lý");
                    break;

                case VNPAY:
                    // VNPay payments require gateway processing
                    String vnpayOrderInfo = String.format("Thanh toan don hang %s - Phan %d",
                            hoaDon.getMaHoaDon(), paymentIndex);
                    String vnpayUrl = createVNPayPayment(hoaDon.getId(), amount.longValue(),
                            vnpayOrderInfo, baseUrl, clientIp);

                    result.put("paymentMethod", "VNPAY");
                    result.put("amount", amount);
                    result.put("status", "PENDING_GATEWAY");
                    result.put("paymentUrl", vnpayUrl);
                    result.put("message", "Chuyển hướng đến VNPay để thanh toán");
                    break;

                case MOMO:
                    // MoMo payments require gateway processing
                    String momoOrderInfo = String.format("Thanh toan don hang %s - Phan %d",
                            hoaDon.getMaHoaDon(), paymentIndex);
                    String momoUrl = createMoMoPayment(hoaDon.getId(), amount.intValue(),
                            momoOrderInfo, baseUrl, clientIp);

                    result.put("paymentMethod", "MOMO");
                    result.put("amount", amount);
                    result.put("status", "PENDING_GATEWAY");
                    result.put("paymentUrl", momoUrl);
                    result.put("message", "Chuyển hướng đến MoMo để thanh toán");
                    break;

                default:
                    throw new IllegalArgumentException("Phương thức thanh toán không được hỗ trợ: " + paymentMethod);
            }

            result.put("paymentIndex", paymentIndex);
            result.put("success", true);

            log.info("Individual payment processed for order {} - Method: {}, Amount: {}, Index: {}, Cumulative context: {}",
                    hoaDon.getId(), paymentMethod, amount, paymentIndex, cumulativePayments);

            return result;

        } catch (Exception e) {
            log.error("Failed to process individual payment for order {} at index {} with cumulative context {}: {}",
                    hoaDon.getId(), paymentIndex, cumulativePayments, e.getMessage(), e);

            result.put("paymentIndex", paymentIndex);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("paymentMethod", payment.getPhuongThucThanhToan().toString());
            result.put("amount", payment.getSoTien());

            throw new RuntimeException("Lỗi xử lý thanh toán thứ " + paymentIndex + ": " + e.getMessage(), e);
        }
    }

    /**
     * Get payment summary for an order including all payment methods used.
     */
    @Transactional(readOnly = true)
    public PaymentSummaryDto getOrderPaymentSummary(Long orderId) {
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        List<HoaDonThanhToan> payments = hoaDonThanhToanRepository.findByHoaDonIdWithPaymentDetails(orderId);
        BigDecimal totalPaid = hoaDonThanhToanRepository.calculateTotalPaidAmount(orderId);
        BigDecimal remainingAmount = hoaDon.getTongThanhToan().subtract(totalPaid);

        return PaymentSummaryDto.builder()
                .orderId(orderId)
                .orderTotal(hoaDon.getTongThanhToan())
                .totalPaid(totalPaid)
                .remainingAmount(remainingAmount.max(BigDecimal.ZERO))
                .paymentStatus(hoaDon.getTrangThaiThanhToan())
                .payments(payments.stream()
                        .map(this::mapToPaymentDetailDto)
                        .toList())
                .build();
    }

    /**
     * Validate if a payment can be added to an order.
     * This method maintains backward compatibility for single payment scenarios.
     */
    private void validatePaymentAddition(HoaDon hoaDon, BigDecimal paymentAmount) {
        // Call the enhanced method with zero cumulative payments for backward compatibility
        validatePaymentAddition(hoaDon, paymentAmount, BigDecimal.ZERO);
    }

    /**
     * Validate if a payment can be added to an order with cumulative payment tracking.
     * This enhanced method addresses the transaction visibility issue in mixed payment scenarios
     * where previously processed payments within the same transaction are not yet committed to the database.
     *
     * @param hoaDon The order to validate payment for
     * @param paymentAmount The amount of the current payment being added
     * @param currentPaidInTransaction The cumulative amount of payments already processed
     *                                within the current transaction context but not yet committed
     * @throws IllegalStateException if the order is already fully paid or refunded
     * @throws IllegalArgumentException if payment amount is invalid or would exceed order total
     */
    private void validatePaymentAddition(HoaDon hoaDon, BigDecimal paymentAmount, BigDecimal currentPaidInTransaction) {
        // Validate order payment status
        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Cannot add payment to fully paid order");
        }

        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            throw new IllegalStateException("Cannot add payment to refunded order");
        }

        // Validate payment amount
        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero");
        }

        // Validate currentPaidInTransaction parameter
        if (currentPaidInTransaction == null) {
            currentPaidInTransaction = BigDecimal.ZERO;
        }
        if (currentPaidInTransaction.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Current paid in transaction amount cannot be negative");
        }

        // Check if payment would exceed order total
        // Combine database-persisted payments with in-transaction payments for accurate validation
        BigDecimal dbPaid = hoaDonThanhToanRepository.calculateTotalPaidAmount(hoaDon.getId());
        BigDecimal totalPaid = dbPaid.add(currentPaidInTransaction);
        BigDecimal newTotal = totalPaid.add(paymentAmount);

        if (newTotal.compareTo(hoaDon.getTongThanhToan()) > 0) {
            BigDecimal maxAllowed = hoaDon.getTongThanhToan().subtract(totalPaid);
            throw new IllegalArgumentException(
                    String.format("Payment amount %s would exceed order total. Maximum allowed: %s",
                            paymentAmount, maxAllowed));
        }

        log.debug("Payment validation successful for order {} - Payment: {}, DB Paid: {}, In-Transaction: {}, Total: {}",
                hoaDon.getId(), paymentAmount, dbPaid, currentPaidInTransaction, totalPaid);
    }

    /**
     * Create a payment record with configurable transaction status.
     * Enhanced to support different transaction states for order creation vs payment confirmation.
     */
    private ThanhToan createPaymentRecord(BigDecimal amount, PhuongThucThanhToan paymentMethod,
                                          String transactionRef, String notes, NguoiDung currentUser,
                                          TrangThaiGiaoDich trangThaiGiaoDich) {
        ThanhToan thanhToan = new ThanhToan();
        thanhToan.setNguoiDung(currentUser);
        thanhToan.setMaGiaoDich(transactionRef);
        thanhToan.setGiaTri(amount);
        thanhToan.setGhiChu(notes);

        // Set payment time only for completed transactions
        if (trangThaiGiaoDich == TrangThaiGiaoDich.THANH_CONG) {
            thanhToan.setThoiGianThanhToan(Instant.now());
        } else {
            thanhToan.setThoiGianThanhToan(null);
        }

        thanhToan.setTrangThaiGiaoDich(trangThaiGiaoDich);
        thanhToan.setPhuongThucThanhToan(paymentMethod);

        return thanhToan;
    }

    /**
     * Create a payment record with default successful status (backward compatibility).
     */
    private ThanhToan createPaymentRecord(BigDecimal amount, PhuongThucThanhToan paymentMethod,
                                          String transactionRef, String notes, NguoiDung currentUser) {
        return createPaymentRecord(amount, paymentMethod, transactionRef, notes, currentUser, TrangThaiGiaoDich.THANH_CONG);
    }

    /**
     * Update order payment status based on total payments received.
     */
    private void updateOrderPaymentStatus(HoaDon hoaDon) {
        BigDecimal totalPaid = hoaDonThanhToanRepository.calculateTotalPaidAmount(hoaDon.getId());
        BigDecimal orderTotal = hoaDon.getTongThanhToan();

        TrangThaiThanhToan oldStatus = hoaDon.getTrangThaiThanhToan();
        TrangThaiThanhToan newStatus;

        if (totalPaid.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = TrangThaiThanhToan.CHUA_THANH_TOAN;
        } else if (totalPaid.compareTo(orderTotal) >= 0) {
            newStatus = TrangThaiThanhToan.DA_THANH_TOAN;
        } else {
            newStatus = TrangThaiThanhToan.THANH_TOAN_MOT_PHAN;
        }

        if (!oldStatus.equals(newStatus)) {
            hoaDon.setTrangThaiThanhToan(newStatus);
            // Save with optimistic locking retry for payment status updates
            optimisticLockingService.executeWithRetryAndConstraintHandling(
                    () -> hoaDonRepository.save(hoaDon),
                    "HoaDon",
                    hoaDon.getId()
            );

            // Handle payment status change implications
            handlePaymentStatusChange(hoaDon, oldStatus, newStatus);

            log.info("Order {} payment status updated from {} to {} (Paid: {}/{})",
                    hoaDon.getId(), oldStatus, newStatus, totalPaid, orderTotal);
        }
    }

    /**
     * Create initial payment record during order creation.
     * Handles both cash payments (immediate completion) and gateway payments (pending status).
     * Leverages existing payment record creation patterns for consistency.
     */
    private void createInitialPaymentRecord(HoaDon hoaDon, PhuongThucThanhToan paymentMethod, NguoiDung currentUser) {
        log.debug("Creating initial payment record for order {} with method {}", hoaDon.getId(), paymentMethod);

        // Determine transaction status and reference based on payment method
        TrangThaiGiaoDich trangThaiGiaoDich;
        String transactionRef;
        String notes;

        switch (paymentMethod) {
            case TIEN_MAT:
                // Cash payments are immediately completed
                trangThaiGiaoDich = TrangThaiGiaoDich.THANH_CONG;
                transactionRef = "CASH-" + hoaDon.getMaHoaDon();
                notes = "Thanh toán tiền mặt tại quầy";
                break;

            case VNPAY:
                // VNPay payments start as pending
                trangThaiGiaoDich = TrangThaiGiaoDich.CHO_XU_LY;
                transactionRef = "VNPAY-PENDING-" + hoaDon.getMaHoaDon();
                notes = "Chờ thanh toán qua VNPay";
                break;

            case MOMO:
                // MoMo payments start as pending
                trangThaiGiaoDich = TrangThaiGiaoDich.CHO_XU_LY;
                transactionRef = "MOMO-PENDING-" + hoaDon.getMaHoaDon();
                notes = "Chờ thanh toán qua MoMo";
                break;

            default:
                log.warn("Unknown payment method: {}, defaulting to pending status", paymentMethod);
                trangThaiGiaoDich = TrangThaiGiaoDich.CHO_XU_LY;
                transactionRef = "PENDING-" + hoaDon.getMaHoaDon();
                notes = "Chờ xử lý thanh toán";
                break;
        }

        // Create payment record using existing pattern
        ThanhToan thanhToan = createPaymentRecord(
                hoaDon.getTongThanhToan(),
                paymentMethod,
                transactionRef,
                notes,
                currentUser,
                trangThaiGiaoDich
        );

        // Save payment record
        thanhToan = thanhToanRepository.save(thanhToan);

        // Create junction table record using existing pattern
        createHoaDonThanhToanJunction(hoaDon, thanhToan, hoaDon.getTongThanhToan());

        log.info("Initial payment record created for order {} - Method: {}, Status: {}, Amount: {}",
                hoaDon.getId(), paymentMethod, trangThaiGiaoDich, hoaDon.getTongThanhToan());
    }

    /**
     * Create HoaDonThanhToan junction record.
     * Extracted from existing addPaymentToOrder() pattern for reusability.
     */
    private void createHoaDonThanhToanJunction(HoaDon hoaDon, ThanhToan thanhToan, BigDecimal soTienApDung) {
        HoaDonThanhToan hoaDonThanhToan = new HoaDonThanhToan();
        HoaDonThanhToanId id = new HoaDonThanhToanId();
        id.setHoaDonId(hoaDon.getId());
        id.setThanhToanId(thanhToan.getId());
        hoaDonThanhToan.setId(id);
        hoaDonThanhToan.setHoaDon(hoaDon);
        hoaDonThanhToan.setThanhToan(thanhToan);
        hoaDonThanhToan.setSoTienApDung(soTienApDung);

        hoaDonThanhToanRepository.save(hoaDonThanhToan);

        log.debug("Junction record created linking order {} to payment {} with amount {}",
                hoaDon.getId(), thanhToan.getId(), soTienApDung);
    }

    /**
     * Get payment method from ThanhToan records for an order.
     * Uses existing repository query to avoid N+1 issues and provides backward compatibility.
     */
    private PhuongThucThanhToan getPaymentMethodFromThanhToan(Long hoaDonId) {
        if (hoaDonId == null) {
            return null;
        }

        try {
            // Use existing query that fetches payment details efficiently
            List<HoaDonThanhToan> paymentRecords = hoaDonThanhToanRepository.findByHoaDonIdWithPaymentDetails(hoaDonId);

            if (paymentRecords.isEmpty()) {
                log.debug("No payment records found for order {}, payment method will be null", hoaDonId);
                return null;
            }

            // Return payment method from the first (primary) payment record
            // Payment records are ordered by creation date DESC, so first is most recent
            ThanhToan thanhToan = paymentRecords.get(0).getThanhToan();
            PhuongThucThanhToan paymentMethod = thanhToan.getPhuongThucThanhToan();

            log.debug("Retrieved payment method {} for order {} from ThanhToan record {}",
                    paymentMethod, hoaDonId, thanhToan.getId());

            return paymentMethod;

        } catch (Exception e) {
            log.warn("Failed to retrieve payment method for order {}: {}", hoaDonId, e.getMessage());
            return null; // Graceful fallback for backward compatibility
        }
    }

    /**
     * Populate payment method in HoaDonDto from ThanhToan records.
     * This method enhances the DTO with payment method data after mapping.
     */
    private void populatePaymentMethod(HoaDonDto hoaDonDto) {
        if (hoaDonDto != null && hoaDonDto.getId() != null) {
            PhuongThucThanhToan paymentMethod = getPaymentMethodFromThanhToan(hoaDonDto.getId());
            hoaDonDto.setPhuongThucThanhToan(paymentMethod);
        }
    }

    /**
     * Populate serial number information in HoaDonChiTietDto objects from SerialNumber records.
     * This method enhances the DTO with serial number data after mapping with comprehensive
     * error handling, performance tracking, and graceful degradation.
     * Follows the same pattern as populatePaymentMethod for consistency.
     *
     * @param hoaDonDto The order DTO to populate with serial number information
     */
    private void populateSerialNumbers(HoaDonDto hoaDonDto) {
        // Performance tracking
        long startTime = System.currentTimeMillis();
        String orderId = hoaDonDto != null && hoaDonDto.getId() != null ? hoaDonDto.getId().toString() : "unknown";

        // Input validation with detailed logging
        if (hoaDonDto == null) {
            log.warn("Serial number population skipped - hoaDonDto is null");
            return;
        }

        if (hoaDonDto.getId() == null) {
            log.warn("Serial number population skipped - order ID is null");
            return;
        }

        if (hoaDonDto.getChiTiet() == null || hoaDonDto.getChiTiet().isEmpty()) {
            log.debug("Serial number population skipped - no order line items found for order ID: {}", orderId);
            return;
        }

        try {
            log.debug("Starting serial number population for order ID: {} with {} line items",
                     orderId, hoaDonDto.getChiTiet().size());

            // Query serial numbers with enhanced error handling
            List<SerialNumber> orderSerialNumbers = null;
            long queryStartTime = System.currentTimeMillis();

            try {
                orderSerialNumbers = serialNumberService.getSerialNumbersByOrderId(orderId);
                long queryTime = System.currentTimeMillis() - queryStartTime;
                log.debug("Serial number query completed for order ID: {} in {}ms", orderId, queryTime);

                // Performance warning for slow queries
                if (queryTime > 1000) {
                    log.warn("Slow serial number query detected for order ID: {} - took {}ms", orderId, queryTime);
                }

            } catch (Exception queryException) {
                long queryTime = System.currentTimeMillis() - queryStartTime;
                log.error("Failed to query serial numbers for order ID: {} after {}ms - Error: {}",
                         orderId, queryTime, queryException.getMessage(), queryException);

                // Graceful degradation - continue without serial numbers
                log.warn("Continuing order processing without serial numbers for order ID: {}", orderId);
                return;
            }

            // Handle empty or null results
            if (orderSerialNumbers == null) {
                log.warn("SerialNumberService returned null for order ID: {} - this may indicate a service issue", orderId);
                return;
            }

            if (orderSerialNumbers.isEmpty()) {
                log.debug("No serial numbers found for order ID: {} - order may not have serialized products", orderId);
                return;
            }

            log.debug("Found {} serial numbers for order ID: {}", orderSerialNumbers.size(), orderId);

            // Validate and filter serial numbers with detailed logging
            List<SerialNumber> validSerialNumbers = new ArrayList<>();
            int invalidSerialCount = 0;

            for (SerialNumber serialNumber : orderSerialNumbers) {
                if (serialNumber == null) {
                    invalidSerialCount++;
                    log.warn("Null serial number found in results for order ID: {}", orderId);
                    continue;
                }

                if (serialNumber.getSanPhamChiTiet() == null) {
                    invalidSerialCount++;
                    log.warn("Serial number {} has null product variant for order ID: {}",
                            serialNumber.getId(), orderId);
                    continue;
                }

                if (serialNumber.getSanPhamChiTiet().getId() == null) {
                    invalidSerialCount++;
                    log.warn("Serial number {} has product variant with null ID for order ID: {}",
                            serialNumber.getId(), orderId);
                    continue;
                }

                validSerialNumbers.add(serialNumber);
            }

            if (invalidSerialCount > 0) {
                log.warn("Found {} invalid serial numbers out of {} total for order ID: {}",
                        invalidSerialCount, orderSerialNumbers.size(), orderId);
            }

            if (validSerialNumbers.isEmpty()) {
                log.warn("No valid serial numbers found for order ID: {} after filtering", orderId);
                return;
            }

            // Group serial numbers by variant ID with error handling
            Map<Long, List<SerialNumber>> serialsByVariant;
            try {
                serialsByVariant = validSerialNumbers.stream()
                    .collect(Collectors.groupingBy(sn -> sn.getSanPhamChiTiet().getId()));

                log.debug("Grouped {} valid serial numbers into {} variants for order ID: {}",
                         validSerialNumbers.size(), serialsByVariant.size(), orderId);

            } catch (Exception groupingException) {
                log.error("Failed to group serial numbers by variant for order ID: {} - Error: {}",
                         orderId, groupingException.getMessage(), groupingException);
                return;
            }

            // Populate serial number information in order line items with enhanced error handling
            int populatedCount = 0;
            int skippedCount = 0;
            int errorCount = 0;

            for (HoaDonChiTietDto chiTiet : hoaDonDto.getChiTiet()) {
                try {
                    if (chiTiet == null) {
                        skippedCount++;
                        log.warn("Null order line item found for order ID: {}", orderId);
                        continue;
                    }

                    if (chiTiet.getSanPhamChiTietId() == null) {
                        skippedCount++;
                        log.warn("Order line item missing sanPhamChiTietId for order ID: {}", orderId);
                        continue;
                    }

                    List<SerialNumber> variantSerials = serialsByVariant.get(chiTiet.getSanPhamChiTietId());

                    if (variantSerials != null && !variantSerials.isEmpty()) {
                        // Enhanced serial number selection with validation
                        SerialNumber selectedSerial = null;

                        for (SerialNumber serialNumber : variantSerials) {
                            if (serialNumber != null &&
                                serialNumber.getId() != null &&
                                serialNumber.getSerialNumberValue() != null &&
                                !serialNumber.getSerialNumberValue().trim().isEmpty()) {
                                selectedSerial = serialNumber;
                                break;
                            }
                        }

                        if (selectedSerial != null) {
                            chiTiet.setSerialNumberId(selectedSerial.getId());
                            chiTiet.setSerialNumber(selectedSerial.getSerialNumberValue().trim());

                            populatedCount++;
                            log.debug("Populated serial number {} for variant ID: {} in order ID: {}",
                                     selectedSerial.getSerialNumberValue(), chiTiet.getSanPhamChiTietId(), orderId);

                            // Log information about multiple serial numbers
                            if (variantSerials.size() > 1) {
                                log.debug("Multiple serial numbers ({}) found for variant ID: {} in order ID: {}, using: {}",
                                         variantSerials.size(), chiTiet.getSanPhamChiTietId(), orderId,
                                         selectedSerial.getSerialNumberValue());
                            }
                        } else {
                            skippedCount++;
                            log.warn("No valid serial numbers found for variant ID: {} in order ID: {} - all serial numbers were null or invalid",
                                    chiTiet.getSanPhamChiTietId(), orderId);
                        }
                    } else {
                        skippedCount++;
                        log.debug("No serial numbers found for variant ID: {} in order ID: {}",
                                 chiTiet.getSanPhamChiTietId(), orderId);
                    }

                } catch (Exception itemException) {
                    errorCount++;
                    log.error("Failed to populate serial number for line item with variant ID: {} in order ID: {} - Error: {}",
                             chiTiet != null ? chiTiet.getSanPhamChiTietId() : "unknown", orderId,
                             itemException.getMessage(), itemException);

                    // Clear any partially set data for this item
                    if (chiTiet != null) {
                        chiTiet.setSerialNumberId(null);
                        chiTiet.setSerialNumber(null);
                    }
                }
            }

            // Final success logging with performance metrics
            long executionTime = System.currentTimeMillis() - startTime;
            log.info("Serial number population completed for order ID: {} - Populated: {}, Skipped: {}, Errors: {}, Total items: {}, Execution time: {}ms",
                     orderId, populatedCount, skippedCount, errorCount, hoaDonDto.getChiTiet().size(), executionTime);

            // Performance warning for slow population
            if (executionTime > 2000) {
                log.warn("Slow serial number population detected for order ID: {} - took {}ms", orderId, executionTime);
            }

        } catch (Exception e) {
            // Comprehensive error handling with detailed logging
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Critical failure during serial number population for order ID: {} after {}ms - Error type: {}, Message: {}",
                     orderId, executionTime, e.getClass().getSimpleName(), e.getMessage(), e);

            // Clear any partially populated serial number data to maintain consistency
            try {
                if (hoaDonDto.getChiTiet() != null) {
                    int clearedCount = 0;
                    for (HoaDonChiTietDto chiTiet : hoaDonDto.getChiTiet()) {
                        if (chiTiet != null && (chiTiet.getSerialNumberId() != null || chiTiet.getSerialNumber() != null)) {
                            chiTiet.setSerialNumberId(null);
                            chiTiet.setSerialNumber(null);
                            clearedCount++;
                        }
                    }

                    if (clearedCount > 0) {
                        log.warn("Cleared {} partially populated serial numbers for order ID: {} due to population failure",
                                clearedCount, orderId);
                    }
                }
            } catch (Exception cleanupException) {
                log.error("Failed to cleanup partially populated serial numbers for order ID: {} - Error: {}",
                         orderId, cleanupException.getMessage(), cleanupException);
            }

            // Log final failure summary
            log.warn("Serial number population failed for order ID: {} - order processing will continue without serial numbers", orderId);
        }
    }

    /**
     * Enhanced toDto method that includes payment method and serial number population.
     * This replaces direct mapper calls to ensure both payment method and serial number
     * information are always populated in the DTO.
     *
     * @param hoaDon The order entity to convert to DTO
     * @return HoaDonDto with populated payment method and serial number information
     */
    private HoaDonDto toDtoWithPaymentMethod(HoaDon hoaDon) {
        long startTime = System.currentTimeMillis();

        try {
            HoaDonDto dto = hoaDonMapper.toDto(hoaDon);

            // Populate payment method information
            populatePaymentMethod(dto);
            log.debug("Payment method populated for order ID: {}", dto.getId());

            // Populate serial number information for order line items
            populateSerialNumbers(dto);
            log.debug("Serial number population completed for order ID: {}", dto.getId());

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Order DTO population completed for order ID: {} in {}ms", dto.getId(), executionTime);

            return dto;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Failed to populate order DTO for order ID: {} after {}ms - Error: {}",
                     hoaDon.getId(), executionTime, e.getMessage(), e);

            // Return basic DTO without enhancements to ensure order retrieval doesn't fail
            // This provides graceful degradation when population methods fail
            HoaDonDto basicDto = hoaDonMapper.toDto(hoaDon);
            log.warn("Returning basic DTO without payment method and serial number population for order ID: {}",
                     basicDto.getId());
            return basicDto;
        }
    }

    /**
     * Enhanced toDtoList method that includes payment method population for all DTOs.
     */
    private List<HoaDonDto> toDtoListWithPaymentMethod(List<HoaDon> hoaDons) {
        List<HoaDonDto> dtos = hoaDonMapper.toDtoList(hoaDons);
        dtos.forEach(this::populatePaymentMethod);
        return dtos;
    }

    /**
     * Map HoaDonThanhToan to PaymentDetailDto.
     */
    private PaymentDetailDto mapToPaymentDetailDto(HoaDonThanhToan hoaDonThanhToan) {
        ThanhToan thanhToan = hoaDonThanhToan.getThanhToan();
        return PaymentDetailDto.builder()
                .paymentId(thanhToan.getId())
                .amount(hoaDonThanhToan.getSoTienApDung())
                .paymentMethod(thanhToan.getPhuongThucThanhToan())
                .transactionRef(thanhToan.getMaGiaoDich())
                .paymentTime(thanhToan.getThoiGianThanhToan())
                .status(thanhToan.getTrangThaiGiaoDich())
                .notes(thanhToan.getGhiChu())
                .createdAt(hoaDonThanhToan.getNgayTao())
                .build();
    }

    /**
     * Create MoMo payment URL for a specific order.
     * This method integrates MoMo payment with order management.
     * Updated to support mixed payment scenarios.
     */
    @Transactional
    public String createMoMoPayment(Long orderId, int amount, String orderInfo, String baseUrl, String clientIp) {
        // Validate order exists and is in correct state
        HoaDon hoaDon = hoaDonRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found with ID: " + orderId));

        // Validate order can be paid (updated for mixed payment support)
        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Order has already been fully paid");
        }

        if (hoaDon.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            throw new IllegalStateException("Cannot process payment for refunded order");
        }

        // Validate payment parameters using direct validation
        validateMoMoPaymentParameters(orderId, amount, orderInfo, baseUrl);

        // Create MoMo payment URL using direct MoMoService call
        String momoUrl = moMoGatewayService.createOrderWithOrderId(orderId, amount, orderInfo, baseUrl, clientIp);

        // Create audit entry for payment attempt
        String auditMessage = String.format("MoMo payment initiated - Amount: %d, OrderInfo: %s", amount, orderInfo);
        HoaDonAuditHistory auditEntry = HoaDonAuditHistory.createEntry(
                orderId,
                createAuditValues(hoaDon),
                hoaDon.getNguoiTao(),
                auditMessage
        );
        auditHistoryRepository.save(auditEntry);

        log.info("MoMo payment URL created for order {} with amount {}", orderId, amount);
        return momoUrl;
    }



    /**
     * Calculate shipping fee automatically using GHN service with manual override capability.
     * If manual shipping fee is provided in DTO, it takes precedence over automatic calculation.
     * Uses GHN service directly for shipping fee calculation.
     * Falls back to zero shipping fee if GHN service fails.
     */
    private BigDecimal calculateShippingFee(HoaDon hoaDon, HoaDonDto hoaDonDto) {
        // Step 1: Check if manual shipping fee is provided (manual override)
        if (hoaDonDto.getPhiVanChuyen() != null) {
            log.info("Using manual shipping fee: {} VND", hoaDonDto.getPhiVanChuyen());
            return hoaDonDto.getPhiVanChuyen();
        }

        // Step 2: Check if order requires shipping (has delivery address)
        if (hoaDon.getDiaChiGiaoHang() == null) {
            log.info("No delivery address provided, setting shipping fee to zero");
            return BigDecimal.ZERO;
        }

        try {
            // Step 3: Build shipping request from order data
            ShippingRequest shippingRequest = buildShippingRequest(hoaDon);

            // Step 4: Use GHN service directly for shipping fee calculation
            ShippingFeeResponse ghnResponse = ghnService.calculateShippingFee(shippingRequest);

            if (ghnResponse.isSuccess() && ghnResponse.getTotalFee() != null) {
                log.info("GHN shipping fee calculated successfully: {} VND", ghnResponse.getTotalFee());
                return ghnResponse.getTotalFee();
            } else {
                log.warn("GHN shipping fee calculation failed: {}", ghnResponse.getErrorMessage());

                // Fallback: Try primary shipping service directly
                if (shippingCalculatorService.isAvailable()) {
                    ShippingFeeResponse fallbackResponse = shippingCalculatorService.calculateShippingFee(shippingRequest);
                    if (fallbackResponse.isSuccess() && fallbackResponse.getTotalFee() != null) {
                        log.info("Fallback shipping fee using {}: {} VND",
                                fallbackResponse.getProviderName(), fallbackResponse.getTotalFee());
                        return fallbackResponse.getTotalFee();
                    }
                }

                log.warn("All shipping providers failed, setting shipping fee to zero");
                return BigDecimal.ZERO;
            }

        } catch (Exception e) {
            log.error("Error calculating shipping fee: {}", e.getMessage(), e);

            // Final fallback: Try primary service
            try {
                if (shippingCalculatorService.isAvailable()) {
                    ShippingRequest shippingRequest = buildShippingRequest(hoaDon);
                    ShippingFeeResponse fallbackResponse = shippingCalculatorService.calculateShippingFee(shippingRequest);
                    if (fallbackResponse.isSuccess() && fallbackResponse.getTotalFee() != null) {
                        log.info("Emergency fallback shipping fee: {} VND", fallbackResponse.getTotalFee());
                        return fallbackResponse.getTotalFee();
                    }
                }
            } catch (Exception fallbackException) {
                log.error("Emergency fallback also failed: {}", fallbackException.getMessage());
            }

            return BigDecimal.ZERO;
        }
    }

    /**
     * Build shipping request from order data for shipping API.
     * Extracts delivery address and package information from the order.
     */
    private ShippingRequest buildShippingRequest(HoaDon hoaDon) {
        // Calculate total weight (assuming 500g per item as default)
        int totalWeight = hoaDon.getHoaDonChiTiets().stream()
                .mapToInt(chiTiet -> chiTiet.getSoLuong() * 500) // 500g per item
                .sum();

        // Ensure minimum weight of 100g
        totalWeight = Math.max(totalWeight, 100);

        // Extract delivery address information from DiaChi entity
        DiaChi diaChiGiaoHang = hoaDon.getDiaChiGiaoHang();

        return ShippingRequest.builder()
                // Delivery location (from DiaChi entity)
                .province(diaChiGiaoHang.getTinhThanh())
                .district(diaChiGiaoHang.getQuanHuyen())
                .ward(diaChiGiaoHang.getPhuongXa())
                .address(diaChiGiaoHang.getDuong())
                // Package details
                .weight(totalWeight)
                .value(hoaDon.getTongTienHang()) // Order value for insurance
                .build();
    }

    // ==================== BUSINESSENTITYSERVICE TEMPLATE METHODS ====================

    @Override
    protected HoaDonRepository getRepository() {
        return hoaDonRepository;
    }



    @Override
    protected HoaDonAuditHistoryRepository getAuditRepository() {
        return auditHistoryRepository;
    }

    @Override
    protected ApplicationEventPublisher getEventPublisher() {
        return eventPublisher;
    }

    @Override
    protected WebSocketIntegrationService getWebSocketIntegrationService() {
        return webSocketIntegrationService;
    }

    @Override
    protected String getEntityTypeForWebSocket() {
        return "hoa-don";
    }

    @Override
    protected String getEntityName() {
        return "Hóa đơn";
    }

    @Override
    protected Long getEntityId(HoaDon entity) {
        return entity.getId();
    }

    @Override
    protected void setEntityId(HoaDon entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected HoaDonDto toDto(HoaDon entity) {
        return toDtoWithPaymentMethod(entity);
    }

    @Override
    protected HoaDon toEntity(HoaDonDto dto) {
        return hoaDonMapper.toEntity(dto);
    }

    @Override
    protected void validateEntity(HoaDon entity) {
        if (entity.getMaHoaDon() == null || entity.getMaHoaDon().trim().isEmpty()) {
            throw new IllegalArgumentException("Mã hóa đơn không được để trống");
        }
        if (entity.getLoaiHoaDon() == null) {
            throw new IllegalArgumentException("Loại hóa đơn không được để trống");
        }
        if (entity.getTrangThaiDonHang() == null) {
            throw new IllegalArgumentException("Trạng thái đơn hàng không được để trống");
        }
        if (entity.getTrangThaiThanhToan() == null) {
            throw new IllegalArgumentException("Trạng thái thanh toán không được để trống");
        }
    }

    @Override
    protected void setSoftDeleteStatus(HoaDon entity, boolean isActive) {
        // HoaDon doesn't use soft delete, use status instead
        if (!isActive) {
            entity.setTrangThaiDonHang(TrangThaiDonHang.DA_HUY);
        }
    }

    @Override
    protected String buildAuditJson(HoaDon entity) {
        return createAuditValues(entity);
    }

    @Override
    protected HoaDonAuditHistory createAuditEntry(Long entityId, String action, String oldValues, String newValues, String nguoiThucHien, String lyDo) {
        return HoaDonAuditHistory.builder()
                .hoaDonId(entityId)
                .hanhDong(action)
                .thoiGianThayDoi(java.time.Instant.now())
                .nguoiThucHien(nguoiThucHien)
                .lyDoThayDoi(lyDo)
                .giaTriCu(oldValues)
                .giaTriMoi(newValues)
                .build();
    }

    @Override
    protected void publishEntityCreatedEvent(HoaDon entity) {
        try {
            OrderChangeEvent event = OrderChangeEvent.builder()
                    .hoaDonId(entity.getId())
                    .maHoaDon(entity.getMaHoaDon())
                    .khachHangId(entity.getKhachHang() != null ? entity.getKhachHang().getId() : null)
                    .tenKhachHang(entity.getKhachHang() != null ? entity.getKhachHang().getHoTen() : "Khách lẻ")
                    .trangThaiCu(null)
                    .trangThaiMoi(entity.getTrangThaiDonHang() != null ? entity.getTrangThaiDonHang().name() : null)
                    .tongTienCu(null)
                    .tongTienMoi(entity.getTongThanhToan())
                    .loaiThayDoi("CREATED")
                    .nguoiThucHien(entity.getNguoiTao())
                    .lyDoThayDoi("Tạo hóa đơn mới")
                    .timestamp(java.time.Instant.now())
                    .phuongThucThanhToan(null) // Payment method will be set when payment is confirmed
                    .build();

            eventPublisher.publishEvent(event);

            // Send WebSocket notification for order creation
            webSocketIntegrationService.sendOrderUpdate(
                    entity.getId().toString(),
                    "CREATED",
                    toDto(entity)
            );

            log.info("Published order created event for order ID: {}", entity.getId());

        } catch (Exception e) {
            log.error("Failed to publish order created event for ID {}: {}", entity.getId(), e.getMessage(), e);
        }
    }

    @Override
    protected void publishEntityUpdatedEvent(HoaDon entity, HoaDon oldEntity) {
        try {
            OrderChangeEvent event = OrderChangeEvent.builder()
                    .hoaDonId(entity.getId())
                    .maHoaDon(entity.getMaHoaDon())
                    .khachHangId(entity.getKhachHang() != null ? entity.getKhachHang().getId() : null)
                    .tenKhachHang(entity.getKhachHang() != null ? entity.getKhachHang().getHoTen() : "Khách lẻ")
                    .trangThaiCu(oldEntity.getTrangThaiDonHang() != null ? oldEntity.getTrangThaiDonHang().name() : null)
                    .trangThaiMoi(entity.getTrangThaiDonHang() != null ? entity.getTrangThaiDonHang().name() : null)
                    .tongTienCu(oldEntity.getTongThanhToan())
                    .tongTienMoi(entity.getTongThanhToan())
                    .loaiThayDoi("UPDATED")
                    .nguoiThucHien(entity.getNguoiCapNhat())
                    .lyDoThayDoi("Cập nhật hóa đơn")
                    .timestamp(java.time.Instant.now())
                    .phuongThucThanhToan(null) // Payment method tracked separately
                    .build();

            eventPublisher.publishEvent(event);

            // Send WebSocket notification for order update
            // Check if status changed to send appropriate notification
            String action = "UPDATED";
            if (oldEntity.getTrangThaiDonHang() != entity.getTrangThaiDonHang()) {
                action = "STATUS_CHANGED";
            }

            webSocketIntegrationService.sendOrderUpdate(
                    entity.getId().toString(),
                    action,
                    toDto(entity)
            );

            log.info("Published order updated event for order ID: {}", entity.getId());

        } catch (Exception e) {
            log.error("Failed to publish order updated event for ID {}: {}", entity.getId(), e.getMessage(), e);
        }
    }

    @Override
    protected void publishEntityDeletedEvent(Long entityId) {
        try {
            OrderChangeEvent event = OrderChangeEvent.builder()
                    .hoaDonId(entityId)
                    .maHoaDon("DELETED-" + entityId)
                    .khachHangId(null)
                    .tenKhachHang("N/A")
                    .trangThaiCu(null)
                    .trangThaiMoi("DELETED")
                    .tongTienCu(null)
                    .tongTienMoi(null)
                    .loaiThayDoi("DELETED")
                    .nguoiThucHien("SYSTEM")
                    .lyDoThayDoi("Xóa hóa đơn")
                    .timestamp(java.time.Instant.now())
                    .phuongThucThanhToan(null)
                    .build();

            eventPublisher.publishEvent(event);

            // Send WebSocket notification for order deletion
            webSocketIntegrationService.sendOrderUpdate(
                    entityId.toString(),
                    "DELETED",
                    null
            );

            log.info("Published order deleted event for order ID: {}", entityId);

        } catch (Exception e) {
            log.error("Failed to publish order deleted event for ID {}: {}", entityId, e.getMessage(), e);
        }
    }

    @Override
    protected void validateBusinessRules(HoaDon entity) {
        // Validate order type specific rules
        if (entity.getLoaiHoaDon() == LoaiHoaDon.ONLINE && entity.getKhachHang() == null) {
            throw new IllegalArgumentException("Đơn hàng online phải có thông tin khách hàng");
        }

        // Validate order totals
        if (entity.getTongThanhToan() != null && entity.getTongThanhToan().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tổng thanh toán không được âm");
        }

        // Validate order items
        if (entity.getHoaDonChiTiets() == null || entity.getHoaDonChiTiets().isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn phải có ít nhất một sản phẩm");
        }
    }

    @Override
    protected void validateBusinessRulesForUpdate(HoaDon entity, HoaDon existingEntity) {
        // Validate status transitions
        if (existingEntity.getTrangThaiDonHang() == TrangThaiDonHang.HOAN_THANH) {
            throw new IllegalArgumentException("Không thể cập nhật đơn hàng đã hoàn thành");
        }

        if (existingEntity.getTrangThaiDonHang() == TrangThaiDonHang.DA_HUY) {
            throw new IllegalArgumentException("Không thể cập nhật đơn hàng đã hủy");
        }

        // Validate payment status transitions
        if (existingEntity.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_HOAN_TIEN) {
            throw new IllegalArgumentException("Không thể cập nhật đơn hàng đã hoàn tiền");
        }
    }

    @Override
    protected HoaDon cloneEntity(HoaDon entity) {
        // Create a shallow clone for event publishing
        HoaDon clone = new HoaDon();
        clone.setId(entity.getId());
        clone.setMaHoaDon(entity.getMaHoaDon());
        clone.setLoaiHoaDon(entity.getLoaiHoaDon());
        clone.setTrangThaiDonHang(entity.getTrangThaiDonHang());
        clone.setTrangThaiThanhToan(entity.getTrangThaiThanhToan());
        clone.setTongThanhToan(entity.getTongThanhToan());
        clone.setTongTienHang(entity.getTongTienHang());
        clone.setPhiVanChuyen(entity.getPhiVanChuyen());
        clone.setGiaTriGiamGiaVoucher(entity.getGiaTriGiamGiaVoucher());
        clone.setKhachHang(entity.getKhachHang());
        clone.setNhanVien(entity.getNhanVien());
        clone.setDiaChiGiaoHang(entity.getDiaChiGiaoHang());
        clone.setNguoiNhanTen(entity.getNguoiNhanTen());
        clone.setNguoiNhanSdt(entity.getNguoiNhanSdt());
        clone.setNgayTao(entity.getNgayTao());
        clone.setNgayCapNhat(entity.getNgayCapNhat());
        clone.setNguoiTao(entity.getNguoiTao());
        clone.setNguoiCapNhat(entity.getNguoiCapNhat());
        return clone;
    }

    @Override
    protected List<HoaDonAuditHistory> getAuditHistoryByEntityId(Long entityId) {
        return auditHistoryRepository.findByHoaDonIdOrderByThoiGianThayDoiDesc(entityId);
    }

    /**
     * Enhanced pre-transaction validation for order creation requests.
     * Validates all required data before starting the transaction.
     */
    private void validateOrderCreationRequest(HoaDonDto hoaDonDto, NguoiDung currentUser) {
        log.debug("Validating order creation request for user: {}", currentUser != null ? currentUser.getEmail() : "null");

        // Validate basic order data
        if (hoaDonDto == null) {
            throw new IllegalArgumentException("Dữ liệu hóa đơn không được để trống");
        }

        if (hoaDonDto.getChiTiet() == null || hoaDonDto.getChiTiet().isEmpty()) {
            throw new IllegalArgumentException("Hóa đơn phải có ít nhất một sản phẩm");
        }

        // Validate order type
        if (hoaDonDto.getLoaiHoaDon() == null) {
            throw new IllegalArgumentException("Loại hóa đơn không được để trống");
        }

        // Validate payment method is provided
        if (hoaDonDto.getPhuongThucThanhToan() == null) {
            throw new IllegalArgumentException("Phương thức thanh toán không được để trống");
        }

        // Handle mixed payment validation
        if (Boolean.TRUE.equals(hoaDonDto.getIsMixedPayment())) {
            // Validate mixed payment configuration
            validateMixedPaymentConfiguration(hoaDonDto);
            log.debug("Mixed payment configuration validated successfully");
        } else {
            // Validate standard payment method
            validatePaymentMethodSupported(hoaDonDto.getPhuongThucThanhToan());
            validatePaymentMethodCompatibility(hoaDonDto.getLoaiHoaDon(), hoaDonDto.getPhuongThucThanhToan());
        }

        // Validate inventory availability before processing
        if (!serialNumberService.isInventoryAvailable(hoaDonDto.getChiTiet())) {
            throw new IllegalArgumentException("Không đủ hàng tồn kho cho một hoặc nhiều sản phẩm trong đơn hàng");
        }

        log.debug("Order creation request validation completed successfully");
    }

    /**
     * Validate mixed payment configuration from frontend.
     */
    private void validateMixedPaymentConfiguration(HoaDonDto hoaDonDto) {
        if (hoaDonDto.getMixedPayments() == null || hoaDonDto.getMixedPayments().isEmpty()) {
            throw new IllegalArgumentException("Cấu hình thanh toán hỗn hợp không được để trống");
        }

        if (hoaDonDto.getMixedPayments().size() < 2) {
            throw new IllegalArgumentException("Thanh toán hỗn hợp phải có ít nhất 2 phương thức thanh toán");
        }

        if (hoaDonDto.getMixedPayments().size() > 3) {
            throw new IllegalArgumentException("Thanh toán hỗn hợp không được vượt quá 3 phương thức thanh toán");
        }

        BigDecimal totalMixedAmount = BigDecimal.ZERO;
        Set<String> usedMethods = new HashSet<>();

        for (int i = 0; i < hoaDonDto.getMixedPayments().size(); i++) {
            Map<String, Object> payment = hoaDonDto.getMixedPayments().get(i);

            // Validate payment structure
            if (!payment.containsKey("method") || !payment.containsKey("amount")) {
                throw new IllegalArgumentException("Thành phần thanh toán thứ " + (i + 1) + " thiếu thông tin method hoặc amount");
            }

            String method = (String) payment.get("method");
            Object amountObj = payment.get("amount");

            // Validate payment method
            if (method == null || method.trim().isEmpty()) {
                throw new IllegalArgumentException("Phương thức thanh toán thứ " + (i + 1) + " không được để trống");
            }

            // Validate supported payment methods
            if (!"TIEN_MAT".equals(method) && !"VNPAY".equals(method) && !"MOMO".equals(method)) {
                throw new IllegalArgumentException("Phương thức thanh toán thứ " + (i + 1) + " không được hỗ trợ: " + method);
            }

            // Check for duplicate payment methods
            if (usedMethods.contains(method)) {
                throw new IllegalArgumentException("Không được sử dụng trùng lặp phương thức thanh toán: " + method);
            }
            usedMethods.add(method);

            // Validate amount
            BigDecimal amount;
            try {
                if (amountObj instanceof Number) {
                    amount = new BigDecimal(amountObj.toString());
                } else {
                    throw new IllegalArgumentException("Số tiền thanh toán thứ " + (i + 1) + " không hợp lệ");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Số tiền thanh toán thứ " + (i + 1) + " không hợp lệ");
            }

            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Số tiền thanh toán thứ " + (i + 1) + " phải lớn hơn 0");
            }

            totalMixedAmount = totalMixedAmount.add(amount);

            // Validate payment method compatibility with order type
            validatePaymentMethodCompatibility(hoaDonDto.getLoaiHoaDon(), method);
        }

        // Validate total amount matches order total
        if (hoaDonDto.getTongThanhToan() != null &&
                totalMixedAmount.compareTo(hoaDonDto.getTongThanhToan()) != 0) {
            throw new IllegalArgumentException(
                    String.format("Tổng số tiền thanh toán hỗn hợp (%s) không khớp với tổng tiền đơn hàng (%s)",
                            totalMixedAmount, hoaDonDto.getTongThanhToan())
            );
        }

        log.debug("Mixed payment configuration validated: {} methods, total amount: {}",
                hoaDonDto.getMixedPayments().size(), totalMixedAmount);
    }

    /**
     * Validate that the payment method is supported by the system.
     * Updated to support mixed payment scenarios.
     */
    private void validatePaymentMethodSupported(PhuongThucThanhToan phuongThucThanhToan) {
        switch (phuongThucThanhToan) {
            case TIEN_MAT:
            case VNPAY:
            case MOMO:
                // These payment methods are supported
                log.debug("Payment method {} is supported", phuongThucThanhToan);
                break;
            default:
                throw new IllegalArgumentException("Phương thức thanh toán không được hỗ trợ: " + phuongThucThanhToan);
        }
    }

    /**
     * Validate that the payment method is supported by the system, including mixed payments.
     * Overloaded method to handle string-based payment method validation for mixed payments.
     */
    private void validatePaymentMethodSupported(String phuongThucThanhToan) {
        if (phuongThucThanhToan == null) {
            throw new IllegalArgumentException("Phương thức thanh toán không được để trống");
        }

        // Handle mixed payment scenario
        if ("MIXED".equals(phuongThucThanhToan)) {
            log.debug("Mixed payment method is supported");
            return;
        }

        // Handle standard payment methods
        try {
            PhuongThucThanhToan paymentMethod = PhuongThucThanhToan.valueOf(phuongThucThanhToan);
            validatePaymentMethodSupported(paymentMethod);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Phương thức thanh toán không được hỗ trợ: " + phuongThucThanhToan);
        }
    }

    /**
     * Validate payment method compatibility with order type.
     */
    private void validatePaymentMethodCompatibility(LoaiHoaDon loaiHoaDon, PhuongThucThanhToan phuongThucThanhToan) {
        switch (phuongThucThanhToan) {
            case TIEN_MAT:
                // Cash payments are valid for all order types
                if (loaiHoaDon == LoaiHoaDon.TAI_QUAY) {
                    log.debug("Cash payment validated for POS order");
                } else {
                    log.debug("Cash on delivery payment validated for online order");
                }
                break;
            case VNPAY:
            case MOMO:
                // Gateway payments are valid for all order types
                log.debug("Gateway payment {} validated for {} order", phuongThucThanhToan, loaiHoaDon);
                break;
            default:
                throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ cho loại đơn hàng " + loaiHoaDon + ": " + phuongThucThanhToan);
        }
    }

    /**
     * Validate payment method compatibility with order type, including mixed payments.
     * Overloaded method to handle string-based payment method validation for mixed payments.
     */
    private void validatePaymentMethodCompatibility(LoaiHoaDon loaiHoaDon, String phuongThucThanhToan) {
        if (phuongThucThanhToan == null) {
            throw new IllegalArgumentException("Phương thức thanh toán không được để trống");
        }

        // Handle mixed payment scenario
        if ("MIXED".equals(phuongThucThanhToan)) {
            // Mixed payments are valid for all order types
            log.debug("Mixed payment validated for {} order", loaiHoaDon);
            return;
        }

        // Handle standard payment methods
        try {
            PhuongThucThanhToan paymentMethod = PhuongThucThanhToan.valueOf(phuongThucThanhToan);
            validatePaymentMethodCompatibility(loaiHoaDon, paymentMethod);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Phương thức thanh toán không hợp lệ cho loại đơn hàng " + loaiHoaDon + ": " + phuongThucThanhToan);
        }
    }

    /**
     * Enhanced inventory reservation with better coordination and error handling.
     */
    private List<Long> reserveInventoryWithCoordination(HoaDonDto hoaDonDto, String orderChannel, String tempOrderId) {
        long startTime = System.currentTimeMillis();

        if (log.isInfoEnabled()) {
            log.info("Bắt đầu đặt trước tồn kho cho đơn hàng {} - Kênh: {}, Số_sản_phẩm: {}",
                    tempOrderId, orderChannel, hoaDonDto.getChiTiet().size());
        }

        // Enhanced pre-validation logging
        if (log.isDebugEnabled()) {
            for (int i = 0; i < hoaDonDto.getChiTiet().size(); i++) {
                HoaDonChiTietDto item = hoaDonDto.getChiTiet().get(i);
                log.debug("Sản phẩm {}/{}: variantId={}, soLuong={}, serialNumberId={}",
                        i + 1, hoaDonDto.getChiTiet().size(),
                        item.getSanPhamChiTietId(), item.getSoLuong(), item.getSerialNumberId());
            }
        }

        try {
            // Pre-check inventory availability for better error messages
            for (HoaDonChiTietDto item : hoaDonDto.getChiTiet()) {
                int availableQuantity = serialNumberService.getAvailableQuantityByVariant(item.getSanPhamChiTietId());

                if (log.isDebugEnabled()) {
                    log.debug("Kiểm tra tồn kho trước khi đặt trước - Variant: {}, Khả_dụng: {}, Yêu_cầu: {}",
                            item.getSanPhamChiTietId(), availableQuantity, item.getSoLuong());
                }

                if (availableQuantity < item.getSoLuong()) {
                    log.warn("Không đủ tồn kho cho variant {}: yêu_cầu={}, khả_dụng={}",
                            item.getSanPhamChiTietId(), item.getSoLuong(), availableQuantity);
                }
            }

            // Reserve inventory items with order tracking
            List<Long> reservedItemIds = serialNumberService.reserveItemsWithTracking(
                    hoaDonDto.getChiTiet(),
                    orderChannel,
                    tempOrderId,
                    "system"
            );

            long executionTime = System.currentTimeMillis() - startTime;

            // Enhanced success logging with Vietnamese terminology
            if (log.isInfoEnabled()) {
                log.info("Hoàn thành đặt trước tồn kho thành công: {} serial number cho đơn hàng {}, " +
                                "kênh={}, thời_gian={}ms",
                        reservedItemIds.size(), tempOrderId, orderChannel, executionTime);
            }

            return reservedItemIds;

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;

            // Enhanced error logging with Vietnamese terminology and context
            log.error("Thất bại trong việc đặt trước tồn kho cho đơn hàng {}: {}, " +
                            "kênh={}, số_sản_phẩm={}, thời_gian_thất_bại={}ms",
                    tempOrderId, e.getMessage(), orderChannel, hoaDonDto.getChiTiet().size(), executionTime);

            // Log specific inventory issues for troubleshooting
            if (e.getMessage().contains("Không đủ hàng tồn kho") || e.getMessage().contains("Insufficient inventory")) {
                log.warn("Chi tiết lỗi tồn kho cho đơn hàng {}:", tempOrderId);
                for (HoaDonChiTietDto item : hoaDonDto.getChiTiet()) {
                    try {
                        int availableQuantity = serialNumberService.getAvailableQuantityByVariant(item.getSanPhamChiTietId());
                        log.warn("  - Variant {}: yêu_cầu={}, khả_dụng={}",
                                item.getSanPhamChiTietId(), item.getSoLuong(), availableQuantity);
                    } catch (Exception ex) {
                        log.warn("  - Variant {}: không thể kiểm tra tồn kho - {}",
                                item.getSanPhamChiTietId(), ex.getMessage());
                    }
                }
            }

            throw new RuntimeException("Không thể đặt trước hàng tồn kho: " + e.getMessage(), e);
        }
    }

    /**
     * Create order entity with enhanced transaction coordination.
     */
    private HoaDon createOrderEntityWithCoordination(HoaDonDto hoaDonDto, NguoiDung currentUser, String tempOrderId) {
        log.debug("Creating order entity for temp order: {}", tempOrderId);

        // Create HoaDon entity manually to avoid mapper issues with nested entities
        HoaDon hoaDon = new HoaDon();
        hoaDon.setMaHoaDon(hoaDonDto.getMaHoaDon() != null ? hoaDonDto.getMaHoaDon() : generateOrderCode());
        hoaDon.setNgayTao(Instant.now());
        hoaDon.setNgayCapNhat(Instant.now());
        hoaDon.setTrangThaiDonHang(TrangThaiDonHang.CHO_XAC_NHAN);
        hoaDon.setTrangThaiThanhToan(TrangThaiThanhToan.CHUA_THANH_TOAN);
        hoaDon.setLoaiHoaDon(hoaDonDto.getLoaiHoaDon());
        hoaDon.setNguoiTao(currentUser != null ? currentUser.getEmail() : "system");
        hoaDon.setNguoiCapNhat(currentUser != null ? currentUser.getEmail() : "system");
        hoaDon.setNguoiNhanEmail(hoaDonDto.getNguoiNhanEmail());

        // Set customer information with enhanced validation
        setCustomerWithValidation(hoaDon, hoaDonDto, currentUser);

        // Set employee information with enhanced validation
        setEmployeeWithValidation(hoaDon, hoaDonDto, currentUser);

        // Validate and set delivery address
        validateAndSetDeliveryAddress(hoaDon, hoaDonDto);

        log.debug("Order entity created successfully for temp order: {}", tempOrderId);
        return hoaDon;
    }

    /**
     * Enhanced customer setting with validation and error handling.
     */
    private void setCustomerWithValidation(HoaDon hoaDon, HoaDonDto hoaDonDto, NguoiDung currentUser) {
        if (hoaDonDto.getKhachHangId() != null) {
            // Use customer ID from DTO (for orders with specific customer)
            NguoiDung customer = nguoiDungRepository.findByIdWithAddresses(hoaDonDto.getKhachHangId())
                    .orElseThrow(() -> new EntityNotFoundException("Khách hàng không tồn tại với ID: " + hoaDonDto.getKhachHangId()));
            hoaDon.setKhachHang(customer);
            log.debug("Set customer from DTO: {}", customer.getHoTen());
        } else if (currentUser != null && currentUser.getVaiTro() == VaiTro.CUSTOMER) {
            // Only auto-assign currentUser as customer if they are actually a customer
            NguoiDung customerWithAddresses = nguoiDungRepository.findByIdWithAddresses(currentUser.getId())
                    .orElse(currentUser);
            hoaDon.setKhachHang(customerWithAddresses);
        } else {

            log.debug("No customer assigned for POS walk-in order");
        }
    }

    /**
     * Enhanced employee setting with validation and error handling.
     */
    private void setEmployeeWithValidation(HoaDon hoaDon, HoaDonDto hoaDonDto, NguoiDung currentUser) {
        if (hoaDonDto.getNhanVienId() != null) {
            // Use explicit staff member ID from DTO
            NguoiDung nhanVien = nguoiDungRepository.findByIdWithAddresses(hoaDonDto.getNhanVienId())
                    .orElseThrow(() -> new EntityNotFoundException("Nhân viên không tồn tại với ID: " + hoaDonDto.getNhanVienId()));
            hoaDon.setNhanVien(nhanVien);
            log.debug("Set employee from DTO: {}", nhanVien.getHoTen());
        } else {
            // Auto-assignment logic for staff
            if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.TAI_QUAY && currentUser != null &&
                    (currentUser.getVaiTro() == VaiTro.STAFF || currentUser.getVaiTro() == VaiTro.ADMIN)) {
                NguoiDung staffWithAddresses = nguoiDungRepository.findByIdWithAddresses(currentUser.getId())
                        .orElse(currentUser);
                hoaDon.setNhanVien(staffWithAddresses);
                log.info("Auto-assigned current user {} to TAI_QUAY order", currentUser.getEmail());
            } else if (hoaDon.getLoaiHoaDon() == LoaiHoaDon.ONLINE) {
                // Online orders don't need staff assignment
                hoaDon.setNhanVien(null);
                log.debug("ONLINE order - no staff assignment needed");
            } else {
                // TAI_QUAY order without valid staff user
                hoaDon.setNhanVien(null);
                log.warn("TAI_QUAY order created without valid staff user. CurrentUser: {}",
                        currentUser != null ? currentUser.getVaiTro() : "null");
            }
        }
    }

    /**
     * Generate unique order code with format HD + timestamp + random number.
     */
    private String generateOrderCode() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String randomSuffix = String.format("%03d", (int) (Math.random() * 1000));
        return "HD" + timestamp.substring(timestamp.length() - 8) + randomSuffix;
    }

    /**
     * Validate VNPay payment parameters.
     * Replaces the validation logic that was previously in PaymentServiceFactory.
     */
    private void validateVNPayPaymentParameters(Long orderId, long amount, String orderInfo, String baseUrl) {
        // Validate order ID
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive number");
        }

        // Validate amount using PaymentValidationService
        paymentParameterValidationService.validateAmount(amount);

        // Validate order info
        paymentParameterValidationService.validateOrderInfo(orderInfo);

        // Validate base URL
        paymentParameterValidationService.validateUrl(baseUrl, "Base URL");

        // Additional VNPay-specific validations
        if (amount < 5000) { // VNPay minimum amount
            throw new IllegalArgumentException("VNPay payment requires minimum amount of 5,000 VND");
        }

        log.debug("VNPay payment parameters validated successfully for order {}", orderId);
    }

    /**
     * Validate MoMo payment parameters.
     * Ensures consistent validation pattern with VNPay implementation.
     */
    private void validateMoMoPaymentParameters(Long orderId, int amount, String orderInfo, String baseUrl) {
        // Validate order ID
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Order ID must be a positive number");
        }

        // Validate amount using PaymentValidationService
        paymentParameterValidationService.validateAmount(amount);

        // Validate order info
        paymentParameterValidationService.validateOrderInfo(orderInfo);

        // Validate base URL
        paymentParameterValidationService.validateUrl(baseUrl, "Base URL");

        // Additional MoMo-specific validations
        if (amount < 1000) { // MoMo minimum amount
            throw new IllegalArgumentException("MoMo payment requires minimum amount of 1,000 VND");
        }

        log.debug("MoMo payment parameters validated successfully for order {}", orderId);
    }

}