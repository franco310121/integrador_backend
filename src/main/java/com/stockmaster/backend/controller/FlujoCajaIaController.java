package com.stockmaster.backend.controller;

import com.stockmaster.backend.dto.AlertaLiquidezDto;
import com.stockmaster.backend.dto.DashboardFinancieroDto;
import com.stockmaster.backend.dto.DesempenoModeloDto;
import com.stockmaster.backend.dto.ProyeccionFlujoDto;
import com.stockmaster.backend.dto.RecomendacionPreventivaDto;
import com.stockmaster.backend.dto.RendimientoSistemaDto;
import com.stockmaster.backend.service.FlujoCajaIaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flujo-caja-ia")
@RequiredArgsConstructor
public class FlujoCajaIaController {

    private final FlujoCajaIaService flujoCajaIaService;

    @PostMapping("/proyecciones")
    public ResponseEntity<ProyeccionFlujoDto.Response> generarProyeccion(@RequestParam(defaultValue = "semanal") String tipo) {
        return ResponseEntity.ok(flujoCajaIaService.generarProyeccion(tipo));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardFinancieroDto> getDashboard() {
        return ResponseEntity.ok(flujoCajaIaService.getDashboardFinanciero());
    }

    @GetMapping("/alertas")
    public ResponseEntity<List<AlertaLiquidezDto.Response>> getAlertas() {
        return ResponseEntity.ok(flujoCajaIaService.getAlertas());
    }

    @PatchMapping("/alertas/{id}/leer")
    public ResponseEntity<AlertaLiquidezDto.Response> marcarAlertaLeida(@PathVariable Long id) {
        return ResponseEntity.ok(flujoCajaIaService.marcarAlertaComoLeida(id));
    }

    @GetMapping("/recomendaciones")
    public ResponseEntity<RecomendacionPreventivaDto.Response> getRecomendaciones(
            @RequestParam(required = false) String nivelRiesgo) {
        return ResponseEntity.ok(flujoCajaIaService.obtenerRecomendacionesPreventivas(nivelRiesgo));
    }

    @GetMapping("/modelo/desempeno")
    public ResponseEntity<DesempenoModeloDto> getDesempenoModelo() {
        return ResponseEntity.ok(flujoCajaIaService.validarDesempenoModelo());
    }

    @PostMapping("/modelo/validar")
    public ResponseEntity<DesempenoModeloDto> validarModelo() {
        return ResponseEntity.ok(flujoCajaIaService.validarDesempenoModelo());
    }

    @GetMapping("/rendimiento")
    public ResponseEntity<RendimientoSistemaDto> evaluarRendimiento(
            @RequestParam(defaultValue = "20") int iteraciones) {
        return ResponseEntity.ok(flujoCajaIaService.evaluarRendimientoSistema(iteraciones));
    }
}
