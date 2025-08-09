package com.lapxpert.backend.giaoca.service.imp;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;
import com.lapxpert.backend.giaoca.entity.LichLamViec;
import com.lapxpert.backend.giaoca.repository.LichLamViecRepository;
import com.lapxpert.backend.giaoca.service.LichLamViecService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

@Service
public class LichLamViecServiceImp implements LichLamViecService {
    @Autowired
    private LichLamViecRepository llvRepository;

    @Override
    public List<LichLamViec> getLLV() {
        return llvRepository.findAll();
    }

    @Override
    public void importExcel(MultipartFile file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<LichLamViec> danhSach = new ArrayList<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    LichLamViec lich = new LichLamViec();

                    // --- Cột 0: Ngày (dạng chuỗi hoặc ngày Excel) ---
                    Cell cellNgay = row.getCell(0);
                    if (cellNgay != null) {
                        String ngayStr;
                        if (cellNgay.getCellType() == CellType.STRING) {
                            ngayStr = cellNgay.getStringCellValue();
                        } else if (cellNgay.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cellNgay)) {
                            LocalDate localDate = cellNgay.getDateCellValue().toInstant()
                                    .atZone(ZoneId.systemDefault()).toLocalDate();
                            ngayStr = localDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                        } else {
                            throw new IllegalArgumentException("Ngày không hợp lệ ở dòng " + (i + 1));
                        }
                        lich.setNgay(ngayStr);
                    } else {
                        throw new IllegalArgumentException("Ngày bị thiếu ở dòng " + (i + 1));
                    }

                    // --- Cột 1: Thời gian ---
                    Cell cellThoiGian = row.getCell(1);
                    if (cellThoiGian != null && cellThoiGian.getCellType() == CellType.STRING) {
                        lich.setThoiGian(cellThoiGian.getStringCellValue());
                    } else {
                        throw new IllegalArgumentException("Thời gian bị thiếu hoặc sai kiểu ở dòng " + (i + 1));
                    }

                    // --- Cột 2: Nhân viên phụ trách ---
                    Cell cellNhanVien = row.getCell(2);
                    if (cellNhanVien != null && cellNhanVien.getCellType() == CellType.STRING) {
                        lich.setNhanVienPhuTrach(cellNhanVien.getStringCellValue());
                    } else {
                        throw new IllegalArgumentException("Nhân viên bị thiếu hoặc sai kiểu ở dòng " + (i + 1));
                    }

                    danhSach.add(lich);
                } catch (Exception e) {
                    System.err.println("❌ Lỗi khi đọc dòng " + (i + 1) + ": " + e.getMessage());
                    continue;
                }
            }

            if (!danhSach.isEmpty()) {
                llvRepository.saveAll(danhSach);
            }
        }

    }

    @Override
    public LichLamViec updateLichLamViec(Long id, LichLamViec updated) {
        LichLamViec existing = llvRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch làm việc với id: " + id));

        existing.setNgay(updated.getNgay());
        existing.setThoiGian(updated.getThoiGian());
        existing.setNhanVienPhuTrach(updated.getNhanVienPhuTrach());

        return llvRepository.save(existing);
    }

    @Override
    public void deleteById(Long id) {
        llvRepository.deleteById(id);
    }
}
