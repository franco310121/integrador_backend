package com.stockmaster.backend.repository;

import com.stockmaster.backend.entity.ModeloPredictivoIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ModeloPredictivoIaRepository extends JpaRepository<ModeloPredictivoIa, Long> {
    Optional<ModeloPredictivoIa> findFirstByOrderByIdModeloDesc();
}
