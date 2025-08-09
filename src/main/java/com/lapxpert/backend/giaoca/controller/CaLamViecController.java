package com.lapxpert.backend.giaoca.controller;

import com.lapxpert.backend.giaoca.dto.MoCaRequest;
import com.lapxpert.backend.giaoca.entity.CaLamViec;
import com.lapxpert.backend.giaoca.service.CaLamViecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clv")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class CaLamViecController {
    @Autowired
    private CaLamViecService caLamViecService;

    @PostMapping("/mo-ca")
    public ResponseEntity<CaLamViec> moCa(@RequestBody MoCaRequest request) {
        CaLamViec result = caLamViecService.moCa(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/hien-tai")
    public ResponseEntity<?> getCaLamViecHienTai() {
        try {
            CaLamViec clv = caLamViecService.hienTai();
            return ResponseEntity.ok(clv);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Không có ca làm việc nào đang mở.");
        }
    }

    @PutMapping("/dong-ca/{id}")
    public ResponseEntity<?> dongCa(@PathVariable("id") Long id) {
        log.info("Yêu cầu đóng ca với id: {}", id);
        try {
            caLamViecService.dongCa(id);
            return ResponseEntity.ok("Đóng ca thành công");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lỗi: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Có lỗi xảy ra khi đóng ca");
        }
    }
}
