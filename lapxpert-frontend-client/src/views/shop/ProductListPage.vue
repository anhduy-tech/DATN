<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useProductStore } from '@/stores/productstore';
import { useAttributeStore } from '@/stores/attributestore';
import { useRouter, useRoute } from 'vue-router';
import storageApi from '@/apis/storage';
import inventoryApi from '@/apis/inventoryApi';
import serialNumberApi from '@/apis/serialNumberApi';
import Card from 'primevue/card';
import InputText from 'primevue/inputtext';
import MultiSelect from 'primevue/multiselect';
import Dropdown from 'primevue/dropdown';
import Button from 'primevue/button';
import InputGroup from 'primevue/inputgroup';
import Slider from 'primevue/slider';
import DataView from 'primevue/dataview';
import SelectButton from 'primevue/selectbutton';
import Badge from 'primevue/badge';
import Tag from 'primevue/tag';

// Initialize stores and router
const productStore = useProductStore();
const attributeStore = useAttributeStore();
const router = useRouter();
const route = useRoute(); // Added to access query parameters

// Reactive state
const filters = ref({
  searchQuery: '',
  categories: [],
  brand: null,
  priceRange: [0, 100000000] // Default price range (0 to 100M VND)
});
const layout = ref('grid');
const layoutOptions = ref(['list', 'grid']);
const imageUrlCache = ref(new Map());
const inventoryData = ref(new Map());
const loading = ref(true);

// Computed properties
const categories = computed(() => attributeStore.danhMuc);
const brands = computed(() => [
  { id: null, moTaThuongHieu: 'Tất cả hãng' },
  ...attributeStore.thuongHieu
]);

const dynamicPricing = computed(() => {
  const prices = productStore.activeProducts
    .filter(product => product.sanPhamChiTiets?.length)
    .flatMap(product => product.sanPhamChiTiets.map(variant => variant.giaKhuyenMai ?? variant.giaBan))
    .filter(price => typeof price === 'number' && !isNaN(price));

  const minPrice = prices.length ? Math.min(...prices) : 0;
  const maxPrice = prices.length ? Math.max(...prices) : 100000000;
  const priceStep = Math.max(100000, Math.round((maxPrice - minPrice) / 100));

  return { minPrice, maxPrice, priceStep };
});

const products = computed(() => {
  let filteredProducts = productStore.activeProducts;

  // Filter by available inventory (> 0, excluding reserved)
  filteredProducts = filteredProducts.filter(product =>
    getAvailableInventory(product.sanPhamChiTiets) > 0
  );

  // Filter by search query
  if (filters.value.searchQuery) {
    const query = filters.value.searchQuery.toLowerCase();
    filteredProducts = filteredProducts.filter(product =>
      product.tenSanPham?.toLowerCase().includes(query) ||
      product.maSanPham?.toLowerCase().includes(query) ||
      product.sanPhamChiTiets?.some(variant =>
        variant.sku?.toLowerCase().includes(query)
      )
    );
  }

  // Filter by multiple categories
  if (filters.value.categories.length) {
    filteredProducts = filteredProducts.filter(product =>
      product.danhMucs?.some(danhMuc => filters.value.categories.includes(danhMuc.id))
    );
  }

  // Filter by brand
  if (filters.value.brand) {
    filteredProducts = filteredProducts.filter(product =>
      product.thuongHieu?.id === filters.value.brand
    );
  }

  // Filter by price range
  filteredProducts = filteredProducts.filter(product =>
    product.sanPhamChiTiets?.some(variant => {
      const price = variant.giaKhuyenMai ?? variant.giaBan;
      return price >= filters.value.priceRange[0] && price <= filters.value.priceRange[1];
    })
  );

  return filteredProducts;
});

const hasActiveFilters = computed(() => {
  return (
    filters.value.searchQuery ||
    filters.value.categories.length ||
    filters.value.brand ||
    filters.value.priceRange[0] !== dynamicPricing.value.minPrice ||
    filters.value.priceRange[1] !== dynamicPricing.value.maxPrice
  );
});

// Methods
const formatCurrency = (value) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
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

const getProductImageUrl = (product) => {
  if (!product?.hinhAnh?.length) return 'https://via.placeholder.com/300';
  const firstImage = product.hinhAnh[0];
  if (!firstImage) return 'https://via.placeholder.com/300';
  if (firstImage.startsWith('http')) return firstImage;
  if (imageUrlCache.value.has(firstImage)) {
    return imageUrlCache.value.get(firstImage);
  }
  loadImageUrl(firstImage);
  return 'https://via.placeholder.com/300';
};

const loadImageUrl = async (imageFilename) => {
  try {
    const presignedUrl = await storageApi.getPresignedUrl('products', imageFilename);
    imageUrlCache.value.set(imageFilename, presignedUrl);
    imageUrlCache.value = new Map(imageUrlCache.value);
  } catch (error) {
    console.warn('Error getting presigned URL for image:', imageFilename, error);
    imageUrlCache.value.set(imageFilename, 'https://via.placeholder.com/300');
  }
};

const onImageError = (event) => {
  event.target.src = 'https://via.placeholder.com/300';
  event.target.style.display = 'block';
};

const getInventoryStatus = (product) => {
  return getAvailableInventory(product.sanPhamChiTiets) > 0 ? 'INSTOCK' : 'OUTOFSTOCK';
};

const getSeverity = (product) => {
  switch (getInventoryStatus(product)) {
    case 'INSTOCK':
      return 'success';
    case 'OUTOFSTOCK':
      return 'danger';
    default:
      return null;
  }
};

const getAvailableInventory = (variants) => {
  if (!variants?.length) return 0;
  return variants.reduce((total, variant) => {
    const inventory = inventoryData.value.get(variant.id);
    return total + (inventory?.available || 0);
  }, 0);
};

const fetchInventoryData = async () => {
  try {
    const variantIds = [];
    productStore.activeProducts.forEach(product => {
      if (product.sanPhamChiTiets) {
        product.sanPhamChiTiets.forEach(variant => {
          variantIds.push(variant.id);
        });
      }
    });

    if (variantIds.length === 0) return;

    const batchResult = await inventoryApi.getBatchAvailability(variantIds);

    if (batchResult.success) {
      const newInventoryData = new Map();
      const reservedPromises = variantIds.map(async (variantId) => {
        try {
          const serialNumbers = await serialNumberApi.getSerialNumbersByVariant(variantId);
          const reserved = serialNumbers.filter(serial => serial.trangThai === 'RESERVED').length;
          return { variantId, reserved };
        } catch (error) {
          console.warn(`Error fetching reserved count for variant ${variantId}:`, error);
          return { variantId, reserved: 0 };
        }
      });

      const reservedResults = await Promise.all(reservedPromises);
      const reservedMap = new Map();
      reservedResults.forEach(result => {
        reservedMap.set(result.variantId, result.reserved);
      });

      variantIds.forEach(variantId => {
        const available = batchResult.data[variantId] || 0;
        const reserved = reservedMap.get(variantId) || 0;
        newInventoryData.set(variantId, {
          available,
          reserved,
          total: available + reserved
        });
      });

      inventoryData.value = newInventoryData;
    }
  } catch (error) {
    console.error('Error fetching inventory data:', error);
  }
};

const goToProductDetail = (productId) => {
  router.push(`/shop/products/${productId}`);
};

const clearAllFilters = () => {
  filters.value = {
    searchQuery: '',
    categories: [],
    brand: null,
    priceRange: [dynamicPricing.value.minPrice, dynamicPricing.value.maxPrice]
  };
};

const fetchData = async () => {
  loading.value = true;
  await Promise.all([
    productStore.fetchActiveProducts(),
    attributeStore.fetchDanhMuc(),
    attributeStore.fetchThuongHieu(),
    fetchInventoryData()
  ]);

  // Check for category query parameter and pre-select it
  const categoryId = route.query.category;
  if (categoryId) {
    const categoryIdNum = Number(categoryId);
    // Verify the category exists to prevent invalid selections
    if (categories.value.some(cat => cat.id === categoryIdNum)) {
      filters.value.categories = [categoryIdNum];
    }
  }

  filters.value.priceRange = [dynamicPricing.value.minPrice, dynamicPricing.value.maxPrice];
  loading.value = false;
};

// Fetch data and handle category query parameter on mount
onMounted(fetchData);

// Watch for route changes to re-fetch data
watch(() => route.fullPath, fetchData);
</script>

<style>
/* Ensure equal height for grid cards */
.grid-card {
  min-height: 400px; /* Adjust based on design needs */
  display: flex;
  flex-direction: column;
}

/* Ensure equal height for list cards */
.list-card {
  min-height: 120px; /* Adjust based on design needs */
  display: flex;
  align-items: center;
}
</style>

<template>
  <div class="container mx-auto p-4">
    <div v-if="loading" class="flex justify-center items-center h-screen">
      <ProgressSpinner />
    </div>
    <div v-else class="grid grid-cols-1 md:grid-cols-4 gap-8">
      <!-- Filters Sidebar -->
      <div class="col-span-1">
        <Card>
          <template #title>
            <h2 class="text-xl font-bold">Bộ lọc</h2>
          </template>
          <template #content>
            <div class="mb-6 border p-4 rounded-lg">
              <!-- Search Input -->
              <div class="mb-4">
                <label class="block mb-2 font-medium">Tìm kiếm sản phẩm</label>
                <InputText
                  v-model="filters.searchQuery"
                  placeholder="Tìm theo tên sản phẩm, mã sản phẩm..."
                  fluid
                  class="w-full"
                />
              </div>

              <!-- Attribute Filters Grid -->
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <!-- Category Filter -->
                <div>
                  <label class="block mb-2 font-medium">Danh mục</label>
                  <InputGroup>
                    <MultiSelect
                      v-model="filters.categories"
                      :options="categories"
                      optionLabel="moTaDanhMuc"
                      optionValue="id"
                      placeholder="Chọn danh mục"
                      fluid
                    />
                    <Button
                      v-if="filters.categories.length"
                      icon="pi pi-times"
                      outlined
                      @click="filters.categories = []"
                    />
                  </InputGroup>
                </div>

                <!-- Brand Filter -->
                <div>
                  <label class="block mb-2 font-medium">Hãng</label>
                  <InputGroup>
                    <Dropdown
                      v-model="filters.brand"
                      :options="brands"
                      optionLabel="moTaThuongHieu"
                      optionValue="id"
                      placeholder="Chọn hãng"
                      fluid
                    />
                    <Button
                      v-if="filters.brand"
                      icon="pi pi-times"
                      outlined
                      @click="filters.brand = null"
                    />
                  </InputGroup>
                </div>

                <!-- Price Range Filter -->
                <div class="col-span-full">
                  <label class="block mb-2 font-medium">Khoảng giá</label>
                  <Slider
                    v-model="filters.priceRange"
                    range
                    :min="dynamicPricing.minPrice"
                    :max="dynamicPricing.maxPrice"
                    :step="dynamicPricing.priceStep"
                    class="w-full"
                  />
                  <div class="flex justify-between mt-2 text-sm">
                    <span>{{ formatCurrency(filters.priceRange[0]) }}</span>
                    <span>{{ formatCurrency(filters.priceRange[1]) }}</span>
                  </div>
                </div>

                <!-- Clear Filters Button -->
                <div class="col-span-full flex justify-end">
                  <Button
                    label="Xóa bộ lọc"
                    icon="pi pi-filter-slash"
                    outlined
                    @click="clearAllFilters"
                    :disabled="!hasActiveFilters"
                  />
                </div>
              </div>
            </div>
          </template>
        </Card>
      </div>

      <!-- Product List -->
      <div class="col-span-1 md:col-span-3">
        <div class="card">
          <DataView :value="products" :layout="layout">
            <template #header>
              <div class="flex justify-end">
                <SelectButton v-model="layout" :options="layoutOptions" :allowEmpty="false">
                  <template #option="{ option }">
                    <i :class="[option === 'list' ? 'pi pi-bars' : 'pi pi-table']" />
                  </template>
                </SelectButton>
              </div>
            </template>

            <template #list="slotProps">
              <div class="flex flex-col">
                <div v-for="(item, index) in slotProps.items" :key="index">
                  <div class="list-card flex flex-col sm:flex-row sm:items-center p-6 gap-4" :class="{ 'border-t border-surface-200 dark:border-surface-700': index !== 0 }">
                    <div class="md:w-40 relative">
                      <img class="block xl:block mx-auto rounded w-full h-32 object-contain" :src="getProductImageUrl(item) || 'https://via.placeholder.com/150'" :alt="item.tenSanPham" @error="onImageError" />
                      <div class="absolute bg-black/70 rounded-border" style="left: 4px; top: 4px">
                        <Tag :value="getInventoryStatus(item) === 'INSTOCK' ? 'Còn hàng' : 'Hết hàng'" :severity="getSeverity(item)"></Tag>
                      </div>
                    </div>
                    <div class="flex flex-col md:flex-row justify-between md:items-center flex-1 gap-6">
                      <div class="flex flex-row md:flex-col justify-between items-start gap-2 flex-1">
                        <div>
                          <span class="font-medium text-surface-500 dark:text-surface-400 text-sm">Thương hiệu: {{ item.thuongHieu?.moTaThuongHieu || 'N/A' }}</span>
                          <div class="text-lg font-medium mt-2 line-clamp-2">{{ item.tenSanPham }}</div>
                          <div class="flex flex-wrap gap-1 mt-1">
                            <Badge v-for="danhMuc in item.danhMucs" :key="danhMuc.id" :value="danhMuc.moTaDanhMuc || danhMuc.tenDanhMuc" severity="info" outlined />
                          </div>
                        </div>
                        <div class="bg-surface-100 p-1 rounded-full">
                          <div class="bg-surface-0 flex items-center gap-2 justify-center py-1 px-2 rounded-full shadow-sm">
                            <span class="text-surface-900 font-medium text-sm">5.0</span>
                            <i class="pi pi-star-fill text-yellow-500"></i>
                          </div>
                        </div>
                      </div>
                      <div class="flex flex-col md:items-end gap-8">
                        <span class="text-xl font-semibold">
                          <span v-if="getMinPrice(item.sanPhamChiTiets) === getMaxPrice(item.sanPhamChiTiets)">
                            {{ formatCurrency(getMinPrice(item.sanPhamChiTiets)) }}
                          </span>
                          <span v-else>
                            {{ formatCurrency(getMinPrice(item.sanPhamChiTiets)) }} - {{ formatCurrency(getMaxPrice(item.sanPhamChiTiets)) }}
                          </span>
                        </span>
                        <div class="flex flex-row-reverse md:flex-row gap-2">
                          <Button icon="pi pi-heart" outlined></Button>
                          <Button icon="pi pi-shopping-cart" label="Xem chi tiết" :disabled="getInventoryStatus(item) === 'OUTOFSTOCK'" class="flex-auto md:flex-initial whitespace-nowrap" @click="goToProductDetail(item.id)"></Button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>

            <template #grid="slotProps">
              <div class="grid grid-cols-12 gap-4">
                <div v-for="(item, index) in slotProps.items" :key="index" class="col-span-12 sm:col-span-6 md:col-span-4 xl:col-span-6 p-2">
                  <div class="grid-card p-6 border border-surface-200 dark:border-surface-700 bg-surface-0 dark:bg-surface-900 rounded flex flex-col">
                    <div class="bg-surface-50 flex justify-center rounded p-4 h-48">
                      <div class="relative mx-auto w-full h-full">
                        <img class="rounded w-full h-full object-contain" :src="getProductImageUrl(item) || 'https://via.placeholder.com/300'" :alt="item.tenSanPham" @error="onImageError" />
                        <div class="absolute bg-black/70 rounded-border" style="left: 4px; top: 4px">
                          <Tag :value="getInventoryStatus(item) === 'INSTOCK' ? 'Còn hàng' : 'Hết hàng'" :severity="getSeverity(item)"></Tag>
                        </div>
                      </div>
                    </div>
                    <div class="pt-6 flex flex-col flex-1">
                      <div class="flex flex-row justify-between items-start gap-2">
                        <div class="flex-1">
                          <span class="font-medium text-surface-500 dark:text-surface-400 text-sm">Thương hiệu: {{ item.thuongHieu?.moTaThuongHieu || 'N/A' }}</span>
                          <div class="text-lg font-medium mt-1 line-clamp-2">{{ item.tenSanPham }}</div>
                          <div class="flex flex-wrap gap-1 mt-1">
                            <Badge v-for="danhMuc in item.danhMucs" :key="danhMuc.id" :value="danhMuc.moTaDanhMuc || danhMuc.tenDanhMuc" severity="info" outlined />
                          </div>
                        </div>
                        <div class="bg-surface-100 p-1 rounded-full">
                          <div class="bg-surface-0 flex items-center gap-2 justify-center py-1 px-2 rounded-full shadow-sm">
                            <span class="text-surface-900 font-medium text-sm">5.0</span>
                            <i class="pi pi-star-fill text-yellow-500"></i>
                          </div>
                        </div>
                      </div>
                      <div class="flex flex-col gap-6 mt-6 flex-1 justify-end">
                        <span class="text-2xl font-semibold">
                          <span v-if="getMinPrice(item.sanPhamChiTiets) === getMaxPrice(item.sanPhamChiTiets)">
                            {{ formatCurrency(getMinPrice(item.sanPhamChiTiets)) }}
                          </span>
                          <span v-else>
                            {{ formatCurrency(getMinPrice(item.sanPhamChiTiets)) }} - {{ formatCurrency(getMaxPrice(item.sanPhamChiTiets)) }}
                          </span>
                        </span>
                        <div class="flex gap-2">
                          <Button icon="pi pi-shopping-cart" label="Xem chi tiết" :disabled="getInventoryStatus(item) === 'OUTOFSTOCK'" class="flex-auto whitespace-nowrap" @click="goToProductDetail(item.id)"></Button>
                          <Button icon="pi pi-heart" outlined></Button>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </DataView>
        </div>
      </div>
    </div>
  </div>
</template>
