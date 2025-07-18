package com.lapxpert.backend.sanpham.mapper;

import com.lapxpert.backend.sanpham.dto.thuoctinh.ManHinhDto;
import com.lapxpert.backend.sanpham.entity.thuoctinh.ManHinh;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface ManHinhMapper {
    ManHinhDto toDto(ManHinh manHinh);

    List<ManHinhDto> toDtos(List<ManHinh> entities);

    Set<ManHinhDto> toDtoSet(Set<ManHinh> entities);

    ManHinh toEntity(ManHinhDto dto);
}
