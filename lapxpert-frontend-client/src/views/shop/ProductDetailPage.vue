<template>
  <Fluid />
  <Toast />

  <!-- Loading State -->
  <div v-if="loading" class="text-center py-16">
    <ProgressSpinner />
    <p class="mt-4 text-surface-600 text-lg">Đang tải thông tin sản phẩm...</p>
  </div>

  <!-- Error State -->
  <div v-else-if="error" class="text-center py-16">
    <i class="pi pi-exclamation-triangle text-5xl text-red-500 mb-4 block"></i>
    <p class="text-surface-600 text-lg">{{ error }}</p>
    <Button
      label="Thử lại"
      icon="pi pi-refresh"
      @click="refreshData"
      class="mt-4"
    />
  </div>

  <!-- Product Content -->
  <div v-else-if="product" class="container mx-auto px-4 py-8">
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-8">
      <!-- Product Images -->
      <div class="space-y-4">
        <div v-if="product.hinhAnh?.length" class="relative">
          <img
            :src="getProductImage(selectedImage || product.hinhAnh[0]) || '/placeholder-product.png'"
            :alt="product.tenSanPham"
            class="w-full h-96 object-contain rounded-lg border border-surface-200"
          />
        </div>
        <div v-if="product.hinhAnh?.length > 1" class="flex gap-2 overflow-x-auto">
          <img
            v-for="(image, index) in product.hinhAnh"
            :key="index"
            :src="getProductImage(image) || '/placeholder-product.png'"
            :alt="`${product.tenSanPham} - ${index + 1}`"
            class="w-20 h-20 object-cover rounded-lg border border-surface-200 cursor-pointer hover:border-primary-500"
            :class="{ 'border-primary-500': selectedImage === image }"
            @click="selectedImage = image"
          />
        </div>
      </div>

      <!-- Product Details -->
      <div class="space-y-6">
        <!-- Product Name and Brand -->
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-3xl font-bold text-surface-900">{{ product.tenSanPham || 'Sản phẩm không tên' }}</h1>
            <p class="text-lg text-surface-600">
              Thương hiệu: {{ product.thuongHieu?.moTaThuongHieu || 'Chưa có thương hiệu' }}
            </p>
            <p class="text-sm text-surface-500">
              Mã sản phẩm: {{ product.maSanPham || 'Chưa có mã' }}
            </p>
          </div>
          <!-- Favorite Button -->
          <Button
            v-if="AuthService.isAuthenticated()"
            :icon="isFavorited ? 'pi pi-heart-fill' : 'pi pi-heart'"
            :severity="isFavorited ? 'danger' : 'secondary'"
            rounded
            outlined
            @click="toggleFavorite"
            class="ml-4"
            v-tooltip.top="isFavorited ? 'Xóa khỏi yêu thích' : 'Thêm vào yêu thích'"
          />
        </div>

        <!-- Categories -->
        <div>
          <span class="text-sm font-medium text-surface-600">Danh mục: </span>
          <span v-if="product.danhMucs?.length" class="flex flex-wrap gap-2">
            <Badge
              v-for="danhMuc in product.danhMucs"
              :key="danhMuc.id"
              :value="danhMuc.moTaDanhMuc || 'Chưa có mô tả'"
              severity="info"
              outlined
            />
          </span>
          <span v-else class="text-surface-500">Chưa phân loại</span>
        </div>

        <!-- Variant Selection -->
        <div>
          <label class="text-sm font-medium text-surface-600">Chọn phiên bản:</label>
          <Select
            v-model="selectedVariant"
            :options="product.sanPhamChiTiets || []"
            optionLabel="sku"
            placeholder="Chọn biến thể"
            class="w-full mt-2"
            :disabled="!product.sanPhamChiTiets?.length"
          />
          <p v-if="!product.sanPhamChiTiets?.length" class="text-sm text-surface-500 mt-2">
            Không có biến thể nào
          </p>
        </div>

        <!-- Price -->
        <div v-if="selectedVariant" class="space-y-2">
          <label class="text-sm font-medium text-surface-600">Giá bán:</label>
          <div v-if="hasActiveDiscount(selectedVariant)" class="flex items-center gap-4">
            <span class="text-2xl font-bold text-red-600">
              {{ formatCurrency(selectedVariant.giaKhuyenMai) }}
            </span>
            <span class="text-lg text-surface-500 line-through">
              {{ formatCurrency(selectedVariant.giaBan) }}
            </span>
          </div>
          <span v-else class="text-2xl font-bold text-surface-900">
            {{ formatCurrency(selectedVariant.giaBan) }}
          </span>
        </div>

        <!-- Technical Specifications -->
        <div v-if="selectedVariant">
          <h3 class="text-lg font-semibold text-surface-900">Thông số kỹ thuật</h3>
          <div class="space-y-2">
            <div class="flex justify-between">
              <span class="text-sm font-medium text-surface-600">CPU:</span>
              <span>{{ selectedVariant.cpu?.moTaCpu || 'Chưa có thông tin' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm font-medium text-surface-600">RAM:</span>
              <span>{{ selectedVariant.ram?.moTaRam || 'Chưa có thông tin' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm font-medium text-surface-600">Ổ cứng:</span>
              <span>{{ getStorageDescription(selectedVariant) || 'Chưa có thông tin' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm font-medium text-surface-600">Card đồ họa:</span>
              <span>{{ selectedVariant.gpu?.moTaGpu || 'Chưa có thông tin' }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm font-medium text-surface-600">Màn hình:</span>
              <span>{{ selectedVariant.manHinh?.moTaManHinh || 'Chưa có thông tin' }}</span>
            </div>
          </div>
        </div>

        <!-- Description -->
        <div>
          <h3 class="text-lg font-semibold text-surface-900">Mô tả sản phẩm</h3>
          <p class="text-surface-700">{{ product.moTa || 'Chưa có mô tả' }}</p>
        </div>

        <!-- Quantity Input -->
        <div v-if="selectedVariant">
          <label for="quantity" class="text-sm font-medium text-surface-600">Số lượng:</label>
          <InputNumber v-model="quantity" inputId="quantity" :min="1" :max="availableInventory" showButtons buttonLayout="horizontal" class="mt-2" />
          <p v-if="availableInventory > 0" class="text-sm text-surface-500 mt-1">Có sẵn: {{ availableInventory }} sản phẩm</p>
          <p v-else class="text-sm text-red-500 mt-1">Sản phẩm này đã hết hàng</p>
        </div>

        <!-- Call to Action -->
        <div class="flex gap-4">
          <Button
            label="Thêm vào giỏ hàng"
            icon="pi pi-shopping-cart"
            class="w-full"
            :disabled="!selectedVariant || availableInventory === 0"
            @click="addToCart"
          />
          <Button
            label="Mua ngay"
            icon="pi pi-check"
            severity="success"
            class="w-full"
            :disabled="!selectedVariant || availableInventory === 0"
            @click="buyNow"
          />
        </div>
      </div>
    </div>

    <!-- Related Products -->
    <div v-if="relatedProducts?.length" class="mt-12">
      <h2 class="text-2xl font-semibold text-surface-900 mb-4">Sản phẩm liên quan</h2>
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div
          v-for="relatedProduct in relatedProducts"
          :key="relatedProduct.id"
          class="card border border-surface-200 rounded-lg p-4 hover:shadow-lg transition-shadow"
        >
          <router-link
            :to="{ name: 'shop-product-detail', params: { id: relatedProduct.id } }"
            class="block"
          >
            <img
              :src="getProductImage(relatedProduct.hinhAnh?.[0]) || '/placeholder-product.png'"
              :alt="relatedProduct.tenSanPham"
              class="w-full h-48 object-cover rounded-lg mb-4"
            />
            <h3 class="text-lg font-semibold text-surface-900 truncate">
              {{ relatedProduct.tenSanPham || 'Sản phẩm không tên' }}
            </h3>
            <p class="text-sm text-surface-600">
              {{ relatedProduct.thuongHieu?.moTaThuongHieu || 'Chưa có thương hiệu' }}
            </p>
            <div v-if="getProductPrice(relatedProduct)" class="mt-2">
              <p v-if="hasActiveDiscount(getProductPrice(relatedProduct))" class="flex items-center gap-2">
                <span class="text-red-600 font-bold">
                  {{ formatCurrency(getProductPrice(relatedProduct).giaKhuyenMai) }}
                </span>
                <span class="text-sm text-surface-500 line-through">
                  {{ formatCurrency(getProductPrice(relatedProduct).giaBan) }}
                </span>
              </p>
              <p v-else class="text-red-600 font-bold">
                {{ formatCurrency(getProductPrice(relatedProduct).giaBan) }}
              </p>
            </div>
            <p v-else class="text-surface-500">Chưa có giá</p>
          </router-link>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useProductStore } from '@/stores/productstore'
import { useCartStore } from '@/stores/cartStore'
import storageApi from '@/apis/storage'
import inventoryApi from '@/apis/inventoryApi';
import AuthService from '@/apis/auth'; 
import FavoriteService from '@/apis/favorite';
import ReviewDialog from '@/components/common/ReviewDialog.vue'; 

const route = useRoute()
const router = useRouter()
const toast = useToast()
const productStore = useProductStore()
const cartStore = useCartStore()

// Component state
const product = ref(null)
const loading = ref(false)
const error = ref(null)
const selectedVariant = ref(null)
const selectedImage = ref(null)
const imageUrlCache = ref(new Map())
const quantity = ref(1)
const availableInventory = ref(0);
const isFavorited = ref(false);



// Watch for route param changes to reload product
watch(() => route.params.id, async (newId, oldId) => {
  if (newId && newId !== oldId) {
    await loadProduct();
  }
});

// Computed property for related products
const relatedProducts = computed(() => {
  if (!product.value?.danhMucs?.length) return []
  const currentCategoryIds = product.value.danhMucs.map(dm => dm.id)
  return productStore.products
    .filter(p => p.id !== product.value.id && // Exclude the current product
                p.danhMucs?.some(dm => currentCategoryIds.includes(dm.id)))
    .slice(0, 8) // Limit to 8 products
})

// New: Check favorite status
const checkFavoriteStatus = async () => {
  if (AuthService.isAuthenticated() && product.value?.id) {
    try {
      isFavorited.value = await FavoriteService.checkProductInWishlist(product.value.id);
    } catch (err) {
      console.error("Error checking favorite status:", err);
      isFavorited.value = false;
    }
  } else {
    isFavorited.value = false;
  }
};

// New: Toggle favorite status
const toggleFavorite = async () => {
  if (!AuthService.isAuthenticated()) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng đăng nhập để thêm sản phẩm vào danh sách yêu thích.', life: 3000 });
    router.push('/login');
    return;
  }

  const user = AuthService.getUser();
  if (!user || !user.id) {
    toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể lấy thông tin người dùng. Vui lòng đăng nhập lại.', life: 3000 });
    return;
  }

  try {
    if (isFavorited.value) {
      await FavoriteService.removeProductFromWishlist(product.value.id);
      toast.add({ severity: 'success', summary: 'Thành công', detail: 'Đã xóa sản phẩm khỏi danh sách yêu thích.', life: 3000 });
    } else {
      await FavoriteService.addProductToWishlist({
        sanPhamId: product.value.id,
        nguoiDungId: user.id,
      });
      toast.add({ severity: 'success', summary: 'Thành công', detail: 'Đã thêm sản phẩm vào danh sách yêu thích.', life: 3000 });
    }
    isFavorited.value = !isFavorited.value; // Toggle status after successful operation
  } catch (err) {
    console.error("Error toggling favorite:", err);
    toast.add({ severity: 'error', summary: 'Lỗi', detail: 'Không thể cập nhật danh sách yêu thích. Vui lòng thử lại.', life: 3000 });
  }
};


// Methods
const formatCurrency = (amount) => {
  if (!amount && amount !== 0) return 'Chưa có giá'
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(amount)
}

const hasActiveDiscount = (variant) => {
  return variant?.giaKhuyenMai && variant.giaKhuyenMai < variant.giaBan
}

const getStorageDescription = (variant) => {
  return (
    variant?.boNho?.moTaBoNho ||
    variant?.bonho?.moTaBoNho ||
    variant?.oCung?.moTaOCung ||
    variant?.ocung?.moTaOCung
  )
}

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
    imageUrlCache.value = new Map(imageUrlCache.value);
  } catch (error) {
    console.warn('Error getting presigned URL for product image:', imageFilename, error);
    imageUrlCache.value.set(imageFilename, '/placeholder-product.png'); // Set to placeholder on error
  }
};

const getProductPrice = (product) => {
  if (!product?.sanPhamChiTiets?.length) return null
  // Use the first variant's price
  return product.sanPhamChiTiets[0]
}

const loadProduct = async () => {
  loading.value = true
  error.value = null
  try {
    const productId = route.params.id
    if (!productId) {
      throw new Error('ID sản phẩm không hợp lệ')
    }
    product.value = productStore.getCachedProduct(productId)
    if (!product.value) {
      await productStore.fetchProducts()
      product.value = productStore.products.find(p => p.id == productId)
    }
    if (!product.value) {
      throw new Error('Không tìm thấy sản phẩm')
    }
    if (product.value.sanPhamChiTiets?.length > 0) {
      selectedVariant.value = product.value.sanPhamChiTiets[0]
      if (product.value.hinhAnh?.length > 0) {
        selectedImage.value = product.value.hinhAnh[0]
      }
    }
    console.log('Loaded product:', product.value)
    await checkFavoriteStatus(); // Call checkFavoriteStatus here
  } catch (err) {
    error.value = err.message || 'Lỗi tải dữ liệu sản phẩm'
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: error.value,
      life: 3000
    })
  } finally {
    loading.value = false
  }
}

const fetchAvailableInventory = async (variantId) => {
  if (!variantId) {
    availableInventory.value = 0;
    return;
  }
  try {
    const response = await inventoryApi.getAvailableQuantity(variantId);
    if (response.success) {
      availableInventory.value = response.data.availableQuantity || 0;
    } else {
      availableInventory.value = 0;
    }
  } catch (err) {
    console.error('Error fetching inventory:', err);
    availableInventory.value = 0;
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tải số lượng tồn kho cho phiên bản này.',
      life: 3000
    });
  }
};

watch(selectedVariant, (newVariant) => {
  if (newVariant) {
    fetchAvailableInventory(newVariant.id);
  } else {
    availableInventory.value = 0;
  }
}, { immediate: true });

const refreshData = async () => {
  const productId = route.params.id
  if (productId) {
    try {
      product.value = await productStore.forceRefreshProduct(productId)
      if (product.value.sanPhamChiTiets?.length > 0 && !selectedVariant.value) {
        selectedVariant.value = product.value.sanPhamChiTiets[0]
      }
      if (product.value.hinhAnh?.length > 0 && !selectedImage.value) {
        selectedImage.value = product.value.hinhAnh[0]
      }
      console.log('Refreshed product:', product.value)
      toast.add({
        severity: 'success',
        summary: 'Thành công',
        detail: 'Đã làm mới dữ liệu',
        life: 2000
      })
    } catch (error) {
      console.error('Error refreshing product:', error)
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Lỗi làm mới dữ liệu',
        life: 3000
      })
    }
  }
}

const addToCart = () => {
  if (!selectedVariant.value) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng chọn một phiên bản sản phẩm.', life: 3000 });
    return;
  }
  if (quantity.value > availableInventory.value) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: `Số lượng tồn kho không đủ. Chỉ còn ${availableInventory.value} sản phẩm.`, life: 3000 });
    return;
  }

  const itemToAdd = {
    productId: product.value.id,
    variantId: selectedVariant.value.id,
    name: product.value.tenSanPham + ' - ' + selectedVariant.value.sku,
    price: selectedVariant.value.giaKhuyenMai || selectedVariant.value.giaBan,
    image: getProductImage(product.value.hinhAnh?.[0]),
    quantity: quantity.value,
  };

  cartStore.addItem(itemToAdd);
  toast.add({ severity: 'success', summary: 'Thành công', detail: `Đã thêm ${quantity.value} sản phẩm vào giỏ hàng.`, life: 3000 });
};

const buyNow = () => {
  if (!selectedVariant.value) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng chọn một phiên bản sản phẩm.', life: 3000 });
    return;
  }
  if (quantity.value > availableInventory.value) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: `Số lượng tồn kho không đủ. Chỉ còn ${availableInventory.value} sản phẩm.`, life: 3000 });
    return;
  }

  router.push({
    name: 'shop-checkout',
    query: {
      productId: product.value.id,
      variantId: selectedVariant.value.id,
      quantity: quantity.value,
    }
  });
};

const goBack = () => {
  router.push({ name: 'products' })
}

const editProduct = () => {
  router.push({ name: 'product-edit', params: { id: product.value.id } })
}

// Lifecycle
onMounted(async () => {
  await loadProduct()
})
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

.card {
  transition: box-shadow 0.3s ease;
}

.card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

button.p-button {
  border-radius: 8px;
}

@media (max-width: 768px) {
  .container {
    padding: 1rem;
  }

  img.h-96 {
    height: 60vw;
  }

  .card {
    padding: 1rem;
  }
}
</style>
