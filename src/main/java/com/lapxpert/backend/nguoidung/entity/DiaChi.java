package com.lapxpert.backend.nguoidung.entity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "dia_chi")
public class DiaChi {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dia_chi_id_gen")
    @SequenceGenerator(name = "dia_chi_id_gen", sequenceName = "dia_chi_id_seq", allocationSize = 1)
    @Column(name = "id", nullable = false)
    private Long id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "nguoi_dung_id")
    private NguoiDung nguoiDung;

    @Column(nullable = false, length = 255)
    private String duong;

    @Column(name = "phuong_xa", nullable = false, length = 100)
    private String phuongXa;

    @Column(name = "quan_huyen", nullable = false, length = 100)
    private String quanHuyen;

    @Column(name = "tinh_thanh", nullable = false, length = 100)
    private String tinhThanh;

    @Column(name = "quoc_gia", length = 100, columnDefinition = "varchar(100) default 'Việt Nam'")
    @Builder.Default
    private String quocGia = "Việt Nam";

    @Column(name = "loai_dia_chi", length = 50)
    private String loaiDiaChi;

    @Column(name = "la_mac_dinh", nullable = false)
    @Builder.Default
    private Boolean laMacDinh = false;

    @Column(name = "ngay_tao", columnDefinition = "timestamp with time zone default CURRENT_TIMESTAMP")
    @Builder.Default
    private ZonedDateTime ngayTao = ZonedDateTime.now();

    @Column(name = "ngay_cap_nhat", columnDefinition = "timestamp with time zone default CURRENT_TIMESTAMP")
    @Builder.Default
    private ZonedDateTime ngayCapNhat = ZonedDateTime.now();
}
