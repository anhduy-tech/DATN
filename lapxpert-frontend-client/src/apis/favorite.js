import { privateApi } from "./axiosAPI";

const FavoriteService = {
  /**
   * Get current user's wishlist
   * @returns {Promise<Array>} List of wishlist items
   */
  async getWishlist() {
    try {
      const response = await privateApi.get("/wishlist");
      return response.data;
    } catch (error) {
      console.error("Error fetching wishlist:", error);
      throw error;
    }
  },

  /**
   * Add product to wishlist
   * @param {Object} request - { sanPhamId: Long, nguoiDungId: Long }
   * @returns {Promise<Object>} Created wishlist item DTO
   */
  async addProductToWishlist(request) {
    try {
      const response = await privateApi.post("/wishlist/add", request);
      return response.data;
    } catch (error) {
      console.error("Error adding product to wishlist:", error);
      throw error;
    }
  },

  /**
   * Remove product from wishlist
   * @param {Long} sanPhamId - Product ID to remove
   * @returns {Promise<Object>} Success message
   */
  async removeProductFromWishlist(sanPhamId) {
    try {
      const response = await privateApi.delete(`/wishlist/remove/${sanPhamId}`);
      return response.data;
    } catch (error) {
      console.error("Error removing product from wishlist:", error);
      throw error;
    }
  },

  /**
   * Check if product is in wishlist
   * @param {Long} sanPhamId - Product ID to check
   * @returns {Promise<Boolean>} True if in wishlist, false otherwise
   */
  async checkProductInWishlist(sanPhamId) {
    try {
      const response = await privateApi.get(`/wishlist/check/${sanPhamId}`);
      return response.data.isInWishlist;
    } catch (error) {
      console.error("Error checking product in wishlist:", error);
      throw error;
    }
  },

  /**
   * Get wishlist count
   * @returns {Promise<Long>} Count of wishlist items
   */
  async getWishlistCount() {
    try {
      const response = await privateApi.get("/wishlist/count");
      return response.data.count;
    } catch (error) {
      console.error("Error getting wishlist count:", error);
      throw error;
    }
  },
};

export default FavoriteService;
