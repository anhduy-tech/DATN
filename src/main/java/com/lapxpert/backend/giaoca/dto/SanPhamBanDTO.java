package com.lapxpert.backend.giaoca.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
public class SanPhamBanDTO {
    private String tenSanPham;
    private int soLuong;

    public SanPhamBanDTO(String tenSanPham, int soLuong) {
        this.tenSanPham = tenSanPham;
        this.soLuong = soLuong;
    }

    // Getter & Setter
    public String getTenSanPham() {
        return tenSanPham;
    }

    public void setTenSanPham(String tenSanPham) {
        this.tenSanPham = tenSanPham;
    }

    public int getSoLuong() {
        return soLuong;
    }

    public void setSoLuong(int soLuong) {
        this.soLuong = soLuong;
    }
}
