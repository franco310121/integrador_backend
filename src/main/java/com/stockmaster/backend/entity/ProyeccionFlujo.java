package com.stockmaster.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "proyecciones_flujo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProyeccionFlujo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modelo_id")
    private ModeloPredictivoIa modelo;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fin", nullable = false)
    private LocalDate periodoFin;

    @Column(name = "tipo_proyeccion", nullable = false, length = 20)
    private String tipoProyeccion; // "semanal", "mensual"

    @Column(name = "ingreso_proyectado", nullable = false, precision = 12, scale = 2)
    private BigDecimal ingresoProyectado;

    @Column(name = "egreso_proyectado", nullable = false, precision = 12, scale = 2)
    private BigDecimal egresoProyectado;

    @Column(name = "saldo_final_estimado", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoFinalEstimado;

    @Column(name = "nivel_confianza", precision = 5, scale = 2)
    private BigDecimal nivelConfianza;

    @Column(name = "fecha_generacion", updatable = false)
    private LocalDateTime fechaGeneracion;

    @PrePersist
    protected void onCreate() {
        this.fechaGeneracion = LocalDateTime.now();
    }
}
