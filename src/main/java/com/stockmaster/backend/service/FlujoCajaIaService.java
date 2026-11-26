package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.*;
import com.stockmaster.backend.entity.*;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlujoCajaIaService {

    private final TransaccionCajaRepository transaccionCajaRepository;
    private final ModeloPredictivoIaRepository modeloPredictivoIaRepository;
    private final ProyeccionFlujoRepository proyeccionFlujoRepository;
    private final AlertaLiquidezRepository alertaLiquidezRepository;
    private final EmpresaRepository empresaRepository;

    @Transactional
    public ProyeccionFlujoDto.Response generarProyeccion(String tipoProyeccion) {
        String tipo = tipoProyeccion.trim().toLowerCase();
        if (!"semanal".equals(tipo) && !"mensual".equals(tipo)) {
            throw new IllegalArgumentException("El tipo de proyección debe ser 'semanal' o 'mensual'");
        }

        // HU 8: Preparar datos para el modelo predictivo
        BigDecimal ingresosTotales = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal egresosTotales = transaccionCajaRepository.sumMontoByTipo("egreso");

        ModeloPredictivoIa modelo = modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()
                .filter(m -> "MEDIA-MOVIL-30D-v1".equals(m.getVersionAlgoritmo()))
                .orElseGet(() -> modeloPredictivoIaRepository.save(
                        ModeloPredictivoIa.builder()
                                .versionAlgoritmo("MEDIA-MOVIL-30D-v1")
                                .build()
                ));

        int dias = "semanal".equals(tipo) ? 7 : 30;
        LocalDateTime ahora = LocalDateTime.now();
        LocalDate inicio = ahora.toLocalDate().plusDays(1);
        LocalDate fin = inicio.plusDays(dias);

        // Ventana de 30 días calendario hasta el momento de generar la proyección.
        LocalDateTime desde = inicio.minusDays(30).atStartOfDay();
        BigDecimal ingresosRecientes = transaccionCajaRepository.sumMontoByTipoAndFechaBetween("ingreso", desde, ahora);
        BigDecimal egresosRecientes = transaccionCajaRepository.sumMontoByTipoAndFechaBetween("egreso", desde, ahora);
        BigDecimal ingresoProyectado = estimarPromedioDiario(ingresosRecientes, 30, dias);
        BigDecimal egresoProyectado = estimarPromedioDiario(egresosRecientes, 30, dias);
        BigDecimal saldoActual = ingresosTotales.subtract(egresosTotales);
        BigDecimal saldoFinalEstimado = saldoActual.add(ingresoProyectado).subtract(egresoProyectado);

        ProyeccionFlujo proyeccion = ProyeccionFlujo.builder()
                .modelo(modelo)
                .periodoInicio(inicio)
                .periodoFin(fin)
                .tipoProyeccion(tipo)
                .ingresoProyectado(ingresoProyectado)
                .egresoProyectado(egresoProyectado)
                .saldoFinalEstimado(saldoFinalEstimado)
                .nivelConfianza(null)
                .build();

        ProyeccionFlujo savedProyeccion = proyeccionFlujoRepository.save(proyeccion);

        // HU 11 y 12: Evaluar riesgo de liquidez y generar alertas preventivas
        evaluarYGenerarAlerta(savedProyeccion);

        return mapProyeccionToDto(savedProyeccion);
    }

    // Promedio móvil de una ventana de días calendario, incluidos días sin movimientos.
    public static BigDecimal estimarPromedioDiario(BigDecimal total, int diasHistorial, int horizonte) {
        if (total == null || diasHistorial <= 0 || horizonte <= 0) {
            throw new IllegalArgumentException("Historial u horizonte invalido");
        }
        return total.multiply(BigDecimal.valueOf(horizonte))
                .divide(BigDecimal.valueOf(diasHistorial), 2, RoundingMode.HALF_UP);
    }

    // Regla anterior conservada exclusivamente como referencia de comparación.
    public static BigDecimal estimarMonto(BigDecimal totalHistorico, String tipo) {
        if (!"semanal".equals(tipo) && !"mensual".equals(tipo)) {
            throw new IllegalArgumentException("Periodo no valido");
        }
        BigDecimal factor = "semanal".equals(tipo) ? new BigDecimal("0.25") : BigDecimal.ONE;
        return totalHistorico.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    private void evaluarYGenerarAlerta(ProyeccionFlujo proyeccion) {
        BigDecimal saldo = proyeccion.getSaldoFinalEstimado();
        String nivelRiesgo;
        String mensaje;
        String recomendacion;

        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            nivelRiesgo = "critico";
            mensaje = "Déficit proyectado de " + saldo.abs() + " PEN para el periodo " + proyeccion.getTipoProyeccion();
            recomendacion = "Postergar egresos no esenciales y acelerar cobro de cuentas pendientes.";
        } else if (saldo.compareTo(BigDecimal.valueOf(1000)) < 0) {
            nivelRiesgo = "medio";
            mensaje = "Margen de liquidez ajustado para el periodo " + proyeccion.getTipoProyeccion();
            recomendacion = "Monitorear de cerca los egresos diarios.";
        } else {
            nivelRiesgo = "bajo";
            mensaje = "Liquidez suficiente proyectada para el periodo " + proyeccion.getTipoProyeccion();
            recomendacion = "Mantener política actual de caja.";
        }

        AlertaLiquidez alerta = AlertaLiquidez.builder()
                .proyeccion(proyeccion)
                .nivelRiesgo(nivelRiesgo)
                .mensajeAlerta(mensaje)
                .recomendacionPreventiva(recomendacion)
                .leido(false)
                .build();

        alertaLiquidezRepository.save(alerta);
    }

    @Transactional(readOnly = true)
    public DashboardFinancieroDto getDashboardFinanciero() {
        Empresa empresa = empresaRepository.findFirstByOrderByIdAsc().orElse(null);
        EmpresaDto.Response empresaDto = empresa != null ? EmpresaDto.Response.builder()
                .id(empresa.getId())
                .nombreMype(empresa.getNombreMype())
                .ruc(empresa.getRuc())
                .sectorComercial(empresa.getSectorComercial())
                .build() : null;

        BigDecimal totalIngresos = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal totalEgresos = transaccionCajaRepository.sumMontoByTipo("egreso");
        BigDecimal saldoActual = totalIngresos.subtract(totalEgresos);

        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime finMes = LocalDate.now().atTime(23, 59, 59);

        BigDecimal ingresosMes = transaccionCajaRepository.sumMontoByTipoAndFechaBetween("ingreso", inicioMes, finMes);
        BigDecimal egresosMes = transaccionCajaRepository.sumMontoByTipoAndFechaBetween("egreso", inicioMes, finMes);

        List<ProyeccionFlujoDto.Response> proyeccionesSemanales = proyeccionFlujoRepository.findByTipoProyeccion("semanal")
                .stream().map(this::mapProyeccionToDto).collect(Collectors.toList());

        List<ProyeccionFlujoDto.Response> proyeccionesMensuales = proyeccionFlujoRepository.findByTipoProyeccion("mensual")
                .stream().map(this::mapProyeccionToDto).collect(Collectors.toList());

        List<AlertaLiquidezDto.Response> alertasActivas = alertaLiquidezRepository.findByLeidoFalse()
                .stream().map(this::mapAlertaToDto).collect(Collectors.toList());

        String nivelRiesgoGlobal = alertasActivas.stream()
                .anyMatch(a -> "critico".equals(a.getNivelRiesgo())) ? "critico" :
                (alertasActivas.stream().anyMatch(a -> "medio".equals(a.getNivelRiesgo())) ? "medio" : "bajo");

        return DashboardFinancieroDto.builder()
                .empresa(empresaDto)
                .saldoActualCaja(saldoActual)
                .totalIngresosMes(ingresosMes)
                .totalEgresosMes(egresosMes)
                .proyeccionesSemanales(proyeccionesSemanales)
                .proyeccionesMensuales(proyeccionesMensuales)
                .alertasLiquidezActivas(alertasActivas)
                .nivelRiesgoGlobal(nivelRiesgoGlobal)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AlertaLiquidezDto.Response> getAlertas() {
        return alertaLiquidezRepository.findByOrderByFechaAlertaDesc().stream()
                .map(this::mapAlertaToDto)
                .collect(Collectors.toList());
    }

    // HU 16: Consultar recomendaciones preventivas para apoyar la toma de decisiones
    @Transactional(readOnly = true)
    public RecomendacionPreventivaDto.Response obtenerRecomendacionesPreventivas(String filtroNivelRiesgo) {
        BigDecimal totalIngresos = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal totalEgresos = transaccionCajaRepository.sumMontoByTipo("egreso");
        BigDecimal saldoActual = totalIngresos.subtract(totalEgresos).setScale(2, RoundingMode.HALF_UP);

        BigDecimal ratioCobertura = totalEgresos.compareTo(BigDecimal.ZERO) > 0
                ? totalIngresos.divide(totalEgresos, 2, RoundingMode.HALF_UP)
                : (totalIngresos.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(99.99) : BigDecimal.ONE);

        List<AlertaLiquidez> alertasBase = (filtroNivelRiesgo != null && !filtroNivelRiesgo.isBlank())
                ? alertaLiquidezRepository.findByNivelRiesgo(filtroNivelRiesgo.trim().toLowerCase())
                : alertaLiquidezRepository.findByLeidoFalse();

        List<AlertaLiquidezDto.Response> alertasDto = alertasBase.stream()
                .map(this::mapAlertaToDto)
                .collect(Collectors.toList());

        String riesgoDeterminado;
        if (saldoActual.compareTo(BigDecimal.ZERO) < 0 || alertasDto.stream().anyMatch(a -> "critico".equals(a.getNivelRiesgo()))) {
            riesgoDeterminado = "critico";
        } else if (saldoActual.compareTo(BigDecimal.valueOf(1000)) < 0 || alertasDto.stream().anyMatch(a -> "medio".equals(a.getNivelRiesgo()))) {
            riesgoDeterminado = "medio";
        } else {
            riesgoDeterminado = "bajo";
        }

        List<RecomendacionPreventivaDto.AccionSugerida> acciones = new ArrayList<>();
        String resumen;

        if ("critico".equals(riesgoDeterminado)) {
            resumen = "Se detecta riesgo crítico de liquidez. Es prioritario acelerar el ingreso de efectivo y diferir compromisos no urgentes.";
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("ALTA")
                    .area("COBRANZAS")
                    .titulo("Acelerar recuperación de cuentas por cobrar")
                    .descripcion("Aplicar descuentos por pronto pago (2% - 5%) a clientes corporativos y ventas recientes.")
                    .impactoEsperado("Inyección inmediata de liquidez en los próximos 3 a 7 días")
                    .build());
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("ALTA")
                    .area("GASTOS")
                    .titulo("Postergar egresos no esenciales y renegociar con proveedores")
                    .descripcion("Diferir compras de baja rotación y solicitar extensión de pago de 15 a 30 días.")
                    .impactoEsperado("Reducción del flujo de salida semanal entre 15% y 25%")
                    .build());
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("MEDIA")
                    .area("INVENTARIO")
                    .titulo("Liquidar stock de baja rotación")
                    .descripcion("Lanzar promociones sobre productos con sobrestock para convertir inventario en caja.")
                    .impactoEsperado("Conversión de capital inmovilizado en efectivo disponible")
                    .build());
        } else if ("medio".equals(riesgoDeterminado)) {
            resumen = "Margen de liquidez ajustado. Se recomienda control preventivo de gastos operativos y monitoreo diario del flujo.";
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("ALTA")
                    .area("TESORERIA")
                    .titulo("Establecer fondo de reserva mínimo")
                    .descripcion("Bloquear un colchón de seguridad de S/ 1,000 a S/ 1,500 para cubrir imprevistos operativos.")
                    .impactoEsperado("Mitigación de déficit ante caídas puntuales de ventas")
                    .build());
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("MEDIA")
                    .area("GASTOS")
                    .titulo("Auditar gastos operativos recurrentes")
                    .descripcion("Revisar servicios y compras menores del mes para evitar desviaciones presupuestarias.")
                    .impactoEsperado("Ahorro operativo estimado del 5% al 10% mensual")
                    .build());
        } else {
            resumen = "Posición de liquidez saludable. La MYPE cuenta con cobertura suficiente para sus obligaciones proyectadas.";
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("MEDIA")
                    .area("INVENTARIO")
                    .titulo("Reabastecer productos de alta rotación")
                    .descripcion("Aprovechar el superávit de caja para negociar descuentos por volumen con proveedores clave.")
                    .impactoEsperado("Mejora del margen bruto entre 3% y 8% en próximas ventas")
                    .build());
            acciones.add(RecomendacionPreventivaDto.AccionSugerida.builder()
                    .prioridad("BAJA")
                    .area("TESORERIA")
                    .titulo("Mantener política actual de control de caja")
                    .descripcion("Continuar registrando ingresos y egresos diariamente para preservar la precisión del modelo IA.")
                    .impactoEsperado("Mejor disponibilidad de datos para evaluar las proyecciones")
                    .build());
        }

        return RecomendacionPreventivaDto.Response.builder()
                .nivelRiesgoActual(riesgoDeterminado)
                .saldoActualCaja(saldoActual)
                .ratioCoberturaLiquidez(ratioCobertura)
                .resumenEjecutivo(resumen)
                .accionesSugeridas(acciones)
                .alertasActivas(alertasDto)
                .build();
    }

    @Transactional
    public AlertaLiquidezDto.Response marcarAlertaComoLeida(Long alertaId) {
        AlertaLiquidez alerta = alertaLiquidezRepository.findById(alertaId)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta de liquidez no encontrada con ID: " + alertaId));
        alerta.setLeido(true);
        return mapAlertaToDto(alertaLiquidezRepository.save(alerta));
    }

    // HU 18: evaluar exclusivamente periodos cerrados de la versión vigente.
    @Transactional
    public DesempenoModeloDto validarDesempenoModelo() {
        ModeloPredictivoIa modelo = modeloPredictivoIaRepository.findFirstByOrderByIdModeloDesc()
                .filter(m -> "MEDIA-MOVIL-30D-v1".equals(m.getVersionAlgoritmo()))
                .orElseGet(() -> modeloPredictivoIaRepository.save(
                        ModeloPredictivoIa.builder().versionAlgoritmo("MEDIA-MOVIL-30D-v1").build()));

        LocalDate hoy = LocalDate.now();
        List<ProyeccionFlujo> proyecciones = proyeccionFlujoRepository.findAll().stream()
                .filter(p -> p.getModelo() != null && "MEDIA-MOVIL-30D-v1".equals(p.getModelo().getVersionAlgoritmo()))
                .sorted(java.util.Comparator.comparing(ProyeccionFlujo::getPeriodoInicio)
                        .thenComparing(ProyeccionFlujo::getId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .toList();
        // Evitar contar dos veces pronósticos del mismo horizonte y fecha de inicio.
        Map<String, ProyeccionFlujo> cerradas = new LinkedHashMap<>();
        for (ProyeccionFlujo p : proyecciones) {
            if (p.getPeriodoFin() != null && p.getPeriodoInicio() != null
                    && p.getPeriodoFin().isAfter(p.getPeriodoInicio())
                    && !p.getPeriodoFin().isAfter(hoy)
                    && p.getFechaGeneracion() != null
                    && p.getFechaGeneracion().isBefore(p.getPeriodoInicio().atStartOfDay())) {
                cerradas.putIfAbsent(p.getPeriodoInicio() + ":" + p.getPeriodoFin(), p);
            }
        }
        BigDecimal sumaAbs = BigDecimal.ZERO;
        BigDecimal sumaCuadrados = BigDecimal.ZERO;
        BigDecimal sumaPorcentajes = BigDecimal.ZERO;
        int observaciones = 0, observacionesMape = 0;
        for (ProyeccionFlujo p : cerradas.values()) {
            LocalDateTime desde = p.getPeriodoInicio().atStartOfDay();
            LocalDateTime hasta = p.getPeriodoFin().atStartOfDay().minusNanos(1000);
            BigDecimal[] reales = {
                transaccionCajaRepository.sumMontoByTipoAndFechaBetween("ingreso", desde, hasta),
                transaccionCajaRepository.sumMontoByTipoAndFechaBetween("egreso", desde, hasta)
            };
            BigDecimal[] estimados = {p.getIngresoProyectado(), p.getEgresoProyectado()};
            for (int i = 0; i < reales.length; i++) {
                BigDecimal error = reales[i].subtract(estimados[i]).abs();
                sumaAbs = sumaAbs.add(error);
                sumaCuadrados = sumaCuadrados.add(error.multiply(error));
                observaciones++;
                if (reales[i].signum() != 0) {
                    sumaPorcentajes = sumaPorcentajes.add(error.multiply(BigDecimal.valueOf(100))
                            .divide(reales[i].abs(), 12, RoundingMode.HALF_UP));
                    observacionesMape++;
                }
            }
        }
        BigDecimal mae = observaciones == 0 ? null : sumaAbs.divide(BigDecimal.valueOf(observaciones), 2, RoundingMode.HALF_UP);
        BigDecimal rmse = observaciones == 0 ? null : sumaCuadrados.divide(BigDecimal.valueOf(observaciones),
                java.math.MathContext.DECIMAL128).sqrt(java.math.MathContext.DECIMAL128).setScale(2, RoundingMode.HALF_UP);
        BigDecimal mape = observacionesMape == 0 ? null : sumaPorcentajes.divide(
                BigDecimal.valueOf(observacionesMape), 2, RoundingMode.HALF_UP);
        // Indicador legado: 100 - MAPE, acotado inferiormente a cero; no es confianza estadística.
        BigDecimal precision = mape == null ? null : BigDecimal.valueOf(100).subtract(mape).max(BigDecimal.ZERO).setScale(2);
        modelo.setPrecisionHistorica(precision);
        modeloPredictivoIaRepository.save(modelo);
        String estado = mape == null ? "SIN_DATOS" : "EVALUADO";
        String mensaje = cerradas.isEmpty() ? "No existen pronósticos de la versión vigente con periodo cerrado."
                : mape == null ? "Los importes reales son cero; MAPE no definido. Consultar MAE y RMSE."
                : "Error observado en periodos cerrados registrados. No se ha establecido un umbral de aceptación.";
        ProyeccionFlujo ultima = proyecciones.isEmpty() ? null : proyecciones.get(proyecciones.size()-1);
        return DesempenoModeloDto.builder()
                .idModelo(modelo.getIdModelo())
                .versionAlgoritmo(modelo.getVersionAlgoritmo())
                .fechaEntrenamiento(modelo.getFechaEntrenamiento())
                .precisionHistorica(precision)
                .precisionActualCalculada(precision)
                .errorPorcentualMedio(mape)
                .mae(mae)
                .rmse(rmse)
                .observacionesEvaluadas(observaciones)
                .observacionesMape(observacionesMape)
                .nivelConfianzaPromedio(null)
                .totalProyeccionesEvaluadas(cerradas.size())
                .ingresosRealesAcumulados(transaccionCajaRepository.sumMontoByTipo("ingreso"))
                .egresosRealesAcumulados(transaccionCajaRepository.sumMontoByTipo("egreso"))
                .ultimaProyeccionIngreso(ultima == null ? null : ultima.getIngresoProyectado())
                .ultimaProyeccionEgreso(ultima == null ? null : ultima.getEgresoProyectado())
                .estadoSaludModelo(estado)
                .mensajeValidacion(mensaje)
                .fechaValidacion(LocalDateTime.now())
                .build();
    }

    // HU 20: Evaluar el rendimiento y escalabilidad del sistema ante diferentes cargas
    @Transactional(readOnly = true)
    public RendimientoSistemaDto evaluarRendimientoSistema(int iteraciones) {
        int ciclos = Math.max(1, Math.min(iteraciones, 200));
        long inicioTotal = System.nanoTime();

        long tCajaInicio = System.nanoTime();
        for (int i = 0; i < ciclos; i++) {
            transaccionCajaRepository.sumMontoByTipo("ingreso");
            transaccionCajaRepository.sumMontoByTipo("egreso");
        }
        long latenciaCajaMs = Math.max(1L, (System.nanoTime() - tCajaInicio) / 1_000_000);

        long tProyInicio = System.nanoTime();
        for (int i = 0; i < ciclos; i++) {
            proyeccionFlujoRepository.findByTipoProyeccion("semanal");
            proyeccionFlujoRepository.findByTipoProyeccion("mensual");
        }
        long latenciaProyeccionesMs = Math.max(1L, (System.nanoTime() - tProyInicio) / 1_000_000);

        long tAlertasInicio = System.nanoTime();
        for (int i = 0; i < ciclos; i++) {
            alertaLiquidezRepository.findByLeidoFalse();
        }
        long latenciaAlertasMs = Math.max(1L, (System.nanoTime() - tAlertasInicio) / 1_000_000);

        long latenciaTotalMs = Math.max(1L, (System.nanoTime() - inicioTotal) / 1_000_000);
        int totalOperaciones = ciclos * 5;
        double throughput = Math.round((totalOperaciones * 1000.0 / latenciaTotalMs) * 100.0) / 100.0;

        Runtime runtime = Runtime.getRuntime();
        long memoriaTotalMb = runtime.totalMemory() / (1024 * 1024);
        long memoriaLibreMb = runtime.freeMemory() / (1024 * 1024);
        long memoriaUsadaMb = memoriaTotalMb - memoriaLibreMb;
        long memoriaMaximaMb = runtime.maxMemory() / (1024 * 1024);

        Map<String, Long> latenciaModulos = new LinkedHashMap<>();
        latenciaModulos.put("transaccionesCajaMs", latenciaCajaMs);
        latenciaModulos.put("proyeccionesIaMs", latenciaProyeccionesMs);
        latenciaModulos.put("alertasLiquidezMs", latenciaAlertasMs);

        String estado = latenciaTotalMs < 1000 ? "OPTIMO" : (latenciaTotalMs < 3000 ? "ESTABLE" : "CARGA_ELEVADA");

        return RendimientoSistemaDto.builder()
                .estadoSistema(estado)
                .latenciaTotalMs(latenciaTotalMs)
                .throughputOperacionesPorSegundo(throughput)
                .iteracionesEvaluadas(ciclos)
                .memoriaUsadaMb(memoriaUsadaMb)
                .memoriaTotalMb(memoriaTotalMb)
                .memoriaMaximaMb(memoriaMaximaMb)
                .hilosActivos(Thread.activeCount())
                .procesadoresDisponibles(runtime.availableProcessors())
                .latenciaPorModuloMs(latenciaModulos)
                .fechaEvaluacion(LocalDateTime.now())
                .build();
    }

    private ProyeccionFlujoDto.Response mapProyeccionToDto(ProyeccionFlujo p) {
        return ProyeccionFlujoDto.Response.builder()
                .id(p.getId())
                .modeloId(p.getModelo() != null ? p.getModelo().getIdModelo() : null)
                .periodoInicio(p.getPeriodoInicio())
                .periodoFin(p.getPeriodoFin())
                .tipoProyeccion(p.getTipoProyeccion())
                .ingresoProyectado(p.getIngresoProyectado())
                .egresoProyectado(p.getEgresoProyectado())
                .saldoFinalEstimado(p.getSaldoFinalEstimado())
                .nivelConfianza(p.getNivelConfianza())
                .fechaGeneracion(p.getFechaGeneracion())
                .build();
    }

    private AlertaLiquidezDto.Response mapAlertaToDto(AlertaLiquidez a) {
        return AlertaLiquidezDto.Response.builder()
                .id(a.getId())
                .proyeccionId(a.getProyeccion() != null ? a.getProyeccion().getId() : null)
                .nivelRiesgo(a.getNivelRiesgo())
                .mensajeAlerta(a.getMensajeAlerta())
                .recomendacionPreventiva(a.getRecomendacionPreventiva())
                .fechaAlerta(a.getFechaAlerta())
                .leido(a.getLeido())
                .build();
    }
}
