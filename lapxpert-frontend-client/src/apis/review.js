import { publicApi, privateApi } from "./axiosAPI";

const ReviewService = {
  /**
   * Get reviews for a specific product with filtering and pagination.
   * @param {Long} sanPhamId - Product ID.
   * @param {Object} params - Query parameters (page, size, rating, minRating, maxRating, sortBy, sortDirection).
   * @returns {Promise<Object>} Page of reviews.
   */
  async getProductReviews(sanPhamId, params = {}) {
    try {
      const response = await publicApi.get(`/danh-gia/san-pham/${sanPhamId}`, { params });
      return response.data;
    } catch (error) {
      console.error("Error fetching product reviews:", error);
      throw error;
    }
  },

  /**
   * Get product rating summary and statistics.
   * @param {Long} sanPhamId - Product ID.
   * @returns {Promise<Object>} Product rating summary.
   */
  async getProductRatingSummary(sanPhamId) {
    try {
      const response = await publicApi.get(`/danh-gia/san-pham/${sanPhamId}/thong-ke`);
      return response.data;
    } catch (error) {
      console.error("Error fetching product rating summary:", error);
      throw error;
    }
  },

  /**
   * Create a new product review.
   * @param {Object} reviewData - Review creation data (CreateReviewDto).
   * @returns {Promise<Object>} Created review DTO.
   */
  async createReview(reviewData) {
    try {
      // Ensure diemDanhGia is used as per backend DTO
      const payload = {
        sanPhamId: reviewData.sanPhamId,
        nguoiDungId: reviewData.nguoiDungId,
        hoaDonChiTietId: reviewData.hoaDonChiTietId, // New: Pass hoaDonChiTietId
        diemDanhGia: reviewData.diemDanhGia, // Use diemDanhGia
        noiDung: reviewData.noiDung,
        // Add other fields if necessary (e.g., hinhAnh, tieuDe)
      };
      const response = await privateApi.post("/danh-gia", payload);
      return response.data;
    } catch (error) {
      console.error("Error creating review:", error);
      throw error;
    }
  },

  /**
   * Update an existing review.
   * @param {Long} id - Review ID.
   * @param {Object} reviewData - Review update data (UpdateReviewDto).
   * @returns {Promise<Object>} Updated review DTO.
   */
  async updateReview(id, reviewData) {
    try {
      const response = await privateApi.put(`/danh-gia/${id}`, reviewData);
      return response.data;
    } catch (error) {
      console.error("Error updating review:", error);
      throw error;
    }
  },

  /**
   * Check if customer can review a specific product.
   * @param {Long} customerId - Customer ID.
   * @param {Long} productId - Product ID.
   * @returns {Promise<Object>} Eligibility result.
   */
  async checkReviewEligibility(customerId, productId) {
    try {
      const response = await privateApi.get(`/danh-gia/kiem-tra-dieu-kien/${customerId}/${productId}`);
      return response.data;
    } catch (error) {
      console.error("Error checking review eligibility:", error);
      throw error;
    }
  },

  /**
   * Get all eligible order items for a specific customer and product.
   * @param {Long} customerId - Customer ID.
   * @param {Long} productId - Product ID.
   * @returns {Promise<Array>} List of HoaDonChiTiet eligible for review.
   */
  async getEligibleOrderItems(customerId, productId) {
    try {
      const response = await privateApi.get(`/danh-gia/eligible-order-items/${customerId}/${productId}`);
      return response.data;
    } catch (error) {
      console.error("Error getting eligible order items:", error);
      throw error;
    }
  },

  /**
   * Get a summary of a customer's purchases for a specific product.
   * @param {Long} customerId - Customer ID.
   * @param {Long} productId - Product ID.
   * @returns {Promise<Object>} ProductPurchaseSummaryDto.
   */
  async getProductPurchaseSummary(customerId, productId) {
    try {
      const response = await privateApi.get(`/danh-gia/purchase-summary/${customerId}/${productId}`);
      return response.data;
    } catch (error) {
      console.error("Error getting product purchase summary:", error);
      throw error;
    }
  },
};

export default ReviewService;
