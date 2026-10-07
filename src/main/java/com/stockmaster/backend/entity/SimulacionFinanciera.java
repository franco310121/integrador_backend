package com.stockmaster.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "simulaciones_financieras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulacionFinanciera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "nombre_escenario", nullable = false, length = 150)
    private String nombreEscenario;

    @Column(name = "porcentaje_ajuste_ingresos", precision = 5, scale = 2)
    private BigDecimal porcentajeAjusteIngresos;

    @Column(name = "porcentaje_ajuste_egresos", precision = 5, scale = 2)
    private BigDecimal porcentajeAjusteEgresos;

    @Column(name = "resultado_simulacion", columnDefinition = "TEXT")
    private String resultadoSimulacion;

    @Column(name = "fecha_simulacion", updatable = false)
    private LocalDateTime fechaSimulacion;

    @PrePersist
    protected void onCreate() {
        this.fechaSimulacion = LocalDateTime.now();
    }
}
