package com.lapxpert.backend.giaoca.service.imp;

import com.lapxpert.backend.giaoca.entity.LichLamViec;
import com.lapxpert.backend.giaoca.repository.LichLamViecRepository;
import com.lapxpert.backend.giaoca.service.LichLamViecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LichLamViecServiceImp implements LichLamViecService {
    @Autowired
    private LichLamViecRepository llvRepository;

    @Override
    public List<LichLamViec> getLLV() {
        return llvRepository.findAll();
    }
}
