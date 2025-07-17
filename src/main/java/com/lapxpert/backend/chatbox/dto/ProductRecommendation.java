package com.lapxpert.backend.chatbox.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO cho product recommendations từ AI chat service
 * Tương ứng với ProductRecommendation model trong Python FastAPI service
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductRecommendation {
    
    @JsonProperty("san_pham_chi_tiet_id")
    private Long sanPhamChiTietId;
    
    @JsonProperty("ten_san_pham")
    private String tenSanPham;
    
    @JsonProperty("gia_ban")
    private BigDecimal giaBan;
    
    @JsonProperty("mo_ta")
    private String moTa;
    
    @JsonProperty("similarity_score")
    private Double similarityScore;
    
    /**
     * Constructor với basic product info
     */
    public ProductRecommendation(Long sanPhamChiTietId, String tenSanPham, BigDecimal giaBan) {
        this.sanPhamChiTietId = sanPhamChiTietId;
        this.tenSanPham = tenSanPham;
        this.giaBan = giaBan;
    }
    
    /**
     * Getter cho formatted price
     */
    public String getFormattedPrice() {
        if (giaBan == null) {
            return "0 VNĐ";
        }
        return String.format("%,.0f VNĐ", giaBan);
    }
    
    /**
     * Getter cho similarity percentage
     */
    public String getSimilarityPercentage() {
        if (similarityScore == null) {
            return "0%";
        }
        return String.format("%.1f%%", similarityScore * 100);
    }
}
