package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.ProductoDto;
import com.stockmaster.backend.entity.Categoria;
import com.stockmaster.backend.entity.Producto;
import com.stockmaster.backend.exception.BadRequestException;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private CategoriaService categoriaService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ProductoService productoService;

    private Categoria categoria;
    private Producto producto;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .id(1L)
                .nombre("Lácteos")
                .descripcion("Productos lácteos")
                .build();

        producto = Producto.builder()
                .id(1L)
                .categoria(categoria)
                .sku("LAC-001")
                .nombre("Leche Gloria 400g")
                .descripcion("Leche evaporada")
                .precioCompra(BigDecimal.valueOf(3.20))
                .precioVenta(BigDecimal.valueOf(4.50))
                .stock(20)
                .stockMinimo(5)
                .estado("activo")
                .build();
    }

    @Test
    void testFindActive_ReturnsActiveProducts() {
        when(productoRepository.findByEstado("activo")).thenReturn(List.of(producto));

        List<ProductoDto.Response> result = productoService.findActive();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Leche Gloria 400g", result.get(0).getNombre());
        assertEquals("Normal", result.get(0).getStockLabel());
    }

    @Test
    void testFindById_Success() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        ProductoDto.Response result = productoService.findById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("LAC-001", result.getSku());
    }

    @Test
    void testFindById_NotFound_ThrowsException() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productoService.findById(99L));
    }

    @Test
    void testCreate_Success() {
        ProductoDto.Request request = ProductoDto.Request.builder()
                .categoriaId(1L)
                .sku("LAC-002")
                .nombre("Yogurt Fresa 1L")
                .precioCompra(BigDecimal.valueOf(5.00))
                .precioVenta(BigDecimal.valueOf(6.50))
                .stock(10)
                .stockMinimo(5)
                .build();

        when(productoRepository.existsBySku("LAC-002")).thenReturn(false);
        when(categoriaService.findEntityById(1L)).thenReturn(categoria);
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> {
            Producto p = i.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductoDto.Response result = productoService.create(request);

        assertNotNull(result);
        assertEquals("Yogurt Fresa 1L", result.getNombre());
        verify(productoRepository, times(1)).save(any(Producto.class));
    }

    @Test
    void testCreate_DuplicateSku_ThrowsBadRequestException() {
        ProductoDto.Request request = ProductoDto.Request.builder()
                .sku("LAC-001")
                .nombre("Otro Producto")
                .categoriaId(1L)
                .precioCompra(BigDecimal.ONE)
                .precioVenta(BigDecimal.TEN)
                .build();

        when(productoRepository.existsBySku("LAC-001")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> productoService.create(request));
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    void testDelete_PerformsLogicalDeactivation() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        productoService.delete(1L);

        assertEquals("inactivo", producto.getEstado());
        verify(productoRepository, times(1)).save(producto);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0,Sin stock", "4,Bajo", "5,Bajo", "6,Normal"})
    void clasificaStockEnValoresLimite(int stock, String etiqueta) {
        producto.setStock(stock);
        assertEquals(etiqueta, productoService.mapToDto(producto).getStockLabel());
    }

    @Test
    void precioVentaCeroNoProvocaDivisionPorCero() {
        producto.setPrecioVenta(BigDecimal.ZERO);
        assertEquals(0.0, productoService.mapToDto(producto).getMargen(), 0.0001);
    }

    @Test
    void eliminarProductoInexistenteNoModificaInventario() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> productoService.delete(99L));
        verify(productoRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }
}
