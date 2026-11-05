package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.SimulacionFinancieraDto;
import com.stockmaster.backend.entity.SimulacionFinanciera;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.SimulacionFinancieraRepository;
import com.stockmaster.backend.repository.TransaccionCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SimulacionFinancieraService {

    private final SimulacionFinancieraRepository simulacionFinancieraRepository;
    private final TransaccionCajaRepository transaccionCajaRepository;

    @Transactional
    public SimulacionFinancieraDto.Response crearSimulacion(SimulacionFinancieraDto.Request request) {
        BigDecimal ingresosBase = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal egresosBase = transaccionCajaRepository.sumMontoByTipo("egreso");

        BigDecimal pctIngresos = request.getPorcentajeAjusteIngresos() != null ? request.getPorcentajeAjusteIngresos() : BigDecimal.ZERO;
        BigDecimal pctEgresos = request.getPorcentajeAjusteEgresos() != null ? request.getPorcentajeAjusteEgresos() : BigDecimal.ZERO;

        BigDecimal multIngresos = BigDecimal.ONE.add(pctIngresos.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal multEgresos = BigDecimal.ONE.add(pctEgresos.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        BigDecimal ingresosSimulados = ingresosBase.multiply(multIngresos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal egresosSimulados = egresosBase.multiply(multEgresos).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoSimulado = ingresosSimulados.subtract(egresosSimulados);

        String jsonResultado = String.format(Locale.US,
                "{\"ingresosSimulados\": %.2f, \"egresosSimulados\": %.2f, \"saldoSimulado\": %.2f}",
                ingresosSimulados.doubleValue(), egresosSimulados.doubleValue(), saldoSimulado.doubleValue());

        SimulacionFinanciera s = SimulacionFinanciera.builder()
                .nombreEscenario(request.getNombreEscenario().trim())
                .porcentajeAjusteIngresos(pctIngresos)
                .porcentajeAjusteEgresos(pctEgresos)
                .resultadoSimulacion(jsonResultado)
                .build();

        SimulacionFinanciera saved = simulacionFinancieraRepository.save(s);
        return mapToResponse(saved, ingresosBase, egresosBase);
    }

    @Transactional(readOnly = true)
    public List<SimulacionFinancieraDto.Response> listarSimulaciones() {
        BigDecimal ingresosBase = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal egresosBase = transaccionCajaRepository.sumMontoByTipo("egreso");
        return simulacionFinancieraRepository.findByOrderByFechaSimulacionDesc().stream()
                .map(s -> mapToResponse(s, ingresosBase, egresosBase))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SimulacionFinancieraDto.Response obtenerPorId(Long id) {
        SimulacionFinanciera s = simulacionFinancieraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Simulación financiera no encontrada con ID: " + id));
        BigDecimal ingresosBase = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal egresosBase = transaccionCajaRepository.sumMontoByTipo("egreso");
        return mapToResponse(s, ingresosBase, egresosBase);
    }

    @Transactional
    public void eliminarSimulacion(Long id) {
        SimulacionFinanciera s = simulacionFinancieraRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Simulación financiera no encontrada con ID: " + id));
        simulacionFinancieraRepository.delete(s);
    }

    private SimulacionFinancieraDto.Response mapToResponse(SimulacionFinanciera s, BigDecimal ingresosBase, BigDecimal egresosBase) {
        BigDecimal pctIng = s.getPorcentajeAjusteIngresos() != null ? s.getPorcentajeAjusteIngresos() : BigDecimal.ZERO;
        BigDecimal pctEgr = s.getPorcentajeAjusteEgresos() != null ? s.getPorcentajeAjusteEgresos() : BigDecimal.ZERO;

        BigDecimal multIng = BigDecimal.ONE.add(pctIng.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
        BigDecimal multEgr = BigDecimal.ONE.add(pctEgr.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));

        BigDecimal ingSim = ingresosBase.multiply(multIng).setScale(2, RoundingMode.HALF_UP);
        BigDecimal egrSim = egresosBase.multiply(multEgr).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoBase = ingresosBase.subtract(egresosBase).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoSim = ingSim.subtract(egrSim).setScale(2, RoundingMode.HALF_UP);
        BigDecimal variacion = saldoSim.subtract(saldoBase).setScale(2, RoundingMode.HALF_UP);

        String impacto;
        String recomendacion;
        if (saldoSim.compareTo(BigDecimal.ZERO) < 0) {
            impacto = "RIESGO_DEFICIT";
            recomendacion = "El escenario genera déficit de caja. Se recomienda reducir costos variables o renegociar plazos con proveedores.";
        } else if (variacion.compareTo(BigDecimal.ZERO) < 0) {
            impacto = "REDUCCION_LIQUIDEZ";
            recomendacion = "El escenario mantiene saldo positivo pero reduce el margen de liquidez. Monitorear gastos operativos.";
        } else {
            impacto = "POSITIVO";
            recomendacion = "Escenario financieramente viable con mejora o estabilidad en el flujo de caja neto.";
        }

        return SimulacionFinancieraDto.Response.builder()
                .id(s.getId())
                .nombreEscenario(s.getNombreEscenario())
                .porcentajeAjusteIngresos(pctIng)
                .porcentajeAjusteEgresos(pctEgr)
                .ingresosBase(ingresosBase.setScale(2, RoundingMode.HALF_UP))
                .egresosBase(egresosBase.setScale(2, RoundingMode.HALF_UP))
                .saldoBase(saldoBase)
                .ingresosSimulados(ingSim)
                .egresosSimulados(egrSim)
                .saldoSimulado(saldoSim)
                .variacionSaldo(variacion)
                .impactoLiquidez(impacto)
                .recomendacion(recomendacion)
                .resultadoSimulacion(s.getResultadoSimulacion())
                .fechaSimulacion(s.getFechaSimulacion())
                .build();
    }
}
