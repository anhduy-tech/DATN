<template>
  <div class="container mx-auto px-4 py-8">
    <h1 class="text-3xl font-bold text-surface-900 mb-6">Danh sách yêu thích của bạn</h1>

    <div v-if="loading" class="text-center py-16">
      <ProgressSpinner />
      <p class="mt-4 text-surface-600 text-lg">Đang tải danh sách yêu thích...</p>
    </div>

    <div v-else-if="error" class="text-center py-16">
      <i class="pi pi-exclamation-triangle text-5xl text-red-500 mb-4 block"></i>
      <p class="text-surface-600 text-lg">{{ error }}</p>
      <Button label="Thử lại" icon="pi pi-refresh" @click="loadWishlist" class="mt-4" />
    </div>

    <div v-else-if="wishlist.length === 0" class="text-center py-16">
      <i class="pi pi-heart text-5xl text-surface-400 mb-4 block"></i>
      <p class="text-surface-600 text-lg">Danh sách yêu thích của bạn đang trống.</p>
      <router-link to="/shop/products" class="text-primary-600 font-medium hover:underline mt-4 block">
        Khám phá sản phẩm ngay!
      </router-link>
    </div>

    <div v-else class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
      <Card v-for="item in wishlist" :key="item.id" class="flex flex-col">
        <template #header>
          <router-link :to="{ name: 'shop-product-detail', params: { id: item.sanPhamId } }">
            <img
              :src="getProductImage(item.hinhAnh) || '/placeholder-product.png'"
              :alt="item.tenSanPham"
              class="w-full h-48 object-contain rounded-t-lg"
            />
          </router-link>
        </template>
        <template #title>
          <router-link :to="{ name: 'shop-product-detail', params: { id: item.sanPhamId } }" class="hover:underline">
            {{ item.tenSanPham || 'Sản phẩm không tên' }}
          </router-link>
        </template>
        <template #subtitle>
          <p class="text-sm text-surface-600">
            Thương hiệu: {{ item.sanPham?.thuongHieu?.moTaThuongHieu || 'Chưa có thương hiệu' }}
          </p>
          <p class="text-sm text-surface-500">
            Ngày thêm: {{ formatDate(item.ngayThem) }}
          </p>
        </template>
                <template #content>
          <div v-if="item.availableVariants?.length" class="mt-2">
            <p class="text-lg font-bold text-red-600">
              {{ formatCurrency(getMinPrice(item.availableVariants)) }}
              <span v-if="getMinPrice(item.availableVariants) !== getMaxPrice(item.availableVariants)">
                - {{ formatCurrency(getMaxPrice(item.availableVariants)) }}
              </span>
            </p>
          </div>
          <p v-else class="text-surface-500">Chưa có giá</p>
        </template>
        <template #footer>
          <div class="flex gap-2 mt-4">
            <Button
              label="Xem chi tiết"
              icon="pi pi-eye"
              class="p-button-sm"
              @click="goToProductDetail(item.sanPhamId)"
            />
            <Button
              icon="pi pi-trash"
              severity="danger"
              outlined
              class="p-button-sm"
              @click="removeProduct(item.sanPhamId)"
            />
          </div>
        </template>
      </Card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useConfirm } from 'primevue/useconfirm';
import FavoriteService from '@/apis/favorite';
import storageApi from '@/apis/storage';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';

const router = useRouter();
const toast = useToast();
const confirm = useConfirm();

const wishlist = ref([]);
const loading = ref(true);
const error = ref(null);
const imageUrlCache = ref(new Map());

const loadWishlist = async () => {
  loading.value = true;
  error.value = null;
  try {
    wishlist.value = await FavoriteService.getWishlist();
  } catch (err) {
    error.value = err.message || 'Không thể tải danh sách yêu thích.';
    toast.add({ severity: 'error', summary: 'Lỗi', detail: error.value, life: 3000 });
  } finally {
    loading.value = false;
  }
};

const getProductImage = (imageFilename) => {
  if (!imageFilename) return '/placeholder-product.png'; // Return placeholder immediately
  if (imageFilename.startsWith('http')) return imageFilename;
  if (imageUrlCache.value.has(imageFilename)) {
    return imageUrlCache.value.get(imageFilename);
  }
  loadProductImageUrl(imageFilename);
  return '/placeholder-product.png'; // Return placeholder while loading
};

const loadProductImageUrl = async (imageFilename) => {
  try {
    const presignedUrl = await storageApi.getPresignedUrl('products', imageFilename);
    imageUrlCache.value.set(imageFilename, presignedUrl);
    imageUrlCache.value = new Map(imageUrlCache.value); // Force reactivity
  } catch (error) {
    console.warn('Error getting presigned URL for product image:', imageFilename, error);
    imageUrlCache.value.set(imageFilename, '/placeholder-product.png'); // Set to placeholder on error
  }
};

const formatCurrency = (amount) => {
  if (!amount && amount !== 0) return 'Chưa có giá';
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(amount);
};

const getMinPrice = (variants) => {
  if (!variants?.length) return 0;
  const validPrices = variants
    .map(variant => variant.giaKhuyenMai ?? variant.giaBan)
    .filter(price => typeof price === 'number' && !isNaN(price));
  return validPrices.length ? Math.min(...validPrices) : 0;
};

const getMaxPrice = (variants) => {
  if (!variants?.length) return 0;
  const validPrices = variants
    .map(variant => variant.giaKhuyenMai ?? variant.giaBan)
    .filter(price => typeof price === 'number' && !isNaN(price));
  return validPrices.length ? Math.max(...validPrices) : 0;
};

const formatDate = (dateString) => {
  if (!dateString) return '';
  return format(new Date(dateString), 'dd/MM/yyyy', { locale: vi });
};

const goToProductDetail = (productId) => {
  router.push({ name: 'shop-product-detail', params: { id: productId } });
};

const confirmRemove = (sanPhamId) => {
  confirm.require({
    message: 'Bạn có chắc chắn muốn xóa sản phẩm này khỏi danh sách yêu thích?',
    header: 'Xác nhận xóa',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      await removeProduct(sanPhamId);
    },
    reject: () => {
      toast.add({ severity: 'info', summary: 'Hủy bỏ', detail: 'Đã hủy xóa sản phẩm.', life: 3000 });
    }
  });
};

const removeProduct = async (sanPhamId) => {
  try {
    await FavoriteService.removeProductFromWishlist(sanPhamId);
    toast.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xóa sản phẩm khỏi danh sách yêu thích.', life: 3000 });
    await loadWishlist(); // Reload the list
  } catch (err) {
    toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể xóa sản phẩm khỏi danh sách yêu thích.', life: 3000 });
  }
};

onMounted(() => {
  loadWishlist();
});
</script>

<style scoped>
.container {
  max-width: 1200px;
}
img {
  transition: all 0.3s ease;
}
img:hover {
  transform: scale(1.02);
}
.p-card {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.p-card-body {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
}
.p-card-content {
  flex-grow: 1;
}
.p-card-footer {
  margin-top: auto; /* Pushes footer to the bottom */
}
</style>
