<template>
  <header class="bg-white shadow-md sticky top-0 z-50">
    <div class="container mx-auto px-4">
      <div class="flex justify-between items-center py-4">
        <!-- Logo -->
        <router-link to="/shop" class="flex items-center">
          <img src="@/assets/logo.svg" alt="LapXpert Logo" class="h-10 mr-3" />
          <span class="text-2xl font-bold text-gray-800">LapXpert</span>
        </router-link>

        <!-- Navigation Links -->
        <nav class="hidden md:flex items-center space-x-6">
          <router-link to="/shop" class="text-gray-600 hover:text-primary-500 transition-colors duration-300">Trang chủ</router-link>
          <router-link to="/shop/products" class="text-gray-600 hover:text-primary-500 transition-colors duration-300">Sản phẩm</router-link>
          <router-link to="/shop/support" class="text-gray-600 hover:text-primary-500 transition-colors duration-300">Hỗ trợ</router-link>
          <router-link to="/shop/vouchers" class="text-gray-600 hover:text-primary-500 transition-colors duration-300">Vouchers</router-link>
        </nav>

        <!-- Header Icons -->
        <div class="flex items-center space-x-4">
          <button class="text-gray-600 hover:text-primary-500">
            <i class="pi pi-search text-xl"></i>
          </button>
          <router-link to="/shop/cart" class="relative text-gray-600 hover:text-primary-500">
            <i class="pi pi-shopping-cart text-xl"></i>
            <span v-if="cartItemCount > 0" class="absolute -top-2 -right-2 bg-primary-500 text-white text-xs rounded-full h-5 w-5 flex items-center justify-center">
              {{ cartItemCount }}
            </span>
          </router-link>

          <!-- User Actions -->
          <div v-if="customerStore.isLoggedIn" class="flex items-center space-x-4">
            <Menu ref="menu" :model="userMenuItems" :popup="true" />
            <button @click="toggleMenu" class="text-gray-600 hover:text-primary-500 flex items-center gap-2">
              <i class="pi pi-user text-xl"></i>
              <span v-if="customerStore.user" class="hidden md:inline-block font-medium">{{ customerStore.user.hoTen }}</span>
            </button>
            <Button label="Đăng xuất" icon="pi pi-sign-out" class="p-button-text p-button-sm" @click="handleLogout" />
          </div>
          <router-link v-else to="/login" class="text-gray-600 hover:text-primary-500">
             <i class="pi pi-sign-in text-xl"></i>
          </router-link>

        </div>

        <div class="md:hidden">
          <button @click="isMenuOpen = !isMenuOpen" class="text-gray-600 hover:text-primary-500">
            <i class="pi pi-bars text-2xl"></i>
          </button>
        </div>
      </div>
    </div>

    <!-- Mobile Menu -->
    <div v-if="isMenuOpen" class="md:hidden bg-white py-4">
      <nav class="flex flex-col items-center space-y-4">
        <router-link to="/shop" class="text-gray-600 hover:text-primary-500" @click="isMenuOpen = false">Trang chủ</router-link>
        <router-link to="/shop/products" class="text-gray-600 hover:text-primary-500" @click="isMenuOpen = false">Sản phẩm</router-link>
        <router-link to="/shop/support" class="text-gray-600 hover:text-primary-500" @click="isMenuOpen = false">Hỗ trợ</router-link>
        <router-link to="/shop/vouchers" class="text-gray-600 hover:text-primary-500" @click="isMenuOpen = false">Vouchers</router-link>
      </nav>
    </div>
  </header>
</template>

<script setup>
import { ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useCartStore } from '@/stores/cartStore';
import { useCustomerStore } from '@/stores/customerstore';
import Menu from 'primevue/menu';
import Button from 'primevue/button';

const router = useRouter();
const cartStore = useCartStore();
const customerStore = useCustomerStore();
const isMenuOpen = ref(false);
const menu = ref();

const cartItemCount = computed(() => cartStore.totalItems);

const handleLogout = () => {
  customerStore.logout();
  router.push('/shop');
};

const userMenuItems = ref([
  {
    label: 'Hồ sơ của bạn',
    icon: 'pi pi-user',
    command: () => {
      router.push('/shop/profile');
    }
  }
]);

const toggleMenu = (event) => {
  menu.value.toggle(event);
};

</script>

