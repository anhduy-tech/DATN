```vue
<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useLayout } from '@/layout/composables/layout'
import ThongKeService from '@/apis/dashboard'
import orderApi from '@/apis/orderApi'
import { useOrderStore } from '@/stores/orderStore'
import * as XLSX from 'xlsx'
import AuthService from '@/apis/auth'
import { useRouter } from 'vue-router'
import { LichLamViecAPI } from '@/apis/workSchedule'
import { CaLamViecAPI } from '@/apis/shifts'

// PrimeVue Components
import Button from 'primevue/button'
import Message from 'primevue/message'
import Chart from 'primevue/chart'
import Calendar from 'primevue/calendar'
import Select from 'primevue/select'
import Badge from 'primevue/badge'
import ProgressBar from 'primevue/progressbar'
import Toast from 'primevue/toast'
import Fluid from 'primevue/fluid'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'

// PrimeVue Components for DataTable
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import ProgressSpinner from 'primevue/progressspinner'

// Custom Components
import DoanhThuCard from '@/views/thongke/components/cards/DoanhThuCard.vue'
import DonHangCard from '@/views/thongke/components/cards/DonHangCard.vue'
import SanPhamCard from '@/views/thongke/components/cards/SanPhamCard.vue'
import KhachHangCard from '@/views/thongke/components/cards/KhachHangCard.vue'
import NotificationsWidget from '@/views/thongke/components/dashboard/NotificationsWidget.vue'

const toast = useToast()
const { getPrimary, getSurface, isDarkTheme } = useLayout()
const orderStore = useOrderStore()
const router = useRouter()

const dinhDangTienTe = (value) => {
  if (!value && value !== 0) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(value)
}

const dinhDangSo = (value) => {
  if (!value && value !== 0) return '0'
  return new Intl.NumberFormat('vi-VN').format(value)
}

const dinhDangPhanTram = (value) => {
  if (!value && value !== 0) return '0%'
  return `${value.toFixed(1)}%`
}

// ==================== METHODS ====================
const hienThiLoi = (message) => {
  toast.add({
    severity: 'error',
    summary: 'Lỗi',
    detail: message,
    life: 5000,
  })
}

const hienThiThanhCong = (message) => {
  toast.add({
    severity: 'success',
    summary: 'Thành công',
    detail: message,
    life: 3000,
  })
}

// Load All Data
const taiTatCaDuLieu = async () => {
  dangTai.value = true
  loi.value = null

  try {
    await Promise.all([
      taiDuLieuTongQuanDashboard(),
      orderStore.fetchOrders(), // Fetch all orders here
      taiDuLieuBieuDoDoanhThu(),
      taiDuLieuBieuDoDonHang(),
      taiDuLieuBieuDoSanPham(),
      taiDonHangGanDay(),
    ])
    hienThiThanhCong('Dữ liệu đã được tải thành công')
  } catch (error) {
    console.error('Lỗi khi tải dữ liệu:', error)
    loi.value = 'Có lỗi xảy ra khi tải dữ liệu'
    hienThiLoi(loi.value)
  } finally {
    dangTai.value = false
  }
}
// Refresh Data
const lamMoiDuLieu = () => {
  taiTatCaDuLieu()
}

// Formatters for DataTable
const formatCurrency = (value) => {
  if (!value && value !== 0) return '0 ₫'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(value)
}

const formatDateTime = (dateString) => {
  if (!dateString) return 'N/A'
  const date = new Date(dateString)
  return new Intl.DateTimeFormat('vi-VN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(date)
}
const caHienTai = ref(null)
const loadingCLV = ref(false)
const fetchCaHienTai = async () => {
  try {
    loadingCLV.value = true
    const res = await CaLamViecAPI.getCaHienTai()
    caHienTai.value = res.data
  } catch (error) {
    console.error('Lỗi khi tải ca hiện tại:', error)
  } finally {
    loadingCLV.value = false
  }
}

const dongCa = () => {
  router.push('/start-shift')
}
onMounted(() => {
  fetchCaHienTai()
})
onMounted(() => {
  taiTatCaDuLieu()
})
</script>

<template>
  <Fluid>
    <Toast />

    <!-- Page Header -->
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
            icon="pi pi-sign-out"
            label="Đóng ca làm việc"
            severity="danger"
            outlined
            size="small"
            @click="dongCaLamViec"
          />
          <Button
            icon="pi pi-refresh"
            label="Làm mới"
            @click="lamMoiDuLieu"
            :loading="dangTai"
            severity="secondary"
            outlined
            size="small"
            v-tooltip.left="'Cập nhật dữ liệu mới nhất'"
          />
        </div>
      </div>
    </div>

    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
            <i class="pi pi-chart-bar text-lg text-primary"></i>
          </div>
          <div class="text-xl font-semibold mb-2">Thông tin ca làm việc hiện tại</div>
        </div>
        <div>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <p class="text-surface-500">Tiền mặt</p>
              <p class="text-lg font-bold text-surface-900"
                >{{ formatCurrency(caHienTai.tienMatDauCa) }} đ</p
              >
            </div>
            <div>
              <p class="text-surface-500">Chuyển khoản</p>
              <p class="text-lg font-bold text-surface-900"
                >{{ formatCurrency(caHienTai.chuyenKhoanDauCa) }} đ</p
              >
            </div>
            <div>
              <p class="text-surface-500">Thời gian mở ca</p>
              <p class="text-lg font-bold text-surface-900">{{
                formatDateTime(caHienTai.gioMoCa)
              }}</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Fluid>
</template>
```
