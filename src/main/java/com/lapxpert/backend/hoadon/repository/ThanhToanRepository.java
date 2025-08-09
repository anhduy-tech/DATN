package com.lapxpert.backend.hoadon.repository;

import com.lapxpert.backend.hoadon.entity.ThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ThanhToanRepository extends JpaRepository<ThanhToan, Long> {
    @Query("SELECT SUM(t.giaTri) FROM ThanhToan t WHERE t.phuongThucThanhToan = 'TIEN_MAT' AND t.thoiGianThanhToan >= :thoiGianMoCa")
    BigDecimal tinhTongTienMat(@Param("thoiGianMoCa") Instant thoiGianMoCa);
    @Query("SELECT SUM(t.giaTri) FROM ThanhToan t WHERE t.phuongThucThanhToan <> 'TIEN_MAT' AND t.thoiGianThanhToan >= :thoiGianMoCa")
    BigDecimal tinhTongChuyenKhoan(@Param("thoiGianMoCa") Instant thoiGianMoCa);
}
