```vue
<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useLayout } from '@/layout/composables/layout'
import ThongKeService from '@/apis/dashboard'
import orderApi from '@/apis/orderApi'
import { useOrderStore } from '@/stores/orderStore'
import * as XLSX from 'xlsx'

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

// ==================== REACTIVE DATA ====================
const dangTai = ref(true)
const loi = ref(null)
const recentOrders = ref([])
const loadingRecentOrders = ref(true)

// Dashboard Summary Data
const tongQuanDashboard = ref({
  doanhThu: {
    homNay: 0,
    tuanNay: 0,
    thangNay: 0,
    namNay: 0,
    tangTruongTheoThang: 0,
    ngayDoanhThuTotNhat: null,
    doanhThuTotNhat: 0,
  },
  donHang: {
    tongSo: 0,
    choXacNhan: 0,
    dangXuLy: 0,
    hoanThanh: 0,
    daHuy: 0,
    tyLeHoanThanh: 0,
  },
  sanPham: {
    tongSo: 0,
    sapHetHang: 0,
    banChayNhat: [],
    danhMucTot: [],
  },
  khachHang: {
    tongSo: 0,
    moiThangNay: 0,
    tyLeGiuChan: 0,
    giaTriTrungBinh: 0,
  },
})

// Chart Data
const duLieuBieuDoDoanhThu = ref({
  labels: [],
  datasets: [],
})

const duLieuBieuDoDonHang = ref({
  labels: [],
  datasets: [],
})

const duLieuBieuDoSanPham = ref({
  labels: [],
  datasets: [],
})

// Date Range Selection
const khoangThoiGian = ref([new Date(Date.now() - 30 * 24 * 60 * 60 * 1000), new Date()])
const kyChon = ref('30_ngay')
const isUpdatingPeriod = ref(false)

// Filter Data for Recent Orders
const filters = ref({
  global: { value: null, matchMode: 'contains' },
  maHoaDon: { operator: 'and', constraints: [{ value: null, matchMode: 'contains' }] },
  khachHang: { operator: 'and', constraints: [{ value: null, matchMode: 'contains' }] },
  trangThaiDonHang: { value: null, matchMode: 'equals' },
  loaiHoaDon: { value: null, matchMode: 'equals' },
})

// Order Status and Type Options
const orderStatusOptions = ref([
  { label: 'Chờ xác nhận', value: 'CHO_XAC_NHAN', severity: 'warning' },
  { label: 'Đang xử lý', value: 'DANG_XU_LY', severity: 'info' },
  { label: 'Đang giao hàng', value: 'DANG_GIAO_HANG', severity: 'primary' },
  { label: 'Hoàn thành', value: 'HOAN_THANH', severity: 'success' },
  { label: 'Đã hủy', value: 'DA_HUY', severity: 'danger' },
])

const orderTypeOptions = ref([
  { label: 'Online', value: 'ONLINE', icon: 'pi pi-globe' },
  { label: 'Tại cửa hàng', value: 'OFFLINE', icon: 'pi pi-shop' },
])

const cacLuaChonKy = [
  { label: '7 ngày qua', value: '7_ngay' },
  { label: '30 ngày qua', value: '30_ngay' },
  { label: '3 tháng qua', value: '3_thang' },
  { label: 'Năm nay', value: 'nam_nay' },
  { label: 'Tùy chọn', value: 'tuy_chon' },
]

// Chart Options with Vietnamese formatting
const tuyChonBieuDoDoanhThu = ref({})
const tuyChonBieuDoDonHang = ref({})
const tuyChonBieuDoSanPham = ref({})

// Initialize chart options
const khoiTaoTuyChonBieuDo = () => {
  const documentStyle = getComputedStyle(document.documentElement)
  const textColor = documentStyle.getPropertyValue('--text-color')
  const textColorSecondary = documentStyle.getPropertyValue('--text-color-secondary')
  const surfaceBorder = documentStyle.getPropertyValue('--surface-border')

  // Revenue Chart Options
  tuyChonBieuDoDoanhThu.value = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          color: textColor,
          usePointStyle: true,
        },
      },
      tooltip: {
        callbacks: {
          label: function (context) {
            return context.dataset.label + ': ' + dinhDangTienTe(context.parsed.y)
          },
        },
      },
    },
    scales: {
      x: {
        ticks: {
          color: textColorSecondary,
        },
        grid: {
          color: surfaceBorder,
          drawBorder: false,
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: textColorSecondary,
          callback: function (value) {
            return dinhDangTienTe(value)
          },
        },
        grid: {
          color: surfaceBorder,
          drawBorder: false,
        },
      },
    },
  }

  // Order Chart Options (Pie/Doughnut)
  tuyChonBieuDoDonHang.value = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          color: textColor,
          usePointStyle: true,
        },
      },
      tooltip: {
        callbacks: {
          label: function (context) {
            const total = context.dataset.data.reduce((a, b) => a + b, 0)
            const percentage = ((context.parsed * 100) / total).toFixed(1)
            return context.label + ': ' + context.parsed + ' (' + percentage + '%)'
          },
        },
      },
    },
  }

  // Product Chart Options (Bar)
  tuyChonBieuDoSanPham.value = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: {
        position: 'bottom',
        labels: {
          color: textColor,
        },
      },
    },
    scales: {
      x: {
        ticks: {
          color: textColorSecondary,
        },
        grid: {
          display: false,
          drawBorder: false,
        },
      },
      y: {
        beginAtZero: true,
        ticks: {
          color: textColorSecondary,
        },
        grid: {
          color: surfaceBorder,
          drawBorder: false,
        },
      },
    },
  }
}

// ==================== COMPUTED PROPERTIES ====================
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

const hasActiveFilters = computed(() => {
  return !!(
    filters.value.global.value ||
    filters.value.maHoaDon.constraints[0].value ||
    filters.value.khachHang.constraints[0].value ||
    filters.value.trangThaiDonHang.value ||
    filters.value.loaiHoaDon.value
  )
})

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

// Load Dashboard Summary Data
const taiDuLieuTongQuanDashboard = async () => {
  try {
    const response = await ThongKeService.layDashboardSummary()
    tongQuanDashboard.value = response.data
  } catch (err) {
    console.error('Lỗi khi tải dữ liệu tổng quan dashboard:', err)
    hienThiLoi('Không thể tải dữ liệu tổng quan dashboard')
  }
}

// Load Revenue Chart Data
const taiDuLieuBieuDoDoanhThu = async () => {
  try {
    const [ngayBatDau, ngayKetThuc] = khoangThoiGian.value
    const tuNgay = ngayBatDau.toISOString().split('T')[0]
    const denNgay = ngayKetThuc.toISOString().split('T')[0]

    const response = await ThongKeService.layDoanhThuTheoNgay(tuNgay, denNgay)
    const documentStyle = getComputedStyle(document.documentElement)

    duLieuBieuDoDoanhThu.value = {
      labels: response.data.labels || [
        '01/01',
        '02/01',
        '03/01',
        '04/01',
        '05/01',
        '06/01',
        '07/01',
      ],
      datasets: [
        {
          label: 'Doanh thu thực tế (VNĐ)',
          data: response.data.data || [
            25000000, 32000000, 28000000, 45000000, 38000000, 52000000, 48000000,
          ],
          backgroundColor: documentStyle.getPropertyValue('--p-primary-100'),
          borderColor: documentStyle.getPropertyValue('--p-primary-500'),
          borderWidth: 3,
          fill: true,
          tension: 0.4,
          pointBackgroundColor: documentStyle.getPropertyValue('--p-primary-500'),
          pointBorderColor: '#fff',
          pointBorderWidth: 2,
          pointRadius: 6,
        },
        {
          label: 'Mục tiêu doanh thu (VNĐ)',
          data: [30000000, 30000000, 30000000, 30000000, 30000000, 30000000, 30000000],
          backgroundColor: 'transparent',
          borderColor: documentStyle.getPropertyValue('--p-orange-500'),
          borderWidth: 2,
          borderDash: [5, 5],
          fill: false,
          tension: 0,
          pointBackgroundColor: documentStyle.getPropertyValue('--p-orange-500'),
          pointBorderColor: '#fff',
          pointBorderWidth: 2,
          pointRadius: 4,
        },
      ],
    }
  } catch (err) {
    console.error('Lỗi khi tải dữ liệu biểu đồ doanh thu:', err)
    hienThiLoi('Không thể tải dữ liệu biểu đồ doanh thu')
  }
}

// Load Order Chart Data
const taiDuLieuBieuDoDonHang = async () => {
  try {
    const [ngayBatDau, ngayKetThuc] = khoangThoiGian.value
    const tuNgay = ngayBatDau.toISOString().split('T')[0]
    const denNgay = ngayKetThuc.toISOString().split('T')[0]

    const response = await ThongKeService.layDonHangTheoTrangThai(tuNgay, denNgay)
    const documentStyle = getComputedStyle(document.documentElement)

    duLieuBieuDoDonHang.value = {
      labels: response.data.labels || [
        'Hoàn thành',
        'Đang xử lý',
        'Chờ xác nhận',
        'Đã hủy',
        'Trả hàng',
      ],
      datasets: [
        {
          label: 'Số đơn hàng',
          data: response.data.data || [156, 43, 28, 12, 8],
          backgroundColor: [
            documentStyle.getPropertyValue('--p-green-500'),
            documentStyle.getPropertyValue('--p-blue-500'),
            documentStyle.getPropertyValue('--p-yellow-500'),
            documentStyle.getPropertyValue('--p-red-500'),
            documentStyle.getPropertyValue('--p-orange-500'),
          ],
          borderColor: [
            documentStyle.getPropertyValue('--p-green-600'),
            documentStyle.getPropertyValue('--p-blue-600'),
            documentStyle.getPropertyValue('--p-yellow-600'),
            documentStyle.getPropertyValue('--p-red-600'),
            documentStyle.getPropertyValue('--p-orange-600'),
          ],
          borderWidth: 2,
          hoverBorderWidth: 3,
          hoverOffset: 8,
        },
      ],
    }
  } catch (err) {
    console.error('Lỗi khi tải dữ liệu biểu đồ đơn hàng:', err)
    hienThiLoi('Không thể tải dữ liệu biểu đồ đơn hàng')
  }
}

// Load Product Chart Data
const taiDuLieuBieuDoSanPham = async () => {
  try {
    const [ngayBatDau, ngayKetThuc] = khoangThoiGian.value
    const tuNgay = ngayBatDau.toISOString().split('T')[0]
    const denNgay = ngayKetThuc.toISOString().split('T')[0]

    const response = await ThongKeService.laySanPhamBanChayNhat(10, tuNgay, denNgay)
    const documentStyle = getComputedStyle(document.documentElement)

    duLieuBieuDoSanPham.value = {
      labels: response.data.labels || [
        'MacBook Pro M3',
        'Dell XPS 13',
        'ASUS ROG Strix',
        'HP Pavilion',
        'Lenovo ThinkPad',
        'Acer Predator',
        'MSI Gaming',
      ],
      datasets: [
        {
          label: 'Số lượng bán',
          data: response.data.data || [45, 38, 32, 28, 25, 22, 18],
          backgroundColor: [
            documentStyle.getPropertyValue('--p-teal-500'),
            documentStyle.getPropertyValue('--p-blue-500'),
            documentStyle.getPropertyValue('--p-green-500'),
            documentStyle.getPropertyValue('--p-purple-500'),
            documentStyle.getPropertyValue('--p-orange-500'),
            documentStyle.getPropertyValue('--p-red-500'),
            documentStyle.getPropertyValue('--p-yellow-500'),
          ],
          borderColor: documentStyle.getPropertyValue('--p-teal-600'),
          borderWidth: 2,
          borderRadius: 8,
          borderSkipped: false,
          hoverBackgroundColor: documentStyle.getPropertyValue('--p-teal-400'),
          hoverBorderWidth: 3,
        },
      ],
    }
  } catch (err) {
    console.error('Lỗi khi tải dữ liệu biểu đồ sản phẩm:', err)
    hienThiLoi('Không thể tải dữ liệu biểu đồ sản phẩm')
  }
}

// Load Recent Orders with Filters
const taiDonHangGanDay = async () => {
  loadingRecentOrders.value = true
  try {
    // Logic is now based on filtering existing data in the store, not re-fetching.
    // The main data fetch is handled by taiTatCaDuLieu or lamMoiDuLieu.
    if (!orderStore.orders || orderStore.orders.length === 0) {
      await orderStore.fetchOrders() // Fetch only if the store is empty
    }
    const [ngayBatDau, ngayKetThuc] = khoangThoiGian.value

    let filteredOrders = orderStore.orders.filter((order) => {
      const orderDate = new Date(order.ngayTao)
      return orderDate >= ngayBatDau && orderDate <= ngayKetThuc
    })

    // Apply additional filters
    const globalFilter = filters.value.global.value?.toLowerCase()
    const maFilter = filters.value.maHoaDon.constraints[0].value?.toLowerCase()
    const khachHangFilter = filters.value.khachHang.constraints[0].value?.toLowerCase()
    const trangThaiFilter = filters.value.trangThaiDonHang.value
    const loaiFilter = filters.value.loaiHoaDon.value

    if (globalFilter) {
      filteredOrders = filteredOrders.filter((order) =>
        Object.values(order).some((val) => String(val).toLowerCase().includes(globalFilter)),
      )
    }
    if (maFilter) {
      filteredOrders = filteredOrders.filter((order) =>
        order.maHoaDon?.toLowerCase().includes(maFilter),
      )
    }
    if (khachHangFilter) {
      filteredOrders = filteredOrders.filter((order) =>
        order.khachHang?.hoTen?.toLowerCase().includes(khachHangFilter),
      )
    }
    if (trangThaiFilter) {
      filteredOrders = filteredOrders.filter((order) => order.trangThaiDonHang === trangThaiFilter)
    }
    if (loaiFilter) {
      filteredOrders = filteredOrders.filter((order) => order.loaiHoaDon === loaiFilter)
    }

    recentOrders.value = filteredOrders
      .sort((a, b) => new Date(b.ngayTao) - new Date(a.ngayTao))
      .slice(0, 5)
      .map((order) => ({
        ...order,
        ngayTao: new Date(order.ngayTao),
      }))
  } catch (err) {
    console.error('Lỗi khi tải đơn hàng gần đây:', err)
    hienThiLoi('Không thể tải đơn hàng gần đây')
    recentOrders.value = []
  } finally {
    loadingRecentOrders.value = false
  }
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

// Clear Filters
const clearFilters = () => {
  filters.value = {
    global: { value: null, matchMode: 'contains' },
    maHoaDon: { operator: 'and', constraints: [{ value: null, matchMode: 'contains' }] },
    khachHang: { operator: 'and', constraints: [{ value: null, matchMode: 'contains' }] },
    trangThaiDonHang: { value: null, matchMode: 'equals' },
    loaiHoaDon: { value: null, matchMode: 'equals' },
  }
  taiDonHangGanDay()
}

// Handle Period Change
const xuLyThayDoiKy = () => {
  // This function is triggered by the Select component.
  // It should only update the date range (`khoangThoiGian`).
  // The watcher will then automatically fetch the data.
  if (isUpdatingPeriod.value) return
  isUpdatingPeriod.value = true

  const hienTai = new Date()
  let newRange = [...khoangThoiGian.value]

  switch (kyChon.value) {
    case '7_ngay':
      newRange = [new Date(hienTai.getTime() - 7 * 24 * 60 * 60 * 1000), hienTai]
      break
    case '30_ngay':
      newRange = [new Date(hienTai.getTime() - 30 * 24 * 60 * 60 * 1000), hienTai]
      break
    case '3_thang':
      newRange = [new Date(hienTai.getTime() - 90 * 24 * 60 * 60 * 1000), hienTai]
      break
    case 'nam_nay':
      newRange = [new Date(hienTai.getFullYear(), 0, 1), hienTai]
      break
    default:
      // Do nothing for 'tuy_chon' as it's handled by the Calendar
      isUpdatingPeriod.value = false
      return
      break
  }
  khoangThoiGian.value = newRange
  isUpdatingPeriod.value = false
}

// Handle Date Range Change
const xuLyThayDoiKhoangThoiGian = () => {
  // This function is triggered on @date-select from the Calendar.
  // The v-model on Calendar already updates khoangThoiGian.
  // We just need to set the period selector to 'tuy_chon'. The watcher will handle the rest.
  kyChon.value = 'tuy_chon'
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

const getStatusLabel = (status) => {
  switch (status) {
    case 'CHO_XAC_NHAN':
      return 'Chờ xác nhận'
    case 'DANG_XU_LY':
      return 'Đang xử lý'
    case 'DA_XAC_NHAN':
      return 'Đã xác nhận'
    case 'DANG_DONG_GOI':
      return 'Đang đóng gói'
    case 'DANG_GIAO_HANG':
      return 'Đang giao hàng'
    case 'DA_GIAO_HANG':
      return 'Đã giao hàng'
    case 'HOAN_THANH':
      return 'Hoàn thành'
    case 'DA_HUY':
      return 'Đã hủy'
    case 'YEU_CAU_TRA_HANG':
      return 'Yêu cầu trả hàng'
    case 'DA_TRA_HANG':
      return 'Đã trả hàng'
    default:
      return status
  }
}

const getSeverity = (status) => {
  switch (status) {
    case 'CHO_XAC_NHAN':
      return 'warning'
    case 'DANG_XU_LY':
      return 'info'
    case 'DA_XAC_NHAN':
      return 'info'
    case 'DANG_DONG_GOI':
      return 'info'
    case 'DANG_GIAO_HANG':
      return 'primary'
    case 'DA_GIAO_HANG':
      return 'success'
    case 'HOAN_THANH':
      return 'success'
    case 'DA_HUY':
      return 'danger'
    case 'YEU_CAU_TRA_HANG':
      return 'danger'
    case 'DA_TRA_HANG':
      return 'danger'
    default:
      return null
  }
}

// Export Report
const xuatBaoCao = () => {
  try {
    const tongQuan = [
      { 'Chỉ số': 'Tổng doanh thu', 'Giá trị': tongQuanDashboard.value.doanhThu.namNay },
      { 'Chỉ số': 'Doanh thu hôm nay', 'Giá trị': tongQuanDashboard.value.doanhThu.homNay },
      { 'Chỉ số': 'Doanh thu tháng này', 'Giá trị': tongQuanDashboard.value.doanhThu.thangNay },
      {
        'Chỉ số': 'Tăng trưởng theo tháng (%)',
        'Giá trị': tongQuanDashboard.value.doanhThu.tangTruongTheoThang,
      },
      {
        'Chỉ số': 'Ngày doanh thu tốt nhất',
        'Giá trị': tongQuanDashboard.value.doanhThu.ngayDoanhThuTotNhat,
      },
      {
        'Chỉ số': 'Doanh thu tốt nhất',
        'Giá trị': tongQuanDashboard.value.doanhThu.doanhThuTotNhat,
      },
      { 'Chỉ số': 'Tổng đơn hàng', 'Giá trị': tongQuanDashboard.value.donHang.tongSo },
      { 'Chỉ số': 'Đơn hoàn thành', 'Giá trị': tongQuanDashboard.value.donHang.hoanThanh },
      { 'Chỉ số': 'Đơn đang xử lý', 'Giá trị': tongQuanDashboard.value.donHang.dangXuLy },
      { 'Chỉ số': 'Đơn chờ xác nhận', 'Giá trị': tongQuanDashboard.value.donHang.choXacNhan },
      { 'Chỉ số': 'Đơn đã hủy', 'Giá trị': tongQuanDashboard.value.donHang.daHuy },
      {
        'Chỉ số': 'Tỷ lệ hoàn thành (%)',
        'Giá trị': tongQuanDashboard.value.donHang.tyLeHoanThanh,
      },
      { 'Chỉ số': 'Tổng khách hàng', 'Giá trị': tongQuanDashboard.value.khachHang.tongSo },
      { 'Chỉ số': 'Khách mới tháng này', 'Giá trị': tongQuanDashboard.value.khachHang.moiThangNay },
      { 'Chỉ số': 'Tỷ lệ giữ chân (%)', 'Giá trị': tongQuanDashboard.value.khachHang.tyLeGiuChan },
      {
        'Chỉ số': 'Giá trị trung bình/khách',
        'Giá trị': tongQuanDashboard.value.khachHang.giaTriTrungBinh,
      },
      { 'Chỉ số': 'Tổng sản phẩm', 'Giá trị': tongQuanDashboard.value.sanPham.tongSo },
      { 'Chỉ số': 'Sản phẩm sắp hết hàng', 'Giá trị': tongQuanDashboard.value.sanPham.sapHetHang },
    ]

    const doanhThuTheoNgay =
      duLieuBieuDoDoanhThu.value.labels?.map((label, idx) => ({
        Ngày: label,
        'Doanh thu': duLieuBieuDoDoanhThu.value.datasets?.[0]?.data?.[idx] ?? '',
      })) || []

    const donHangTheoTrangThai =
      duLieuBieuDoDonHang.value.labels?.map((label, idx) => ({
        'Trạng thái': label,
        'Số lượng': duLieuBieuDoDonHang.value.datasets?.[0]?.data?.[idx] ?? '',
      })) || []

    const topSanPham =
      duLieuBieuDoSanPham.value.labels?.map((label, idx) => ({
        'Sản phẩm': label,
        'Số lượng bán': duLieuBieuDoSanPham.value.datasets?.[0]?.data?.[idx] ?? '',
      })) || []

    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(tongQuan), 'TongQuan')
    if (doanhThuTheoNgay.length)
      XLSX.utils.book_append_sheet(
        workbook,
        XLSX.utils.json_to_sheet(doanhThuTheoNgay),
        'DoanhThuTheoNgay',
      )
    if (donHangTheoTrangThai.length)
      XLSX.utils.book_append_sheet(
        workbook,
        XLSX.utils.json_to_sheet(donHangTheoTrangThai),
        'DonHangTheoTrangThai',
      )
    if (topSanPham.length)
      XLSX.utils.book_append_sheet(workbook, XLSX.utils.json_to_sheet(topSanPham), 'TopSanPham')

    const excelBuffer = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' })
    const blob = new Blob([excelBuffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `BaoCaoThongKe_${new Date().toISOString().slice(0, 10)}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    hienThiThanhCong('Xuất báo cáo chi tiết thành công!')
  } catch (error) {
    hienThiLoi('Xuất báo cáo thất bại!')
    console.error(error)
  }
}

// ==================== WATCHERS ====================
watch(
  [getPrimary, getSurface, isDarkTheme],
  () => {
    khoiTaoTuyChonBieuDo()
  },
  { immediate: true },
)

// Watch for changes in the date range to reload all relevant data
watch(
  khoangThoiGian,
  () => {
    // This watcher is now the single source of truth for reloading data based on time.
    // It ensures that for each user action, data is fetched only once.
    taiDuLieuBieuDoDoanhThu()
    taiDuLieuBieuDoDonHang()
    taiDuLieuBieuDoSanPham()
    taiDonHangGanDay()
  },
  { deep: true },
)

// Watch for filter changes to reload only the recent orders table
watch(
  filters,
  () => {
    taiDonHangGanDay()
  },
  { deep: true },
)

// ==================== LIFECYCLE ====================
onMounted(() => {
  khoiTaoTuyChonBieuDo()
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
            icon="pi pi-refresh"
            label="Làm mới"
            @click="lamMoiDuLieu"
            :loading="dangTai"
            severity="secondary"
            outlined
            size="small"
            v-tooltip.left="'Cập nhật dữ liệu mới nhất'"
          />
          <Button
            icon="pi pi-download"
            label="Xuất báo cáo"
            severity="primary"
            outlined
            size="small"
            v-tooltip.left="'Xuất báo cáo thống kê'"
            @click="xuatBaoCao"
          />
        </div>
      </div>
    </div>

    <div class="grid grid-cols-12 gap-8">
      <!-- Error Message -->
      <div v-if="loi" class="col-span-12">
        <Message severity="error" :closable="false">{{ loi }}</Message>
      </div>

      <!-- Charts Section -->
      <div class="col-span-12">
        <div class="card">
          <div class="card-header border-b border-surface-200 dark:border-surface-700 pb-4 mb-6">
            <h2 class="text-xl font-semibold text-surface-900 dark:text-surface-0 m-0">
              📊 Phân Tích Chi Tiết
            </h2>
            <p class="text-surface-600 dark:text-surface-400 text-sm mt-1 mb-0">
              Biểu đồ và thống kê chuyên sâu về hoạt động kinh doanh
            </p>
          </div>

          <!-- Time Period Controls -->
          <div
            class="flex flex-wrap gap-4 items-center justify-between p-4 bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-700 mb-6"
          >
            <div class="flex items-center gap-3">
              <i class="pi pi-calendar text-primary"></i>
              <span class="font-medium text-surface-700 dark:text-surface-300"
                >Khoảng thời gian:</span
              >
              <Select
                v-model="kyChon"
                :options="cacLuaChonKy"
                optionLabel="label"
                optionValue="value"
                placeholder="Chọn khoảng thời gian"
                @change="xuLyThayDoiKy"
                class="w-48"
              />
            </div>
            <div v-if="kyChon === 'tuy_chon'" class="flex gap-2">
              <Calendar
                v-model="khoangThoiGian"
                selectionMode="range"
                :manualInput="false"
                dateFormat="dd/mm/yy"
                placeholder="Chọn khoảng thời gian"
                @date-select="xuLyThayDoiKhoangThoiGian"
                showIcon
              />
            </div>
          </div>

          <Tabs value="0" class="custom-tabs">
            <TabList class="flex border-b border-surface-200 dark:border-surface-700 mb-6">
              <Tab
                value="0"
                class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-all duration-200 border-b-2 border-transparent hover:text-primary hover:border-primary-200 data-[state=active]:text-primary data-[state=active]:border-primary"
              >
                <i class="pi pi-chart-line text-base"></i>
                <span>Doanh Thu & Tăng Trưởng</span>
              </Tab>
              <Tab
                value="1"
                class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-all duration-200 border-b-2 border-transparent hover:text-primary hover:border-primary-200 data-[state=active]:text-primary data-[state=active]:border-primary"
              >
                <i class="pi pi-shopping-cart text-base"></i>
                <span>Đơn Hàng & Chuyển Đổi</span>
              </Tab>
              <Tab
                value="2"
                class="flex items-center gap-2 px-4 py-3 text-sm font-medium transition-all duration-200 border-b-2 border-transparent hover:text-primary hover:border-primary-200 data-[state=active]:text-primary data-[state=active]:border-primary"
              >
                <i class="pi pi-box text-base"></i>
                <span>Sản Phẩm & Hiệu Suất</span>
              </Tab>
            </TabList>

            <TabPanels>
              <!-- Revenue & Growth Analysis Tab -->
              <TabPanel value="0">
                <div class="space-y-6">
                  <div class="grid grid-cols-12 gap-6">
                    <!-- Main Revenue Chart -->
                    <div class="col-span-12 lg:col-span-8">
                      <div
                        class="bg-gradient-to-br from-primary-50 to-blue-50 dark:from-surface-800 dark:to-surface-900 rounded-lg border border-primary-200 dark:border-surface-700 p-6"
                      >
                        <div class="flex justify-between items-center mb-4">
                          <div>
                            <h3
                              class="text-lg font-semibold text-surface-900 dark:text-surface-0 m-0"
                            >
                              💰 Xu Hướng Doanh Thu
                            </h3>
                            <p class="text-surface-600 dark:text-surface-400 text-sm mt-1">
                              Theo dõi doanh thu theo thời gian với xu hướng tăng trưởng
                            </p>
                          </div>
                          <Badge v-if="dangTai" value="Đang tải..." severity="info" />
                        </div>
                        <div class="h-80">
                          <Chart
                            v-if="!dangTai && duLieuBieuDoDoanhThu.labels?.length > 0"
                            type="line"
                            :data="duLieuBieuDoDoanhThu"
                            :options="tuyChonBieuDoDoanhThu"
                            class="h-full"
                          />
                          <div v-else-if="dangTai" class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <ProgressBar mode="indeterminate" style="height: 6px" class="mb-3" />
                              <p class="text-surface-500 text-sm">Đang tải dữ liệu doanh thu...</p>
                            </div>
                          </div>
                          <div v-else class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <i class="pi pi-chart-line text-4xl text-surface-400 mb-3"></i>
                              <p class="text-surface-500">Không có dữ liệu doanh thu để hiển thị</p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Recent Orders Filters -->
                  <div
                    class="bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-700 p-4 mb-4"
                  >
                    <div class="flex justify-between items-center mb-4">
                      <div class="flex items-center gap-2">
                        <i class="pi pi-filter text-primary"></i>
                        <span class="font-medium text-surface-700 dark:text-surface-300"
                          >Lọc đơn hàng gần đây</span
                        >
                      </div>
                      <Button
                        icon="pi pi-filter-slash"
                        label="Xóa bộ lọc"
                        outlined
                        size="small"
                        @click="clearFilters"
                        :disabled="!hasActiveFilters"
                      />
                    </div>
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                      <div>
                        <label class="block mb-2 text-sm">Mã đơn hàng</label>
                        <InputText
                          v-model="filters.maHoaDon.constraints[0].value"
                          placeholder="Lọc mã đơn hàng"
                          class="w-full"
                        />
                      </div>
                      <div>
                        <label class="block mb-2 text-sm">Khách hàng</label>
                        <InputText
                          v-model="filters.khachHang.constraints[0].value"
                          placeholder="Lọc tên khách hàng"
                          class="w-full"
                        />
                      </div>
                      <div>
                        <label class="block mb-2 text-sm">Trạng thái đơn hàng</label>
                        <Select
                          v-model="filters.trangThaiDonHang.value"
                          :options="orderStatusOptions"
                          optionLabel="label"
                          optionValue="value"
                          placeholder="Chọn trạng thái"
                          class="w-full"
                        >
                          <template #option="{ option }">
                            <Tag :value="option.label" :severity="option.severity" />
                          </template>
                        </Select>
                      </div>
                    </div>
                  </div>

                  <!-- Recent Orders Table -->
                  <div
                    class="bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-700 p-6"
                  >
                    <div class="flex items-center gap-3 mb-4">
                      <i class="pi pi-list text-primary text-lg"></i>
                      <h3 class="text-lg font-semibold text-surface-900 dark:text-surface-0 m-0">
                        📋 Đơn Hàng Gần Đây
                      </h3>
                    </div>
                    <DataTable
                      :value="recentOrders"
                      :loading="loadingRecentOrders"
                      :rows="5"
                      :paginator="recentOrders.length > 5"
                      class="p-datatable-sm"
                      showGridlines
                      responsiveLayout="scroll"
                    >
                      <Column header="STT" style="width: 3rem">
                        <template #body="{ index }">
                          {{ index + 1 }}
                        </template>
                      </Column>
                      <Column
                        field="maHoaDon"
                        header="Mã Đơn Hàng"
                        style="min-width: 8rem"
                      ></Column>
                      <Column field="ngayTao" header="Ngày Đặt" style="min-width: 10rem">
                        <template #body="{ data }">
                          {{ formatDateTime(data.ngayTao) }}
                        </template>
                      </Column>
                      <Column field="tongThanhToan" header="Tổng Tiền" style="min-width: 10rem">
                        <template #body="{ data }">
                          {{ formatCurrency(data.tongThanhToan) }}
                        </template>
                      </Column>
                      <Column field="trangThaiDonHang" header="Trạng Thái" style="min-width: 10rem">
                        <template #body="{ data }">
                          <Tag
                            :value="getStatusLabel(data.trangThaiDonHang)"
                            :severity="getSeverity(data.trangThaiDonHang)"
                          />
                        </template>
                      </Column>
                      <template #empty>
                        <div class="text-center py-4">
                          <i class="pi pi-box text-4xl text-surface-400 mb-3"></i>
                          <p class="text-surface-500">Không có đơn hàng gần đây</p>
                        </div>
                      </template>
                      <template #loading>
                        <div class="text-center py-4">
                          <ProgressSpinner />
                          <p class="mt-2 text-surface-500">Đang tải đơn hàng...</p>
                        </div>
                      </template>
                    </DataTable>
                  </div>
                </div>
              </TabPanel>

              <!-- Orders & Conversion Analysis Tab -->
              <TabPanel value="1">
                <div class="space-y-6">
                  <div class="grid grid-cols-12 gap-6">
                    <div class="col-span-12 lg:col-span-8">
                      <div
                        class="bg-gradient-to-br from-orange-50 to-red-50 dark:from-surface-800 dark:to-surface-900 rounded-lg border border-orange-200 dark:border-surface-700 p-6"
                      >
                        <div class="flex justify-between items-center mb-4">
                          <div>
                            <h3
                              class="text-lg font-semibold text-surface-900 dark:text-surface-0 m-0"
                            >
                              🛒 Phân Bố Trạng Thái Đơn Hàng
                            </h3>
                            <p class="text-surface-600 dark:text-surface-400 text-sm mt-1">
                              Tỷ lệ đơn hàng theo từng trạng thái xử lý
                            </p>
                          </div>
                          <Badge v-if="dangTai" value="Đang tải..." severity="info" />
                        </div>
                        <div class="h-80">
                          <Chart
                            v-if="!dangTai && duLieuBieuDoDonHang.labels?.length > 0"
                            type="doughnut"
                            :data="duLieuBieuDoDonHang"
                            :options="tuyChonBieuDoDonHang"
                            class="h-full"
                          />
                          <div v-else-if="dangTai" class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <ProgressBar mode="indeterminate" style="height: 6px" class="mb-3" />
                              <p class="text-surface-500 text-sm">Đang tải dữ liệu đơn hàng...</p>
                            </div>
                          </div>
                          <div v-else class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <i class="pi pi-shopping-cart text-4xl text-surface-400 mb-3"></i>
                              <p class="text-surface-500">Không có dữ liệu đơn hàng để hiển thị</p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div class="col-span-12 lg:col-span-4">
                      <div class="space-y-4">
                        <div
                          class="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-green-100 dark:bg-green-800 rounded-full flex items-center justify-center"
                            >
                              <i class="pi pi-check-circle text-green-600 dark:text-green-400"></i>
                            </div>
                            <span class="font-medium text-green-800 dark:text-green-200"
                              >Tỷ Lệ Hoàn Thành</span
                            >
                          </div>
                          <p class="text-2xl font-bold text-green-700 dark:text-green-300 mb-1">{{
                            dinhDangPhanTram(tongQuanDashboard.donHang.tyLeHoanThanh || 0)
                          }}</p>
                          <p class="text-sm text-green-600 dark:text-green-400"
                            >Đơn hàng thành công</p
                          >
                        </div>

                        <div
                          class="bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-yellow-100 dark:bg-yellow-800 rounded-full flex items-center justify-center"
                            >
                              <i class="pi pi-clock text-yellow-600 dark:text-yellow-400"></i>
                            </div>
                            <span class="font-medium text-yellow-800 dark:text-yellow-200"
                              >Đang Xử Lý</span
                            >
                          </div>
                          <p class="text-2xl font-bold text-yellow-700 dark:text-yellow-300 mb-1">{{
                            dinhDangSo(tongQuanDashboard.donHang.dangXuLy || 0)
                          }}</p>
                          <p class="text-sm text-yellow-600 dark:text-yellow-400"
                            >Đơn hàng cần xử lý</p
                          >
                        </div>

                        <div
                          class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-blue-100 dark:bg-blue-800 rounded-full flex items-center justify-center"
                            >
                              <i class="pi pi-percentage text-blue-600 dark:text-blue-400"></i>
                            </div>
                            <span class="font-medium text-blue-800 dark:text-blue-200"
                              >Tỷ Lệ Chuyển Đổi</span
                            >
                          </div>
                          <p class="text-2xl font-bold text-blue-700 dark:text-blue-300 mb-1"
                            >3.2%</p
                          >
                          <p class="text-sm text-blue-600 dark:text-blue-400"
                            >Từ lượt xem thành đơn hàng</p
                          >
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </TabPanel>

              <!-- Products & Performance Analysis Tab -->
              <TabPanel value="2">
                <div class="space-y-6">
                  <div class="grid grid-cols-12 gap-6">
                    <div class="col-span-12 lg:col-span-8">
                      <div
                        class="bg-gradient-to-br from-teal-50 to-green-50 dark:from-surface-800 dark:to-surface-900 rounded-lg border border-teal-200 dark:border-surface-700 p-6"
                      >
                        <div class="flex justify-between items-center mb-4">
                          <div>
                            <h3
                              class="text-lg font-semibold text-surface-900 dark:text-surface-0 m-0"
                            >
                              📦 Top Sản Phẩm Bán Chạy
                            </h3>
                            <p class="text-surface-600 dark:text-surface-400 text-sm mt-1">
                              Sản phẩm có doanh số cao nhất trong kỳ
                            </p>
                          </div>
                          <Badge v-if="dangTai" value="Đang tải..." severity="info" />
                        </div>
                        <div class="h-80">
                          <Chart
                            v-if="!dangTai && duLieuBieuDoSanPham.labels?.length > 0"
                            type="bar"
                            :data="duLieuBieuDoSanPham"
                            :options="tuyChonBieuDoSanPham"
                            class="h-full"
                          />
                          <div v-else-if="dangTai" class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <ProgressBar mode="indeterminate" style="height: 6px" class="mb-3" />
                              <p class="text-surface-500 text-sm">Đang tải dữ liệu sản phẩm...</p>
                            </div>
                          </div>
                          <div v-else class="flex justify-center items-center h-full">
                            <div class="text-center">
                              <i class="pi pi-box text-4xl text-surface-400 mb-3"></i>
                              <p class="text-surface-500">Không có dữ liệu sản phẩm để hiển thị</p>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>

                    <div class="col-span-12 lg:col-span-4">
                      <div class="space-y-4">
                        <div
                          class="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-blue-100 dark:bg-blue-800 rounded-full flex items-center justify-center"
                            >
                              <i class="pi pi-box text-blue-600 dark:text-blue-400"></i>
                            </div>
                            <span class="font-medium text-blue-800 dark:text-blue-200"
                              >Tổng Sản Phẩm</span
                            >
                          </div>
                          <p class="text-2xl font-bold text-blue-700 dark:text-blue-300 mb-1">{{
                            dinhDangSo(tongQuanDashboard.sanPham.tongSo || 0)
                          }}</p>
                          <p class="text-sm text-blue-600 dark:text-blue-400">Sản phẩm trong kho</p>
                        </div>

                        <div
                          class="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-red-100 dark:bg-red-800 rounded-full flex items-center justify-center"
                            >
                              <i
                                class="pi pi-exclamation-triangle text-red-600 dark:text-red-400"
                              ></i>
                            </div>
                            <span class="font-medium text-red-800 dark:text-red-200"
                              >Sắp Hết Hàng</span
                            >
                          </div>
                          <p class="text-2xl font-bold text-red-700 dark:text-red-300 mb-1">{{
                            dinhDangSo(tongQuanDashboard.sanPham.sapHetHang || 0)
                          }}</p>
                          <p class="text-sm text-red-600 dark:text-red-400"
                            >Sản phẩm cần nhập thêm</p
                          >
                        </div>

                        <div
                          class="bg-purple-50 dark:bg-purple-900/20 border border-purple-200 dark:border-purple-800 rounded-lg p-4"
                        >
                          <div class="flex items-center gap-3 mb-2">
                            <div
                              class="w-8 h-8 bg-purple-100 dark:bg-purple-800 rounded-full flex items-center justify-center"
                            >
                              <i class="pi pi-tags text-purple-600 dark:text-purple-400"></i>
                            </div>
                            <span class="font-medium text-purple-800 dark:text-purple-200"
                              >Danh Mục Tốt Nhất</span
                            >
                          </div>
                          <p class="text-lg font-bold text-purple-700 dark:text-purple-300 mb-1"
                            >Laptop Gaming</p
                          >
                          <p class="text-sm text-purple-600 dark:text-purple-400"
                            >Doanh số cao nhất</p
                          >
                        </div>
                      </div>
                    </div>
                  </div>

                  <!-- Inventory Alerts -->
                  <div
                    class="bg-surface-50 dark:bg-surface-800 rounded-lg border border-surface-200 dark:border-surface-700 p-6"
                  >
                    <div class="flex items-center gap-3 mb-4">
                      <i class="pi pi-bell text-primary text-lg"></i>
                      <h3 class="text-lg font-semibold text-surface-900 dark:text-surface-0 m-0">
                        🔔 Cảnh Báo Tồn Kho
                      </h3>
                    </div>
                    <NotificationsWidget />
                  </div>
                </div>
              </TabPanel>
            </TabPanels>
          </Tabs>
        </div>
      </div>

      <!-- Summary Cards -->
      <div class="col-span-12">
        <div class="grid grid-cols-12 gap-6">
          <div class="col-span-12 lg:col-span-3">
            <DoanhThuCard
              :data="tongQuanDashboard.doanhThu"
              :loading="dangTai"
              :formatCurrency="dinhDangTienTe"
              :formatPercentage="dinhDangPhanTram"
            />
          </div>
          <div class="col-span-12 lg:col-span-3">
            <DonHangCard
              :data="tongQuanDashboard.donHang"
              :loading="dangTai"
              :formatNumber="dinhDangSo"
              :formatPercentage="dinhDangPhanTram"
            />
          </div>
          <div class="col-span-12 lg:col-span-3">
            <SanPhamCard
              :data="tongQuanDashboard.sanPham"
              :loading="dangTai"
              :formatNumber="dinhDangSo"
            />
          </div>
          <div class="col-span-12 lg:col-span-3">
            <KhachHangCard
              :data="tongQuanDashboard.khachHang"
              :loading="dangTai"
              :formatNumber="dinhDangSo"
              :formatCurrency="dinhDangTienTe"
              :formatPercentage="dinhDangPhanTram"
            />
          </div>
        </div>
      </div>
    </div>
  </Fluid>
</template>
```
