package com.lapxpert.backend.hoadon.repository;

import com.lapxpert.backend.hoadon.entity.HoaDon;
import com.lapxpert.backend.hoadon.enums.LoaiHoaDon;
import com.lapxpert.backend.hoadon.enums.TrangThaiDonHang;
import com.lapxpert.backend.hoadon.enums.PhuongThucThanhToan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface HoaDonRepository extends JpaRepository<HoaDon, Long> {

    // Find invoices by order status
    List<HoaDon> findByTrangThaiDonHang(TrangThaiDonHang trangThaiDonHang);

    // Find invoices by customer's email
    List<HoaDon> findByKhachHang_Email(String email);

    /**
     * Get customer ID for an order without fetching full entities
     */
    @Query("SELECT h.khachHang.id FROM HoaDon h WHERE h.id = :orderId")
    Long findCustomerIdByOrderId(@Param("orderId") Long orderId);

    /**
     * Find order by ID with staff and customer relationships eagerly loaded
     */
    @Query("SELECT h FROM HoaDon h " +
            "LEFT JOIN FETCH h.nhanVien " +
            "LEFT JOIN FETCH h.khachHang " +
            "LEFT JOIN FETCH h.diaChiGiaoHang " +
            "WHERE h.id = :id")
    Optional<HoaDon> findByIdWithStaffAndCustomer(@Param("id") Long id);

    // ==================== STATISTICS METHODS ====================

    /**
     * Count orders by status
     */
    Long countByTrangThaiDonHang(TrangThaiDonHang trangThaiDonHang);

    /**
     * Count orders by type (online/POS)
     */
    Long countByLoaiHoaDon(LoaiHoaDon loaiHoaDon);

    /**
     * Find orders by date range and status
     */
    List<HoaDon> findByNgayTaoBetweenAndTrangThaiDonHang(Instant startDate, Instant endDate, TrangThaiDonHang trangThaiDonHang);

    /**
     * Count orders by date range
     */
    Long countByNgayTaoBetween(Instant startDate, Instant endDate);

    /**
     * Count orders by status and date range
     */
    Long countByTrangThaiDonHangAndNgayTaoBetween(TrangThaiDonHang trangThaiDonHang, Instant startDate, Instant endDate);

    /**
     * Find top N orders ordered by creation date descending.
     */
    List<HoaDon> findTopByOrderByNgayTaoDesc(Pageable pageable);

    // ==================== CUSTOMER VALUE STATISTICS ====================

    /**
     * Get customer value statistics
     * @return Array of [customer_count, total_value, avg_value, max_value, min_value]
     */
    @Query("SELECT COUNT(DISTINCT h.khachHang.id), " +
            "SUM(h.tongThanhToan), " +
            "AVG(h.tongThanhToan), " +
            "MAX(h.tongThanhToan), " +
            "MIN(h.tongThanhToan) " +
            "FROM HoaDon h " +
            "WHERE h.trangThaiDonHang = :trangThai")
    Object[] getCustomerValueStatistics(@Param("trangThai") TrangThaiDonHang trangThai);

    /**
     * Get customer lifetime value
     * @param customerId customer ID
     * @return total value of completed orders
     */
    @Query("SELECT COALESCE(SUM(h.tongThanhToan), 0) FROM HoaDon h " +
            "WHERE h.khachHang.id = :customerId " +
            "AND h.trangThaiDonHang = :trangThai")
    BigDecimal getCustomerLifetimeValue(@Param("customerId") Long customerId,
                                        @Param("trangThai") TrangThaiDonHang trangThai);

    /**
     * Count customers who made orders in a period
     */
    @Query("SELECT COUNT(DISTINCT h.khachHang.id) FROM HoaDon h " +
            "WHERE h.ngayTao BETWEEN :tuNgay AND :denNgay " +
            "AND h.trangThaiDonHang = :trangThai")
    Long countActiveCustomers(@Param("tuNgay") Instant tuNgay,
                              @Param("denNgay") Instant denNgay,
                              @Param("trangThai") TrangThaiDonHang trangThai);

    /**
     * Count customers who made repeat orders
     */
    @Query("SELECT COUNT(DISTINCT h.khachHang.id) FROM HoaDon h " +
            "WHERE h.khachHang.id IN (" +
            "  SELECT h2.khachHang.id FROM HoaDon h2 " +
            "  WHERE h2.ngayTao BETWEEN :tuNgay AND :denNgay " +
            "  AND h2.trangThaiDonHang = :trangThai " +
            "  GROUP BY h2.khachHang.id " +
            "  HAVING COUNT(h2.id) > 1" +
            ")")
    Long countRepeatCustomers(@Param("tuNgay") Instant tuNgay,
                              @Param("denNgay") Instant denNgay,
                              @Param("trangThai") TrangThaiDonHang trangThai);

    // ==================== PAYMENT MONITORING METHODS ====================

    /**
     * Find orders with payment timeout
     */
    @Query("SELECT h FROM HoaDon h WHERE h.trangThaiThanhToan = 'CHUA_THANH_TOAN' AND h.ngayTao < :timeoutThreshold")
    List<HoaDon> findOrdersWithPaymentTimeout(@Param("timeoutThreshold") Instant timeoutThreshold);

    /**
     * Count orders in period
     */
    @Query("SELECT COUNT(h) FROM HoaDon h WHERE h.ngayTao >= :since")
    long countOrdersInPeriod(@Param("since") Instant since);

    /**
     * Count paid orders in period
     */
    @Query("SELECT COUNT(h) FROM HoaDon h WHERE h.trangThaiThanhToan = 'DA_THANH_TOAN' AND h.ngayTao >= :since")
    long countPaidOrdersInPeriod(@Param("since") Instant since);

    /**
     * Count pending payment orders in period
     */
    @Query("SELECT COUNT(h) FROM HoaDon h WHERE h.trangThaiThanhToan = 'CHUA_THANH_TOAN' AND h.ngayTao >= :since")
    long countPendingPaymentOrdersInPeriod(@Param("since") Instant since);

    /**
     * Count orders by payment method in period
     * Joins through HoaDonThanhToan to ThanhToan to get payment method
     */
    @Query("SELECT COUNT(DISTINCT h) FROM HoaDon h " +
            "JOIN HoaDonThanhToan hdt ON h.id = hdt.hoaDon.id " +
            "JOIN ThanhToan t ON hdt.thanhToan.id = t.id " +
            "WHERE t.phuongThucThanhToan = :paymentMethod AND h.ngayTao >= :since")
    long countOrdersByPaymentMethodInPeriod(@Param("paymentMethod") PhuongThucThanhToan paymentMethod, @Param("since") Instant since);

    // ==================== ORDER EXPIRATION METHODS ====================

    /**
     * Find expired unpaid orders for automatic cancellation
     * Orders that are unpaid and created before the cutoff time
     */
    @Query("SELECT h FROM HoaDon h " +
            "LEFT JOIN FETCH h.khachHang " +
            "LEFT JOIN FETCH h.hoaDonChiTiets " +
            "WHERE h.trangThaiThanhToan = 'CHUA_THANH_TOAN' " +
            "AND h.trangThaiDonHang NOT IN ('DA_HUY', 'HOAN_THANH') " +
            "AND h.ngayTao < :cutoffTime")
    List<HoaDon> findExpiredUnpaidOrders(@Param("cutoffTime") Instant cutoffTime);
}