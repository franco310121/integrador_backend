package com.stockmaster.backend.reliability;

import com.stockmaster.backend.dto.*;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.exception.*;
import com.stockmaster.backend.repository.*;
import com.stockmaster.backend.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:finvora_reliability_qa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConfiabilidadTest {
    @Autowired private TransaccionCajaService caja;
    @Autowired private TransaccionCajaRepository cajaRepo;
    @Autowired private VentaService ventas;
    @Autowired private VentaRepository ventaRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private MovimientoStockRepository movimientoRepo;
    @Autowired private UsuarioService usuarios;
    @Autowired private CategoriaService categorias;
    @Autowired private ProductoService productos;
    @Autowired private PlatformTransactionManager transactionManager;

    private record Datos(String correo, Long producto1, Long producto2) {}

    private Datos preparar() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String sufijo = UUID.randomUUID().toString();
            String correo = "qa-" + sufijo + "@example.test";
            usuarios.create(AuthDto.RegisterRequest.builder().correo(correo)
                    .nombreCompleto("QA").password("Qa-only-123!").rol(Rol.VENDEDOR).build());
            CategoriaDto.Request cat = new CategoriaDto.Request();
            cat.setNombre("QA " + sufijo);
            Long categoria = categorias.create(cat).getId();
            Long p1 = productos.create(ProductoDto.Request.builder().categoriaId(categoria)
                    .sku("A-" + sufijo).nombre("QA A").precioCompra(new BigDecimal("2.00"))
                    .precioVenta(new BigDecimal("3.33")).stock(10).stockMinimo(2).build()).getId();
            Long p2 = productos.create(ProductoDto.Request.builder().categoriaId(categoria)
                    .sku("B-" + sufijo).nombre("QA B").precioCompra(new BigDecimal("1.00"))
                    .precioVenta(new BigDecimal("2.00")).stock(1).stockMinimo(0).build()).getId();
            return new Datos(correo, p1, p2);
        });
    }

    private VentaDto.DetalleItemRequest item(Long id, int cantidad) {
        return VentaDto.DetalleItemRequest.builder().productoId(id).cantidad(cantidad).build();
    }

    private VentaDto.Request pedido(VentaDto.DetalleItemRequest... items) {
        return VentaDto.Request.builder().cliente("Cliente QA").items(List.of(items)).build();
    }

    private int stock(Long id) {
        return productoRepo.findById(id).orElseThrow().getStock();
    }

    private void monto(String esperado, BigDecimal actual) {
        assertEquals(0, new BigDecimal(esperado).compareTo(actual));
    }

    @Test
    void tipoInvalidoNoPersisteNiCambiaSaldo() {
        long antes = cajaRepo.count();
        var saldo = caja.getHistoricoFlujoCaja().getSaldoNetoHistorico();
        assertThrows(IllegalArgumentException.class, () -> caja.registrarTransaccion(
                TransaccionCajaDto.Request.builder().tipo("otro").monto(BigDecimal.TEN).build()));
        assertEquals(antes, cajaRepo.count());
        assertEquals(0, saldo.compareTo(caja.getHistoricoFlujoCaja().getSaldoNetoHistorico()));
    }

    @Test
    void importacionVaciaNoModificaDatos() {
        long antes = cajaRepo.count();
        var resultado = caja.importarTransacciones(ImportarFinancieroDto.Request.builder()
                .registros(List.of()).build());
        assertEquals(0, resultado.getProcesadosExitosos());
        assertFalse(resultado.getDetallesError().isEmpty());
        assertEquals(antes, cajaRepo.count());
    }

    @Test
    void importacionConMontosInvalidosNoPersiste() {
        long antes = cajaRepo.count();
        var resultado = caja.importarTransacciones(ImportarFinancieroDto.Request.builder()
                .registros(List.of(
                    ImportarFinancieroDto.RegistroImportacion.builder().tipo("ingreso").monto(0.0).build(),
                    ImportarFinancieroDto.RegistroImportacion.builder().tipo("egreso").monto(-1.0).build(),
                    ImportarFinancieroDto.RegistroImportacion.builder().tipo("ingreso").build()
                )).build());
        assertEquals(0, resultado.getProcesadosExitosos());
        assertEquals(3, resultado.getRegistrosConError());
        assertEquals(antes, cajaRepo.count());
    }

    @Test
    void ventaFallidaRevierteDescuentoDeStockAnterior() {
        Datos d = preparar();
        long totalVentas = ventaRepo.count();
        long movimientos = movimientoRepo.count();
        assertThrows(InsufficientStockException.class, () -> ventas.createVenta(
                pedido(item(d.producto1(), 2), item(d.producto2(), 5)), d.correo()));
        assertEquals(10, stock(d.producto1()));
        assertEquals(1, stock(d.producto2()));
        assertEquals(totalVentas, ventaRepo.count());
        assertEquals(movimientos, movimientoRepo.count());
    }

    @Test
    void productoInexistenteRevierteVentaParcial() {
        Datos d = preparar();
        long totalVentas = ventaRepo.count();
        long movimientos = movimientoRepo.count();
        assertThrows(ResourceNotFoundException.class, () -> ventas.createVenta(
                pedido(item(d.producto1(), 2), item(Long.MAX_VALUE, 1)), d.correo()));
        assertEquals(10, stock(d.producto1()));
        assertEquals(totalVentas, ventaRepo.count());
        assertEquals(movimientos, movimientoRepo.count());
    }

    @Test
    void ventaYAnulacionMantienenStockConsistente() {
        Datos d = preparar();
        long movimientos = movimientoRepo.count();
        var venta = ventas.createVenta(pedido(item(d.producto1(), 3)), d.correo());
        assertEquals(7, stock(d.producto1()));
        assertEquals(movimientos + 1, movimientoRepo.count());
        ventas.anularVenta(venta.getId(), d.correo());
        assertEquals(10, stock(d.producto1()));
        assertEquals("anulada", ventaRepo.findById(venta.getId()).orElseThrow().getEstado());
        assertEquals(movimientos + 2, movimientoRepo.count());
    }

    @Test
    void segundaAnulacionNoDuplicaStockNiMovimientos() {
        Datos d = preparar();
        var venta = ventas.createVenta(pedido(item(d.producto1(), 2)), d.correo());
        ventas.anularVenta(venta.getId(), d.correo());
        long movimientos = movimientoRepo.count();
        assertThrows(BadRequestException.class, () -> ventas.anularVenta(venta.getId(), d.correo()));
        assertEquals(10, stock(d.producto1()));
        assertEquals(movimientos, movimientoRepo.count());
    }

    @Test
    void calculoMonetarioCoincideConVentaPersistida() {
        Datos d = preparar();
        var request = pedido(item(d.producto1(), 3));
        request.setDescuento(new BigDecimal("0.10"));
        var venta = ventas.createVenta(request, d.correo());
        var guardada = ventaRepo.findById(venta.getId()).orElseThrow();
        monto("9.89", venta.getSubtotal());
        monto("1.78", venta.getIgv());
        monto("11.67", venta.getTotal());
        monto("9.89", guardada.getSubtotal());
        monto("1.78", guardada.getIgv());
        monto("11.67", guardada.getTotal());
    }
}
