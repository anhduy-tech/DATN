package com.lapxpert.backend.giaoca.service;

import com.lapxpert.backend.giaoca.entity.LichLamViec;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LichLamViecService {
    List<LichLamViec> getLLV();
    void importExcel(MultipartFile file) throws Exception;
    LichLamViec updateLichLamViec(Long id, LichLamViec updated);
    void deleteById(Long id);
}
