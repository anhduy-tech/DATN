import {privateApi} from "./axiosAPI";
// Backend HoaDonController uses /api/v1/hoa-don (privateApi already includes /api/v1)
const API_URL = '/hoa-don';

/**
 * @deprecated This API module is deprecated. Use orderApi.js for new development.
 * This module contains legacy CRUD methods for backward compatibility only.
 * For comprehensive order management features, use @/apis/orderApi.js instead.
 */

export default {
    /**
     * @deprecated Use orderApi.getAllOrders() instead
     * Legacy method for backward compatibility only
     */
    getAllHoaDons() {
        return privateApi.get(API_URL);
    },

    /**
     * @deprecated Use orderApi.getOrderById() instead
     * Legacy method for backward compatibility only
     */
    getHoaDonById(id) {
        return privateApi.get(`${API_URL}/${id}`);
    },

    /**
     * @deprecated Use orderApi.createOrder() instead
     * Legacy method for backward compatibility only
     */
    createHoaDon(hoaDon) {
        return privateApi.post(API_URL, hoaDon);
    },

    /**
     * @deprecated Use orderApi.updateOrder() instead
     * Legacy method for backward compatibility only
     */
    updateHoaDon(id, hoaDon) {
        return privateApi.put(`${API_URL}/${id}`, hoaDon);
    },
};
