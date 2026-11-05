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
        assertTrue(desempeno.getTotalProyeccionesEvaluadas() >= 2);

        // 8. Registro y Consulta de Auditoría Transversal
        auditoriaService.registrar(usuarioPromovido.getCorreo(), "VER", "IA_FINANZAS", "Validación Integral", "Prueba integral completada");
        List<AuditoriaLogDto.Response> logs = auditoriaService.findAll();
        assertFalse(logs.isEmpty());
    }
}

