<template>
  <Dialog v-model:visible="dialogVisible" modal :header="`Viết đánh giá cho ${productName}`" :style="{ width: '50vw' }">
    <div class="p-fluid">
      <div class="field mb-4">
        <label for="rating" class="font-semibold">Số sao đánh giá:</label>
        <Rating v-model="newReview.rating" :cancel="false" :stars="5" class="mt-2" />
      </div>
      <div class="field mb-4">
        <label for="comment" class="font-semibold">Bình luận:</label>
        <Textarea v-model="newReview.comment" rows="5" cols="30" class="mt-2" placeholder="Viết bình luận của bạn..." />
      </div>
    </div>
    <template #footer>
      <Button label="Hủy" icon="pi pi-times" text @click="closeDialog" />
      <Button label="Gửi đánh giá" icon="pi pi-check" @click="submitReview" :disabled="!newReview.rating || isSubmitting" :loading="isSubmitting" />
    </template>
  </Dialog>
</template>

<script setup>
import { ref, watch } from 'vue';
import { useToast } from 'primevue/usetoast';
import Dialog from 'primevue/dialog';
import Rating from 'primevue/rating';
import Textarea from 'primevue/textarea';
import Button from 'primevue/button';
import ReviewService from '@/apis/review';
import AuthService from '@/apis/auth';

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  sanPhamId: {
    type: Number,
    required: true
  },
  nguoiDungId: {
    type: Number,
    required: true
  },
  hoaDonChiTietId: {
    type: Number,
    required: true
  },
  productName: {
    type: String,
    default: 'Sản phẩm'
  }
});

const emit = defineEmits(['update:visible', 'submitted']);

const toast = useToast();
const dialogVisible = ref(props.visible);
const newReview = ref({ rating: 0, comment: '' });
const isSubmitting = ref(false);

// Watch for changes in the visible prop to control dialog visibility
watch(() => props.visible, (newValue) => {
  dialogVisible.value = newValue;
  if (newValue) {
    // Reset form when dialog opens
    newReview.value = { rating: 0, comment: '' };
  }
});

// Emit update:visible event when dialogVisible changes
watch(dialogVisible, (newValue) => {
  emit('update:visible', newValue);
});

const closeDialog = () => {
  dialogVisible.value = false;
};

const submitReview = async () => {
  if (!newReview.value.rating || newReview.value.rating === 0) {
    toast.add({ severity: 'warn', summary: 'Cảnh báo', detail: 'Vui lòng chọn số sao đánh giá.', life: 3000 });
    return;
  }

  isSubmitting.value = true;
  try {
    const reviewData = {
      sanPhamId: props.sanPhamId,
      nguoiDungId: props.nguoiDungId,
      hoaDonChiTietId: props.hoaDonChiTietId, // Pass the specific order item ID
      diemDanhGia: newReview.value.rating, // Use diemDanhGia as per backend DTO
      noiDung: newReview.value.comment,
    };
    await ReviewService.createReview(reviewData);
    toast.add({ severity: 'success', summary: 'Thành công', detail: 'Đánh giá của bạn đã được gửi.', life: 3000 });
    closeDialog();
    emit('submitted'); // Notify parent component
  } catch (err) {
    console.error("Error submitting review:", err);
    toast.add({ severity: 'error', summary: 'Lỗi', detail: err.response?.data?.message || 'Không thể gửi đánh giá. Vui lòng thử lại.', life: 5000 });
  } finally {
    isSubmitting.value = false;
  }
};
</script>
