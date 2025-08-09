<script setup>
import { ref, onMounted, computed } from 'vue';
import { useProductStore } from '@/stores/productstore';
import { useAttributeStore } from '@/stores/attributestore';
import { useVoucherStore } from '@/stores/voucherStore';
import { useRouter } from 'vue-router';
import storageApi from '@/apis/storage';
import inventoryApi from '@/apis/inventoryApi';
import serialNumberApi from '@/apis/serialNumberApi';
import Carousel from 'primevue/carousel';

const productStore = useProductStore();
const attributeStore = useAttributeStore();
const voucherStore = useVoucherStore();
const router = useRouter();

const imageUrlCache = ref(new Map());
const inventoryData = ref(new Map()); // Cache for inventory data by variant ID
const loading = ref(true);

const featuredProducts = computed(() => {
  console.log('HomePage: featuredProducts computed property accessed.');
  console.log('HomePage: productStore.activeProducts for featuredProducts:', productStore.activeProducts);
  // Filter products with available inventory (> 0, excluding reserved)
  const availableProducts = productStore.activeProducts.filter(product => {
    const hasVariants = product.sanPhamChiTiets && product.sanPhamChiTiets.length > 0;
    const availableCount = hasVariants ? getAvailableInventory(product.sanPhamChiTiets) : 0;
    console.log(`HomePage: Product ${product.id} (${product.tenSanPham}) - hasVariants: ${hasVariants}, availableCount: ${availableCount}`);
    return hasVariants && availableCount > 0;
  });
  console.log('HomePage: availableProducts after filtering:', availableProducts);
  // Take the first 8 available products as featured
  return availableProducts.slice(0, 8);
});

const categories = computed(() => {
  const allCategories = attributeStore.danhMuc;
  // Add an "All Products" category at the beginning
  return [{
    id: 'all',
    tenDanhMuc: 'Tất cả sản phẩm',
    icon: 'th-large' // A generic icon for all products
  }, ...allCategories];
});

const latestVouchers = computed(() => {
  // Filter out vouchers that have ended or are not yet active
  const activeAndFutureVouchers = voucherStore.vouchers.filter(v => {
    const endDate = new Date(v.ngayKetThuc);
    const startDate = new Date(v.ngayBatDau);
    const now = new Date();
    return v.trangThai === 'DA_DIEN_RA' || (v.trangThai === 'CHUA_DIEN_RA' && startDate > now);
  });
  // Sort by start date descending and take the top 3
  return activeAndFutureVouchers.sort((a, b) => new Date(b.ngayBatDau) - new Date(a.ngayBatDau)).slice(0, 3);
});

const formatCurrency = (value) => {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

const formatDiscountValue = (voucher) => {
  if (!voucher) return '';
  if (voucher.loaiGiamGia === 'PHAN_TRAM') {
    return `${voucher.giaTriGiam} %`;
  } else {
    return `${voucher.giaTriGiam} VND`;
  }
};

const getVoucherStatusSeverity = (status) => {
  switch (status) {
    case 'DA_DIEN_RA': return 'success';
    case 'CHUA_DIEN_RA': return 'info';
    case 'KET_THUC': return 'danger';
    default: return 'secondary';
  }
};

const getVoucherStatusText = (status) => {
  switch (status) {
    case 'DA_DIEN_RA': return 'Đang diễn ra';
    case 'CHUA_DIEN_RA': return 'Sắp diễn ra';
    case 'KET_THUC': return 'Đã kết thúc';
    default: return 'Không xác định';
  }
};

const getAvailableInventory = (variants) => {
  if (!variants?.length) return 0;
  return variants.reduce((total, variant) => {
    const inventory = inventoryData.value.get(variant.id);
    console.log(`HomePage: getAvailableInventory - Variant ID: ${variant.id}, Inventory:`, inventory);
    return total + (inventory?.available || 0);
  }, 0);
};

const fetchInventoryData = async () => {
  console.log('HomePage: fetchInventoryData called');
  try {
    // Get all variant IDs from current products
    const variantIds = [];
    productStore.activeProducts.forEach(product => {
      if (product.sanPhamChiTiets) {
        product.sanPhamChiTiets.forEach(variant => {
          variantIds.push(variant.id);
        });
      }
    });

    if (variantIds.length === 0) {
      console.log('HomePage: No variant IDs to fetch inventory for.');
      return;
    }

    // Use batch availability API for better performance
    const batchResult = await inventoryApi.getBatchAvailability(variantIds);

    if (batchResult.success) {
      // Update inventory cache with available quantities
      const newInventoryData = new Map();

      // For each variant, get available quantity and fetch reserved count separately
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

      // Combine available and reserved data
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
      console.log('HomePage: inventoryData after fetch:', inventoryData.value);
    }
  } catch (error) {
    console.error('Error fetching inventory data:', error);
  }
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

const goToProductDetail = (productId) => {
  console.log('HomePage: Navigating to product detail:', productId);
  router.push(`/shop/products/${productId}`);
};

const goToCategoryProducts = (categoryId) => {
  console.log('HomePage: Navigating to category products:', categoryId);
  if (categoryId === 'all') {
    router.push({ name: 'shop-products' });
  } else {
    router.push({ name: 'shop-products', query: { category: categoryId } });
  }
};

const getMinPrice = (variants) => {
  if (!variants?.length) return 0;
  const validPrices = variants
    .map(variant => {
      const regularPrice = variant.giaBan;
      const promoPrice = variant.giaKhuyenMai;
      const price = (promoPrice !== null && promoPrice !== undefined && !isNaN(promoPrice) && promoPrice < regularPrice) ? promoPrice : regularPrice;
      return typeof price === 'number' && !isNaN(price) ? price : null;
    })
    .filter(price => price !== null);
  return validPrices.length ? Math.min(...validPrices) : 0;
};

const getMaxPrice = (variants) => {
  if (!variants?.length) return 0;
  const validPrices = variants
    .map(variant => {
      const regularPrice = variant.giaBan;
      const promoPrice = variant.giaKhuyenMai;
      const price = (promoPrice !== null && promoPrice !== undefined && !isNaN(promoPrice) && promoPrice < regularPrice) ? promoPrice : regularPrice;
      return typeof price === 'number' && !isNaN(price) ? price : null;
    })
    .filter(price => price !== null);
  return validPrices.length ? Math.max(...validPrices) : 0;
};

const responsiveOptions = ref([
  {
    breakpoint: '1400px',
    numVisible: 4,
    numScroll: 1
  },
  {
    breakpoint: '1199px',
    numVisible: 3,
    numScroll: 1
  },
  {
    breakpoint: '767px',
    numVisible: 2,
    numScroll: 1
  },
  {
    breakpoint: '575px',
    numVisible: 1,
    numScroll: 1
  }
]);

onMounted(async () => {
  await productStore.fetchActiveProducts(); // Ensure products are fetched first
  await Promise.all([
    attributeStore.fetchDanhMuc(),
    voucherStore.fetchVouchers(),
    fetchInventoryData() // Now fetch inventory data
  ]);
  loading.value = false;
});
</script>

<template>
  <div class="container mx-auto p-4">
    <div v-if="loading" class="flex justify-center items-center h-screen">
      <ProgressSpinner />
    </div>
    <div v-else>
      <!-- Banner Section -->
      <div class="relative bg-gradient-to-r from-blue-500 to-purple-600 rounded-lg shadow-lg p-8 mb-12 text-white overflow-hidden">
        <div class="absolute inset-0 opacity-20" style="background-image: url('https://media.istockphoto.com/id/2157499482/vi/anh/c%E1%BA%ADn-c%E1%BA%A3nh-m%E1%BB%99t-doanh-nh%C3%A2n-l%C3%A0m-vi%E1%BB%87c-tr%C3%AAn-m%C3%A1y-t%C3%ADnh-x%C3%A1ch-tay-v%C3%A0-c%E1%BA%A7m-v%C3%A0-nh%C3%ACn-v%C3%A0o-%C4%91i%E1%BB%87n-tho%E1%BA%A1i-di-%C4%91%E1%BB%99ng.jpg?s=2048x2048&w=is&k=20&c=Qh60-Ea-ZMVW3h_V55oEJGw9om5jPfkkVO6u6gg5Sn0='); background-size: cover;"></div>
        <div class="relative z-10 flex flex-col md:flex-row items-center justify-between">
          <div class="text-center md:text-left mb-6 md:mb-0">
            <h2 class="text-4xl md:text-5xl font-extrabold leading-tight">Ưu đãi Laptop cực sốc!</h2>
            <p class="mt-3 text-lg md:text-xl opacity-90">Giảm giá lên đến 50% cho các dòng laptop mới nhất.</p>
            <Button label="Mua ngay" icon="pi pi-arrow-right" class="mt-6 p-button-lg p-button-secondary" @click="router.push('/shop/products')" />
          </div>
          <div class="md:w-1/2 flex justify-center">
            <img src="https://media.istockphoto.com/id/1028869800/vi/anh/c%E1%BA%ADn-c%E1%BA%A3nh-b%C3%A0n-tay-c%E1%BB%A7a-m%E1%BB%99t-doanh-nh%C3%A2n-tr%C3%AAn-b%C3%A0n-ph%C3%ADm.jpg?s=2048x2048&w=is&k=20&c=YRw10AHAaGBHDUOjNRAXT6dQtDjwfwuN_J4Vb7-wKdU=" alt="Laptop Sale" class="max-h-64 md:max-h-80 object-contain" />
          </div>
        </div>
      </div>

      <!-- Categories Section -->
      <section class="mb-12">
        <h2 class="text-3xl font-bold text-center mb-8 text-gray-800">Khám phá theo danh mục</h2>
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-6">
          <Card v-for="category in categories" :key="category.id" class="text-center cursor-pointer hover:shadow-xl transition-shadow duration-300 h-full" @click="goToCategoryProducts(category.id)">
            <template #content>
              <div class="flex flex-col justify-center items-center h-full p-4">
                <i :class="`pi pi-${category.icon || 'desktop'} text-5xl text-blue-500 mb-4`"></i>
                <p class="text-xl font-semibold text-gray-700">{{ category.moTaDanhMuc || category.tenDanhMuc }}</p>
              </div>
            </template>
          </Card>
        </div>
      </section>

      <!-- Best-Selling Products Section -->
      <section class="mb-12">
        <h2 class="text-3xl font-bold text-center mb-8 text-gray-800">Sản phẩm nổi bật</h2>
        <Carousel :value="featuredProducts" :numVisible="4" :numScroll="1" :responsiveOptions="responsiveOptions" class="product-carousel" :showNavigators="false">
          <template #item="{ data: product }">
            <Card class="border h-[500px] rounded-lg overflow-hidden shadow-lg hover:shadow-xl transition-shadow duration-300 bg-white mx-2">
              <template #content>
                <div class="relative w-full h-60 mb-4">
                  <img :src="getProductImageUrl(product) || 'https://via.placeholder.com/300'" :alt="product.tenSanPham" class="w-full h-60 object-contain" @error="onImageError"/>
                </div>
                <h3 class="text-lg font-semibold h-14 overflow-hidden text-ellipsis">{{ product.tenSanPham }}</h3>
                <p class="text-gray-500 text-sm mb-1">Thương hiệu: <span class="font-medium text-gray-700">{{ product.thuongHieu?.moTaThuongHieu || 'N/A' }}</span></p>
                <div class="flex flex-wrap gap-1 mb-2">
                  <Badge v-for="danhMuc in product.danhMucs" :key="danhMuc.id" :value="danhMuc.moTaDanhMuc || danhMuc.tenDanhMuc" severity="info" outlined />
                </div>
                <p class="text-blue-600 font-bold text-xl mt-2">
                  <span v-if="getMinPrice(product.sanPhamChiTiets) === getMaxPrice(product.sanPhamChiTiets)">
                    {{ formatCurrency(getMinPrice(product.sanPhamChiTiets)) }}
                  </span>
                  <span v-else>
                    {{ formatCurrency(getMinPrice(product.sanPhamChiTiets)) }} - {{ formatCurrency(getMaxPrice(product.sanPhamChiTiets)) }}
                  </span>
                </p>
                <Button label="Xem chi tiết" icon="pi pi-search" class="w-full mt-4 p-button-sm" @click="goToProductDetail(product.id)" />
              </template>
            </Card>
          </template>
        </Carousel>
      </section>

      <!-- Latest Vouchers Section -->
      <section class="mb-12">
        <h2 class="text-3xl font-bold text-center mb-8 text-gray-800">Voucher hấp dẫn</h2>
        <div v-if="latestVouchers.length > 0">
          <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
            <Card v-for="voucher in latestVouchers" :key="voucher.id" class="shadow-md border-l-4 border-blue-400">
              <template #title>
                <div class="flex justify-between items-center">
                  <span class="text-xl font-bold text-blue-700">{{ voucher.maPhieuGiamGia }}</span>
                  <Tag :severity="getVoucherStatusSeverity(voucher.trangThai)" :value="getVoucherStatusText(voucher.trangThai)" />
                </div>
              </template>
              <template #content>
                <p class="text-gray-700 mb-2">{{ voucher.tenPhieuGiamGia }}</p>
                <p class="text-lg font-semibold text-green-600">Giảm: {{ formatDiscountValue(voucher) }}</p>
                <p class="text-sm text-gray-500 mt-2">HSD: {{ new Date(voucher.ngayKetThuc).toLocaleDateString('vi-VN') }}</p>
              </template>
            </Card>
          </div>
          <div class="text-center mt-8">
            <Button label="Xem tất cả" icon="pi pi-arrow-right" @click="router.push('/shop/vouchers')" />
          </div>
        </div>
        <div v-else class="text-center text-gray-500 py-8">
          <p>Hiện chưa có voucher nào.</p>
        </div>
      </section>
    </div>
  </div>
</template>
