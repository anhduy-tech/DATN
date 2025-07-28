<script setup>
import { ref, computed } from 'vue';
import { useCartStore } from '@/stores/cartStore';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';

const cartStore = useCartStore();
const router = useRouter();
const toast = useToast();


const cartItems = computed(() => cartStore.items);
const selectedItems = ref([]); // To store variantIds of selected items

const formatCurrency = (value) => {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(value);
};

const updateQuantity = (variantId, quantity) => {
    cartStore.updateQuantity(variantId, quantity);
};

const removeItem = (variantId) => {
    cartStore.removeItem(variantId);
    const index = selectedItems.value.indexOf(variantId);
    if (index > -1) {
        selectedItems.value.splice(index, 1);
    }
    toast.add({ severity: 'info', summary: 'Thông báo', detail: 'Đã xóa sản phẩm khỏi giỏ hàng.', life: 3000 });
};

const toggleSelectItem = (item) => {
    const index = selectedItems.value.indexOf(item.variantId);
    if (index > -1) {
        selectedItems.value.splice(index, 1);
    } else {
        selectedItems.value.push(item.variantId);
    }
};

const isItemSelected = (item) => {
    return selectedItems.value.includes(item.variantId);
};

const proceedToCheckout = () => {
    if (selectedItems.value.length === 0) {
        toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng chọn ít nhất một sản phẩm để thanh toán.', life: 3000 });
        return;
    }

    const itemsToCheckout = cartItems.value.filter(item => selectedItems.value.includes(item.variantId));
    cartStore.setSelectedItemsForCheckout(itemsToCheckout);

    router.push('/shop/checkout');
};

const isCheckoutButtonDisabled = computed(() => {
    return selectedItems.value.length === 0;
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
                    <div class="border-b mb-4 pb-4" v-for="item in cartItems" :key="item.variantId">
                        <div class="grid grid-cols-12 gap-4 items-center p-2">
                            <!-- Checkbox -->
                            <div class="col-span-1 flex justify-center">
                                <Checkbox v-model="selectedItems" :inputId="item.variantId.toString()" :value="item.variantId" />
                            </div>
                            <!-- Product Image -->
                            <div class="col-span-2">
                                <img :src="item.image || 'https://via.placeholder.com/150'" :alt="item.name" class="w-24 h-24 object-contain rounded-md" />
                            </div>
                            <!-- Product Name -->
                            <div class="col-span-4">
                                <h3 class="font-semibold">{{ item.name }}</h3>
                            </div>
                            <!-- Quantity -->
                            <div class="col-span-2">
                                <InputNumber v-model="item.quantity" @input="updateQuantity(item.variantId, $event.value)" showButtons buttonLayout="horizontal" :min="1" />
                            </div>
                            <!-- Product Price -->
                            <div class="col-span-2">
                                <p>{{ formatCurrency(item.price * item.quantity) }}</p>
                            </div>
                            <!-- Remove Button -->
                            <div class="col-span-1 text-right">
                                <Button icon="pi pi-trash" severity="danger" text rounded @click="removeItem(item.variantId)" />
                            </div>
                        </div>
                    </div>

                    <!-- Cart Summary and Actions -->
                    <div class="mt-6 text-right">
                        <p class="text-xl font-semibold">Tổng cộng: <span class="text-blue-600">{{ formatCurrency(cartStore.totalPrice) }}</span></p>
                        <div class="mt-4 flex justify-end gap-4">
                            <Button label="Tiếp tục mua sắm" severity="secondary" outlined @click="continueShopping" />
                            <Button
                                label="Tiến hành thanh toán"
                                icon="pi pi-shopping-cart"
                                @click="proceedToCheckout"
                                :disabled="isCheckoutButtonDisabled"
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
