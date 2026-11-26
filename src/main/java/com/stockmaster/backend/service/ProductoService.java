package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.ProductoDto;
import com.stockmaster.backend.entity.Categoria;
import com.stockmaster.backend.entity.Producto;
import com.stockmaster.backend.exception.BadRequestException;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final CategoriaService categoriaService;
    private final AuditoriaService auditoriaService;

    public List<ProductoDto.Response> findAll() {
        return productoRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<ProductoDto.Response> findActive() {
        return productoRepository.findByEstado("activo").stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProductoDto.Response findById(Long id) {
        return findById(id, null);
    }

    public ProductoDto.Response findById(Long id, String userCorreo) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (userCorreo != null) {
            auditoriaService.registrar(
                    userCorreo,
                    "VER",
                    "PRODUCTOS",
                    producto.getNombre(),
                    "Consulta de producto (SKU: " + producto.getSku() + ")"
            );
        }

        return mapToDto(producto);
    }

    public Producto findEntityById(Long id) {
        return productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
    }

    public List<ProductoDto.Response> search(String query) {
        if (query == null || query.isBlank()) {
            return findActive();
        }
        return productoRepository.searchProducts(query.trim()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public List<ProductoDto.Response> findLowStock() {
        return productoRepository.findLowStockProducts().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public ProductoDto.Response create(ProductoDto.Request request) {
        return create(request, null);
    }

    @Transactional
    public ProductoDto.Response create(ProductoDto.Request request, String userCorreo) {
        if (request.getSku() != null && !request.getSku().isBlank()) {
            if (productoRepository.existsBySku(request.getSku().trim())) {
                throw new BadRequestException("Ya existe un producto con el SKU: " + request.getSku());
            }
        }

        Categoria categoria = categoriaService.findEntityById(request.getCategoriaId());

        Producto producto = Producto.builder()
                .categoria(categoria)
                .sku(request.getSku() != null && !request.getSku().isBlank() ? request.getSku().trim() : null)
                .nombre(request.getNombre().trim())
                .descripcion(request.getDescripcion() != null ? request.getDescripcion().trim() : null)
                .imagenUrl(request.getImagenUrl() != null && !request.getImagenUrl().isBlank() ? request.getImagenUrl().trim() : null)
                .precioCompra(request.getPrecioCompra())
                .precioVenta(request.getPrecioVenta())
                .stock(request.getStock() != null ? request.getStock() : 0)
                .stockMinimo(request.getStockMinimo() != null ? request.getStockMinimo() : 5)
                .estado(request.getEstado() != null ? request.getEstado() : "activo")
                .build();

        Producto guardado = productoRepository.save(producto);

        auditoriaService.registrar(
                userCorreo,
                "CREAR",
                "PRODUCTOS",
                guardado.getNombre(),
                "Producto creado (SKU: " + guardado.getSku() + ", Stock inicial: " + guardado.getStock() + ")"
        );

        return mapToDto(guardado);
    }

    public ProductoDto.Response update(Long id, ProductoDto.Request request) {
        return update(id, request, null);
    }

    @Transactional
    public ProductoDto.Response update(Long id, ProductoDto.Request request, String userCorreo) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));

        if (request.getSku() != null && !request.getSku().isBlank()) {
            if (!request.getSku().equalsIgnoreCase(producto.getSku()) &&
                    productoRepository.existsBySku(request.getSku().trim())) {
                throw new BadRequestException("Ya existe otro producto con el SKU: " + request.getSku());
            }
            producto.setSku(request.getSku().trim());
        }

        if (request.getCategoriaId() != null) {
            Categoria categoria = categoriaService.findEntityById(request.getCategoriaId());
            producto.setCategoria(categoria);
        }

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            producto.setNombre(request.getNombre().trim());
        }
        if (request.getDescripcion() != null) {
            producto.setDescripcion(request.getDescripcion().trim());
        }
        if (request.getImagenUrl() != null) {
            producto.setImagenUrl(request.getImagenUrl().trim());
        }
        if (request.getPrecioCompra() != null) {
            producto.setPrecioCompra(request.getPrecioCompra());
        }
        if (request.getPrecioVenta() != null) {
            producto.setPrecioVenta(request.getPrecioVenta());
        }
        if (request.getStock() != null) {
            producto.setStock(request.getStock());
        }
        if (request.getStockMinimo() != null) {
            producto.setStockMinimo(request.getStockMinimo());
        }
        if (request.getEstado() != null) {
            producto.setEstado(request.getEstado());
        }

        Producto actualizado = productoRepository.save(producto);

        auditoriaService.registrar(
                userCorreo,
                "EDITAR",
                "PRODUCTOS",
                actualizado.getNombre(),
                "Actualización de producto ID: " + id + " (Stock actual: " + actualizado.getStock() + ")"
        );

        return mapToDto(actualizado);
    }

    public void delete(Long id) {
        delete(id, null);
    }

    @Transactional
    public void delete(Long id, String userCorreo) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + id));
        producto.setEstado("inactivo");
        productoRepository.save(producto);

        auditoriaService.registrar(
                userCorreo,
                "ELIMINAR",
                "PRODUCTOS",
                producto.getNombre(),
                "Inactivación de producto ID: " + id + " (" + producto.getSku() + ")"
        );
    }

    public ProductoDto.Response mapToDto(Producto p) {
        double margen = 0.0;
        if (p.getPrecioVenta() != null && p.getPrecioVenta().compareTo(BigDecimal.ZERO) > 0 &&
                p.getPrecioCompra() != null) {
            BigDecimal diff = p.getPrecioVenta().subtract(p.getPrecioCompra());
            margen = diff.divide(p.getPrecioVenta(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return ProductoDto.Response.builder()
                .id(p.getId())
                .categoriaId(p.getCategoria().getId())
                .categoriaNombre(p.getCategoria().getNombre())
                .sku(p.getSku())
                .nombre(p.getNombre())
                .descripcion(p.getDescripcion())
                .imagenUrl(p.getImagenUrl())
                .precioCompra(p.getPrecioCompra())
                .precioVenta(p.getPrecioVenta())
                .stock(p.getStock())
                .stockMinimo(p.getStockMinimo())
                .stockLabel(p.getStock() == null || p.getStock() <= 0 ? "Sin stock"
                        : p.getStockMinimo() != null && p.getStock() <= p.getStockMinimo() ? "Bajo" : "Normal")
                .estado(p.getEstado())
                .margen(margen)
                .margenGanancia(margen)
                .fechaCreacion(p.getFechaCreacion())
                .fechaActualizacion(p.getFechaActualizacion())
                .build();
    }
}
