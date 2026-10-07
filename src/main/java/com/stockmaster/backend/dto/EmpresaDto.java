package com.stockmaster.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

public class EmpresaDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "El nombre de la MYPE es obligatorio")
        private String nombreMype;

        @NotBlank(message = "El RUC es obligatorio")
        private String ruc;

        private String direccion;
        private String telefono;
        private String correo;
        private String sectorComercial;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String nombreMype;
        private String ruc;
        private String direccion;
        private String telefono;
        private String correo;
        private String sectorComercial;
        private LocalDateTime fechaCreacion;
    }
}
