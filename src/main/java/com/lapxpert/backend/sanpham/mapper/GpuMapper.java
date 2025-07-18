package com.lapxpert.backend.sanpham.mapper;

import com.lapxpert.backend.sanpham.dto.thuoctinh.GpuDto;
import com.lapxpert.backend.sanpham.entity.thuoctinh.Gpu;
import org.mapstruct.Mapper;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring")
public interface GpuMapper {
    GpuDto toDto(Gpu gpu);

    List<GpuDto> toDtos(List<Gpu> entities);

    Set<GpuDto> toDtoSet(Set<Gpu> entities);

    Gpu toEntity(GpuDto dto);
}
