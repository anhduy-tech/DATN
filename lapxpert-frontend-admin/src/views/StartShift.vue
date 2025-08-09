<template>
  <div class="h-screen flex items-center justify-center bg-gray-100">
    <div class="w-full max-w-2xl p-12 bg-white rounded-xl shadow-2xl">
      <h2 class="text-4xl font-bold mb-10 text-center">Mở Ca Làm Việc</h2>

      <div class="mb-8">
        <label class="block mb-3 font-semibold text-2xl">Tiền mặt đầu ca</label>
        <input
          v-model.number="tienMatDauCa"
          type="number"
          class="w-full border-2 px-6 py-4 rounded-xl text-2xl focus:outline-none focus:ring-2 focus:ring-blue-400"
          placeholder="Nhập tiền mặt đầu ca"
        />
      </div>

      <div class="mb-8">
        <label class="block mb-3 font-semibold text-2xl">Chuyển khoản đầu ca</label>
        <input
          v-model.number="chuyenKhoanDauCa"
          type="number"
          class="w-full border-2 px-6 py-4 rounded-xl text-2xl focus:outline-none focus:ring-2 focus:ring-blue-400"
          placeholder="Nhập chuyển khoản đầu ca"
        />
      </div>

      <button
        @click="moCaLamViec"
        class="w-full bg-blue-600 text-white py-4 rounded-xl text-2xl font-bold hover:bg-blue-700 transition duration-300"
      >
        Mở Ca Làm Việc
      </button>

      <div v-if="message" class="mt-8 text-green-600 font-semibold text-center text-2xl">
        {{ message }}
      </div>
    </div>
  </div>
</template>


<script>
import { CaLamViecAPI } from '@/apis/shifts'

export default {
  name: 'MoCaLamViec',
  data() {
    return {
      tienMatDauCa: null,
      chuyenKhoanDauCa: null,
      message: '',
    }
  },
  methods: {
    async moCaLamViec() {
      if (this.tienMatDauCa == null || this.chuyenKhoanDauCa == null) {
        this.message = 'Vui lòng nhập đầy đủ thông tin.'
        return
      }

      const payload = {
        tienMatDauCa: this.tienMatDauCa,
        chuyenKhoanDauCa: this.chuyenKhoanDauCa,
      }

      try {
        await CaLamViecAPI.moCaLamViec(payload)
        this.message = 'Mở ca làm việc thành công!'

        // ✅ Reset form
        this.tienMatDauCa = null
        this.chuyenKhoanDauCa = null

        // ✅ Chuyển hướng về trang chính
        this.$router.push('/')
      } catch (error) {
        console.error('Lỗi mở ca:', error)
        this.message = 'Đã xảy ra lỗi khi mở ca.'
      }
    },
  },
}
</script>
