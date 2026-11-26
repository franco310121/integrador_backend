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
                .versionAlgoritmo("ARIMA-v2.1")
                .precisionHistorica(BigDecimal.valueOf(94.50))
                .build();

        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(10000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(4000));
        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(proyeccionFlujoRepository.save(any(ProyeccionFlujo.class))).thenAnswer(inv -> {
            ProyeccionFlujo p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });

        ProyeccionFlujoDto.Response response = flujoCajaIaService.generarProyeccion("semanal");

        assertNotNull(response);
        assertEquals("semanal", response.getTipoProyeccion());
        assertEquals(new BigDecimal("2500.00"), response.getIngresoProyectado());
        assertEquals(new BigDecimal("1000.00"), response.getEgresoProyectado());
        assertEquals(new BigDecimal("7500.00"), response.getSaldoFinalEstimado());
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

    @Test
    void testValidarDesempenoModelo_CompruebaPrecisionOptima() {
        ModeloPredictivoIa modelo = ModeloPredictivoIa.builder()
                .idModelo(1L)
                .versionAlgoritmo("ARIMA-v2.1")
                .precisionHistorica(BigDecimal.valueOf(92.50))
                .build();

        ProyeccionFlujo proyeccion = ProyeccionFlujo.builder()
                .id(1L)
                .modelo(modelo)
                .tipoProyeccion("mensual")
                .periodoInicio(LocalDate.now())
                .periodoFin(LocalDate.now().plusDays(30))
                .ingresoProyectado(BigDecimal.valueOf(19500))
                .egresoProyectado(BigDecimal.valueOf(8000))
                .saldoFinalEstimado(BigDecimal.valueOf(11500))
                .nivelConfianza(BigDecimal.valueOf(95.00))
                .build();

        when(modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()).thenReturn(Optional.of(modelo));
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(20000));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(8200));
        when(proyeccionFlujoRepository.findAll()).thenReturn(List.of(proyeccion));

        DesempenoModeloDto desempeno = flujoCajaIaService.validarDesempenoModelo();

        assertNotNull(desempeno);
        assertEquals("ARIMA-v2.1", desempeno.getVersionAlgoritmo());
        assertEquals("OPTIMO", desempeno.getEstadoSaludModelo());
        assertTrue(desempeno.getPrecisionActualCalculada().compareTo(BigDecimal.valueOf(90)) >= 0);
        assertEquals(1, desempeno.getTotalProyeccionesEvaluadas());
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
        ModeloPredictivoIa modelo = ModeloPredictivoIa.builder().idModelo(1L).build();
        when(transaccionCajaRepository.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.valueOf(ingreso));
        when(transaccionCajaRepository.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(egreso));
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

