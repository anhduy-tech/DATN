<template>
  <div class="flex flex-col sm:flex-row items-center gap-4 mt-4 mb-5">
    <label
      for="uploadFile"
      class="flex items-center gap-2 cursor-pointer bg-blue-500 hover:bg-blue-600 text-white py-2 px-4 rounded-xl transition duration-300"
    >
      <i class="pi pi-upload"></i>
      <span>Chọn File Excel</span>
      <input id="uploadFile" type="file" class="hidden" accept=".xlsx" @change="handleFileChange" />
    </label>

    <Button
      label="Tải lên"
      icon="pi pi-check"
      class="p-button-success"
      @click="uploadFile"
      :disabled="!selectedFile"
    />
  </div>

  <div class="card mb-6">
    <table class="table-auto w-full text-black">
      <thead>
        <tr class="bg-gray-200">
          <th class="border px-4 py-2">#</th>
          <th class="border px-4 py-2">Ngày</th>
          <th class="border px-4 py-2">Thời gian</th>
          <th class="border px-4 py-2">Nhân viên phụ trách</th>
          <th class="border px-4 py-2">Thao tác</th>
        </tr>
      </thead>
      <tbody>
        <!-- Hiển thị khi đang tải dữ liệu -->
        <tr v-if="loading">
          <td colspan="4" class="text-center py-4">Đang tải dữ liệu...</td>
        </tr>

        <!-- Hiển thị khi có dữ liệu -->
        <tr v-else-if="lichLamViec.length > 0" v-for="(llv, index) in lichLamViec" :key="llv.id">
          <td class="border px-4 py-2 text-center">{{ index + 1 }}</td>
          <td class="border px-4 py-2 text-center">{{ llv.ngay }}</td>
          <td class="border px-4 py-2 text-center">{{ llv.thoiGian }}</td>
          <td class="border px-4 py-2 text-center">{{ llv.nhanVienPhuTrach }}</td>
          <td class="border px-4 py-2 text-center">
            <Button icon="pi pi-pencil" severity="info" text rounded @click="openEditDialog(llv)" />
            <Button icon="pi pi-trash" severity="danger" @click="deleteLichLamViec(llv.id)" />
          </td>
        </tr>

        <!-- Hiển thị khi không có dữ liệu (và không loading) -->
        <tr v-else>
          <td colspan="4" class="text-center py-4">Không có dữ liệu lịch làm việc</td>
        </tr>
      </tbody>
    </table>
  </div>
  <Dialog
    v-model:visible="showEditDialog"
    modal
    header="Cập nhật lịch làm việc"
    :style="{ width: '30vw' }"
  >
    <div class="flex flex-col gap-4">
      <div>
        <label>Ngày:</label>
        <input
          v-model="editData.ngay"
          type="text"
          class="border rounded p-2 w-full"
          placeholder="dd-mm-yyyy"
        />
      </div>
      <div>
        <label>Thời gian:</label>
        <select v-model="editData.thoiGian" class="border rounded p-2 w-full">
          <option value="8h-15h">8h-15h</option>
          <option value="15h-22h">15h-22h</option>
        </select>
      </div>
      <div>
        <label>Nhân viên phụ trách:</label>
        <input v-model="editData.nhanVienPhuTrach" type="text" class="border rounded p-2 w-full" />
      </div>
      <div class="flex justify-end gap-2 mt-4">
        <Button label="Hủy" severity="secondary" @click="showEditDialog = false" />
        <Button label="Cập nhật" severity="success" @click="updateLichLamViec" />
      </div>
    </div>
  </Dialog>
</template>

<script>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { LichLamViecAPI } from '@/apis/workSchedule'
import Toolbar from 'primevue/toolbar'
import Button from 'primevue/button'
import ToggleButton from 'primevue/togglebutton'
import Toast from 'primevue/toast'
import ConfirmDialog from 'primevue/confirmdialog'
import * as XLSX from 'xlsx'

export default {
  components: {
    Toolbar,
    Button,
    ToggleButton,
    Toast,
    ConfirmDialog,
  },
  setup() {
    const toast = useToast()
    const hienThiThanhCong = (message) => {
      toast.add({
        severity: 'success',
        summary: 'Thành công',
        detail: message,
        life: 3000,
      })
    }
    const hienThiLoi = (message) => {
      toast.add({
        severity: 'error',
        summary: 'Lỗi',
        detail: message,
        life: 5000,
      })
    }
    return {
      hienThiThanhCong,
      hienThiLoi,
      toast,
    }
  },
  data() {
    return {
      selectedFile: null,
      lichLamViec: [],
      loading: false,
      editData: {
        id: null,
        ngay: '',
        thoiGian: '',
        nhanVienPhuTrach: '',
      },
      showEditDialog: false,
    }
  },
  methods: {
    handleFileChange(event) {
      this.selectedFile = event.target.files[0]
    },
    async uploadFile() {
      try {
        if (!this.selectedFile) {
          this.hienThiLoi('Vui lòng chọn file')
          return
        }
        const res = await LichLamViecAPI.importExcel(this.selectedFile)
        this.hienThiThanhCong(res.data)
        this.loadLichLamViec()
      } catch (err) {
        this.hienThiLoi(err.response?.data || 'Lỗi khi tải file')
      }
    },
    async loadLichLamViec() {
      this.loading = true
      try {
        const response = await LichLamViecAPI.getAllLLVs()
        this.lichLamViec = response.data
      } catch (error) {
        console.error('Lỗi khi tải dữ liệu:', error)
      } finally {
        this.loading = false
      }
    },
    openEditDialog(llv) {
      this.editData = { ...llv }
      this.showEditDialog = true
    },
    async updateLichLamViec() {
      try {
        const res = await LichLamViecAPI.updateLichLamViec(this.editData.id, this.editData)
        this.hienThiThanhCong('Cập nhật thành công!')
        this.showEditDialog = false
        this.loadLichLamViec()
      } catch (error) {
        this.hienThiLoi(error.response?.data || 'Lỗi khi cập nhật')
      }
    },
    fetchData() {
      // gọi API để lấy dữ liệu mới nhất
      LichLamViecAPI.getAllLLVs()
        .then((response) => {
          this.lichLamViecs = response.data
        })
        .catch((error) => {
          console.error('Lỗi khi tải dữ liệu:', error)
        })
    },

    deleteLichLamViec(id) {
      this.$confirm.require({
        message: 'Bạn có chắc chắn muốn xóa lịch làm việc này?',
        header: 'Xác nhận',
        icon: 'pi pi-exclamation-triangle',
        acceptLabel: 'Đồng ý',
        rejectLabel: 'Hủy',
        accept: () => {
          LichLamViecAPI.deleteLichLamViec(id)
            .then(() => {
              this.hienThiThanhCong('Xóa thành công!')
              this.fetchData() // lỗi nằm ở đây nếu fetchData không tồn tại
            })
            .catch((error) => {
              this.hienThiLoi('Xóa thất bại!')
              console.error('Lỗi khi xóa:', error)
            })
        },
      })
    },
  },
  mounted() {
    this.loadLichLamViec() 
    this.fetchData()// <-- GỌI HÀM Ở ĐÂY
  },
}
</script>
