package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.DevolucionDto;
import com.stockmaster.backend.entity.*;
import com.stockmaster.backend.exception.*;
import com.stockmaster.backend.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DevolucionCoberturaTest {
    final DevolucionRepository devoluciones=mock(DevolucionRepository.class);
    final VentaRepository ventas=mock(VentaRepository.class);
    final ProductoRepository productos=mock(ProductoRepository.class);
    final UsuarioService usuarios=mock(UsuarioService.class);
    final MovimientoStockRepository movimientos=mock(MovimientoStockRepository.class);
    final AuditoriaService auditoria=mock(AuditoriaService.class);
    final DevolucionService service=new DevolucionService(devoluciones,ventas,productos,usuarios,movimientos,auditoria);
    final Producto producto=Producto.builder().id(1L).nombre("QA").sku("QA1").stock(5).build();
    final Venta venta=Venta.builder().id(2L).numeroBoleta("B-QA").cliente("Cliente QA").build();

    @BeforeEach void setup() {
        venta.addDetalle(DetalleVenta.builder().producto(producto).cantidad(3).precioUnitario(new BigDecimal("8.50")).build());
        when(ventas.findById(2L)).thenReturn(Optional.of(venta));
        when(productos.findById(1L)).thenReturn(Optional.of(producto));
        when(usuarios.findEntityByCorreo("qa")).thenReturn(Usuario.builder().id(3L).nombreCompleto("QA").build());
        when(devoluciones.save(any(Devolucion.class))).thenAnswer(i->i.getArgument(0));
    }
    DevolucionDto.Request req(int cantidad,String destino) {
        return DevolucionDto.Request.builder().ventaId(2L).motivo(" QA ").destinoStock(destino)
            .items(List.of(DevolucionDto.ItemRequest.builder().productoId(1L).cantidad(cantidad).build())).build();
    }
    @Test void reingresoCalculaMontoYRestituyeStock() {
        var d=service.createDevolucion(req(2,null),"qa");
        assertEquals(7,producto.getStock()); assertEquals(new BigDecimal("17.00"),d.getMontoTotal());
        assertEquals("reingreso",d.getDestinoStock()); assertEquals("efectivo",d.getMetodoReembolso());
        assertEquals(2,d.getItems().getFirst().getCantidad());
        verify(productos).save(producto); verify(movimientos).saveAll(anyList());
        verify(auditoria).registrar(eq("qa"),eq("CREAR"),eq("DEVOLUCIONES"),anyString(),anyString());
    }
    @Test void mermaNoIncrementaInventario() {
        var r=req(1,"merma"); r.setMetodoReembolso("TRANSFERENCIA");
        var d=service.createDevolucion(r,"qa");
        assertEquals(5,producto.getStock()); assertEquals("transferencia",d.getMetodoReembolso());
        assertEquals(new BigDecimal("8.50"),d.getMontoTotal()); verify(productos,never()).save(any());
    }
    @Test void cantidadExcesivaNoPersiste() {
        assertThrows(BadRequestException.class,()->service.createDevolucion(req(4,null),"qa"));
        assertEquals(5,producto.getStock()); verify(devoluciones,never()).save(any());
    }
    @Test void ventaAnuladaNoPermiteDevolucion() {
        venta.setEstado("anulada");
        assertThrows(BadRequestException.class,()->service.createDevolucion(req(1,null),"qa"));
        verify(devoluciones,never()).save(any()); verifyNoInteractions(productos);
    }
    @Test void ventaOProductoInexistente() {
        var r=req(1,null); r.setVentaId(99L);
        assertThrows(ResourceNotFoundException.class,()->service.createDevolucion(r,"qa"));
        r.setVentaId(2L); r.getItems().getFirst().setProductoId(99L);
        assertThrows(ResourceNotFoundException.class,()->service.createDevolucion(r,"qa"));
        verify(devoluciones,never()).save(any());
    }
    @Test void consultaDevolucionYFiltros() {
        var d=Devolucion.builder().id(4L).venta(venta).numeroDevolucion("DEV-QA").build();
        when(devoluciones.findById(4L)).thenReturn(Optional.of(d));
        when(devoluciones.findAllByOrderByFechaDevolucionDesc()).thenReturn(List.of(d));
        when(devoluciones.findByVentaIdOrderByFechaDevolucionDesc(2L)).thenReturn(List.of(d));
        assertEquals("DEV-QA",service.findById(4L,"qa").getNumeroDevolucion());
        assertEquals(1,service.findAll().size()); assertEquals(1,service.findByVentaId(2L).size());
        assertThrows(ResourceNotFoundException.class,()->service.findById(99L,"qa"));
    }
}
