package com.lapxpert.backend.giaoca.repository;

import com.lapxpert.backend.giaoca.entity.LichLamViec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LichLamViecRepository extends JpaRepository<LichLamViec, Long> {
    List<LichLamViec> findByNgay(String ngay);
    List<LichLamViec> findByNhanVienPhuTrach(String nhanVienPhuTrach);
}
