package com.stockmaster.backend.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RendimientoSistemaDto {
    private String estadoSistema; // "OPTIMO", "ESTABLE", "CARGA_ELEVADA"
    private long latenciaTotalMs;
    private double throughputOperacionesPorSegundo;
    private int iteracionesEvaluadas;
    private long memoriaUsadaMb;
    private long memoriaTotalMb;
    private long memoriaMaximaMb;
    private int hilosActivos;
    private int procesadoresDisponibles;
    private Map<String, Long> latenciaPorModuloMs;
    private LocalDateTime fechaEvaluacion;
}

