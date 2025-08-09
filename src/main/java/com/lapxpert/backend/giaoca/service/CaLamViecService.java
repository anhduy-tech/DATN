package com.lapxpert.backend.giaoca.service;

import com.lapxpert.backend.giaoca.dto.MoCaRequest;
import com.lapxpert.backend.giaoca.entity.CaLamViec;

public interface CaLamViecService {
    CaLamViec moCa(MoCaRequest request);
    CaLamViec hienTai();
    void dongCa(Long id);
}
