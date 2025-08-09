package com.lapxpert.backend.giaoca.repository;

import com.lapxpert.backend.giaoca.entity.CaLamViec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CaLamViecRepository extends JpaRepository<CaLamViec, Long> {
    Optional<CaLamViec> findByTrangThai(String trangThai);
}