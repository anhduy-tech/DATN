import { defineStore } from 'pinia';
import { ref, computed, watch } from 'vue';

export const useCartStore = defineStore('cart', () => {
    // Load cart items from localStorage or initialize as empty array
    const items = ref(JSON.parse(localStorage.getItem('cartItems') || '[]')); // { productId, variantId, serialId, name, price, image }

    // Watch for changes in items and save to localStorage
    watch(items, (newItems) => {
        localStorage.setItem('cartItems', JSON.stringify(newItems));
    }, { deep: true });

    const totalItems = computed(() => items.value.length);
    const totalPrice = computed(() => items.value.reduce((sum, item) => sum + item.price, 0));

    /**
     * Adds a product to the cart. Each serialId represents a unique item.
     * @param {Object} product - The product object containing productId, variantId, serialId, name, price, image.
     */
    function addItem(product) {
        // Ensure product has necessary IDs
        if (!product.productId || !product.variantId || !product.serialId) {
            console.error("Attempted to add product to cart without complete ID information:", product);
            return;
        }

        // Check if this specific serialId is already in the cart
        const existingItem = items.value.find(item => item.serialId === product.serialId);
        if (!existingItem) {
            items.value.push({
                productId: product.productId,
                variantId: product.variantId,
                serialId: product.serialId,
                name: product.name,
                price: product.price,
                image: product.image // Assuming product object has an image property
            });
        } else {
            console.warn(`Product with serialId ${product.serialId} is already in the cart.`);
        }
    }

    /**
     * Removes a product from the cart based on its serialId.
     * @param {string} serialId - The serial ID of the product to remove.
     */
    function removeItem(serialId) {
        items.value = items.value.filter(item => item.serialId !== serialId);
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

    return { items, addItem, removeItem, clearCart, totalItems, totalPrice, selectedItemsForCheckout, setSelectedItemsForCheckout, clearSelectedItemsForCheckout };
});
