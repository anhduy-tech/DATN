import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

export const useCartStore = defineStore('cart', () => {
    const items = ref(JSON.parse(localStorage.getItem('cartItems') || '[]'));

    watch(items, (newItems) => {
        localStorage.setItem('cartItems', JSON.stringify(newItems));
    }, { deep: true });

    const totalItems = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0));
    const totalPrice = computed(() => items.value.reduce((sum, item) => sum + (item.price * item.quantity), 0));

    function addItem(product) {
        if (!product.productId || !product.variantId) {
            console.error("Product must have productId and variantId", product);
            return;
        }

        const existingItem = items.value.find(item => item.variantId === product.variantId);
        if (existingItem) {
            existingItem.quantity += product.quantity || 1;
        } else {
            items.value.push({
                ...product,
                quantity: product.quantity || 1,
            });
        }
    }

    function removeItem(variantId) {
        items.value = items.value.filter(item => item.variantId !== variantId);
    }

    function updateQuantity(variantId, quantity) {
        const item = items.value.find(item => item.variantId === variantId);
        if (item) {
            if (quantity > 0) {
                item.quantity = quantity;
            } else {
                removeItem(variantId);
            }
        }
    }

    function clearCart() {
        items.value = [];
    }

    const selectedItemsForCheckout = ref([]);

    function setSelectedItemsForCheckout(items) {
        selectedItemsForCheckout.value = items;
    }

    function clearSelectedItemsForCheckout() {
        selectedItemsForCheckout.value = [];
    }

    return {
        items,
        addItem,
        removeItem,
        updateQuantity,
        clearCart,
        totalItems,
        totalPrice,
        selectedItemsForCheckout,
        setSelectedItemsForCheckout,
        clearSelectedItemsForCheckout
    };
});
