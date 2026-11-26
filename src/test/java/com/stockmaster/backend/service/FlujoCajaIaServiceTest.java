package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.AlertaLiquidezDto;
import com.stockmaster.backend.dto.DesempenoModeloDto;
import com.stockmaster.backend.dto.ProyeccionFlujoDto;
import com.stockmaster.backend.dto.RecomendacionPreventivaDto;
import com.stockmaster.backend.entity.AlertaLiquidez;
import com.stockmaster.backend.entity.ModeloPredictivoIa;
import com.stockmaster.backend.entity.ProyeccionFlujo;
import com.stockmaster.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlujoCajaIaServiceTest {

    @Mock
    private TransaccionCajaRepository transaccionCajaRepository;

    @Mock
    private ModeloPredictivoIaRepository modeloPredictivoIaRepository;

    @Mock
    private ProyeccionFlujoRepository proyeccionFlujoRepository;

    @Mock
    private AlertaLiquidezRepository alertaLiquidezRepository;

    @Mock
    private EmpresaRepository empresaRepository;

    @InjectMocks
    private FlujoCajaIaService flujoCajaIaService;

    @Test
    void testGenerarProyeccionSemanal_GeneraProyeccionYAlerta() {
        ModeloPredictivoIa modelo = ModeloPredictivoIa.builder()
                .idModelo(1L)
                .versionAlgoritmo("MEDIA-MOVIL-30D-v1")
                .precisionHistorica(BigDecimal.valueOf(94.50))
                .build();

        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(10000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(4000));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween(eq("ingreso"), any(), any())).thenReturn(BigDecimal.valueOf(10000));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween(eq("egreso"), any(), any())).thenReturn(BigDecimal.valueOf(4000));
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.save(any(ProyeccionFlujo.class))).thenAnswer(inv -> {
            ProyeccionFlujo p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProyeccionFlujoDto.Response response = flujoCajaIaService.generarProyeccion("semanal");

        assertNotNull(response);
        assertEquals("semanal", response.getTipoProyeccion());
        assertEquals(new BigDecimal("2333.33"), response.getIngresoProyectado());
        assertEquals(new BigDecimal("933.33"), response.getEgresoProyectado());
        assertEquals(new BigDecimal("7400.00"), response.getSaldoFinalEstimado());
        verify(alertaLiquidezRepository, times(1)).save(any(AlertaLiquidez.class));
    }

    @Test
    void testObtenerRecomendacionesPreventivas_RiesgoCritico() {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(2000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(3500));
        when(alertaLiquidezRepository.findByLeidoFalse()).thenReturn(List.of(
                AlertaLiquidez.builder()
                        .id(1L)
                        .nivelRiesgo("critico")
                        .mensajeAlerta("Déficit proyectado")
                        .recomendacionPreventiva("Postergar egresos")
                        .leido(false)
                        .build()
        ));

        RecomendacionPreventivaDto.Response rec = flujoCajaIaService.obtenerRecomendacionesPreventivas(null);

        assertNotNull(rec);
        assertEquals("critico", rec.getNivelRiesgoActual());
        assertEquals(new BigDecimal("-1500.00"), rec.getSaldoActualCaja());
        assertFalse(rec.getAccionesSugeridas().isEmpty());
        assertEquals("ALTA", rec.getAccionesSugeridas().get(0).getPrioridad());
    }

    @Test
    void testMarcarAlertaComoLeida() {
        AlertaLiquidez alerta = AlertaLiquidez.builder()
                .id(3L)
                .nivelRiesgo("medio")
                .mensajeAlerta("Margen ajustado")
                .leido(false)
                .build();

        when(alertaLiquidezRepository.findById(3L)).thenReturn(Optional.of(alerta));
        when(alertaLiquidezRepository.save(any(AlertaLiquidez.class))).thenAnswer(inv -> inv.getArgument(0));

        AlertaLiquidezDto.Response res = flujoCajaIaService.marcarAlertaComoLeida(3L);

        assertTrue(res.getLeido());
    }

    private ModeloPredictivoIa modeloVigente() {
        return ModeloPredictivoIa.builder().idModelo(1L).versionAlgoritmo("MEDIA-MOVIL-30D-v1").build();
    }

    private ProyeccionFlujo pronostico(ModeloPredictivoIa modelo, LocalDate inicio, long id) {
        return ProyeccionFlujo.builder().id(id).modelo(modelo).tipoProyeccion("semanal")
                .periodoInicio(inicio).periodoFin(inicio.plusDays(7))
                .fechaGeneracion(inicio.minusDays(1).atTime(12,0))
                .ingresoProyectado(new BigDecimal("300")).egresoProyectado(new BigDecimal("40")).build();
    }

    @Test
    void desempenoComparaMismoPeriodoSinLimitarError() {
        var modelo = modeloVigente();
        var inicio = LocalDate.now().minusDays(8);
        var p = pronostico(modelo,inicio,1);
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.findAll()).thenReturn(List.of(p));
        var desde = inicio.atStartOfDay();
        var hasta = inicio.plusDays(7).atStartOfDay().minusNanos(1000);
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween("ingreso",desde,hasta)).thenReturn(new BigDecimal("100"));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween("egreso",desde,hasta)).thenReturn(new BigDecimal("20"));
        var resultado = flujoCajaIaService.validarDesempenoModelo();
        assertEquals(new BigDecimal("150.00"),resultado.getErrorPorcentualMedio());
        assertEquals(new BigDecimal("110.00"),resultado.getMae());
        assertEquals(new BigDecimal("142.13"),resultado.getRmse());
        assertEquals(new BigDecimal("0.00"),resultado.getPrecisionActualCalculada());
        assertEquals("EVALUADO",resultado.getEstadoSaludModelo());
        assertNull(resultado.getNivelConfianzaPromedio());
        assertEquals(1,resultado.getTotalProyeccionesEvaluadas());
        assertEquals(2,resultado.getObservacionesMape());
    }

    @Test
    void desempenoSinPeriodosCerradosNoInventaMetricas() {
        var modelo = modeloVigente();
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.findAll()).thenReturn(List.of(pronostico(modelo,LocalDate.now().plusDays(1),1)));
        var resultado = flujoCajaIaService.validarDesempenoModelo();
        assertEquals("SIN_DATOS",resultado.getEstadoSaludModelo());
        assertEquals(0,resultado.getTotalProyeccionesEvaluadas());
        assertNull(resultado.getErrorPorcentualMedio());
        assertNull(resultado.getPrecisionActualCalculada());
        assertNull(resultado.getMae());
        verify(transaccionCajaRepository,never()).sumMontoByTipoAndFechaBetween(any(),any(),any());
    }

    @Test
    void desempenoExcluyeVersionAnteriorYDuplicadosYMapeConCeros() {
        var modelo = modeloVigente();
        var inicio = LocalDate.now().minusDays(8);
        var anterior = ModeloPredictivoIa.builder().versionAlgoritmo("ARIMA-v2.1").build();
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.findAll()).thenReturn(List.of(
                pronostico(anterior,inicio,1),pronostico(modelo,inicio,2),pronostico(modelo,inicio,3)));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween(any(),any(),any())).thenReturn(BigDecimal.ZERO);
        var resultado = flujoCajaIaService.validarDesempenoModelo();
        assertEquals(1,resultado.getTotalProyeccionesEvaluadas());
        assertEquals(2,resultado.getObservacionesEvaluadas());
        assertEquals(0,resultado.getObservacionesMape());
        assertNull(resultado.getErrorPorcentualMedio());
        assertEquals(new BigDecimal("170.00"),resultado.getMae());
    }

    @Test
    void rechazaPeriodoInvalidoSinConsultarDatos() {
        assertThrows(IllegalArgumentException.class, () -> flujoCajaIaService.generarProyeccion("anual"));
        verifyNoInteractions(transaccionCajaRepository, modeloPredictivoIaRepository,
                proyeccionFlujoRepository, alertaLiquidezRepository);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "500, 1000, -1000.00, critico",
        "1000, 1000, 0.00, medio",
        "1499, 1000, 998.00, medio",
        "1500, 1000, 1000.00, bajo"
    })
    void proyeccionMensualClasificaLimitesLiquidez(int ingreso, int egreso,
            BigDecimal saldoEsperado, String riesgoEsperado) {
        ModeloPredictivoIa modelo = ModeloPredictivoIa.builder().idModelo(1L).versionAlgoritmo("MEDIA-MOVIL-30D-v1").build();
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(ingreso));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(egreso));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween(eq("ingreso"), any(), any())).thenReturn(BigDecimal.valueOf(ingreso));
        when(transaccionCajaRepository.sumMontoByTipoAndFechaBetween(eq("egreso"), any(), any())).thenReturn(BigDecimal.valueOf(egreso));
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.save(any(ProyeccionFlujo.class))).thenAnswer(i -> i.getArgument(0));

        var resultado = flujoCajaIaService.generarProyeccion(" MENSUAL ");
        assertEquals("mensual", resultado.getTipoProyeccion());
        assertEquals(30, java.time.temporal.ChronoUnit.DAYS.between(resultado.getPeriodoInicio(), resultado.getPeriodoFin()));
        assertEquals(0, saldoEsperado.compareTo(resultado.getSaldoFinalEstimado()));
        var captor = org.mockito.ArgumentCaptor.forClass(AlertaLiquidez.class);
        verify(alertaLiquidezRepository).save(captor.capture());
        assertEquals(riesgoEsperado, captor.getValue().getNivelRiesgo());
        assertFalse(captor.getValue().getLeido());
    }

    @Test
    void alertaInexistenteNoSeGuarda() {
        when(alertaLiquidezRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(com.stockmaster.backend.exception.ResourceNotFoundException.class,
                () -> flujoCajaIaService.marcarAlertaComoLeida(99L));
        verify(alertaLiquidezRepository, never()).save(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,0,medio,1", "1500,0,bajo,99.99", "1500,1000,medio,1.50"})
    void recomendacionesSinAlertasCubrenRatios(int ingresos, int egresos, String riesgo, BigDecimal ratio) {
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(ingresos));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(egresos));
        when(alertaLiquidezRepository.findByLeidoFalse()).thenReturn(List.of());
        var resultado = flujoCajaIaService.obtenerRecomendacionesPreventivas(null);
        assertEquals(riesgo, resultado.getNivelRiesgoActual());
        assertEquals(0, ratio.compareTo(resultado.getRatioCoberturaLiquidez()));
        assertFalse(resultado.getAccionesSugeridas().isEmpty());
    }
}

