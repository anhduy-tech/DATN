package com.lapxpert.backend.giaoca.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoCaRequest {
    private BigDecimal tienMatDauCa;
    private BigDecimal chuyenKhoanDauCa;
}
