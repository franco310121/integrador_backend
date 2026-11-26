package com.stockmaster.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DesempenoModeloDto {
    private Long idModelo;
    private String versionAlgoritmo;
    private LocalDateTime fechaEntrenamiento;
    private BigDecimal precisionHistorica;
    private BigDecimal precisionActualCalculada;
    private BigDecimal errorPorcentualMedio;
    private BigDecimal mae;
    private BigDecimal rmse;
    private Integer observacionesEvaluadas;
    private Integer observacionesMape;
    private BigDecimal nivelConfianzaPromedio;
    private Integer totalProyeccionesEvaluadas;
    private BigDecimal ingresosRealesAcumulados;
    private BigDecimal egresosRealesAcumulados;
    private BigDecimal ultimaProyeccionIngreso;
    private BigDecimal ultimaProyeccionEgreso;
    private String estadoSaludModelo; // "SIN_DATOS", "EVALUADO"
    private String mensajeValidacion;
    private LocalDateTime fechaValidacion;
}

