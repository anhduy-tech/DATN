package com.lapxpert.backend.giaoca.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaoCaoCaLamViecDTO {
    private Long caLamViecId;
    private Instant gioMoCa;
    private Instant gioDongCa;
    private int soLuongHoaDon;
    private BigDecimal tongTienMat;
    private BigDecimal tongChuyenKhoan;
    private List<SanPhamBanDTO> sanPhamDaBan;
}
