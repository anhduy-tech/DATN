package com.lapxpert.backend.sanpham.controller;

import com.lapxpert.backend.sanpham.dto.SanPhamChiTietDto;
import com.lapxpert.backend.sanpham.entity.sanpham.SanPhamChiTiet;
import com.lapxpert.backend.sanpham.service.SanPhamChiTietService;
import com.lapxpert.backend.sanpham.service.PriceChangeNotificationService;
import com.lapxpert.backend.sanpham.mapper.SanPhamChiTietMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping({"/api/v1/products-details","/api/v2/products-details"})
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Validated
@Slf4j
public class SanPhamChiTietController {
    private final SanPhamChiTietService sanPhamChiTietService;
    private final PriceChangeNotificationService priceChangeNotificationService;
    private final SanPhamChiTietMapper sanPhamChiTietMapper;

    // Lấy danh sách sản phẩm có trạng thái = true
    @GetMapping("/list")
    public List<SanPhamChiTietDto> getActiveProductsDetailed() {
        return sanPhamChiTietService.getActiveProducts();
    }

    // Thêm sản phẩm mới
    @PostMapping("/add")
    public ResponseEntity<SanPhamChiTietDto> addProductDetailed(@Valid @RequestBody SanPhamChiTiet sanPham) {
        SanPhamChiTiet savedProduct = sanPhamChiTietService.addProduct(sanPham);
        SanPhamChiTietDto dto = sanPhamChiTietMapper.toDto(savedProduct);
        return ResponseEntity.ok(dto);
    }

    // Cập nhật sản phẩm - Fixed to return DTO to prevent circular reference serialization
    @PutMapping("/update/{id}")
    public ResponseEntity<SanPhamChiTietDto> updateProductDetailed(@PathVariable Long id, @Valid @RequestBody SanPhamChiTiet sanPham) {
        SanPhamChiTiet updatedProduct = sanPhamChiTietService.updateProduct(id, sanPham);
        SanPhamChiTietDto dto = sanPhamChiTietMapper.toDto(updatedProduct);
        return ResponseEntity.ok(dto);
    }

    // Xóa mềm sản phẩm
    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> softDeleteProductDetailed(@PathVariable Long id) {
        sanPhamChiTietService.softDeleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Test price change notification endpoint
     * For testing WebSocket price update integration
     * Enhanced to test circular reference fix
     */
    @PostMapping("/test-price-notification/{id}")
    public ResponseEntity<Map<String, Object>> testPriceNotification(
            @PathVariable Long id,
            @RequestParam Double newPrice) {
        try {
            log.info("Testing price notification for variant {} with new price {}", id, newPrice);

            // Get the variant
            Optional<SanPhamChiTiet> variantOpt = sanPhamChiTietService.findById(id);
            if (variantOpt.isEmpty()) {
                log.warn("Variant {} not found for price notification test", id);
                return ResponseEntity.notFound().build();
            }

            SanPhamChiTiet variant = variantOpt.get();

            Double oldPrice = variant.getGiaBan() != null ? variant.getGiaBan().doubleValue() : 0.0;
            String productName = variant.getSanPham() != null ? variant.getSanPham().getTenSanPham() : "Test Product";
            String sku = variant.getSku() != null ? variant.getSku() : "TEST-SKU";

            log.info("Sending test notification: {} ({}) {} -> {}", productName, sku, oldPrice, newPrice);

            // Send test notification
            priceChangeNotificationService.sendTestNotification(
                id,
                sku,
                productName,
                oldPrice,
                newPrice
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test price notification sent successfully");
            response.put("variantId", id);
            response.put("productName", productName);
            response.put("sku", sku);
            response.put("oldPrice", oldPrice);
            response.put("newPrice", newPrice);
            response.put("timestamp", Instant.now().toString());

            log.info("Test price notification completed successfully for variant {}", id);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error sending test price notification for variant {}: {}", id, e.getMessage(), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("variantId", id);
            response.put("timestamp", Instant.now().toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
