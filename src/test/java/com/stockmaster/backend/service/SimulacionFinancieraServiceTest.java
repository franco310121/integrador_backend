package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.SimulacionFinancieraDto;
import com.stockmaster.backend.entity.SimulacionFinanciera;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.SimulacionFinancieraRepository;
import com.stockmaster.backend.repository.TransaccionCajaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SimulacionFinancieraServiceTest {

    @Mock
    private SimulacionFinancieraRepository simulacionFinancieraRepository;

    @Mock
    private TransaccionCajaRepository transaccionCajaRepository;

    @InjectMocks
    private SimulacionFinancieraService simulacionFinancieraService;

    @Test
    void testCrearSimulacion_EscenarioOptimista_CalculaImpactoPositivo() {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(20000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(10000));

        when(simulacionFinancieraRepository.save(any(SimulacionFinanciera.class))).thenAnswer(inv -> {
            SimulacionFinanciera s = inv.getArgument(0);
            s.setId(1L);
            s.setFechaSimulacion(LocalDateTime.now());
            return s;
        });

        SimulacionFinancieraDto.Request request = SimulacionFinancieraDto.Request.builder()
                .nombreEscenario("Aumento de ventas 20%")
                .porcentajeAjusteIngresos(BigDecimal.valueOf(20))
                .porcentajeAjusteEgresos(BigDecimal.valueOf(5))
                .build();

        SimulacionFinancieraDto.Response response = simulacionFinancieraService.crearSimulacion(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(new BigDecimal("24000.00"), response.getIngresosSimulados());
        assertEquals(new BigDecimal("10500.00"), response.getEgresosSimulados());
        assertEquals(new BigDecimal("13500.00"), response.getSaldoSimulado());
        assertEquals(new BigDecimal("3500.00"), response.getVariacionSaldo());
        assertEquals("POSITIVO", response.getImpactoLiquidez());
        assertNotNull(response.getRecomendacion());
    }

    @Test
    void testCrearSimulacion_EscenarioDeficit_DetectaRiesgoDeficit() {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(10000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(9000));

        when(simulacionFinancieraRepository.save(any(SimulacionFinanciera.class))).thenAnswer(inv -> {
            SimulacionFinanciera s = inv.getArgument(0);
            s.setId(2L);
            return s;
        });

        SimulacionFinancieraDto.Request request = SimulacionFinancieraDto.Request.builder()
                .nombreEscenario("Caída de ingresos -30% y subida costos +20%")
                .porcentajeAjusteIngresos(BigDecimal.valueOf(-30))
                .porcentajeAjusteEgresos(BigDecimal.valueOf(20))
                .build();

        SimulacionFinancieraDto.Response response = simulacionFinancieraService.crearSimulacion(request);

        assertEquals(new BigDecimal("7000.00"), response.getIngresosSimulados());
        assertEquals(new BigDecimal("10800.00"), response.getEgresosSimulados());
        assertEquals(new BigDecimal("-3800.00"), response.getSaldoSimulado());
        assertEquals("RIESGO_DEFICIT", response.getImpactoLiquidez());
    }

    @Test
    void testListarYEliminarSimulacion() {
        SimulacionFinanciera sim = SimulacionFinanciera.builder()
                .id(5L)
                .nombreEscenario("Escenario Base")
                .porcentajeAjusteIngresos(BigDecimal.TEN)
                .porcentajeAjusteEgresos(BigDecimal.ZERO)
                .resultadoSimulacion("{}")
                .build();

        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(5000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(3000));
        when(simulacionFinancieraRepository.findByOrderByFechaSimulacionDesc()).thenReturn(List.of(sim));
        when(simulacionFinancieraRepository.findById(5L)).thenReturn(Optional.of(sim));

        List<SimulacionFinancieraDto.Response> lista = simulacionFinancieraService.listarSimulaciones();
        assertEquals(1, lista.size());

        simulacionFinancieraService.eliminarSimulacion(5L);
        verify(simulacionFinancieraRepository).delete(sim);
    }

    @Test
    void testObtenerPorId_NoExiste_LanzaExcepcion() {
        when(simulacionFinancieraRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> simulacionFinancieraService.obtenerPorId(99L));
    }

    @Test
    void simulacionConReduccionDeLiquidezSinDeficit() {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(10000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(5000));
        when(simulacionFinancieraRepository.save(any(SimulacionFinanciera.class))).thenAnswer(i -> i.getArgument(0));
        var resultado = simulacionFinancieraService.crearSimulacion(SimulacionFinancieraDto.Request.builder()
                .nombreEscenario("Reduccion").porcentajeAjusteIngresos(BigDecimal.valueOf(-20)).build());
        assertEquals(new BigDecimal("3000.00"), resultado.getSaldoSimulado());
        assertEquals(new BigDecimal("-2000.00"), resultado.getVariacionSaldo());
        assertEquals("REDUCCION_LIQUIDEZ", resultado.getImpactoLiquidez());
    }

    @Test
    void simulacionSinAjustesMantieneSaldo() {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(1000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(400));
        when(simulacionFinancieraRepository.save(any(SimulacionFinanciera.class))).thenAnswer(i -> i.getArgument(0));
        var resultado = simulacionFinancieraService.crearSimulacion(SimulacionFinancieraDto.Request.builder()
                .nombreEscenario(" Base ").build());
        assertEquals("Base", resultado.getNombreEscenario());
        assertEquals(new BigDecimal("600.00"), resultado.getSaldoSimulado());
        assertEquals(new BigDecimal("0.00"), resultado.getVariacionSaldo());
        assertEquals("POSITIVO", resultado.getImpactoLiquidez());
    }

    @Test
    void eliminarSimulacionInexistenteNoEliminaDatos() {
        when(simulacionFinancieraRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> simulacionFinancieraService.eliminarSimulacion(99L));
        verify(simulacionFinancieraRepository, never()).delete(any());
    }
}

