package com.stockmaster.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SimulacionFinancieraDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "El nombre del escenario es obligatorio")
        private String nombreEscenario;

        private BigDecimal porcentajeAjusteIngresos;
        private BigDecimal porcentajeAjusteEgresos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String nombreEscenario;
        private BigDecimal porcentajeAjusteIngresos;
        private BigDecimal porcentajeAjusteEgresos;
        private String resultadoSimulacion;
        private LocalDateTime fechaSimulacion;
    }
}
