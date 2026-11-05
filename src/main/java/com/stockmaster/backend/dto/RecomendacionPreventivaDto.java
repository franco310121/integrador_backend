package com.stockmaster.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

public class RecomendacionPreventivaDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccionSugerida {
        private String prioridad; // "ALTA", "MEDIA", "BAJA"
        private String area;      // "COBRANZAS", "GASTOS", "INVENTARIO", "TESORERIA"
        private String titulo;
        private String descripcion;
        private String impactoEsperado;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String nivelRiesgoActual;
        private BigDecimal saldoActualCaja;
        private BigDecimal ratioCoberturaLiquidez;
        private String resumenEjecutivo;
        private List<AccionSugerida> accionesSugeridas;
        private List<AlertaLiquidezDto.Response> alertasActivas;
    }
}

