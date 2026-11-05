package com.stockmaster.backend.performance;

import com.stockmaster.backend.dto.RendimientoSistemaDto;
import com.stockmaster.backend.dto.SimulacionFinancieraDto;
import com.stockmaster.backend.dto.TransaccionCajaDto;
import com.stockmaster.backend.service.FlujoCajaIaService;
import com.stockmaster.backend.service.SimulacionFinancieraService;
import com.stockmaster.backend.service.TransaccionCajaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 20: Evaluar el rendimiento y escalabilidad para comprobar que el sistema
 * responda adecuadamente ante diferentes cargas.
 */
@SpringBootTest
@ActiveProfiles("dev")
class RendimientoEscalabilidadTest {

    @Autowired
    private FlujoCajaIaService flujoCajaIaService;

    @Autowired
    private TransaccionCajaService transaccionCajaService;

    @Autowired
    private SimulacionFinancieraService simulacionFinancieraService;

    @Test
    void testEvaluarRendimientoSistema_BajoDiferentesCargas() {
        // Carga ligera (10 iteraciones)
        RendimientoSistemaDto cargaLigera = flujoCajaIaService.evaluarRendimientoSistema(10);
        assertNotNull(cargaLigera);
        assertTrue(cargaLigera.getLatenciaTotalMs() < 3000, "La latencia en carga ligera debe ser menor a 3000 ms");
        assertTrue(cargaLigera.getThroughputOperacionesPorSegundo() > 0);

        // Carga media/alta (50 iteraciones)
        RendimientoSistemaDto cargaAlta = flujoCajaIaService.evaluarRendimientoSistema(50);
        assertNotNull(cargaAlta);
        assertEquals(50, cargaAlta.getIteracionesEvaluadas());
        assertTrue(cargaAlta.getLatenciaTotalMs() < 5000, "La latencia en carga alta debe ser menor a 5000 ms");
        assertNotNull(cargaAlta.getLatenciaPorModuloMs().get("transaccionesCajaMs"));
        assertNotNull(cargaAlta.getLatenciaPorModuloMs().get("proyeccionesIaMs"));
    }

    @Test
    void testEscalabilidadConcurrente_MultiplesHilosSimultaneos() throws InterruptedException, ExecutionException, TimeoutException {
        // Sembrar datos iniciales de caja
        transaccionCajaService.registrarTransaccion(TransaccionCajaDto.Request.builder()
                .tipo("ingreso")
                .categoriaGasto("Ventas")
                .monto(BigDecimal.valueOf(18000))
                .observaciones("Carga inicial")
                .build());

        int numHilos = 10;
        int tareasTotales = 30;
        ExecutorService executor = Executors.newFixedThreadPool(numHilos);
        List<Future<Long>> futuros = new ArrayList<>();

        long inicioGlobal = System.currentTimeMillis();

        for (int i = 0; i < tareasTotales; i++) {
            final int index = i;
            futuros.add(executor.submit(() -> {
                long t0 = System.currentTimeMillis();
                if (index % 3 == 0) {
                    flujoCajaIaService.generarProyeccion("semanal");
                } else if (index % 3 == 1) {
                    simulacionFinancieraService.crearSimulacion(SimulacionFinancieraDto.Request.builder()
                            .nombreEscenario("Simulación Concurrente #" + index)
                            .porcentajeAjusteIngresos(BigDecimal.valueOf(10))
                            .porcentajeAjusteEgresos(BigDecimal.valueOf(5))
                            .build());
                } else {
                    flujoCajaIaService.obtenerRecomendacionesPreventivas(null);
                }
                return System.currentTimeMillis() - t0;
            }));
        }

        for (Future<Long> f : futuros) {
            long tiempoTarea = f.get(10, TimeUnit.SECONDS);
            assertTrue(tiempoTarea < 5000, "Cada operación concurrente debe responder en menos de 5 segundos");
        }

        executor.shutdown();
        long duracionGlobal = System.currentTimeMillis() - inicioGlobal;
        assertTrue(duracionGlobal < 15000, "Las 30 operaciones concurrentes deben completarse en menos de 15 segundos");
    }
}

