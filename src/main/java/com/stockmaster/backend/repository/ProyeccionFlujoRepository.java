package com.stockmaster.backend.repository;

import com.stockmaster.backend.entity.ProyeccionFlujo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProyeccionFlujoRepository extends JpaRepository<ProyeccionFlujo, Long> {
    List<ProyeccionFlujo> findByTipoProyeccion(String tipoProyeccion);
    List<ProyeccionFlujo> findByOrderByPeriodoInicioAsc();
}
