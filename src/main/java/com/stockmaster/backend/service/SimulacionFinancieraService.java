package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.SimulacionFinancieraDto;
import com.stockmaster.backend.entity.SimulacionFinanciera;
import com.stockmaster.backend.repository.SimulacionFinancieraRepository;
import com.stockmaster.backend.repository.TransaccionCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

        String jsonResultado = String.format("{\"ingresosSimulados\": %.2f, \"egresosSimulados\": %.2f, \"saldoSimulado\": %.2f}",
                ingresosSimulados.doubleValue(), egresosSimulados.doubleValue(), saldoSimulado.doubleValue());

        SimulacionFinanciera s = SimulacionFinanciera.builder()
                .nombreEscenario(request.getNombreEscenario())
                .porcentajeAjusteIngresos(pctIngresos)
                .porcentajeAjusteEgresos(pctEgresos)
                .resultadoSimulacion(jsonResultado)
                .build();

        SimulacionFinanciera saved = simulacionFinancieraRepository.save(s);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SimulacionFinancieraDto.Response> listarSimulaciones() {
        return simulacionFinancieraRepository.findByOrderByFechaSimulacionDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private SimulacionFinancieraDto.Response mapToResponse(SimulacionFinanciera s) {
        return SimulacionFinancieraDto.Response.builder()
                .id(s.getId())
                .nombreEscenario(s.getNombreEscenario())
                .porcentajeAjusteIngresos(s.getPorcentajeAjusteIngresos())
                .porcentajeAjusteEgresos(s.getPorcentajeAjusteEgresos())
                .resultadoSimulacion(s.getResultadoSimulacion())
                .fechaSimulacion(s.getFechaSimulacion())
                .build();
    }
}
