<template>
  <div class="container mx-auto p-4">
    <Toast />
    <h1 class="text-3xl font-bold text-surface-900 mb-6">Lịch sử đơn hàng của bạn</h1>

    <div v-if="loading" class="text-center py-16">
      <ProgressSpinner />
      <p class="mt-4 text-surface-600 text-lg">Đang tải lịch sử đơn hàng...</p>
    </div>

    <div v-else-if="error" class="text-center py-16">
      <i class="pi pi-exclamation-triangle text-5xl text-red-500 mb-4 block"></i>
      <p class="text-surface-600 text-lg">{{ error }}</p>
      <Button label="Thử lại" icon="pi pi-refresh" @click="loadOrders" class="mt-4" />
    </div>

    <div v-else-if="orders.length === 0" class="text-center py-16">
      <i class="pi pi-box text-5xl text-surface-400 mb-4 block"></i>
      <p class="text-surface-600 text-lg">Bạn chưa có đơn hàng nào.</p>
      <router-link to="/shop/products" class="text-primary-600 font-medium hover:underline mt-4 block">
        Bắt đầu mua sắm ngay!
      </router-link>
    </div>

    <div v-else class="card">
      <DataTable :value="orders" paginator :rows="10" :rowsPerPageOptions="[5, 10, 20]"
        paginatorTemplate="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport RowsPerPageDropdown"
        currentPageReportTemplate="Hiển thị {first} đến {last} của {totalRecords} đơn hàng" responsiveLayout="scroll">
        <Column field="maHoaDon" header="Mã đơn hàng" :sortable="true"></Column>
        <Column field="ngayTao" header="Ngày đặt" :sortable="true">
          <template #body="{ data }">
            {{ formatDateTime(data.ngayTao) }}
          </template>
        </Column>
        <Column field="tongThanhToan" header="Tổng tiền" :sortable="true">
          <template #body="{ data }">
            {{ formatCurrency(data.tongThanhToan) }}
          </template>
        </Column>
        <Column field="trangThaiDonHang" header="Trạng thái đơn hàng" :sortable="true">
          <template #body="{ data }">
            <Badge :value="getOrderStatusInfo(data.trangThaiDonHang).label"
              :severity="getOrderStatusInfo(data.trangThaiDonHang).severity" />
          </template>
        </Column>
        <Column field="trangThaiThanhToan" header="Trạng thái thanh toán" :sortable="true">
          <template #body="{ data }">
            <Badge :value="getPaymentStatusInfo(data.trangThaiThanhToan).label"
              :severity="getPaymentStatusInfo(data.trangThaiThanhToan).severity" />
          </template>
        </Column>
        <Column header="Hành động">
          <template #body="{ data }">
            <Button icon="pi pi-eye" severity="info" outlined size="small" class="mr-2"
              @click="viewOrderDetails(data.id)" v-tooltip.top="'Xem chi tiết'" />
            <Button
              v-if="data.trangThaiDonHang === 'CHO_XAC_NHAN'"
              icon="pi pi-pencil"
              severity="warning"
              outlined
              size="small"
              @click="editOrder(data.id)"
              v-tooltip.top="'Chỉnh sửa đơn hàng'"
            />
          </template>
        </Column>
      </DataTable>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useOrderStore } from '@/stores/orderStore';
import AuthService from '@/apis/auth';
import orderApi from '@/apis/orderApi';

// PrimeVue Components
import Toast from 'primevue/toast';
import Button from 'primevue/button';
import ProgressSpinner from 'primevue/progressspinner';
import DataTable from 'primevue/datatable';
import Column from 'primevue/column';
import Badge from 'primevue/badge';

const router = useRouter();
const toast = useToast();
const orderStore = useOrderStore();

const orders = ref([]);
const loading = ref(true);
const error = ref(null);

const loadOrders = async () => {
  loading.value = true;
  error.value = null;
  try {
    if (!AuthService.isAuthenticated()) {
      error.value = 'Vui lòng đăng nhập để xem lịch sử đơn hàng.';
      loading.value = false;
      return;
    }
    const user = AuthService.getUser();
    if (!user || !user.id) {
      error.value = 'Không thể lấy thông tin người dùng. Vui lòng đăng nhập lại.';
      loading.value = false;
      return;
    }

    // Use orderApi.getMyOrders() which is designed for the authenticated user
    const response = await orderApi.getMyOrders();
    if (response.success) {
      orders.value = response.data;
    } else {
      throw new Error(response.message || 'Không thể tải lịch sử đơn hàng.');
    }
  } catch (err) {
    console.error('Error loading order history:', err);
    error.value = err.message || 'Lỗi tải lịch sử đơn hàng.';
    toast.add({ severity: 'error', summary: 'Lỗi', detail: error.value, life: 3000 });
  } finally {
    loading.value = false;
  }
};

const formatDateTime = (dateString) => {
  if (!dateString) return 'N/A';
  const date = new Date(dateString);
  return date.toLocaleString('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const formatCurrency = (amount) => {
  if (amount === null || amount === undefined) return '0 ₫';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(amount);
};

const getOrderStatusInfo = (status) => {
  return orderStore.getOrderStatusInfo(status);
};

const getPaymentStatusInfo = (status) => {
  const info = { label: status, severity: 'secondary' };
  switch (status) {
    case 'CHUA_THANH_TOAN':
      info.label = 'Chưa thanh toán';
      info.severity = 'warning';
      break;
    case 'DA_THANH_TOAN':
      info.label = 'Đã thanh toán';
      info.severity = 'success';
      break;
    case 'DA_HOAN_TIEN':
      info.label = 'Đã hoàn tiền';
      info.severity = 'info';
      break;
    case 'HUY_THANH_TOAN':
      info.label = 'Hủy thanh toán';
      info.severity = 'danger';
      break;
  }
  return info;
};

const viewOrderDetails = (orderId) => {
  router.push({ name: 'shop-order-detail', params: { id: orderId } });
};

const editOrder = (orderId) => {
  // For now, navigate to the existing order detail page.
  // If complex editing is needed, a dedicated OrderEditPage might be required.
  router.push({ name: 'shop-order-detail', params: { id: orderId } });
  toast.add({ severity: 'info', summary: 'Thông báo', detail: 'Bạn có thể chỉnh sửa thông tin đơn hàng trên trang chi tiết.', life: 3000 });
};

onMounted(() => {
  loadOrders();
});
</script>

<style scoped>
.card {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
</style>
