package com.lapxpert.backend.hoadon.controller;

import com.lapxpert.backend.hoadon.dto.HoaDonDto;
import com.lapxpert.backend.hoadon.entity.HoaDonAuditHistory;
import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import com.lapxpert.backend.hoadon.enums.TrangThaiThanhToan;
import com.lapxpert.backend.hoadon.service.HoaDonService;
import com.lapxpert.backend.hoadon.service.ReceiptPreviewService;
import com.lapxpert.backend.nguoidung.entity.NguoiDung;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping({"/api/v1/hoa-don","/api/v2/hoa-don"})
public class HoaDonController {

    private final HoaDonService hoaDonService;
    private final ReceiptPreviewService receiptPreviewService;


    public HoaDonController(HoaDonService hoaDonService, ReceiptPreviewService receiptPreviewService) {
        this.hoaDonService = hoaDonService;
        this.receiptPreviewService = receiptPreviewService;
    }

    // Lấy tất cả hóa đơn hoặc lọc theo trạng thái giao hàng
    // This effectively serves as getAllHoaDons for admin if trangThai is null/empty
    @GetMapping
    public ResponseEntity<List<HoaDonDto>> getAllHoaDon(@RequestParam(value = "trangThai", required = false) String trangThai) {
        List<HoaDonDto> hoaDonDtos = hoaDonService.getHoaDonsByTrangThai(trangThai);
        return ResponseEntity.ok(hoaDonDtos);
    }

    // Thêm mới hóa đơn - Path changed from /add to / to match frontend, NguoiDung added
    @PostMapping
    public ResponseEntity<HoaDonDto> createHoaDon(@RequestBody HoaDonDto hoaDonDto, @AuthenticationPrincipal NguoiDung currentUser) {
        HoaDonDto createdHoaDonDto = hoaDonService.createHoaDon(hoaDonDto, currentUser);
        return new ResponseEntity<>(createdHoaDonDto, HttpStatus.CREATED);
    }

    // Lấy hóa đơn theo ID với kiểm tra bảo mật
    @GetMapping("/{id}")
    public ResponseEntity<HoaDonDto> getHoaDonById(@PathVariable Long id, @AuthenticationPrincipal NguoiDung currentUser) {
        HoaDonDto hoaDonDto = hoaDonService.getHoaDonByIdSecure(id, currentUser);
        return ResponseEntity.ok(hoaDonDto);
    }

    // Cập nhật hóa đơn với kiểm tra bảo mật
    @PutMapping("/{id}")
    public ResponseEntity<HoaDonDto> updateHoaDon(@PathVariable Long id, @RequestBody HoaDonDto hoaDonDto, @AuthenticationPrincipal NguoiDung currentUser) {
        // Security check is handled in the service method
        HoaDonDto updatedHoaDonDto = hoaDonService.updateHoaDon(id, hoaDonDto, currentUser);
        return ResponseEntity.ok(updatedHoaDonDto);
    }

    // Endpoint to get orders for the authenticated user - NEW
    @GetMapping("/me")
    public ResponseEntity<List<HoaDonDto>> getMyOrders(@AuthenticationPrincipal NguoiDung currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        List<HoaDonDto> orders = hoaDonService.findByNguoiDungEmail(currentUser.getEmail());
        return ResponseEntity.ok(orders);
    }

    // Note: Timeline functionality is now handled by HoaDonAuditHistory
    // Use HoaDonAuditHistoryDto.TimelineEntry for frontend timeline display

    // Endpoint để xác nhận thanh toán với kiểm tra bảo mật
    @PostMapping("/{orderId}/confirm-payment")
    public ResponseEntity<HoaDonDto> confirmPayment(
            @PathVariable Long orderId,
            @RequestParam PhuongThucThanhToan phuongThucThanhToan,
            @AuthenticationPrincipal NguoiDung currentUser) {
        HoaDonDto confirmedOrder = hoaDonService.confirmPaymentSecure(orderId, phuongThucThanhToan, currentUser);
        return ResponseEntity.ok(confirmedOrder);
    }

    // Endpoint để hủy đơn hàng với kiểm tra bảo mật
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<HoaDonDto> cancelOrder(
            @PathVariable Long orderId,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal NguoiDung currentUser) {
        HoaDonDto cancelledOrder = hoaDonService.cancelOrderSecure(orderId, reason != null ? reason : "Cancelled by user", currentUser);
        return ResponseEntity.ok(cancelledOrder);
    }

    // Endpoint để cập nhật trạng thái thanh toán với kiểm tra bảo mật
    @PutMapping("/{orderId}/payment-status")
    public ResponseEntity<HoaDonDto> updatePaymentStatus(
            @PathVariable Long orderId,
            @RequestParam TrangThaiThanhToan trangThaiThanhToan,
            @RequestParam(required = false) String ghiChu,
            @AuthenticationPrincipal NguoiDung currentUser) {
        HoaDonDto updatedOrder = hoaDonService.updatePaymentStatusSecure(orderId, trangThaiThanhToan, ghiChu, currentUser);
        return ResponseEntity.ok(updatedOrder);
    }

    // Endpoint để lấy dữ liệu preview hóa đơn
    @GetMapping("/{orderId}/receipt-preview")
    public ResponseEntity<ReceiptPreviewService.ReceiptPreviewData> getReceiptPreview(
            @PathVariable Long orderId,
            @AuthenticationPrincipal NguoiDung currentUser) {
        // Security check through service layer
        hoaDonService.getHoaDonByIdSecure(orderId, currentUser); // This will throw if user doesn't have access

        ReceiptPreviewService.ReceiptPreviewData preview = receiptPreviewService.generateReceiptPreview(orderId);
        return ResponseEntity.ok(preview);
    }

    // Endpoint để xử lý thanh toán VNPay cho đơn hàng cụ thể
    @PostMapping("/{orderId}/vnpay-payment")
    public ResponseEntity<Map<String, String>> processVNPayPayment(
            @PathVariable Long orderId,
            @RequestBody VNPayPaymentRequest vnpayRequest,
            @AuthenticationPrincipal NguoiDung currentUser,
            HttpServletRequest request) {

        // Security check: user can only process payment for their own orders
        HoaDonDto order = hoaDonService.getHoaDonByIdSecure(orderId, currentUser);

        // Validate order can be paid
        if (order.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Đơn hàng đã được thanh toán");
        }

        // Create VNPay payment URL with order correlation
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String clientIp = getClientIpAddress(request);
        String vnpayUrl = hoaDonService.createVNPayPayment(orderId, vnpayRequest.getAmount(),
                vnpayRequest.getOrderInfo(), baseUrl, clientIp);

        Map<String, String> response = new HashMap<>();
        response.put("paymentUrl", vnpayUrl);
        response.put("orderId", orderId.toString());
        return ResponseEntity.ok(response);
    }

    // Endpoint để xử lý thanh toán MoMo cho đơn hàng cụ thể
    @PostMapping("/{orderId}/momo-payment")
    public ResponseEntity<Map<String, String>> processMoMoPayment(
            @PathVariable Long orderId,
            @RequestBody MoMoPaymentRequest momoRequest,
            @AuthenticationPrincipal NguoiDung currentUser,
            HttpServletRequest request) {

        // Security check: user can only process payment for their own orders
        HoaDonDto order = hoaDonService.getHoaDonByIdSecure(orderId, currentUser);

        // Validate order can be paid
        if (order.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
            throw new IllegalStateException("Đơn hàng đã được thanh toán");
        }

        // Create MoMo payment URL with order correlation
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String clientIp = getClientIpAddress(request);
        String momoUrl = hoaDonService.createMoMoPayment(orderId, momoRequest.getAmount(),
                momoRequest.getOrderInfo(), baseUrl, clientIp);

        Map<String, String> response = new HashMap<>();
        response.put("paymentUrl", momoUrl);
        response.put("orderId", orderId.toString());
        response.put("paymentMethod", "MOMO");
        return ResponseEntity.ok(response);
    }

    // Endpoint để xử lý thanh toán hỗn hợp cho đơn hàng cụ thể
    @PostMapping("/{orderId}/mixed-payment")
    public ResponseEntity<Map<String, Object>> processMixedPayment(
            @PathVariable Long orderId,
            @RequestBody MixedPaymentRequest mixedPaymentRequest,
            @AuthenticationPrincipal NguoiDung currentUser,
            HttpServletRequest request) {

        try {
            // Validate mixed payment request
            validateMixedPaymentRequest(mixedPaymentRequest);

            // Security check: user can only process payment for their own orders
            HoaDonDto order = hoaDonService.getHoaDonByIdSecure(orderId, currentUser);

            // Validate order can be paid
            if (order.getTrangThaiThanhToan() == TrangThaiThanhToan.DA_THANH_TOAN) {
                throw new IllegalStateException("Đơn hàng đã được thanh toán");
            }

            // Validate order total matches mixed payment total
            if (order.getTongThanhToan().compareTo(mixedPaymentRequest.getTongTienHang()) != 0) {
                throw new IllegalArgumentException("Tổng tiền thanh toán không khớp với tổng tiền đơn hàng");
            }

            // Get base URL and client IP for payment gateway processing
            String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
            String clientIp = getClientIpAddress(request);

            // Process mixed payment through service layer
            Map<String, Object> paymentResult = hoaDonService.processMixedPayment(
                    orderId,
                    mixedPaymentRequest.getPayments(),
                    baseUrl,
                    clientIp,
                    currentUser
            );

            // Return successful response with payment processing results
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("orderId", orderId.toString());
            response.put("message", "Thanh toán hỗn hợp đã được xử lý thành công");
            response.put("paymentResults", paymentResult);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            // Handle validation errors
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "VALIDATION_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (IllegalStateException e) {
            // Handle business logic errors (e.g., order already paid)
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "BUSINESS_LOGIC_ERROR");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            // Handle unexpected errors
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "INTERNAL_ERROR");
            errorResponse.put("message", "Có lỗi xảy ra khi xử lý thanh toán hỗn hợp");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // DTO class for VNPay payment request
    public static class VNPayPaymentRequest {
        private long amount;
        private String orderInfo;
        private String returnUrl;

        // Getters and setters
        public long getAmount() { return amount; }
        public void setAmount(long amount) { this.amount = amount; }

        public String getOrderInfo() { return orderInfo; }
        public void setOrderInfo(String orderInfo) { this.orderInfo = orderInfo; }

        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    }

    // DTO class for MoMo payment request
    public static class MoMoPaymentRequest {
        private int amount;
        private String orderInfo;
        private String returnUrl;

        // Getters and setters
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        public String getOrderInfo() { return orderInfo; }
        public void setOrderInfo(String orderInfo) { this.orderInfo = orderInfo; }

        public String getReturnUrl() { return returnUrl; }
        public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    }

    // DTO class for mixed payment request
    public static class MixedPaymentRequest {
        private List<PaymentComponent> payments;
        private BigDecimal tongTienHang;

        // Getters and setters
        public List<PaymentComponent> getPayments() { return payments; }
        public void setPayments(List<PaymentComponent> payments) { this.payments = payments; }

        public BigDecimal getTongTienHang() { return tongTienHang; }
        public void setTongTienHang(BigDecimal tongTienHang) { this.tongTienHang = tongTienHang; }
    }

    // DTO class for individual payment component in mixed payment
    public static class PaymentComponent {
        private PhuongThucThanhToan phuongThucThanhToan;
        private BigDecimal soTien;
        private String ghiChu;

        // Getters and setters
        public PhuongThucThanhToan getPhuongThucThanhToan() { return phuongThucThanhToan; }
        public void setPhuongThucThanhToan(PhuongThucThanhToan phuongThucThanhToan) {
            this.phuongThucThanhToan = phuongThucThanhToan;
        }

        public BigDecimal getSoTien() { return soTien; }
        public void setSoTien(BigDecimal soTien) { this.soTien = soTien; }

        public String getGhiChu() { return ghiChu; }
        public void setGhiChu(String ghiChu) { this.ghiChu = ghiChu; }
    }

    // Validation methods for mixed payment processing
    private void validateMixedPaymentRequest(MixedPaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu thanh toán hỗn hợp không được để trống");
        }

        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            throw new IllegalArgumentException("Danh sách phương thức thanh toán không được để trống");
        }

        if (request.getPayments().size() < 2) {
            throw new IllegalArgumentException("Thanh toán hỗn hợp phải có ít nhất 2 phương thức thanh toán");
        }

        if (request.getPayments().size() > 3) {
            throw new IllegalArgumentException("Thanh toán hỗn hợp không được vượt quá 3 phương thức thanh toán");
        }

        if (request.getTongTienHang() == null || request.getTongTienHang().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Tổng tiền hàng phải lớn hơn 0");
        }

        validatePaymentComponents(request.getPayments());
        validateTotalAmountConsistency(request.getPayments(), request.getTongTienHang());
    }

    private void validatePaymentComponents(List<PaymentComponent> payments) {
        for (int i = 0; i < payments.size(); i++) {
            PaymentComponent payment = payments.get(i);

            if (payment == null) {
                throw new IllegalArgumentException("Thành phần thanh toán thứ " + (i + 1) + " không được để trống");
            }

            if (payment.getPhuongThucThanhToan() == null) {
                throw new IllegalArgumentException("Phương thức thanh toán thứ " + (i + 1) + " không được để trống");
            }

            // Validate supported payment methods
            if (payment.getPhuongThucThanhToan() != PhuongThucThanhToan.TIEN_MAT &&
                    payment.getPhuongThucThanhToan() != PhuongThucThanhToan.VNPAY &&
                    payment.getPhuongThucThanhToan() != PhuongThucThanhToan.MOMO) {
                throw new IllegalArgumentException("Phương thức thanh toán thứ " + (i + 1) + " không được hỗ trợ");
            }

            if (payment.getSoTien() == null || payment.getSoTien().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Số tiền thanh toán thứ " + (i + 1) + " phải lớn hơn 0");
            }
        }

        // Check for duplicate payment methods
        long uniqueMethodsCount = payments.stream()
                .map(PaymentComponent::getPhuongThucThanhToan)
                .distinct()
                .count();

        if (uniqueMethodsCount != payments.size()) {
            throw new IllegalArgumentException("Không được sử dụng trùng lặp phương thức thanh toán");
        }
    }

    private void validateTotalAmountConsistency(List<PaymentComponent> payments, BigDecimal tongTienHang) {
        BigDecimal totalPaymentAmount = payments.stream()
                .map(PaymentComponent::getSoTien)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalPaymentAmount.compareTo(tongTienHang) != 0) {
            throw new IllegalArgumentException(
                    String.format("Tổng số tiền thanh toán (%s) không khớp với tổng tiền hàng (%s)",
                            totalPaymentAmount, tongTienHang)
            );
        }
    }

    // Endpoint để lấy HTML preview hóa đơn
    @GetMapping("/{orderId}/receipt-preview-html")
    public ResponseEntity<String> getReceiptPreviewHtml(
            @PathVariable Long orderId,
            @AuthenticationPrincipal NguoiDung currentUser) {
        // Security check through service layer
        hoaDonService.getHoaDonByIdSecure(orderId, currentUser); // This will throw if user doesn't have access

        String htmlPreview = receiptPreviewService.generateReceiptPreviewHtml(orderId);
        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(htmlPreview);
    }

    // Endpoint để tạo và tải PDF hóa đơn
    @GetMapping("/{orderId}/receipt")
    public ResponseEntity<byte[]> generateReceiptPdf(
            @PathVariable Long orderId,
            @AuthenticationPrincipal NguoiDung currentUser) {
        // Security check through service layer
        hoaDonService.getHoaDonByIdSecure(orderId, currentUser); // This will throw if user doesn't have access

        try {
            byte[] pdfBytes = receiptPreviewService.generateReceiptPdf(orderId);

            return ResponseEntity.ok()
                    .header("Content-Type", "application/pdf")
                    .header("Content-Disposition", "attachment; filename=hoa-don-" + orderId + ".pdf")
                    .body(pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF receipt: " + e.getMessage(), e);
        }
    }

    // Endpoint để lấy lịch sử audit của đơn hàng
    @GetMapping("/{id}/audit-history")
    public ResponseEntity<List<HoaDonAuditHistory>> getOrderAuditHistory(@PathVariable Long id, @AuthenticationPrincipal NguoiDung currentUser) {
        try {
            List<HoaDonAuditHistory> auditHistory = hoaDonService.getAuditHistory(id);
            return ResponseEntity.ok(auditHistory);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Get client IP address from request, handling proxy headers
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }

        // Handle multiple IPs in X-Forwarded-For header
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }

        return ipAddress != null ? ipAddress : "127.0.0.1";
    }
}