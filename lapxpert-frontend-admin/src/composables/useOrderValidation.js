import { ref } from 'vue'

/**
 * Order Validation Composable
 * Extracted from OrderCreate.vue for reusability across order components
 * Provides comprehensive validation for order data with Bean Validation alignment
 */
export function useOrderValidation() {
  // Validation state
  const validationErrors = ref({})
  const isValidating = ref(false)

  /**
   * Validate individual field with multiple rules
   * @param {string} fieldName - Name of the field being validated
   * @param {any} value - Value to validate
   * @param {Array} rules - Array of validation rules
   * @returns {Array} Array of error messages
   */
  const validateField = (fieldName, value, rules) => {
    const errors = []

    for (const rule of rules) {
      switch (rule.type) {
        case 'required':
          if (!value || (typeof value === 'string' && !value.trim())) {
            errors.push(rule.message || `${fieldName} không được để trống`)
          }
          break
        case 'email':
          if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
            errors.push(rule.message || 'Email không hợp lệ')
          }
          break
        case 'phone':
          if (value && !/^[0-9]{10,11}$/.test(value.replace(/\s/g, ''))) {
            errors.push(rule.message || 'Số điện thoại phải có 10-11 chữ số')
          }
          break
        case 'address':
          if (value && value.trim().length < 5) {
            errors.push(rule.message || 'Địa chỉ phải có ít nhất 5 ký tự')
          }
          break
        case 'maxLength':
          if (value && value.length > rule.value) {
            errors.push(rule.message || `${fieldName} không được vượt quá ${rule.value} ký tự`)
          }
          break
        case 'minValue':
          if (value !== null && value !== undefined && Number(value) < rule.value) {
            errors.push(rule.message || `${fieldName} phải lớn hơn hoặc bằng ${rule.value}`)
          }
          break
        case 'pattern':
          if (value && !rule.value.test(value)) {
            errors.push(rule.message || `${fieldName} không đúng định dạng`)
          }
          break
      }
    }

    return errors
  }

  /**
   * Validate complete order data
   * @param {Object} orderData - Order data to validate
   * @returns {Object} Validation errors object
   */
  const validateOrderData = (orderData) => {
    const errors = {}

    // Validate customer information for delivery orders
    if (orderData.giaohang) {
      if (!orderData.khachHang) {
        errors.khachHang = ['Khách hàng không được để trống cho đơn giao hàng']
      }

      if (!orderData.diaChiGiaoHang) {
        errors.diaChiGiaoHang = ['Địa chỉ giao hàng không được để trống']
      }

      // Validate delivery contact information
      if (orderData.khachHang) {
        const phoneErrors = validateField('Số điện thoại', orderData.khachHang.soDienThoai, [
          { type: 'required' },
          { type: 'phone' }
        ])
        if (phoneErrors.length > 0) {
          errors.soDienThoai = phoneErrors
        }

        const nameErrors = validateField('Tên khách hàng', orderData.khachHang.hoTen, [
          { type: 'required' },
          { type: 'maxLength', value: 255 }
        ])
        if (nameErrors.length > 0) {
          errors.tenKhachHang = nameErrors
        }
      }
    }

    // Validate products
    if (!orderData.sanPhamList || orderData.sanPhamList.length === 0) {
      errors.sanPhamList = ['Đơn hàng phải có ít nhất một sản phẩm']
    } else {
      orderData.sanPhamList.forEach((item, index) => {
        const quantityErrors = validateField(`Số lượng sản phẩm ${index + 1}`, item.soLuong, [
          { type: 'required' },
          { type: 'minValue', value: 1 }
        ])
        if (quantityErrors.length > 0) {
          errors[`sanPham_${index}_soLuong`] = quantityErrors
        }
      })
    }

    // Validate payment method
    if (!orderData.phuongThucThanhToan) {
      errors.phuongThucThanhToan = ['Phương thức thanh toán không được để trống']
    }

    // Validate order notes
    if (orderData.ghiChu) {
      const noteErrors = validateField('Ghi chú', orderData.ghiChu, [
        { type: 'maxLength', value: 500 }
      ])
      if (noteErrors.length > 0) {
        errors.ghiChu = noteErrors
      }
    }

    // Validate order totals
    const totalErrors = validateField('Tổng tiền xác nhận', orderData.tongThanhToan, [
      { type: 'required' },
      { type: 'minValue', value: 0 }
    ])
    if (totalErrors.length > 0) {
      errors.tongThanhToan = totalErrors
    }

    return errors
  }

  /**
   * Validate order tab data
   * @param {Object} tabData - Tab data to validate
   * @returns {Object} Validation errors object
   */
  const validateTabData = (tabData) => {
    if (!tabData) return {}

    isValidating.value = true
    const errors = validateOrderData(tabData)
    validationErrors.value = errors
    isValidating.value = false

    return errors
  }

  /**
   * Clear validation errors
   */
  const clearValidationErrors = () => {
    validationErrors.value = {}
  }

  /**
   * Check if there are any validation errors
   * @returns {boolean} True if there are errors
   */
  const hasValidationErrors = () => {
    return Object.keys(validationErrors.value).length > 0
  }

  /**
   * Get error message for a specific field
   * @param {string} fieldName - Field name to get error for
   * @returns {string|null} Error message or null
   */
  const getFieldError = (fieldName) => {
    const errors = validationErrors.value[fieldName]
    return errors && errors.length > 0 ? errors[0] : null
  }

  /**
   * Validate phone number with Vietnamese format
   * @param {string} phone - Phone number to validate
   * @returns {Object} Validation result with isValid and error properties
   */
  const validatePhoneNumber = (phone) => {
    if (!phone) {
      return { isValid: false, error: null }
    }

    const phoneRegex = /^[0-9]{10,11}$/
    const cleanPhone = phone.replace(/\s/g, '')

    if (!phoneRegex.test(cleanPhone)) {
      return { isValid: false, error: 'Số điện thoại phải có 10-11 chữ số' }
    }

    return { isValid: true, error: null }
  }

  /**
   * Validate email address format
   * @param {string} email - Email address to validate
   * @returns {Object} Validation result with isValid and error properties
   */
  const validateEmailAddress = (email) => {
    if (!email) {
      return { isValid: false, error: null }
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (!emailRegex.test(email)) {
      return { isValid: false, error: 'Email không hợp lệ' }
    }

    return { isValid: true, error: null }
  }

  /**
   * Validate street address format
   * @param {string} address - Street address to validate
   * @returns {Object} Validation result with isValid and error properties
   */
  const validateStreetAddress = (address) => {
    if (!address) {
      return { isValid: false, error: null }
    }

    const trimmedAddress = address.trim()

    if (trimmedAddress.length < 5) {
      return { isValid: false, error: 'Địa chỉ phải có ít nhất 5 ký tự' }
    }

    // Additional length check for maximum length
    if (trimmedAddress.length > 255) {
      return { isValid: false, error: 'Địa chỉ đường không được vượt quá 255 ký tự' }
    }

    // Check for obviously invalid test addresses
    const lowerAddress = trimmedAddress.toLowerCase()
    if (lowerAddress.includes('test') || lowerAddress.includes('fake') || lowerAddress.includes('demo')) {
      return { isValid: false, error: 'Địa chỉ có vẻ không hợp lệ. Vui lòng nhập địa chỉ thực tế.' }
    }

    return { isValid: true, error: null }
  }

  /**
   * Validate Vietnamese address hierarchy (Province-District-Ward consistency)
   * @param {Object} hierarchyData - Object containing province, district, ward, and available options
   * @param {string} hierarchyData.province - Selected province name
   * @param {string} hierarchyData.district - Selected district name
   * @param {string} hierarchyData.ward - Selected ward name
   * @param {Array} hierarchyData.availableDistricts - Available districts for the province
   * @param {Array} hierarchyData.availableWards - Available wards for the district
   * @returns {Object} Validation result with isValid and error properties
   */
  const validateAddressHierarchy = (hierarchyData) => {
    const { province, district, ward, availableDistricts = [], availableWards = [] } = hierarchyData

    // Basic required field validation
    if (!province) {
      return { isValid: false, error: 'Vui lòng chọn tỉnh/thành phố' }
    }

    if (!district) {
      return { isValid: false, error: 'Vui lòng chọn quận/huyện' }
    }

    if (!ward) {
      return { isValid: false, error: 'Vui lòng chọn phường/xã' }
    }

    // Validate province-district consistency
    if (availableDistricts.length > 0) {
      const districtExists = availableDistricts.some((d) => d.name === district)
      if (!districtExists) {
        return { isValid: false, error: 'Quận/Huyện không thuộc Tỉnh/Thành phố đã chọn' }
      }
    }

    // Validate district-ward consistency
    if (availableWards.length > 0) {
      const wardExists = availableWards.some((w) => w.name === ward)
      if (!wardExists) {
        return { isValid: false, error: 'Phường/Xã không thuộc Quận/Huyện đã chọn' }
      }
    }

    return { isValid: true, error: null }
  }

  /**
   * Validate complete Vietnamese address data
   * @param {Object} addressData - Complete address object
   * @param {string} addressData.duong - Street address
   * @param {string} addressData.phuongXa - Ward name
   * @param {string} addressData.quanHuyen - District name
   * @param {string} addressData.tinhThanh - Province name
   * @param {Array} addressData.availableDistricts - Available districts (optional)
   * @param {Array} addressData.availableWards - Available wards (optional)
   * @returns {Object} Validation result with isValid, error, and fieldErrors properties
   */
  const validateCompleteAddress = (addressData) => {
    const fieldErrors = {}
    let hasErrors = false

    // Validate street address
    const streetValidation = validateStreetAddress(addressData.duong)
    if (!streetValidation.isValid && streetValidation.error) {
      fieldErrors.duong = streetValidation.error
      hasErrors = true
    }

    // Validate address hierarchy
    const hierarchyValidation = validateAddressHierarchy({
      province: addressData.tinhThanh,
      district: addressData.quanHuyen,
      ward: addressData.phuongXa,
      availableDistricts: addressData.availableDistricts || [],
      availableWards: addressData.availableWards || []
    })

    if (!hierarchyValidation.isValid && hierarchyValidation.error) {
      // Determine which field the hierarchy error applies to
      if (hierarchyValidation.error.includes('tỉnh/thành phố')) {
        fieldErrors.tinhThanh = hierarchyValidation.error
      } else if (hierarchyValidation.error.includes('quận/huyện')) {
        fieldErrors.quanHuyen = hierarchyValidation.error
      } else if (hierarchyValidation.error.includes('phường/xã')) {
        fieldErrors.phuongXa = hierarchyValidation.error
      } else {
        fieldErrors.general = hierarchyValidation.error
      }
      hasErrors = true
    }

    return {
      isValid: !hasErrors,
      error: hasErrors ? 'Địa chỉ không hợp lệ' : null,
      fieldErrors
    }
  }

  return {
    // State
    validationErrors,
    isValidating,

    // Methods
    validateField,
    validateOrderData,
    validateTabData,
    clearValidationErrors,
    hasValidationErrors,
    getFieldError,

    // Standalone validation functions
    validatePhoneNumber,
    validateEmailAddress,
    validateStreetAddress,
    validateAddressHierarchy,
    validateCompleteAddress
  }
}
