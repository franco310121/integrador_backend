package com.stockmaster.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "alertas_liquidez")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertaLiquidez {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyeccion_id")
    private ProyeccionFlujo proyeccion;

    @Column(name = "nivel_riesgo", nullable = false, length = 20)
    private String nivelRiesgo; // "bajo", "medio", "critico"

    @Column(name = "mensaje_alerta", nullable = false, columnDefinition = "TEXT")
    private String mensajeAlerta;

    @Column(name = "recomendacion_preventiva", columnDefinition = "TEXT")
    private String recomendacionPreventiva;

    @Column(name = "fecha_alerta", updatable = false)
    private LocalDateTime fechaAlerta;

    @Builder.Default
    private Boolean leido = false;

    @PrePersist
    protected void onCreate() {
        this.fechaAlerta = LocalDateTime.now();
        if (this.leido == null) {
            this.leido = false;
        }
    }
}
