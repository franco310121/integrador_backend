package com.stockmaster.backend.service;

import com.stockmaster.backend.controller.*;
import com.stockmaster.backend.dto.*;
import com.stockmaster.backend.entity.*;
import com.stockmaster.backend.exception.*;
import com.stockmaster.backend.repository.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CatalogosCoberturaTest {
    @Test void categoriaCrud() {
        var repo=mock(CategoriaRepository.class);
        var service=new CategoriaService(repo);
        var api=new CategoriaController(service);
        var entity=Categoria.builder().id(1L).nombre("Alimentos").build();
        when(repo.findAllByOrderByNombreAsc()).thenReturn(List.of(entity));
        when(repo.findById(1L)).thenReturn(Optional.of(entity));
        when(repo.save(any(Categoria.class))).thenAnswer(i->i.getArgument(0));
        when(repo.existsById(1L)).thenReturn(true);
        var req=new CategoriaDto.Request(); req.setNombre(" Bebidas "); req.setDescripcion(" QA ");
        assertEquals("Alimentos",api.getAll().getBody().getFirst().getNombre());
        assertEquals(1L,api.getById(1L).getBody().getId());
        assertSame(entity,service.findEntityById(1L));
        assertEquals(201,api.create(req).getStatusCode().value());
        assertEquals("Bebidas",api.update(1L,req).getBody().getNombre());
        assertEquals("QA",entity.getDescripcion());
        assertEquals(204,api.delete(1L).getStatusCode().value());
        verify(repo).deleteById(1L);
    }
    @Test void categoriaErrores() {
        var repo=mock(CategoriaRepository.class); var s=new CategoriaService(repo);
        var req=new CategoriaDto.Request(); req.setNombre("Duplicada");
        when(repo.existsByNombre("Duplicada")).thenReturn(true);
        assertThrows(BadRequestException.class,()->s.create(req));
        assertThrows(ResourceNotFoundException.class,()->s.findById(9L));
        assertThrows(ResourceNotFoundException.class,()->s.findEntityById(9L));
        assertThrows(ResourceNotFoundException.class,()->s.update(9L,req));
        assertThrows(ResourceNotFoundException.class,()->s.delete(9L));
        when(repo.findById(1L)).thenReturn(Optional.of(Categoria.builder().nombre("Otra").build()));
        assertThrows(BadRequestException.class,()->s.update(1L,req));
        verify(repo,never()).save(any());
    }
    @Test void proveedorCrud() {
        var repo=mock(ProveedorRepository.class); var s=new ProveedorService(repo); var api=new ProveedorController(s);
        var e=Proveedor.builder().id(1L).razonSocial("Inicial").ruc("12345678901").build();
        when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(repo.findAllByOrderByRazonSocialAsc()).thenReturn(List.of(e));
        when(repo.save(any(Proveedor.class))).thenAnswer(i->i.getArgument(0));
        when(repo.existsById(1L)).thenReturn(true);
        var req=new ProveedorDto.Request(); req.setRazonSocial(" Nuevo "); req.setRuc("10987654321");
        assertEquals("Inicial",api.getAll().getBody().getFirst().getRazonSocial());
        assertEquals(1L,api.getById(1L).getBody().getId());
        assertSame(e,s.findEntityById(1L));
        assertEquals(201,api.create(req).getStatusCode().value());
        assertEquals("Nuevo",api.update(1L,req).getBody().getRazonSocial());
        req.setRuc(null); assertEquals("10987654321",api.patch(1L,req).getBody().getRuc());
        assertEquals(204,api.delete(1L).getStatusCode().value()); verify(repo).deleteById(1L);
    }
    @Test void proveedorDuplicadoYNoEncontrado() {
        var repo=mock(ProveedorRepository.class); var s=new ProveedorService(repo);
        var req=new ProveedorDto.Request(); req.setRazonSocial("QA"); req.setRuc("12345678901");
        when(repo.existsByRuc("12345678901")).thenReturn(true);
        assertThrows(BadRequestException.class,()->s.create(req));
        when(repo.findById(1L)).thenReturn(Optional.of(Proveedor.builder().ruc("00000000000").build()));
        assertThrows(BadRequestException.class,()->s.update(1L,req));
        assertThrows(ResourceNotFoundException.class,()->s.findById(9L));
        assertThrows(ResourceNotFoundException.class,()->s.findEntityById(9L));
        assertThrows(ResourceNotFoundException.class,()->s.update(9L,req));
        assertThrows(ResourceNotFoundException.class,()->s.delete(9L));
        verify(repo,never()).save(any());
    }
    @Test void proveedorSinRuc() {
        var repo=mock(ProveedorRepository.class); var s=new ProveedorService(repo);
        when(repo.save(any(Proveedor.class))).thenAnswer(i->i.getArgument(0));
        var req=new ProveedorDto.Request(); req.setRazonSocial("QA"); req.setRuc(" ");
        assertNull(s.create(req).getRuc()); verify(repo,never()).existsByRuc(anyString());
    }
    @Test void metodosPago() {
        var repo=mock(MetodoPagoRepository.class); var s=new MetodoPagoService(repo); var api=new MetodoPagoController(s);
        var e=MetodoPago.builder().id(1L).nombre("Efectivo").build();
        when(repo.findAll()).thenReturn(List.of(e)); when(repo.findById(1L)).thenReturn(Optional.of(e));
        when(repo.save(any(MetodoPago.class))).thenAnswer(i->i.getArgument(0));
        var req=new MetodoPagoDto.Request(); req.setNombre(" Tarjeta ");
        assertEquals("Efectivo",api.getAll().getBody().getFirst().getNombre());
        assertEquals(201,api.create(req).getStatusCode().value());
        assertSame(e,s.findEntityById(1L)); assertNull(s.findEntityById(null));
        assertThrows(ResourceNotFoundException.class,()->s.findEntityById(9L));
        when(repo.existsByNombre("Tarjeta")).thenReturn(true);
        assertThrows(BadRequestException.class,()->s.create(req));
    }
    @Test void empresaAusenteCreacionYActualizacion() {
        var repo=mock(EmpresaRepository.class); var s=new EmpresaService(repo); var api=new EmpresaController(s);
        when(repo.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());
        when(repo.save(any(Empresa.class))).thenAnswer(i->i.getArgument(0));
        assertEquals(404,api.getEmpresa().getStatusCode().value());
        var req=new EmpresaDto.Request(); req.setNombreMype("QA"); req.setRuc("12345678901");
        assertEquals("QA",api.guardarEmpresa(req).getBody().getNombreMype());
        var e=new Empresa(); e.setNombreMype("Inicial");
        when(repo.findFirstByOrderByIdAsc()).thenReturn(Optional.of(e));
        assertEquals("Inicial",api.getEmpresa().getBody().getNombreMype());
        req.setNombreMype("Actualizada"); assertEquals("Actualizada",api.actualizarEmpresa(req).getBody().getNombreMype());
        verify(repo).save(e);
    }
    @Test void clienteValidaciones() {
        var repo=mock(ClienteRepository.class); var audit=mock(AuditoriaService.class); var s=new ClienteService(repo,audit);
        var req=new ClienteDto.Request(); req.setNombre("QA");
        assertThrows(BadRequestException.class,()->s.create(req,"qa"));
        req.setNumeroDocumento("123"); assertThrows(BadRequestException.class,()->s.create(req,"qa"));
        req.setTipoDocumento("RUC"); assertThrows(BadRequestException.class,()->s.create(req,"qa"));
        req.setNumeroDocumento("12345678901"); when(repo.existsByNumeroDocumento("12345678901")).thenReturn(true);
        assertThrows(BadRequestException.class,()->s.create(req,"qa"));
        verify(repo,never()).save(any()); verifyNoInteractions(audit);
    }
    @Test void clienteCrudYFiltros() {
        var repo=mock(ClienteRepository.class); var audit=mock(AuditoriaService.class); var s=new ClienteService(repo,audit); var api=new ClienteController(s);
        var e=Cliente.builder().id(1L).nombre("Inicial").tipoDocumento("DNI").numeroDocumento("12345678").estado("activo").build();
        when(repo.findAllByOrderByNombreAsc()).thenReturn(List.of(e));
        when(repo.findByEstadoOrderByNombreAsc("activo")).thenReturn(List.of(e));
        when(repo.findByNombreContainingIgnoreCaseOrNumeroDocumentoContainingIgnoreCaseOrderByNombreAsc("QA","QA")).thenReturn(List.of(e));
        when(repo.findById(1L)).thenReturn(Optional.of(e)); when(repo.save(any(Cliente.class))).thenAnswer(i->i.getArgument(0));
        var req=new ClienteDto.Request(); req.setNombre(" QA "); req.setNumeroDocumento("12345678");
        assertEquals(1,api.getAll(null,null).getBody().size());
        assertEquals(1,api.getAll("activo",null).getBody().size());
        assertEquals(1,api.getAll(null," QA ").getBody().size()); assertEquals(1,s.search(" ").size());
        assertEquals("Inicial",api.getById(1L,null).getBody().getNombre());
        assertEquals(201,api.create(req,null).getStatusCode().value());
        assertEquals("QA",api.update(1L,req,null).getBody().getNombre());
        assertEquals(204,api.delete(1L,null).getStatusCode().value()); assertEquals("inactivo",e.getEstado());
        verify(audit).registrar(isNull(),eq("ELIMINAR"),eq("CLIENTES"),eq("QA"),anyString());
    }
}
