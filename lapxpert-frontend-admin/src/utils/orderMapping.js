/**
 * Shared order mapping utility for converting tab data to HoaDonDto structure.
 * Consolidates duplicate mapping logic from orderStore.js and OrderCreate.vue.
 *
 * This utility eliminates the architectural inconsistency that led to payment status bugs
 * by providing a single source of truth for order data mapping.
 *
 * @author LapXpert Development Team
 * @since 2025-01-28
 */

/**
 * Default validation functions for serial numbers
 */
const defaultValidationFunctions = {
  validateSerialNumberId: (serialNumberId) => {
    if (serialNumberId === undefined || serialNumberId === null) {
      console.warn('Serial number ID is missing from cart item')
      return null
    }
    if (typeof serialNumberId !== 'number' || serialNumberId <= 0) {
      console.warn('Invalid serial number ID:', serialNumberId)
      return null
    }
    return serialNumberId
  },

  validateSerialNumber: (serialNumber) => {
    // Only validate that we have a non-empty string - let backend handle format rules
    if (!serialNumber || typeof serialNumber !== 'string' || serialNumber === '') {
      console.warn('Serial number value is missing or invalid from cart item')
      return null
    }
    // Return the original serial number unchanged - no modification, no arbitrary validation
    // Backend will handle all format validation according to actual business rules
    return serialNumber
  }
}

/**
 * Maps frontend tab data to backend HoaDonDto structure.
 * Supports both simple (orderStore) and complex (OrderCreate) mapping scenarios.
 *
 * @param {Object} tab - The order tab data from frontend
 * @param {Object} options - Optional parameters for complex mapping scenarios
 * @param {Object} options.addressData - Delivery address data (for complex scenarios)
 * @param {Object} options.recipientInfo - Recipient information (for complex scenarios)
 * @param {number} options.consolidatedShippingFee - Calculated shipping fee
 * @param {number} options.dynamicOrderTotal - Calculated order total
 * @param {Object} options.validationFunctions - Custom validation functions
 * @param {Function} options.updateActiveTabData - Function to update tab data
 * @param {string} options.source - Source identifier for debug logging
 * @returns {Object} HoaDonDto structure for backend API
 * @throws {Error} If required data is missing or invalid
 */
export const mapTabToHoaDonDto = (tab, options = {}) => {
  // Destructure options with defaults
  const {
    addressData = null,
    recipientInfo = null,
    consolidatedShippingFee = 0,
    dynamicOrderTotal = null,
    validationFunctions = {},
    updateActiveTabData = null,
    source = 'unknown'
  } = options

  // Merge validation functions with defaults
  const validators = {
    ...defaultValidationFunctions,
    ...validationFunctions
  }

  // Validate required tab data
  if (!tab) {
    throw new Error('Tab data is required for order mapping')
  }

  if (!tab.phuongThucThanhToan) {
    console.warn('Payment method not provided in tab data:', tab)
    throw new Error('Phương thức thanh toán không được để trống')
  }

  // Handle delivery address payload (complex scenario)
  let deliveryAddressPayload = null
  if (tab.giaohang && addressData && addressData.duong && addressData.duong.trim()) {
    deliveryAddressPayload = {
      duong: addressData.duong.trim(),
      phuongXa: addressData.phuongXa,
      quanHuyen: addressData.quanHuyen,
      tinhThanh: addressData.tinhThanh,
      loaiDiaChi: addressData.loaiDiaChi || 'Nhà riêng'
    }
  }

  // Determine customer ID (complex scenario logic)
  const customerId = tab.khachHang?.id || null

  // Update tab data if function provided (complex scenario)
  if (updateActiveTabData && dynamicOrderTotal !== null) {
    updateActiveTabData({ tongThanhToan: dynamicOrderTotal })
    updateActiveTabData({ phiVanChuyen: consolidatedShippingFee })
  }

  // Calculate financial values
  const tongTienHang = tab.tongTienHang || 0
  const giaTriGiamGiaVoucher = tab.giaTriGiamGiaVoucher || 0
  const phiVanChuyen = consolidatedShippingFee || tab.phiVanChuyen || 0
  const tongThanhToan = dynamicOrderTotal !== null ? dynamicOrderTotal : tab.tongThanhToan || 0

  // Determine recipient information (complex vs simple scenario)
  let nguoiNhanTen, nguoiNhanSdt
  if (recipientInfo && tab.giaohang) {
    // Complex scenario: use recipient info
    nguoiNhanTen = recipientInfo.hoTen ? recipientInfo.hoTen.trim() : tab.khachHang?.hoTen || null
    nguoiNhanSdt = recipientInfo.soDienThoai ? recipientInfo.soDienThoai.trim() : tab.khachHang?.soDienThoai || null
  } else {
    // Simple scenario: use customer info
    nguoiNhanTen = tab.khachHang?.hoTen || null
    nguoiNhanSdt = tab.khachHang?.soDienThoai || null
  }

  // Build the HoaDonDto structure
  const dto = {
    // Basic order information
    maHoaDon: tab.maHoaDon,
    loaiHoaDon: tab.loaiHoaDon,

    // Customer information - send only ID to avoid transient entity issues
    khachHangId: customerId,

    // Staff member information - backend will handle automatic assignment
    nhanVienId: JSON.parse(localStorage.getItem("nguoiDung")).id || null,

    // Delivery information
    diaChiGiaoHang: deliveryAddressPayload,
    diaChiGiaoHangId: tab.diaChiGiaoHang?.id || null, // Simple scenario fallback
    nguoiNhanTen: nguoiNhanTen,
    nguoiNhanSdt: nguoiNhanSdt,

    // Financial information
    tongTienHang: tongTienHang,
    giaTriGiamGiaVoucher: giaTriGiamGiaVoucher,
    phiVanChuyen: phiVanChuyen,
    tongThanhToan: tongThanhToan,

    // Status information
    trangThaiDonHang: tab.giaohang ? 'CHO_GIAO_HANG' : 'HOAN_THANH',
    // Payment status depends on payment method - unified logic for all scenarios
    trangThaiThanhToan: (tab.phuongThucThanhToan === 'VNPAY' ||
                        tab.phuongThucThanhToan === 'MOMO' ||
                        tab.phuongThucThanhToan === 'MIXED')
      ? 'CHUA_THANH_TOAN'  // Gateway payments and mixed payments start as unpaid
      : 'DA_THANH_TOAN',   // Cash payments are immediately paid

    // Payment method for backend processing
    // For mixed payments, use the first payment method as the primary method
    phuongThucThanhToan: tab.phuongThucThanhToan === 'MIXED'
      ? (tab.mixedPayments && tab.mixedPayments.length > 0 ? tab.mixedPayments[0].method : 'TIEN_MAT')
      : tab.phuongThucThanhToan,

    // Mixed payment indicator and data for backend processing
    isMixedPayment: tab.phuongThucThanhToan === 'MIXED',
    mixedPayments: tab.phuongThucThanhToan === 'MIXED' ? tab.mixedPayments : undefined,

    // Order details - map product items
    chiTiet: tab.sanPhamList.map((item) => ({
      sanPhamChiTietId: item.sanPhamChiTiet?.id || item.sanPham?.id,
      soLuong: item.soLuong,
      donGia: item.donGia,
      giaBan: item.donGia, // Support both field names for compatibility
      thanhTien: item.donGia * item.soLuong,
      // Serial number validation using provided or default validators
      serialNumberId: validators.validateSerialNumberId(item.sanPhamChiTiet?.serialNumberId),
      serialNumber: validators.validateSerialNumber(item.sanPhamChiTiet?.serialNumber)
    })),

    // Voucher information
    voucherCodes: tab.voucherList.map((voucher) => voucher.maPhieuGiamGia)
  }

  // Debug logging with source identification
  console.log(`OrderMapping [${source}] - Payment method:`, tab.phuongThucThanhToan, 'Payment status:', dto.trangThaiThanhToan)
  console.log(`OrderMapping [${source}] - Generated HoaDonDto:`, dto)

  return dto
}

/**
 * Export default object for compatibility
 */
export default {
  mapTabToHoaDonDto
}
