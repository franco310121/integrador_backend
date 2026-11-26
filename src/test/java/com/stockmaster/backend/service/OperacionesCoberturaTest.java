package com.stockmaster.backend.service;

import com.stockmaster.backend.controller.*;
import com.stockmaster.backend.dto.*;
import com.stockmaster.backend.entity.*;
import com.stockmaster.backend.exception.*;
import com.stockmaster.backend.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OperacionesCoberturaTest {
    @Test void dashboardExcluyeVentasAnuladasYClasificaStock() {
        var p=mock(ProductoRepository.class); var v=mock(VentaRepository.class); var c=mock(CompraRepository.class);
        when(p.findByEstado("activo")).thenReturn(List.of(Producto.builder().stock(0).stockMinimo(2).build(),Producto.builder().stock(2).stockMinimo(2).build(),Producto.builder().stock(9).stockMinimo(2).build()));
        when(v.findAll()).thenReturn(List.of(Venta.builder().estado("completada").total(BigDecimal.TEN).build(),Venta.builder().estado("anulada").total(BigDecimal.ONE).build()));
        when(c.findAll()).thenReturn(List.of(Compra.builder().total(BigDecimal.ONE).build()));
        var d=new DashboardController(new DashboardService(p,v,c)).getStats().getBody();
        assertEquals(3L,d.getTotalProductos()); assertEquals(1L,d.getProductosBajoStock()); assertEquals(1L,d.getProductosSinStock());
        assertEquals(BigDecimal.TEN,d.getTotalIngresos()); assertEquals(BigDecimal.ONE,d.getTotalEgresos());
    }
    @Test void movimientosEntradaSalidaAjusteYErrores() {
        var m=mock(MovimientoStockRepository.class); var p=mock(ProductoRepository.class); var u=mock(UsuarioService.class);
        var s=new MovimientoStockService(m,p,u);
        var producto=Producto.builder().id(1L).nombre("QA").sku("QA1").stock(10).build();
        when(p.findById(1L)).thenReturn(Optional.of(producto));
        when(u.findEntityByCorreo("qa")).thenReturn(Usuario.builder().id(1L).nombreCompleto("QA").build());
        when(m.save(any(MovimientoStock.class))).thenAnswer(i->i.getArgument(0));
        var req=new MovimientoStockDto.Request(); req.setProductoId(1L); req.setCantidad(3); req.setTipo("ENTRADA");
        assertEquals(3,s.createMovimiento(req,"qa").getCantidad()); assertEquals(13,producto.getStock());
        req.setTipo("salida"); assertEquals(-3,s.createMovimiento(req,"qa").getCantidad()); assertEquals(10,producto.getStock());
        req.setTipo("ajuste"); req.setMotivo(" Conteo "); var a=s.createMovimiento(req,"qa");
        assertEquals(-7,a.getCantidad()); assertEquals(3,producto.getStock()); assertEquals("Conteo",a.getMotivo());
        req.setTipo("venta"); req.setCantidad(4); assertThrows(InsufficientStockException.class,()->s.createMovimiento(req,"qa"));
        req.setTipo("invalido"); assertThrows(IllegalArgumentException.class,()->s.createMovimiento(req,"qa"));
        req.setProductoId(9L); assertThrows(ResourceNotFoundException.class,()->s.createMovimiento(req,"qa"));
        assertEquals(3,producto.getStock()); verify(m,times(3)).save(any());
    }
    @Test void movimientosFiltros() {
        var m=mock(MovimientoStockRepository.class); var s=new MovimientoStockService(m,mock(ProductoRepository.class),mock(UsuarioService.class));
        var api=new MovimientoStockController(s); var desde=LocalDateTime.of(2026,1,1,0,0); var hasta=desde.plusDays(1);
        assertTrue(api.getAll(1L,null,null,null).getBody().isEmpty()); verify(m).findByProductoIdOrderByFechaMovimientoDesc(1L);
        assertTrue(api.getAll(null,"ENTRADA",null,null).getBody().isEmpty()); verify(m).findByTipoOrderByFechaMovimientoDesc("entrada");
        assertTrue(api.getAll(null,null,desde,hasta).getBody().isEmpty()); verify(m).findByFechaMovimientoBetweenOrderByFechaMovimientoDesc(desde,hasta);
        assertTrue(api.getAll(null,null,null,null).getBody().isEmpty()); verify(m).findAllByOrderByFechaMovimientoDesc();
    }
    @Test void auditoriaIdentidadYNormalizacion() {
        var logs=mock(AuditoriaLogRepository.class); var usuarios=mock(UsuarioRepository.class); var s=new AuditoriaService(logs,usuarios);
        when(usuarios.findByCorreo("qa")).thenReturn(Optional.of(Usuario.builder().nombreCompleto("Usuario QA").build()));
        s.registrar("qa","crear","clientes","Ficha","Detalle"); s.registrar(null,null,null,null,null);
        var captor=org.mockito.ArgumentCaptor.forClass(AuditoriaLog.class); verify(logs,times(2)).save(captor.capture());
        var entries=captor.getAllValues(); assertEquals("Usuario QA",entries.getFirst().getQuienLoHizo()); assertEquals("CREAR",entries.getFirst().getAccion());
        assertEquals("Sistema",entries.getLast().getQuienLoHizo()); assertEquals("GENERAL",entries.getLast().getModulo());
        when(logs.findAllByOrderByFechaDesc()).thenReturn(entries);
        assertEquals(2,new AuditoriaController(s).getAll(null,null,null).getBody().size());
        var d=LocalDateTime.of(2026,1,1,0,0);
        new AuditoriaController(s).getAll("CLIENTES",null,null); verify(logs).findByModuloIgnoreCaseOrderByFechaDesc("CLIENTES");
        new AuditoriaController(s).getAll(null,d,d.plusDays(1)); verify(logs).findByFechaBetweenOrderByFechaDesc(d,d.plusDays(1));
    }
    @Test void cajaImportacionMixtaYFechas() {
        var repo=mock(TransaccionCajaRepository.class); var s=new TransaccionCajaService(repo); var api=new TransaccionCajaController(s);
        var req=ImportarFinancieroDto.Request.builder().registros(List.of(
            ImportarFinancieroDto.RegistroImportacion.builder().tipo(" INGRESO ").monto(10.0).fechaTransaccion("2026-01-01").build(),
            ImportarFinancieroDto.RegistroImportacion.builder().tipo("egreso").monto(3.0).fechaTransaccion("2026-01-02T12:00:00").build(),
            ImportarFinancieroDto.RegistroImportacion.builder().tipo("otro").monto(2.0).build()
        )).build();
        var result=api.importarFinanciero(req).getBody(); assertEquals(2,result.getProcesadosExitosos()); assertEquals(1,result.getRegistrosConError());
        var cap=org.mockito.ArgumentCaptor.forClass(TransaccionCaja.class); verify(repo,times(2)).save(cap.capture());
        assertEquals(LocalDateTime.of(2026,1,1,0,0),cap.getAllValues().getFirst().getFechaTransaccion());
        assertEquals("egreso",cap.getAllValues().getLast().getTipo());
        when(repo.findHistorico()).thenReturn(cap.getAllValues());
        when(repo.sumMontoByTipo("ingreso")).thenReturn(BigDecimal.TEN); when(repo.sumMontoByTipo("egreso")).thenReturn(BigDecimal.valueOf(3));
        assertEquals(BigDecimal.valueOf(7),api.getHistorico().getBody().getSaldoNetoHistorico());
    }
}
