package com.lapxpert.backend.giaoca.controller;

import com.lapxpert.backend.giaoca.entity.LichLamViec;
import com.lapxpert.backend.giaoca.service.LichLamViecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/llv")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class LichLamViecController {
    @Autowired
    private LichLamViecService lichLamViecService;

    @GetMapping
    public List<LichLamViec> getLichLamViec() {
        return lichLamViecService.getLLV();
    }

    @PostMapping("/import")
    public ResponseEntity<?> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("File rỗng hoặc không tồn tại");
            }
            System.out.println("Tên file: " + file.getOriginalFilename());
            System.out.println("Kích thước: " + file.getSize());
            lichLamViecService.importExcel(file);
            return ResponseEntity.ok("Import thành công!");
        } catch (Exception e) {
            e.printStackTrace(); // ⚠️ In lỗi chi tiết ra console
            return ResponseEntity.badRequest().body("Lỗi xử lý file: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateLichLamViec(@PathVariable Long id, @RequestBody LichLamViec lichUpdate) {
        try {
            LichLamViec updated = lichLamViecService.updateLichLamViec(id, lichUpdate);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLichLamViec(@PathVariable Long id) {
        try {
            lichLamViecService.deleteById(id);
            return ResponseEntity.ok("Xóa thành công bản ghi có id = " + id);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Lỗi khi xóa bản ghi: " + e.getMessage());
        }
    }
}
