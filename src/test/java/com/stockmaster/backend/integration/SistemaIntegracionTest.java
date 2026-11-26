package com.stockmaster.backend.integration;

import com.stockmaster.backend.dto.*;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HU 19: Ejecutar pruebas integrales del sistema para verificar que los módulos
 * funcionen correctamente de forma conjunta.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SistemaIntegracionTest {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private TransaccionCajaService transaccionCajaService;

    @Autowired
    private FlujoCajaIaService flujoCajaIaService;

    @Autowired
    private SimulacionFinancieraService simulacionFinancieraService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Test
    void testFlujoIntegralSistemaCompleto() {
        // 1. Gestión de Usuarios y Roles (HU 17)
        AuthDto.RegisterRequest userReq = AuthDto.RegisterRequest.builder()
                .correo("admin.integral@stockmaster.pe")
                .password("AdminPass123!")
                .nombreCompleto("Admin Integral")
                .rol(Rol.VENDEDOR)
                .build();
        UserDto.UserResponse usuario = usuarioService.create(userReq);
        assertNotNull(usuario.getId());
        assertEquals("vendedor", usuario.getRol());

        UserDto.UserResponse usuarioPromovido = usuarioService.cambiarRol(usuario.getId(), Rol.ADMIN);
        assertEquals("admin", usuarioPromovido.getRol());

        // 2. Creación de Categoría y Producto (Inventario)
        CategoriaDto.Request catReq = new CategoriaDto.Request();
        catReq.setNombre("Equipos Cómputo Test");
        catReq.setDescripcion("Categoría de prueba integral");
        CategoriaDto.Response categoria = categoriaService.create(catReq);
        assertNotNull(categoria.getId());

        ProductoDto.Request prodReq = ProductoDto.Request.builder()
                .nombre("Laptop Empresarial Pro")
                .sku("LAP-INT-001")
                .categoriaId(categoria.getId())
                .precioCompra(BigDecimal.valueOf(2000))
                .precioVenta(BigDecimal.valueOf(3000))
                .stock(10)
                .stockMinimo(3)
                .build();
        ProductoDto.Response producto = productoService.create(prodReq);
        assertNotNull(producto.getId());

        // 3. Registro de Transacciones de Caja (Ingresos y Egresos)
        TransaccionCajaDto.Request ingresoReq = TransaccionCajaDto.Request.builder()
                .tipo("ingreso")
                .categoriaGasto("Ventas Corporativas")
                .monto(BigDecimal.valueOf(25000))
                .observaciones("Cobro de lote corporativo")
                .build();
        transaccionCajaService.registrarTransaccion(ingresoReq);

        TransaccionCajaDto.Request egresoReq = TransaccionCajaDto.Request.builder()
                .tipo("egreso")
                .categoriaGasto("Reposición de Inventario")
                .monto(BigDecimal.valueOf(10000))
                .observaciones("Pago a proveedor")
                .build();
        transaccionCajaService.registrarTransaccion(egresoReq);

        FlujoCajaHistoricoDto historico = transaccionCajaService.getHistoricoFlujoCaja();
        assertTrue(historico.getSaldoNetoHistorico().compareTo(BigDecimal.valueOf(15000)) >= 0);

        // 4. Proyección de Flujo de Caja con IA (Semanal y Mensual)
        ProyeccionFlujoDto.Response proySemanal = flujoCajaIaService.generarProyeccion("semanal");
        assertNotNull(proySemanal.getId());
        assertTrue(proySemanal.getSaldoFinalEstimado().compareTo(BigDecimal.ZERO) > 0);

        ProyeccionFlujoDto.Response proyMensual = flujoCajaIaService.generarProyeccion("mensual");
        assertNotNull(proyMensual.getId());

        // 5. Consulta de Recomendaciones Preventivas y Alertas (HU 16)
        RecomendacionPreventivaDto.Response recomendaciones = flujoCajaIaService.obtenerRecomendacionesPreventivas(null);
        assertNotNull(recomendaciones);
        assertEquals("bajo", recomendaciones.getNivelRiesgoActual());
        assertFalse(recomendaciones.getAccionesSugeridas().isEmpty());
        assertFalse(recomendaciones.getAlertasActivas().isEmpty());

        Long alertaId = recomendaciones.getAlertasActivas().get(0).getId();
        AlertaLiquidezDto.Response alertaLeida = flujoCajaIaService.marcarAlertaComoLeida(alertaId);
        assertTrue(alertaLeida.getLeido());

        // 6. Simulación de Escenarios Financieros (HU 15)
        SimulacionFinancieraDto.Request simReq = SimulacionFinancieraDto.Request.builder()
                .nombreEscenario("Escenario Expansión +25% Ingresos")
                .porcentajeAjusteIngresos(BigDecimal.valueOf(25))
                .porcentajeAjusteEgresos(BigDecimal.valueOf(10))
                .build();
        SimulacionFinancieraDto.Response simulacion = simulacionFinancieraService.crearSimulacion(simReq);
        assertNotNull(simulacion.getId());
        assertEquals("POSITIVO", simulacion.getImpactoLiquidez());

        // 7. Validación del Desempeño del Modelo Predictivo (HU 18)
        DesempenoModeloDto desempeno = flujoCajaIaService.validarDesempenoModelo();
        assertNotNull(desempeno);
        assertNotNull(desempeno.getEstadoSaludModelo());
        assertEquals(0, desempeno.getTotalProyeccionesEvaluadas());
        assertEquals("SIN_DATOS", desempeno.getEstadoSaludModelo());
        assertNull(desempeno.getErrorPorcentualMedio());

        // 8. Registro y Consulta de Auditoría Transversal
        auditoriaService.registrar(usuarioPromovido.getCorreo(), "VER", "IA_FINANZAS", "Validación Integral", "Prueba integral completada");
        List<AuditoriaLogDto.Response> logs = auditoriaService.findAll();
        assertFalse(logs.isEmpty());
    }

    @Autowired
    private com.stockmaster.backend.repository.TransaccionCajaRepository cajaRepository;

    @Autowired
    private com.stockmaster.backend.repository.ProyeccionFlujoRepository proyeccionRepository;

    @Autowired
    private com.stockmaster.backend.repository.AlertaLiquidezRepository alertaRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private TransaccionCajaDto.Response registrar(String tipo, String monto) {
        return transaccionCajaService.registrarTransaccion(TransaccionCajaDto.Request.builder()
                .tipo(tipo).monto(new BigDecimal(monto)).categoriaGasto("QA").build());
    }

    private void verificarMonto(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real));
    }

    @Test
    void ingresoSePersisteYNormaliza() {
        var ingreso = registrar(" INGRESO ", "1250.50");
        entityManager.flush();
        entityManager.clear();
        var guardado = cajaRepository.findById(ingreso.getId()).orElseThrow();
        assertEquals("ingreso", guardado.getTipo());
        assertEquals("efectivizado", guardado.getEstado());
        verificarMonto("1250.50", guardado.getMonto());
    }

    @Test
    void egresoSePersisteConCategoria() {
        var egreso = registrar("egreso", "420.75");
        entityManager.flush();
        entityManager.clear();
        var guardado = cajaRepository.findById(egreso.getId()).orElseThrow();
        assertEquals("egreso", guardado.getTipo());
        assertEquals("QA", guardado.getCategoriaGasto());
        verificarMonto("420.75", guardado.getMonto());
    }

    @Test
    void movimientosActualizanSaldoHistoricoExacto() {
        var antes = transaccionCajaService.getHistoricoFlujoCaja();
        registrar("ingreso", "1000.00");
        registrar("egreso", "300.25");
        entityManager.flush();
        entityManager.clear();
        var despues = transaccionCajaService.getHistoricoFlujoCaja();
        verificarMonto("1000.00", despues.getTotalIngresos().subtract(antes.getTotalIngresos()));
        verificarMonto("300.25", despues.getTotalEgresos().subtract(antes.getTotalEgresos()));
        verificarMonto("699.75", despues.getSaldoNetoHistorico().subtract(antes.getSaldoNetoHistorico()));
        assertEquals(antes.getCantidadTransacciones() + 2, despues.getCantidadTransacciones());
    }

    @Test
    void importacionMixtaPersisteSoloRegistrosValidos() {
        long antes = cajaRepository.count();
        var resultado = transaccionCajaService.importarTransacciones(ImportarFinancieroDto.Request.builder()
                .registros(List.of(
                        ImportarFinancieroDto.RegistroImportacion.builder().tipo("ingreso").monto(150.50).fechaTransaccion("2026-01-15").build(),
                        ImportarFinancieroDto.RegistroImportacion.builder().tipo("egreso").monto(50.25).build(),
                        ImportarFinancieroDto.RegistroImportacion.builder().tipo("egreso").monto(-10.0).build(),
                        ImportarFinancieroDto.RegistroImportacion.builder().tipo("otro").monto(20.0).build()
                )).build());
        entityManager.flush();
        entityManager.clear();
        assertEquals(2, resultado.getProcesadosExitosos());
        assertEquals(2, resultado.getRegistrosConError());
        assertEquals(2, resultado.getDetallesError().size());
        assertEquals(antes + 2, cajaRepository.count());
    }

    @Test
    void proyeccionMensualPersisteAlertaCriticaRelacionada() {
        BigDecimal ingresos = cajaRepository.sumMontoByTipo("ingreso");
        BigDecimal egresos = cajaRepository.sumMontoByTipo("egreso");
        BigDecimal ajuste = ingresos.subtract(egresos).max(BigDecimal.ZERO).add(new BigDecimal("1000.00"));
        registrar("egreso", ajuste.toPlainString());
        var proyeccion = flujoCajaIaService.generarProyeccion("mensual");
        entityManager.flush();
        entityManager.clear();
        var guardada = proyeccionRepository.findById(proyeccion.getId()).orElseThrow();
        assertTrue(guardada.getSaldoFinalEstimado().signum() < 0);
        var alerta = alertaRepository.findByLeidoFalse().stream()
                .filter(a -> a.getProyeccion() != null && proyeccion.getId().equals(a.getProyeccion().getId()))
                .findFirst().orElseThrow();
        assertEquals("critico", alerta.getNivelRiesgo());
        assertNotNull(alerta.getRecomendacionPreventiva());
        Long alertaId = alerta.getId();
        flujoCajaIaService.marcarAlertaComoLeida(alertaId);
        entityManager.flush();
        entityManager.clear();
        assertTrue(alertaRepository.findById(alertaId).orElseThrow().getLeido());
        assertTrue(alertaRepository.findByLeidoFalse().stream().noneMatch(a -> alertaId.equals(a.getId())));
    }

    @Test
    void transaccionesPendientesNoAlteranSaldoEfectivo() {
        var antes = transaccionCajaService.getHistoricoFlujoCaja();
        cajaRepository.save(com.stockmaster.backend.entity.TransaccionCaja.builder()
                .tipo("ingreso").monto(new BigDecimal("999.00")).estado("pendiente").build());
        entityManager.flush();
        entityManager.clear();
        var despues = transaccionCajaService.getHistoricoFlujoCaja();
        assertEquals(0, antes.getSaldoNetoHistorico().compareTo(despues.getSaldoNetoHistorico()));
        assertEquals(antes.getCantidadTransacciones(), despues.getCantidadTransacciones());
    }
}

