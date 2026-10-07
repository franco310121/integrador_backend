package com.stockmaster.backend.repository;

import com.stockmaster.backend.entity.TransaccionCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransaccionCajaRepository extends JpaRepository<TransaccionCaja, Long> {
    List<TransaccionCaja> findByTipo(String tipo);
    List<TransaccionCaja> findByEstado(String estado);
    List<TransaccionCaja> findByFechaTransaccionBetween(LocalDateTime inicio, LocalDateTime fin);

    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM TransaccionCaja t WHERE t.tipo = :tipo AND t.estado = 'efectivizado'")
    BigDecimal sumMontoByTipo(@Param("tipo") String tipo);

    @Query("SELECT COALESCE(SUM(t.monto), 0) FROM TransaccionCaja t WHERE t.tipo = :tipo AND t.estado = 'efectivizado' AND t.fechaTransaccion BETWEEN :inicio AND :fin")
    BigDecimal sumMontoByTipoAndFechaBetween(@Param("tipo") String tipo, @Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    @Query("SELECT t FROM TransaccionCaja t WHERE t.estado = 'efectivizado' ORDER BY t.fechaTransaccion DESC")
    List<TransaccionCaja> findHistorico();
}
