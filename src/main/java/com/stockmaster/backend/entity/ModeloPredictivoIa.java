package com.stockmaster.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "modelo_predictivo_ia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModeloPredictivoIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_modelo")
    private Long idModelo;

    @Column(name = "version_algoritmo", nullable = false, length = 50)
    private String versionAlgoritmo;

    @Column(name = "precision_historica", precision = 5, scale = 2)
    private BigDecimal precisionHistorica;

    @Column(name = "fecha_entrenamiento", updatable = false)
    private LocalDateTime fechaEntrenamiento;

    @PrePersist
    protected void onCreate() {
        this.fechaEntrenamiento = LocalDateTime.now();
    }
}
