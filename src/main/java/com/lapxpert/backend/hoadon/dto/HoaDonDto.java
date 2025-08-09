package com.lapxpert.backend.hoadon.dto;

import com.lapxpert.backend.hoadon.enums.LoaiHoaDon;
import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import com.lapxpert.backend.hoadon.enums.TrangThaiDonHang;
import com.lapxpert.backend.hoadon.enums.TrangThaiThanhToan;
import com.lapxpert.backend.nguoidung.dto.DiaChiDto;
import com.lapxpert.backend.nguoidung.dto.KhachHangDTO;
import com.lapxpert.backend.nguoidung.dto.NhanVienDTO;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
public class HoaDonDto {
    private Long id;
    private String maHoaDon;
    private Long khachHangId;
    private Long nhanVienId;

    // Full customer and employee objects for frontend display
    private KhachHangDTO khachHang;
    private NhanVienDTO nhanVien;

    // Delivery address - can be provided as ID or full object
    private Long diaChiGiaoHangId;
    private DiaChiDto diaChiGiaoHang;

    // Delivery contact information (can be different from account holder)
    private String nguoiNhanTen;
    private String nguoiNhanSdt;
    private String nguoiNhanEmail;

    private BigDecimal tongTienHang;
    private BigDecimal giaTriGiamGiaVoucher;
    private BigDecimal phiVanChuyen;
    private BigDecimal tongThanhToan;
    private TrangThaiDonHang trangThaiDonHang;
    private TrangThaiThanhToan trangThaiThanhToan;
    private PhuongThucThanhToan phuongThucThanhToan;
    private Instant ngayTao;
    private Instant ngayCapNhat;
    private LoaiHoaDon loaiHoaDon;
    // private List<LichSuHoaDonDto> lichSuHoaDons; // Removed as LichSuHoaDon is typically handled separately
    private List<HoaDonChiTietDto> chiTiet;

    // Mixed payment support
    private Boolean isMixedPayment; // Indicates if this order uses mixed payment methods
    private List<Map<String, Object>> mixedPayments; // Mixed payment configuration from frontend

    // Voucher fields for order creation
    private List<String> voucherCodes; // List of voucher codes to apply

    private String trangThaiCongNo;

}
