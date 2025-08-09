package com.lapxpert.backend.giaoca.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ca_lam_viec")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CaLamViec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tien_mat_dau_ca", nullable = false)
    private BigDecimal tienMatDauCa;

    @Column(name = "chuyen_khoan_dau_ca", nullable = false)
    private BigDecimal chuyenKhoanDauCa;

    @Column(name = "tien_mat_cuoi_ca", nullable = false)
    private BigDecimal tienMatCuoiCa;

    @Column(name = "chuyen_khoan_cuoi_ca", nullable = false)
    private BigDecimal chuyenKhoanCuoiCa;

    @CreatedDate
    @Column(name = "gio_mo_ca", nullable = false, updatable = false)
    private Instant gioMoCa;

    @CreatedDate
    @Column(name = "gio_dong_ca", nullable = false)
    private Instant gioDongCa;

    @Column(name = "trang_thai", nullable = false)
    private String trangThai;
}
