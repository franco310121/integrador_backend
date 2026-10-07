package com.stockmaster.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardFinancieroDto {
    private EmpresaDto.Response empresa;
    private BigDecimal saldoActualCaja;
    private BigDecimal totalIngresosMes;
    private BigDecimal totalEgresosMes;
    private List<ProyeccionFlujoDto.Response> proyeccionesSemanales;
    private List<ProyeccionFlujoDto.Response> proyeccionesMensuales;
    private List<AlertaLiquidezDto.Response> alertasLiquidezActivas;
    private String nivelRiesgoGlobal;
}
