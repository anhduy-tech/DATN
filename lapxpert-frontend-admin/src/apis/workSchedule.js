import { privateApi } from './axiosAPI'

const API_URL = '/llv'

export const LichLamViecAPI = {
  getAllLLVs() {
    return privateApi.get(API_URL)
  },
  importExcel(file) {
    const formData = new FormData()
    formData.append('file', file) // 👈 tên phải trùng với tên biến ở backend (@RequestParam("file"))

    return privateApi.post(`${API_URL}/import`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  },
  updateLichLamViec(id, data) {
    return privateApi.put(`${API_URL}/${id}`, data)
  },
  deleteLichLamViec(id) {
    return privateApi.delete(`${API_URL}/${id}`)
  },
}
