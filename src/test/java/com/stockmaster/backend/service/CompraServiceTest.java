package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.CompraDto;
import com.stockmaster.backend.entity.Compra;
import com.stockmaster.backend.entity.Producto;
import com.stockmaster.backend.entity.Proveedor;
import com.stockmaster.backend.entity.Usuario;
import com.stockmaster.backend.repository.CompraRepository;
import com.stockmaster.backend.repository.MovimientoStockRepository;
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
class CompraServiceTest {

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoStockRepository movimientoStockRepository;

    @Mock
    private ProveedorService proveedorService;

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private CompraService compraService;

    private Proveedor proveedor;
    private Usuario usuario;
    private Producto producto;

    @BeforeEach
    void setUp() {
        proveedor = Proveedor.builder()
                .id(1L)
                .razonSocial("Gloria S.A.")
                .ruc("20100190797")
                .build();

        usuario = Usuario.builder()
                .id(1L)
                .correo("admin@stockmaster.com")
                .nombreCompleto("Admin")
                .build();

        producto = Producto.builder()
                .id(1L)
                .nombre("Leche Gloria")
                .precioCompra(BigDecimal.valueOf(3.00))
                .stock(10)
                .build();
    }

    @Test
    void testCreateCompra_IncreasesStockAndUpdatesCost() {
        CompraDto.Request request = CompraDto.Request.builder()
                .proveedorId(1L)
                .items(List.of(
                        CompraDto.DetalleItemRequest.builder()
                                .productoId(1L)
                                .cantidad(20)
                                .costoUnitario(BigDecimal.valueOf(3.50))
                                .build()
                ))
                .build();

        when(proveedorService.findEntityById(1L)).thenReturn(proveedor);
        when(usuarioService.findEntityByCorreo("admin@stockmaster.com")).thenReturn(usuario);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(compraRepository.save(any(Compra.class))).thenAnswer(i -> {
            Compra c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CompraDto.Response response = compraService.createCompra(request, "admin@stockmaster.com");

        assertNotNull(response);
        // Stock 10 + 20 = 30
        assertEquals(30, producto.getStock());
        // Cost price updated to 3.50
        assertEquals(BigDecimal.valueOf(3.50), producto.getPrecioCompra());
        // Subtotal = 20 * 3.50 = 70.00
        assertEquals(BigDecimal.valueOf(70.00).setScale(2), response.getSubtotal());

        verify(productoRepository, times(1)).save(producto);
        verify(compraRepository, times(1)).save(any(Compra.class));
        verify(movimientoStockRepository, times(1)).saveAll(any());
    }
}
