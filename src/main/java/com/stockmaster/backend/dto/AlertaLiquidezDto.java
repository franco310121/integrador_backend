package com.stockmaster.backend.dto;

import lombok.*;

import java.time.LocalDateTime;

public class AlertaLiquidezDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long proyeccionId;
        private String nivelRiesgo; // "bajo", "medio", "critico"
        private String mensajeAlerta;
        private String recomendacionPreventiva;
        private LocalDateTime fechaAlerta;
        private Boolean leido;
    }
}
