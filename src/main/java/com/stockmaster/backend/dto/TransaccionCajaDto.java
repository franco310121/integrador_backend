package com.stockmaster.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TransaccionCajaDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "El tipo es obligatorio (ingreso o egreso)")
        private String tipo;

        private String categoriaGasto;

        @NotNull(message = "El monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        private BigDecimal monto;

        private LocalDateTime fechaTransaccion;
        private String observaciones;
        private Long ventaId;
        private Long compraId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String tipo;
        private String categoriaGasto;
        private BigDecimal monto;
        private LocalDateTime fechaTransaccion;
        private String estado;
        private String observaciones;
        private Long ventaId;
        private Long compraId;
        private LocalDateTime fechaCreacion;
    }
}
