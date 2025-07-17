<script setup>
import { ref, computed, watch } from 'vue';
import { useCartStore } from '@/stores/cartStore';
import { useOrderStore } from '@/stores/orderStore'; // Import orderStore
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast'; // Import useToast

const cartStore = useCartStore();
const orderStore = useOrderStore(); // Initialize orderStore
const router = useRouter();
const toast = useToast(); // Initialize toast

const cartItems = computed(() => cartStore.items);
const selectedItems = ref(new Set()); // To store serialIds of selected items
console.log('selectedItems initialized:', selectedItems.value);

// Watch for changes in cartItems and update selectedItems if an item is removed
watch(cartItems, (newItems, oldItems) => {
    console.log('Cart items changed. New:', newItems.length, 'Old:', oldItems.length);
    const newSerialIds = new Set(newItems.map(item => item.serialId));
    // Remove items from selectedItems if their serialId is no longer in cartItems
    selectedItems.value.forEach(selectedSerialId => {
        if (!newSerialIds.has(selectedSerialId)) {
            selectedItems.value.delete(selectedSerialId);
            console.log('Removed from selection due to cart removal:', selectedSerialId);
        }
    });
}, { deep: true });

const formatCurrency = (value) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

const toggleSelectItem = (item) => {
    if (selectedItems.value.has(item.serialId)) {
        selectedItems.value.delete(item.serialId);
        console.log('Item deselected:', item.serialId, 'Current selected items count:', selectedItems.value.size);
    } else {
        if (selectedItems.value.size < 5) { // Limit to 5 selections
            selectedItems.value.add(item.serialId);
            console.log('Item selected:', item.serialId, 'Current selected items count:', selectedItems.value.size);
        } else {
            toast.add({
                severity: 'warn',
                summary: 'Cảnh báo',
                detail: 'Bạn chỉ có thể chọn tối đa 5 sản phẩm để mua cùng lúc.',
                life: 3000
            });
            console.log('Selection limit reached. Item not selected:', item.serialId);
        }
    }
};

const isItemSelected = (item) => {
    return selectedItems.value.has(item.serialId);
};

const createBatchOrders = async () => {
    if (selectedItems.value.size === 0) {
        toast.add({
            severity: 'warn',
            summary: 'Cảnh báo',
            detail: 'Vui lòng chọn ít nhất một sản phẩm để tạo đơn hàng.',
            life: 3000
        });
        return;
    }

    if (selectedItems.value.size > 5) {
        toast.add({
            severity: 'warn',
            summary: 'Cảnh báo',
            detail: 'Bạn chỉ có thể tạo tối đa 5 đơn hàng cùng lúc.',
            life: 3000
        });
        return;
    }

    // Group selected items by product to create individual orders
    const itemsToCheckout = cartItems.value.filter(item => selectedItems.value.has(item.serialId));
    cartStore.setSelectedItemsForCheckout(itemsToCheckout);

    // Remove items from cart after they are selected for checkout
    selectedItems.value.forEach(item => {
        cartStore.removeItem(item.serialId);
    });
    selectedItems.value = new Set(); // Clear selected items after moving to checkout

    router.push('/shop/checkout'); // Navigate to the checkout page
};

const isCreateOrderButtonDisabled = computed(() => {
    return !selectedItems.value || selectedItems.value.size === 0 || selectedItems.value.size > 5;
});

const continueShopping = () => {
    router.push('/shop/products');
};
</script>

<template>
    <div class="container mx-auto p-4">
        <Card>
            <template #title>
                <h1 class="text-2xl font-bold">Giỏ hàng của bạn</h1>
            </template>
            <template #content>
                <div v-if="cartItems.length > 0">
                    <div class="border-b mb-4 pb-4" v-for="item in cartItems" :key="item.serialId">
                        <div class="grid grid-cols-12 gap-4 items-center cursor-pointer p-2"
                             :class="{ 'border-2 border-blue-500 rounded-lg': isItemSelected(item) }"
                             @click="toggleSelectItem(item)">
                            <!-- Product Image -->
                            <div class="col-span-2">
                                <img :src="item.image || 'https://via.placeholder.com/150'" :alt="item.name" class="w-24 h-24 object-contain rounded-md" />
                            </div>
                            <!-- Product Name & Serial -->
                            <div class="col-span-6">
                                <h3 class="font-semibold">{{ item.name }}</h3>
                                <p class="text-sm text-gray-500">Serial: {{ item.serialId }}</p>
                            </div>
                            <!-- Product Price -->
                            <div class="col-span-2">
                                <p>{{ formatCurrency(item.price) }}</p>
                            </div>
                            <!-- Remove Button -->
                            <div class="col-span-2 text-right">
                                <Button icon="pi pi-trash" severity="danger" text rounded @click.stop="cartStore.removeItem(item.serialId)" />
                            </div>
                        </div>
                    </div>

                    <!-- Cart Summary and Actions -->
                    <div class="mt-6 text-right">
                        <p class="text-xl font-semibold">Tổng cộng: <span class="text-blue-600">{{ formatCurrency(cartStore.totalPrice) }}</span></p>
                        <div class="mt-4 flex justify-end gap-4">
                            <Button label="Tiếp tục mua sắm" severity="secondary" outlined @click="continueShopping" />
                            <Button
                                label="Tạo đơn hàng từ mục đã chọn"
                                icon="pi pi-shopping-cart"
                                @click="createBatchOrders"
                                :disabled="isCreateOrderButtonDisabled"
                            />
                        </div>
                    </div>
                </div>
                <div v-else class="text-center py-16">
                    <i class="pi pi-shopping-cart text-6xl text-gray-400 mb-4"></i>
                    <p class="text-gray-500 text-xl">Giỏ hàng của bạn đang trống.</p>
                     <div class="mt-6">
                        <Button label="Bắt đầu mua sắm" icon="pi pi-arrow-right" @click="continueShopping" />
                    </div>
                </div>
            </template>
        </Card>
    </div>
</template>
