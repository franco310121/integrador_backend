package com.stockmaster.backend.dto;

import lombok.*;

import java.util.List;

public class ImportarFinancieroDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegistroImportacion {
        private String tipo; // "ingreso", "egreso"
        private String categoriaGasto;
        private Double monto;
        private String fechaTransaccion; // ISO string o "yyyy-MM-dd"
        private String observaciones;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private List<RegistroImportacion> registros;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private int procesadosExitosos;
        private int registrosConError;
        private List<String> detallesError;
        private String mensaje;
    }
}
