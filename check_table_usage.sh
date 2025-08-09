#!/bin/bash
tables=(
bo_nho
chuyen_doi_trang_thai_hoa_don
cpu
danh_muc
databasechangelog
databasechangeloglock
dot_giam_gia
dot_giam_gia_audit_history
gpu
hoa_don
hoa_don_audit_history
hoa_don_chi_tiet
hoa_don_phieu_giam_gia
hoa_don_thanh_toan
man_hinh
mau_sac
nguoi_dung
nguoi_dung_audit_history
phieu_giam_gia
phieu_giam_gia_audit_history
phieu_giam_gia_nguoi_dung
ram
san_pham
san_pham_audit_history
san_pham_chi_tiet
san_pham_chi_tiet_audit_history
san_pham_chi_tiet_dot_giam_gia
san_pham_danh_muc
san_pham_embeddings
serial_number
serial_number_audit_history
serial_number_hoa_don_chi_tiet
thanh_toan
thuong_hieu
dia_chi
gio_hang
gio_hang_chi_tiet
danh_sach_yeu_thich
danh_gia
)

SRC_DIR="src/" 

used=()
unused=()

for table in "${tables[@]}"; do
    entity_name=$(echo "$table" | awk -F'_' '{for(i=1;i<=NF;i++) $i=toupper(substr($i,1,1)) substr($i,2)} 1' OFS='')
    if grep -r -i -q --include="*.java" --include="*.kt" --include="*.xml" --include="*.sql" "$table" "$SRC_DIR" || \
       grep -r -i -q --include="*.java" "$entity_name" "$SRC_DIR"
    then
        used+=("$table")
    else
        unused+=("$table")
    fi
done

echo "==== USED TABLES ===="
for t in "${used[@]}"; do echo "$t"; done

echo ""
echo "==== UNUSED TABLES ===="
for t in "${unused[@]}"; do echo "$t"; done