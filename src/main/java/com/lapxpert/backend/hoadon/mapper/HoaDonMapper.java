package com.lapxpert.backend.hoadon.mapper;

import com.lapxpert.backend.hoadon.dto.HoaDonDto;
import com.lapxpert.backend.hoadon.entity.HoaDon;
import com.lapxpert.backend.nguoidung.mapper.DiaChiMapper;
import com.lapxpert.backend.nguoidung.mapper.NguoiDungMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(componentModel = "spring", uses = {HoaDonChiTietMapper.class, DiaChiMapper.class, NguoiDungMapper.class})
public interface HoaDonMapper {

    HoaDonMapper INSTANCE = Mappers.getMapper(HoaDonMapper.class);

    @Mapping(source = "khachHang.id", target = "khachHangId")
    @Mapping(source = "nhanVien.id", target = "nhanVienId")
    @Mapping(source = "diaChiGiaoHang.id", target = "diaChiGiaoHangId")
    @Mapping(source = "diaChiGiaoHang", target = "diaChiGiaoHang")
    @Mapping(source = "hoaDonChiTiets", target = "chiTiet")
    @Mapping(source = "khachHang", target = "khachHang")
    @Mapping(source = "nhanVien", target = "nhanVien")
    @Mapping(target = "voucherCodes", ignore = true)
    @Mapping(target = "phuongThucThanhToan", ignore = true) // Payment method populated by service layer from ThanhToan records
    @Mapping(target = "isMixedPayment", ignore = true) // Mixed payment flag populated by service layer
    @Mapping(target = "mixedPayments", ignore = true) // Mixed payment configuration populated by service layer
    @Mapping(source = "nguoiNhanEmail", target = "nguoiNhanEmail")
    HoaDonDto toDto(HoaDon hoaDon);

    @Mapping(source = "khachHangId", target = "khachHang.id")
    @Mapping(source = "nhanVienId", target = "nhanVien.id")
    @Mapping(source = "diaChiGiaoHangId", target = "diaChiGiaoHang.id")
    @Mapping(source = "chiTiet", target = "hoaDonChiTiets")
    @Mapping(target = "nguoiCapNhat", ignore = true)
    @Mapping(target = "nguoiTao", ignore = true)
    @Mapping(target = "hoaDonPhieuGiamGias", ignore = true)
    @Mapping(target = "maVanDon", ignore = true)
    @Mapping(target = "ngayDuKienGiaoHang", ignore = true)
    @Mapping(target = "khachHang", ignore = true) // Full object ignored for entity mapping
    @Mapping(target = "nhanVien", ignore = true) // Full object ignored for entity mapping
    @Mapping(source = "nguoiNhanEmail", target = "nguoiNhanEmail")
    HoaDon toEntity(HoaDonDto hoaDonDto);

    List<HoaDonDto> toDtoList(List<HoaDon> hoaDonList);

    List<HoaDon> toEntityList(List<HoaDonDto> hoaDonDtoList);
}
