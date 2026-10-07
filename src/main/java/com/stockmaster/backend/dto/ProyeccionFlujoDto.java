package com.stockmaster.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProyeccionFlujoDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String tipoProyeccion; // "semanal", "mensual"
        private LocalDate periodoInicio;
        private LocalDate periodoFin;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long modeloId;
        private LocalDate periodoInicio;
        private LocalDate periodoFin;
        private String tipoProyeccion;
        private BigDecimal ingresoProyectado;
        private BigDecimal egresoProyectado;
        private BigDecimal saldoFinalEstimado;
        private BigDecimal nivelConfianza;
        private LocalDateTime fechaGeneracion;
    }
}
