package com.lapxpert.backend.sanpham.repository;

import com.lapxpert.backend.sanpham.entity.sanpham.SanPham;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SanPhamRepository extends JpaRepository<SanPham, Long> {
    List<SanPham> findAllByTrangThai(Boolean trangThai);

    Long countByTrangThai(Boolean trangThai);

    @Query(value = "SELECT s.ma_san_pham FROM san_pham s WHERE s.ma_san_pham LIKE 'SP%' ORDER BY s.id DESC LIMIT 1", nativeQuery = true)
    String findLastMaSanPham();

    // Search products by name, code, or description
    @Query("SELECT s FROM SanPham s WHERE " +
           "(:tenSanPham IS NULL OR LOWER(s.tenSanPham) LIKE LOWER(CONCAT('%', :tenSanPham, '%'))) AND " +
           "(:maSanPham IS NULL OR LOWER(s.maSanPham) LIKE LOWER(CONCAT('%', :maSanPham, '%'))) AND " +
           "(:moTa IS NULL OR LOWER(s.moTa) LIKE LOWER(CONCAT('%', :moTa, '%'))) AND " +
           "s.trangThai = true")
    List<SanPham> searchProducts(@Param("tenSanPham") String tenSanPham,
                                @Param("maSanPham") String maSanPham,
                                @Param("moTa") String moTa);

    boolean existsByMaSanPham(String maSanPham);

    /**
     * Finds all active products that have at least one available serial number across all their variants.
     * This is used for displaying products on the client-side (e.g., home page).
     *
     * @return A list of SanPham entities that are active and have available stock.
     */
    @Query(value = """
        SELECT DISTINCT sp.* FROM san_pham sp
        JOIN san_pham_chi_tiet spct ON sp.id = spct.san_pham_id
        LEFT JOIN serial_number sn ON spct.id = sn.san_pham_chi_tiet_id AND sn.trang_thai = 'AVAILABLE'
        WHERE sp.trang_thai = true
        GROUP BY sp.id
        HAVING COUNT(sn.id) > 0
        """, nativeQuery = true)
    List<SanPham> findActiveProductsWithAvailableStock();

}