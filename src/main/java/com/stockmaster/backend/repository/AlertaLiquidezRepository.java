package com.stockmaster.backend.repository;

import com.stockmaster.backend.entity.AlertaLiquidez;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertaLiquidezRepository extends JpaRepository<AlertaLiquidez, Long> {
    List<AlertaLiquidez> findByLeidoFalse();
    List<AlertaLiquidez> findByNivelRiesgo(String nivelRiesgo);
    List<AlertaLiquidez> findByOrderByFechaAlertaDesc();
}
