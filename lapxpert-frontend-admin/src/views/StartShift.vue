<script setup>
import { ref } from 'vue'
import NotificationsWidget from '@/views/thongke/components/dashboard/NotificationsWidget.vue'

const hienModal = ref(false)
const soTienTam = ref(null)
const soTienBanDau = ref(null)
const daMoCa = ref(false)

const xacNhanMoCa = () => {
  soTienBanDau.value = soTienTam.value
  daMoCa.value = true
  hienModal.value = false
  router.push('/')
}
</script>
<template>
  <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
            <i class="pi pi-chart-bar text-lg text-primary"></i>
          </div>
          <div>
            <h1 class="font-semibold text-xl text-surface-900 m-0"> Thống Kê </h1>
            <p class="text-surface-500 text-sm mt-1 mb-0">
              Tổng quan về doanh thu, đơn hàng và hiệu suất kinh doanh
            </p>
          </div>
        </div>
        <div class="flex gap-2">
          <Button
            icon="pi pi-plus"
            label="Mở ca"
            @click="hienModal = true"
            :loading="dangTai"
            severity="primary"
            outlined
            size="small"
            v-tooltip.left="'Mở ca làm việc mới'"
          />
        </div>
      </div>
    </div>
  <div
    v-if="hienModal"
    class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50"
  >
    <div class="bg-white p-6 rounded-lg shadow-lg w-[400px]">
      <h3 class="text-xl font-bold mb-4">Nhập số tiền đầu ca</h3>
      <input
        v-model.number="soTienTam"
        type="number"
        min="0"
        placeholder="VD: 1,000,000"
        class="border px-3 py-2 w-full rounded mb-4"
      />
      <div class="flex justify-end space-x-2">
        <button @click="hienModal = false" class="px-4 py-2 rounded bg-gray-300 hover:bg-gray-400">
          Hủy
        </button>
        <button
          :disabled="!soTienTam"
          @click="xacNhanMoCa"
          class="px-4 py-2 rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-50"
        >
          Xác nhận
        </button>
      </div>
    </div>
  </div>
</template>
