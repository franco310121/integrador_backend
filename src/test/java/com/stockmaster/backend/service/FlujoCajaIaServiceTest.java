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
}

