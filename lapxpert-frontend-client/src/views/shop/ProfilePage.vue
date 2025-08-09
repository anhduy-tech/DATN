<template>
  <Fluid>
    <Toast />

    <!-- Page Header -->
    <div class="card mb-6">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 bg-primary/10 rounded-lg flex items-center justify-center">
            <i class="pi pi-user text-lg text-primary"></i>
          </div>
          <div>
            <h1 class="font-semibold text-xl text-surface-900 m-0">Hồ sơ của bạn</h1>
            <p class="text-surface-500 text-sm mt-1 mb-0">Chỉnh sửa thông tin cá nhân và địa chỉ của bạn</p>
          </div>
        </div>
        <Button
          icon="pi pi-arrow-left"
          severity="secondary"
          outlined
          size="small"
          @click="goBack"
          v-tooltip.left="'Quay lại'"
        />
      </div>
    </div>

    <form @submit.prevent="handleSubmit">
      <div class="flex flex-col gap-6">

        <!-- Personal Information Card -->
        <div class="card">
          <div class="flex items-center gap-2 mb-4">
            <i class="pi pi-user text-primary"></i>
            <span class="font-semibold text-xl">Thông tin cá nhân</span>
          </div>

          <div class="grid grid-cols-12 gap-6">
            <!-- Avatar Section -->
            <div class="col-span-12 lg:col-span-4">
              <div class="flex flex-col gap-4">
                <label class="font-semibold">Ảnh đại diện</label>

                <!-- Avatar Preview -->
                <div class="flex justify-center">
                  <div class="relative">
                    <div class="w-32 h-32 border-2 border-dashed border-surface-300 rounded-lg flex items-center justify-center overflow-hidden bg-surface-50">
                      <img
                        v-if="imagePreview"
                        :src="imagePreview"
                        alt="Avatar preview"
                        class="w-full h-full object-cover"
                      />
                      <div v-else class="text-center">
                        <i class="pi pi-camera text-3xl text-surface-400 mb-2 block"></i>
                        <span class="text-surface-600 text-sm">Chọn ảnh</span>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- Upload Button -->
                <FileUpload
                  mode="basic"
                  name="avatar"
                  accept="image/*"
                  :maxFileSize="2000000"
                  chooseLabel="Chọn ảnh"
                  @select="onAvatarSelect"
                  :class="{
                    'p-invalid': submitted && !form.avatar && !imagePreview,
                  }"
                  customUpload
                />

                <small
                  class="text-red-500"
                  v-if="submitted && !form.avatar && !imagePreview"
                >
                  Vui lòng chọn ảnh đại diện
                </small>
              </div>
            </div>

            <!-- Basic Information Fields -->
            <div class="col-span-12 lg:col-span-8">
              <div class="grid grid-cols-12 gap-4">

                <!-- Full Name -->
                <div class="col-span-12 md:col-span-6">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Họ và tên <span class="text-red-500">*</span>
                    </label>
                    <InputText
                      v-model="form.hoTen"
                      placeholder="Nhập họ và tên"
                      :invalid="submitted && !form.hoTen"
                    />
                    <small class="text-red-500" v-if="submitted && !form.hoTen">
                      Họ tên là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Gender -->
                <div class="col-span-12 md:col-span-6">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Giới tính <span class="text-red-500">*</span>
                    </label>
                    <Select
                      v-model="form.gioiTinh"
                      :options="genderOptions"
                      optionLabel="label"
                      optionValue="value"
                      placeholder="Chọn giới tính"
                      :invalid="submitted && form.gioiTinh === null"
                    />
                    <small class="text-red-500" v-if="submitted && form.gioiTinh === null">
                      Giới tính là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Birth Date -->
                <div class="col-span-12 md:col-span-6">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Ngày sinh <span class="text-red-500">*</span>
                    </label>
                    <DatePicker
                      v-model="form.ngaySinh"
                      dateFormat="dd/mm/yy"
                      :showIcon="true"
                      :maxDate="maxBirthDate"
                      placeholder="Chọn ngày sinh"
                      :invalid="submitted && !form.ngaySinh"
                    />
                    <small class="text-red-500" v-if="submitted && !form.ngaySinh">
                      Ngày sinh là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Email -->
                <div class="col-span-12 md:col-span-6">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Email <span class="text-red-500">*</span>
                    </label>
                    <InputText
                      v-model="form.email"
                      placeholder="Nhập email"
                      :invalid="(submitted && !form.email) || emailError"
                      @blur="validateEmail"
                    />
                    <small class="text-red-500" v-if="submitted && !form.email">
                      Email là bắt buộc
                    </small>
                    <small class="text-red-500" v-if="emailError">
                      {{ emailError }}
                    </small>
                  </div>
                </div>

                <!-- Phone -->
                <div class="col-span-12 md:col-span-6">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Số điện thoại <span class="text-red-500">*</span>
                    </label>
                    <InputText
                      v-model="form.soDienThoai"
                      placeholder="Nhập số điện thoại"
                      :invalid="(submitted && !form.soDienThoai) || phoneError"
                      @blur="validatePhone"
                    />
                    <small class="text-red-500" v-if="submitted && !form.soDienThoai">
                      Số điện thoại là bắt buộc
                    </small>
                    <small class="text-red-500" v-if="phoneError">
                      {{ phoneError }}
                    </small>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Address Section -->
        <div class="card">
          <div class="flex items-center gap-2 mb-4">
            <i class="pi pi-map-marker text-primary"></i>
            <span class="font-semibold text-xl">Địa chỉ</span>
          </div>

          <div class="flex flex-col gap-4">
            <div
              v-for="(address, index) in form.diaChis"
              :key="index"
              class="border border-surface-200 rounded-lg p-4"
            >
              <div class="flex justify-between items-center mb-4">
                <div class="flex items-center gap-2">
                  <i class="pi pi-home text-primary"></i>
                  <span class="font-semibold">Địa chỉ {{ index + 1 }}</span>
                </div>
                <Button
                  v-if="form.diaChis.length > 1"
                  icon="pi pi-trash"
                  severity="danger"
                  text
                  rounded
                  @click="removeAddress(index)"
                  :disabled="form.diaChis.length === 1"
                  v-tooltip.top="'Xóa địa chỉ'"
                />
              </div>

              <div class="grid grid-cols-12 gap-4">
                <!-- Street -->
                <div class="col-span-12">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Đường/Số nhà <span class="text-red-500">*</span>
                    </label>
                    <InputText
                      v-model="address.duong"
                      placeholder="Nhập đường/số nhà"
                      :invalid="submitted && !address.duong"
                    />
                    <small class="text-red-500" v-if="submitted && !address.duong">
                      Đường/Số nhà là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Province -->
                <div class="col-span-12 md:col-span-4">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Tỉnh/Thành phố <span class="text-red-500">*</span>
                    </label>
                    <Select
                      v-model="address.tinhThanh"
                      :options="provinces"
                      optionLabel="name"
                      optionValue="code"
                      placeholder="Chọn tỉnh/thành"
                      :invalid="submitted && !address.tinhThanh"
                      @change="getDistricts(address, index)"
                    />
                    <small class="text-red-500" v-if="submitted && !address.tinhThanh">
                      Tỉnh/Thành phố là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- District -->
                <div class="col-span-12 md:col-span-4">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Quận/Huyện <span class="text-red-500">*</span>
                    </label>
                    <Select
                      v-model="address.quanHuyen"
                      :options="districts[index]"
                      optionLabel="name"
                      optionValue="code"
                      placeholder="Chọn quận/huyện"
                      :invalid="submitted && !address.quanHuyen"
                      @change="getWards(address, index)"
                      :disabled="!address.tinhThanh"
                    />
                    <small class="text-red-500" v-if="submitted && !address.quanHuyen">
                      Quận/Huyện là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Ward -->
                <div class="col-span-12 md:col-span-4">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">
                      Phường/Xã <span class="text-red-500">*</span>
                    </label>
                    <Select
                      v-model="address.phuongXa"
                      :options="wards[index]"
                      optionLabel="name"
                      optionValue="code"
                      placeholder="Chọn phường/xã"
                      :invalid="submitted && !address.phuongXa"
                      :disabled="!address.quanHuyen"
                    />
                    <small class="text-red-500" v-if="submitted && !address.phuongXa">
                      Phường/Xã là bắt buộc
                    </small>
                  </div>
                </div>

                <!-- Address Type -->
                <div class="col-span-12 md:col-span-8">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">Loại địa chỉ</label>
                    <InputText
                      v-model="address.loaiDiaChi"
                      placeholder="Ví dụ: Nhà riêng, Công ty..."
                    />
                  </div>
                </div>

                <!-- Default Address Checkbox -->
                <div class="col-span-12 md:col-span-4">
                  <div class="flex flex-col gap-2">
                    <label class="font-semibold">Tùy chọn</label>
                    <div class="flex items-center gap-2">
                      <Checkbox
                        v-model="address.laMacDinh"
                        :binary="true"
                        @change="setDefaultAddress(index)"
                        :disabled="form.diaChis.length === 1"
                        inputId="defaultAddress"
                      />
                      <label for="defaultAddress">Địa chỉ mặc định</label>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="flex justify-end mt-4">
              <Button
                label="Thêm địa chỉ"
                icon="pi pi-plus"
                outlined
                @click="addNewAddress"
                :disabled="form.diaChis.length >= 5"
                v-tooltip.top="'Tối đa 5 địa chỉ'"
              />
            </div>
          </div>
        </div>

        <!-- Form Actions -->
        <div class="flex justify-end gap-3 pt-6 border-t border-surface-200">
          <Button
            label="Hủy bỏ"
            icon="pi pi-times"
            severity="secondary"
            outlined
            @click="goBack"
          />
          <Button
            type="submit"
            label="Cập nhật"
            icon="pi pi-check"
          />
        </div>
      </div>
    </form>
  </Fluid>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useToast } from 'primevue/usetoast';
import { useCustomerStore } from '@/stores/customerstore';
import AuthService from '@/apis/auth';
import addressApi from '@/apis/address';
import storageApi from '@/apis/storage';

// PrimeVue Components
import Card from 'primevue/card';
import InputText from 'primevue/inputtext';
import Select from 'primevue/select';
import DatePicker from 'primevue/datepicker';
import FileUpload from 'primevue/fileupload';
import Checkbox from 'primevue/checkbox';
import Button from 'primevue/button';
import Toast from 'primevue/toast';

const toast = useToast();
const router = useRouter();
const customerStore = useCustomerStore();

const customerId = ref(null);

// Form data
const form = ref({
  avatar: null,
  hoTen: '',
  gioiTinh: null,
  ngaySinh: null,
  email: '',
  soDienThoai: '',
  trangThai: 'HOAT_DONG', // Default status for a customer profile
  diaChis: [
    {
      duong: '',
      phuongXa: '',
      quanHuyen: '',
      tinhThanh: '',
      loaiDiaChi: '',
      laMacDinh: true,
    },
  ],
});

const imagePreview = ref(null);
const submitted = ref(false);
const emailError = ref(null);
const phoneError = ref(null);

// Options
const genderOptions = ref([
  { label: 'Nam', value: 'NAM' },
  { label: 'Nữ', value: 'NU' },
]);

const maxBirthDate = computed(() => {
  const date = new Date();
  date.setFullYear(date.getFullYear() - 10); // Must be at least 10 years old
  return date;
});

// Address data
const provinces = ref([]);
const districts = ref([]);
const wards = ref([]);

// Load customer data on mount
onMounted(async () => {
  await loadProvinces();

  const currentUser = AuthService.getUser();
  if (currentUser && currentUser.id) {
    customerId.value = currentUser.id;
    try {
      const customer = await customerStore.fetchCustomerById(customerId.value);

      // Populate form with fetched data
      form.value.avatar = customer.avatar;
      form.value.hoTen = customer.hoTen;
      form.value.gioiTinh = customer.gioiTinh;
      form.value.ngaySinh = customer.ngaySinh ? new Date(customer.ngaySinh) : null;
      form.value.email = customer.email;
      form.value.soDienThoai = customer.soDienThoai;
      form.value.trangThai = customer.trangThai;

      // Deep copy addresses and initialize dropdowns
      if (customer.diaChis && customer.diaChis.length > 0) {
        form.value.diaChis = customer.diaChis.map(addr => ({
          ...addr,
          // Store original names for re-mapping to codes
          tinhThanhName: addr.tinhThanh,
          quanHuyenName: addr.quanHuyen,
          phuongXaName: addr.phuongXa,
          // Reset codes for re-selection
          tinhThanh: '',
          quanHuyen: '',
          phuongXa: '',
        }));

        districts.value = Array.from({ length: form.value.diaChis.length }, () => []);
        wards.value = Array.from({ length: form.value.diaChis.length }, () => []);

        for (let i = 0; i < form.value.diaChis.length; i++) {
          const address = form.value.diaChis[i];
          const provinceName = address.tinhThanhName;
          const districtName = address.quanHuyenName;
          const wardName = address.phuongXaName;

          const province = provinces.value.find(p => p.name === provinceName);
          if (province) {
            address.tinhThanh = province.code;
            await getDistricts(address, i);

            if (districts.value[i] && districts.value[i].length > 0) {
              const district = districts.value[i].find(d => d.name === districtName);
              if (district) {
                address.quanHuyen = district.code;
                await getWards(address, i);

                if (wards.value[i] && wards.value[i].length > 0) {
                  const ward = wards.value[i].find(w => w.name === wardName);
                  if (ward) {
                    address.phuongXa = ward.code;
                  }
                }
              }
            }
          }
          // Clean up temporary fields
          delete address.tinhThanhName;
          delete address.quanHuyenName;
          delete address.phuongXaName;
        }
      } else {
        // If no addresses, ensure at least one empty address form is present
        form.value.diaChis = [
          {
            duong: '',
            phuongXa: '',
            quanHuyen: '',
            tinhThanh: '',
            loaiDiaChi: '',
            laMacDinh: true,
          },
        ];
      }

      if (customer.avatar) {
        imagePreview.value = customer.avatar;
      }

    } catch (error) {
      console.error("Failed to fetch customer profile:", error);
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: 'Không thể tải thông tin hồ sơ.',
        life: 3000
      });
    }
  } else {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Không tìm thấy ID người dùng. Vui lòng đăng nhập lại.',
      life: 3000
    });
    router.push('/login'); // Redirect to login if no user ID
  }
});

// Load provinces
const loadProvinces = async () => {
  try {
    const response = await addressApi.getProvinces();
    provinces.value = response.data;
  } catch (error) {
    console.error('Error loading provinces:', error);
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tải danh sách tỉnh/thành',
      life: 3000,
    });
  }
};

// Get districts for a province
const getDistricts = async (address, index) => {
  try {
    const response = await addressApi.getDistricts(address.tinhThanh);
    districts.value[index] = response.data.districts || [];
    address.quanHuyen = '';
    address.phuongXa = '';
    wards.value[index] = [];
  } catch (error) {
    console.error('Error loading districts:', error);
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tải danh sách quận/huyện',
      life: 3000,
    });
  }
};

// Get wards for a district
const getWards = async (address, index) => {
  try {
    const response = await addressApi.getWards(address.quanHuyen);
    wards.value[index] = response.data.wards || [];
    address.phuongXa = '';
  } catch (error) {
    console.error('Error loading wards:', error);
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: 'Không thể tải danh sách phường/xã',
      life: 3000,
    });
  }
};

// Handle avatar selection
const onAvatarSelect = (event) => {
  const file = event.files[0];
  if (file) {
    form.value.avatar = file;
    const reader = new FileReader();
    reader.onload = (e) => {
      imagePreview.value = e.target.result;
    };
    reader.readAsDataURL(file);
  }
};

// Validate email
const validateEmail = () => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!form.value.email) {
    emailError.value = null;
    return false;
  }
  if (!emailRegex.test(form.value.email)) {
    emailError.value = 'Email không hợp lệ';
    return false;
  }
  emailError.value = null;
  return true;
};

// Validate phone
const validatePhone = () => {
  const phoneRegex = /^[0-9]{10,11}$/;
  if (!form.value.soDienThoai) {
    phoneError.value = null;
    return false;
  }
  if (!phoneRegex.test(form.value.soDienThoai)) {
    phoneError.value = 'Số điện thoại phải có 10-11 chữ số';
    return false;
  }
  phoneError.value = null;
  return true;
};

// Add new address
const addNewAddress = () => {
  form.value.diaChis.push({
    duong: '',
    phuongXa: '',
    quanHuyen: '',
    tinhThanh: '',
    loaiDiaChi: '',
    laMacDinh: false,
  });
  districts.value.push([]);
  wards.value.push([]);
};

// Remove address
const removeAddress = (index) => {
  const wasDefault = form.value.diaChis[index].laMacDinh;

  form.value.diaChis.splice(index, 1);
  districts.value.splice(index, 1);
  wards.value.splice(index, 1);

  // If we removed the default address and there are still addresses left
  if (wasDefault && form.value.diaChis.length > 0) {
    form.value.diaChis[0].laMacDinh = true;
  }
};

// Set default address
const setDefaultAddress = (index) => {
  if (form.value.diaChis[index].laMacDinh) {
    form.value.diaChis.forEach((addr, i) => {
      if (i !== index) {
        addr.laMacDinh = false;
      }
    });
  } else if (form.value.diaChis.filter((addr) => addr.laMacDinh).length === 0) {
    // If no default address is selected, set the first one as default
    form.value.diaChis[0].laMacDinh = true;
  }
};

// Handle form submission
const handleSubmit = async () => {
  submitted.value = true;

  // Validate form
  const isEmailValid = validateEmail();
  const isPhoneValid = validatePhone();

  // Check required fields
  const isFormValid =
    form.value.hoTen &&
    form.value.gioiTinh !== null &&
    form.value.ngaySinh &&
    form.value.email &&
    form.value.soDienThoai &&
    isEmailValid &&
    isPhoneValid &&
    form.value.diaChis.every(
      (addr) => addr.duong && addr.tinhThanh && addr.quanHuyen && addr.phuongXa,
    );

  if (!isFormValid) {
    toast.add({
      severity: 'warn',
      summary: 'Cảnh báo',
      detail: 'Vui lòng điền đầy đủ thông tin bắt buộc',
      life: 3000,
    });
    return;
  }

  try {
    let avatarUrl = null;

    // Upload avatar file if it's a File object (new upload)
    if (form.value.avatar && form.value.avatar instanceof File) {
      try {
        avatarUrl = await storageApi.uploadFile(form.value.avatar, 'avatars');
        if (!avatarUrl) {
          throw new Error('No file URL returned from upload');
        }
      } catch (uploadError) {
        console.error('Avatar upload error:', uploadError);
        toast.add({
          severity: 'error',
          summary: 'Lỗi',
          detail: uploadError.message || 'Không thể tải lên ảnh đại diện',
          life: 3000,
        });
        return;
      }
    } else if (typeof form.value.avatar === 'string') {
      // If avatar is already a string (URL), use it as is (for edit mode)
      avatarUrl = form.value.avatar;
    }

    // Prepare data for API
    const customerData = {
      id: customerId.value, // Include ID for update
      ...form.value,
      avatar: avatarUrl, // Use the uploaded URL or existing URL
      ngaySinh: form.value.ngaySinh.toISOString().split('T')[0],
      diaChis: form.value.diaChis.map((addr, index) => {
        // Find name from code or keep as is (if already name)
        const province = provinces.value.find((p) => p.code === addr.tinhThanh);
        const district = districts.value[index]?.find((d) => d.code === addr.quanHuyen);
        const ward = wards.value[index]?.find((w) => w.code === addr.phuongXa);

        return {
          ...addr,
          tinhThanh: province ? province.name : addr.tinhThanh,
          quanHuyen: district ? district.name : addr.quanHuyen,
          phuongXa: ward ? ward.name : addr.phuongXa,
        };
      }),
    };

    await customerStore.updateCustomer(customerId.value, customerData);
    toast.add({
      severity: 'success',
      summary: 'Thành công',
      detail: 'Cập nhật hồ sơ thành công',
      life: 3000,
    });

  } catch (error) {
    const errorMessage = error.response?.data?.message || error.message || 'Đã xảy ra lỗi khi lưu thông tin hồ sơ';
    toast.add({
      severity: 'error',
      summary: 'Lỗi',
      detail: errorMessage,
      life: 5000,
    });
  }
};

// Navigate back
const goBack = () => {
  router.push('/shop'); // Or wherever you want to go back to
};
</script>

<style scoped>
.profile-page p {
  background-color: #f9fafb;
  padding: 0.75rem;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

/* Custom transitions */
.p-card {
  transition: all 0.3s ease;
}

/* Improved input focus */
.p-inputtext:focus, .p-dropdown:focus, .p-calendar:focus {
  box-shadow: 0 0 0 0.2rem rgba(99, 102, 241, 0.25);
  border-color: #6366f1;
}

/* Better dropdown styling */
.p-dropdown-panel .p-dropdown-items .p-dropdown-item {
  padding: 0.5rem 1rem;
}

.p-dropdown-panel .p-dropdown-items .p-dropdown-item:hover {
  background-color: #f9fafb;
}

/* Responsive adjustments */
@media (max-width: 768px) {
  .p-divider .p-divider-content {
    background: white;
    padding: 0 0.5rem;
  }
}
</style>