package com.stockmaster.backend.controller;

import com.stockmaster.backend.dto.SimulacionFinancieraDto;
import com.stockmaster.backend.service.SimulacionFinancieraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulaciones-financieras")
@RequiredArgsConstructor
public class SimulacionFinancieraController {

    private final SimulacionFinancieraService simulacionFinancieraService;

    @PostMapping
    public ResponseEntity<SimulacionFinancieraDto.Response> crearSimulacion(@Valid @RequestBody SimulacionFinancieraDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulacionFinancieraService.crearSimulacion(request));
    }

    @GetMapping
    public ResponseEntity<List<SimulacionFinancieraDto.Response>> listarSimulaciones() {
        return ResponseEntity.ok(simulacionFinancieraService.listarSimulaciones());
    }
}
