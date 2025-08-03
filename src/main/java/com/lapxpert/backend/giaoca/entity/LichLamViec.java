package com.lapxpert.backend.giaoca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "lich_lam_viec")
public class LichLamViec {
    @Id
    @Column(name = "id", nullable = false)
    private Long id;
    @Column(name = "ngay", nullable = false)
    private String ngay;
    @Column(name = "thoi_gian", nullable = false)
    private String thoiGian;
    @Column(name = "nhan_vien_phu_trach", nullable = false)
    private String nhanVienPhuTrach;
}
