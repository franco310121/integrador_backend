package com.stockmaster.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlujoCajaHistoricoDto {
    private BigDecimal totalIngresos;
    private BigDecimal totalEgresos;
    private BigDecimal saldoNetoHistorico;
    private Long cantidadTransacciones;
    private List<TransaccionCajaDto.Response> transacciones;
}
