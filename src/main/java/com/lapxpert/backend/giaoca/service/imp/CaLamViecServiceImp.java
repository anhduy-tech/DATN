package com.lapxpert.backend.giaoca.service.imp;

import com.lapxpert.backend.giaoca.dto.MoCaRequest;
import com.lapxpert.backend.giaoca.entity.CaLamViec;
import com.lapxpert.backend.giaoca.repository.CaLamViecRepository;
import com.lapxpert.backend.giaoca.service.CaLamViecService;
import com.lapxpert.backend.hoadon.repository.ThanhToanRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Transactional
public class CaLamViecServiceImp implements CaLamViecService {
    @Autowired
    private CaLamViecRepository repository;
    @Autowired
    private ThanhToanRepository ttRepository;

    @Override
    public CaLamViec moCa(MoCaRequest request) {
        CaLamViec ca = new CaLamViec();
        ca.setTienMatDauCa(request.getTienMatDauCa());
        ca.setChuyenKhoanDauCa(request.getChuyenKhoanDauCa());
        ca.setTienMatCuoiCa(BigDecimal.ZERO);
        ca.setChuyenKhoanCuoiCa(BigDecimal.ZERO);
        ca.setGioMoCa(Instant.now());
        ca.setGioDongCa(Instant.now());
        ca.setTrangThai("ca hiện tại");

        return repository.save(ca);
    }

    @Override
    public CaLamViec hienTai() {
        return repository.findByTrangThai("ca hiện tại")
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca hiện tại"));
    }

    @Override
    public void dongCa(Long id) {
        CaLamViec ca = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ca làm việc với ID: " + id));

        Instant gioMoCa = ca.getGioMoCa();
        Instant gioHienTai = Instant.now();

        // Gọi repository để lấy tổng tiền mặt và chuyển khoản kể từ khi mở ca
        BigDecimal tongTienMat = ttRepository.tinhTongTienMat(gioMoCa);
        BigDecimal tongChuyenKhoan = ttRepository.tinhTongChuyenKhoan(gioMoCa);

        // Gán giá trị an toàn (tránh null)
        ca.setTienMatCuoiCa(tongTienMat != null ? tongTienMat : BigDecimal.ZERO);
        ca.setChuyenKhoanCuoiCa(tongChuyenKhoan != null ? tongChuyenKhoan : BigDecimal.ZERO);

        ca.setGioDongCa(gioHienTai);
        ca.setTrangThai("ca quá khứ"); // hoặc enum nếu bạn dùng enum

        System.out.println("Tien mat cuoi ca: " + tongTienMat);
        System.out.println("Chuyen khoan cuoi ca: " + tongChuyenKhoan);

        repository.save(ca);
    }


}
