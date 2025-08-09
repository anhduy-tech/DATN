<template>
  <div class="pos-updates-container p-4 max-w-4xl mx-auto">
    <Toast />
    <Card class="shadow-lg border-0 rounded-xl overflow-hidden">
      <template #header>
        <div class="p-4 bg-gradient-to-r from-blue-600 to-blue-400 text-white flex items-center justify-between">
          <div class="flex items-center gap-3">
            <i class="pi pi-shopping-cart text-2xl"></i>
            <h1 class="text-xl font-semibold m-0">Cập nhật giỏ hàng thời gian thực</h1>
          </div>
          <span class="text-sm opacity-80">Order ID: {{ latestUpdate.orderId || 'N/A' }}</span>
        </div>
      </template>
      <template #content>
        <div class="max-h-[70vh] overflow-y-auto p-4" ref="updatesContainer">
          <div v-if="latestUpdate.products?.length" class="space-y-4">
            <div v-for="(item, index) in latestUpdate.products" :key="item.sanPhamChiTiet.id"
                 class="flex items-center gap-4 p-4 bg-white rounded-lg shadow-sm hover:shadow-md transition-all duration-300 border border-gray-100">
              <img :src="item.sanPhamChiTiet.hinhAnh?.[0] || '/placeholder-product.png'"
                   :alt="item.sanPhamChiTiet.tenSanPham"
                   class="w-16 h-16 object-cover rounded-lg border border-gray-200" />
              <div class="flex-1 min-w-0">
                <div class="font-semibold text-base text-gray-800 truncate">{{ item.sanPhamChiTiet.tenSanPham }}</div>
                <div class="text-sm text-gray-500">{{ item.sanPhamChiTiet.sku }}</div>
                <div class="text-sm text-gray-600 mt-1">{{ item.groupInfo.displayName }}</div>
                <div class="text-base font-semibold text-blue-600 mt-1">{{ formatCurrency(item.donGia) }}</div>
              </div>
              <div class="flex items-center gap-4">
                <div class="flex items-center gap-2 px-3 py-2 bg-blue-50 border border-blue-200 rounded-lg">
                  <i class="pi pi-barcode text-blue-600"></i>
                  <div class="flex flex-col">
                    <span class="text-xs text-gray-500 uppercase font-medium">Serial</span>
                    <span class="text-sm font-bold text-blue-700">{{ item.sanPhamChiTiet.serialNumber || 'N/A' }}</span>
                  </div>
                </div>
                <div class="text-right">
                  <div v-if="item.sanPhamChiTiet.giaKhuyenMai" class="space-y-1">
                    <div class="text-sm text-gray-500 line-through">{{ formatCurrency(item.sanPhamChiTiet.giaBan * item.soLuong) }}</div>
                    <div class="text-lg font-semibold text-red-600 flex items-center gap-1">
                      {{ formatCurrency(item.sanPhamChiTiet.giaKhuyenMai * item.soLuong) }}
                      <span class="text-xs bg-red-100 text-red-700 px-2 py-1 rounded">Giảm giá</span>
                    </div>
                  </div>
                  <div v-else class="text-lg font-semibold text-blue-600">
                    {{ formatCurrency(item.thanhTien) }}
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="text-center text-gray-500 py-8">
            Chưa có cập nhật giỏ hàng
          </div>
        </div>
        <Divider />
        <div class="p-4 bg-gray-50 text-right">
          <span class="text-sm text-gray-600">Cập nhật lúc: {{ formatTimestamp(latestUpdate.timestamp) }}</span>
        </div>
      </template>
    </Card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue';
import { useToast } from 'primevue/usetoast';
import { connectPosWebSocket } from '@/apis/posView';
import Card from 'primevue/card';
import Toast from 'primevue/toast';
import Divider from 'primevue/divider';

// Khởi tạo
const toast = useToast();
const latestUpdate = ref({ orderId: null, products: [], timestamp: null });
const updatesContainer = ref(null);

// Hàm format tiền
const formatCurrency = (value) => {
  if (!value) return '0 ₫';
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

// Hàm format thời gian
const formatTimestamp = (timestamp) => {
  if (!timestamp) return 'N/A';
  return new Date(timestamp).toLocaleString('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  });
};

// Kết nối WebSocket và nhận cập nhật
onMounted(() => {
  connectPosWebSocket(
    (msg) => {
      if (msg.action === 'UPDATE_CART') {
        latestUpdate.value = {
          orderId: msg.orderId,
          products: msg.products,
          timestamp: msg.timestamp,
        };
        // Cuộn xuống cuối danh sách
        nextTick(() => {
          if (updatesContainer.value) {
            updatesContainer.value.scrollTop = updatesContainer.value.scrollHeight;
          }
        });
      } else if (msg.action === 'PRICE_UPDATE') {
        if (latestUpdate.value.products) {
          const product = latestUpdate.value.products.find(
            (item) => item.sanPhamChiTiet.id === msg.productId
          );
          if (product) {
            product.donGia = msg.newPrice;
            product.thanhTien = msg.newPrice * product.soLuong;
            toast.add({
              severity: 'info',
              summary: 'Cập nhật giá',
              detail: `Giá sản phẩm ${product.sanPhamChiTiet.tenSanPham} đã được cập nhật.`,
              life: 3000,
            });
          }
        }
      } else if (msg.action === 'STOCK_UPDATE') {
        toast.add({
          severity: 'warn',
          summary: 'Cập nhật tồn kho',
          detail: `Sản phẩm ${msg.productId} có tồn kho mới: ${msg.stock}`,
          life: 3000,
        });
      }
    },
    () => {

      toast.add({
        severity: 'success',
        summary: 'Kết nối POS',
        detail: 'Đã kết nối POS thời gian thực.',
        life: 3000,
      });
    },
    (error) => {

      toast.add({
        severity: 'error',
        summary: 'Lỗi kết nối POS',
        detail: error,
        life: 5000,
      });
    }
  );
});
</script>

<style scoped>
.pos-updates-container {
  min-height: 100vh;
  background-color: #f5f7fa;
}

.card {
  background: linear-gradient(145deg, #ffffff, #e6e6e6);
  border-radius: 12px;
  transition: all 0.3s ease;
}

.card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.max-h-\[70vh\] {
  scroll-behavior: smooth;
}

.p-card-content {
  padding: 0;
}
</style>