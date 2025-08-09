import { privateApi } from './axiosAPI'
const API_URL = '/clv'

export const CaLamViecAPI = {
  moCaLamViec(data) {
    return privateApi.post(`${API_URL}/mo-ca`, data)
  },
  getCaHienTai() {
    return privateApi.get(`${API_URL}/hien-tai`)
  },
  dongCa(id) {
    return privateApi.put(`${API_URL}/dong-ca/${id}`);
  },
}