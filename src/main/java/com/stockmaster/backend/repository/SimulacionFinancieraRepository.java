package com.stockmaster.backend.repository;

import com.stockmaster.backend.entity.SimulacionFinanciera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulacionFinancieraRepository extends JpaRepository<SimulacionFinanciera, Long> {
    List<SimulacionFinanciera> findByUsuarioId(Long usuarioId);
    List<SimulacionFinanciera> findByOrderByFechaSimulacionDesc();
}
