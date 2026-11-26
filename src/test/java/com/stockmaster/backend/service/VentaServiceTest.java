package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.VentaDto;
import com.stockmaster.backend.entity.*;
import com.stockmaster.backend.exception.InsufficientStockException;
import com.stockmaster.backend.repository.MovimientoStockRepository;
import com.stockmaster.backend.repository.ProductoRepository;
import com.stockmaster.backend.repository.VentaRepository;
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
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoStockRepository movimientoStockRepository;

    @Mock
    private MetodoPagoService metodoPagoService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private VentaService ventaService;

    private Usuario vendedor;
    private Producto producto;
    private MetodoPago metodoPago;

    @BeforeEach
    void setUp() {
        vendedor = Usuario.builder()
                .id(1L)
                .correo("vendedor@stockmaster.com")
                .nombreCompleto("Juan Perez")
                .rol(Rol.VENDEDOR)
                .estado("activo")
                .build();

        producto = Producto.builder()
                .id(1L)
                .nombre("Inca Kola 500ml")
                .precioCompra(BigDecimal.valueOf(2.00))
                .precioVenta(BigDecimal.valueOf(3.00))
                .stock(15)
                .stockMinimo(5)
                .estado("activo")
                .build();

        metodoPago = MetodoPago.builder()
                .id(1L)
                .nombre("Efectivo")
                .build();
    }

    @Test
    void testCreateVenta_Success_StockDecreased() {
        VentaDto.Request request = VentaDto.Request.builder()
                .cliente("Carlos Gomez")
                .metodoPagoId(1L)
                .descuento(BigDecimal.ZERO)
                .items(List.of(
                        VentaDto.DetalleItemRequest.builder()
                                .productoId(1L)
                                .cantidad(3)
                                .precioUnitario(BigDecimal.valueOf(3.00))
                                .build()
                ))
                .build();

        when(usuarioService.findEntityByCorreo("vendedor@stockmaster.com")).thenReturn(vendedor);
        when(metodoPagoService.findEntityById(1L)).thenReturn(metodoPago);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> {
            Venta v = i.getArgument(0);
            v.setId(10L);
            return v;
        });

        VentaDto.Response response = ventaService.createVenta(request, "vendedor@stockmaster.com");

        assertNotNull(response);
        assertEquals("Carlos Gomez", response.getCliente());
        // Initial stock was 15, sold 3 -> should be 12
        assertEquals(12, producto.getStock());
        // Subtotal = 3 * 3 = 9.00
        // IGV 18% = 1.62
        // Total = 10.62
        assertEquals(BigDecimal.valueOf(9.00).setScale(2), response.getSubtotal());
        assertEquals(BigDecimal.valueOf(1.62), response.getIgv());
        assertEquals(BigDecimal.valueOf(10.62), response.getTotal());

        verify(productoRepository, times(1)).save(producto);
        verify(ventaRepository, times(1)).save(any(Venta.class));
        verify(movimientoStockRepository, times(1)).saveAll(any());
    }

    @Test
    void testCreateVenta_InsufficientStock_ThrowsException() {
        VentaDto.Request request = VentaDto.Request.builder()
                .cliente("Carlos Gomez")
                .items(List.of(
                        VentaDto.DetalleItemRequest.builder()
                                .productoId(1L)
                                .cantidad(20) // Only 15 in stock!
                                .build()
                ))
                .build();

        when(usuarioService.findEntityByCorreo("vendedor@stockmaster.com")).thenReturn(vendedor);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        assertThrows(InsufficientStockException.class,
                () -> ventaService.createVenta(request, "vendedor@stockmaster.com"));

        // Stock should remain unchanged
        assertEquals(15, producto.getStock());
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void testAnularVenta_Success_StockRestored() {
        Venta venta = Venta.builder()
                .id(1L)
                .numeroBoleta("B20260924-TEST")
                .vendedor(vendedor)
                .cliente("Carlos Gomez")
                .subtotal(BigDecimal.valueOf(9.00))
                .igv(BigDecimal.valueOf(1.62))
                .total(BigDecimal.valueOf(10.62))
                .estado("completada")
                .build();

        DetalleVenta detalle = DetalleVenta.builder()
                .id(1L)
                .venta(venta)
                .producto(producto)
                .cantidad(5)
                .precioUnitario(BigDecimal.valueOf(3.00))
                .subtotal(BigDecimal.valueOf(15.00))
                .build();
        venta.addDetalle(detalle);

        // Current stock is 10
        producto.setStock(10);

        when(ventaRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(usuarioService.findEntityByCorreo("vendedor@stockmaster.com")).thenReturn(vendedor);
        when(ventaRepository.save(any(Venta.class))).thenReturn(venta);

        VentaDto.Response response = ventaService.anularVenta(1L, "vendedor@stockmaster.com");

        assertNotNull(response);
        assertEquals("anulada", response.getEstado());
        // Restored 5 back to 10 -> 15
        assertEquals(15, producto.getStock());
        verify(productoRepository, times(1)).save(producto);
        verify(ventaRepository, times(1)).save(venta);
    }
}
