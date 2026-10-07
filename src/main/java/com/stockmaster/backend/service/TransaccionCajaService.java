package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.FlujoCajaHistoricoDto;
import com.stockmaster.backend.dto.ImportarFinancieroDto;
import com.stockmaster.backend.dto.TransaccionCajaDto;
import com.stockmaster.backend.entity.TransaccionCaja;
import com.stockmaster.backend.repository.TransaccionCajaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransaccionCajaService {

    private final TransaccionCajaRepository transaccionCajaRepository;

    @Transactional
    public TransaccionCajaDto.Response registrarTransaccion(TransaccionCajaDto.Request request) {
        // Validar y normalizar datos financieros
        String tipoNormalizado = request.getTipo().trim().toLowerCase();
        if (!"ingreso".equals(tipoNormalizado) && !"egreso".equals(tipoNormalizado)) {
            throw new IllegalArgumentException("El tipo de transacción debe ser 'ingreso' o 'egreso'");
        }

        TransaccionCaja transaccion = TransaccionCaja.builder()
                .tipo(tipoNormalizado)
                .categoriaGasto(request.getCategoriaGasto() != null ? request.getCategoriaGasto().trim() : "General")
                .monto(request.getMonto())
                .fechaTransaccion(request.getFechaTransaccion() != null ? request.getFechaTransaccion() : LocalDateTime.now())
                .observaciones(request.getObservaciones())
                .estado("efectivizado")
                .build();

        TransaccionCaja saved = transaccionCajaRepository.save(transaccion);
        return mapToResponse(saved);
    }

    @Transactional
    public ImportarFinancieroDto.Response importarTransacciones(ImportarFinancieroDto.Request request) {
        if (request.getRegistros() == null || request.getRegistros().isEmpty()) {
            return ImportarFinancieroDto.Response.builder()
                    .procesadosExitosos(0)
                    .registrosConError(0)
                    .detallesError(List.of("No se enviaron registros para importar"))
                    .mensaje("Importación fallida: lista vacía")
                    .build();
        }

        int exitosos = 0;
        int errores = 0;
        List<String> erroresDetalle = new ArrayList<>();

        for (int i = 0; i < request.getRegistros().size(); i++) {
            ImportarFinancieroDto.RegistroImportacion reg = request.getRegistros().get(i);
            try {
                if (reg.getTipo() == null || reg.getMonto() == null || reg.getMonto() <= 0) {
                    throw new IllegalArgumentException("El tipo o el monto es inválido");
                }
                String tipoNorm = reg.getTipo().trim().toLowerCase();
                if (!"ingreso".equals(tipoNorm) && !"egreso".equals(tipoNorm)) {
                    throw new IllegalArgumentException("Tipo no permitido: " + reg.getTipo());
                }

                LocalDateTime fecha = LocalDateTime.now();
                if (reg.getFechaTransaccion() != null && !reg.getFechaTransaccion().isBlank()) {
                    try {
                        fecha = LocalDateTime.parse(reg.getFechaTransaccion());
                    } catch (Exception e) {
                        try {
                            fecha = LocalDateTime.parse(reg.getFechaTransaccion() + "T00:00:00");
                        } catch (Exception ex) {
                            fecha = LocalDateTime.now();
                        }
                    }
                }

                TransaccionCaja t = TransaccionCaja.builder()
                        .tipo(tipoNorm)
                        .categoriaGasto(reg.getCategoriaGasto() != null ? reg.getCategoriaGasto().trim() : "Importación")
                        .monto(BigDecimal.valueOf(reg.getMonto()))
                        .fechaTransaccion(fecha)
                        .observaciones(reg.getObservaciones() != null ? reg.getObservaciones() : "Importado desde Excel/CSV")
                        .estado("efectivizado")
                        .build();

                transaccionCajaRepository.save(t);
                exitosos++;
            } catch (Exception e) {
                errores++;
                erroresDetalle.add("Fila " + (i + 1) + ": " + e.getMessage());
            }
        }

        return ImportarFinancieroDto.Response.builder()
                .procesadosExitosos(exitosos)
                .registrosConError(errores)
                .detallesError(erroresDetalle)
                .mensaje("Importación completada con " + exitosos + " registros exitosos.")
                .build();
    }

    @Transactional(readOnly = true)
    public FlujoCajaHistoricoDto getHistoricoFlujoCaja() {
        List<TransaccionCaja> historico = transaccionCajaRepository.findHistorico();

        BigDecimal totalIngresos = transaccionCajaRepository.sumMontoByTipo("ingreso");
        BigDecimal totalEgresos = transaccionCajaRepository.sumMontoByTipo("egreso");
        BigDecimal saldoNeto = totalIngresos.subtract(totalEgresos);

        List<TransaccionCajaDto.Response> dtos = historico.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return FlujoCajaHistoricoDto.builder()
                .totalIngresos(totalIngresos)
                .totalEgresos(totalEgresos)
                .saldoNetoHistorico(saldoNeto)
                .cantidadTransacciones((long) historico.size())
                .transacciones(dtos)
                .build();
    }

    private TransaccionCajaDto.Response mapToResponse(TransaccionCaja t) {
        return TransaccionCajaDto.Response.builder()
                .id(t.getId())
                .tipo(t.getTipo())
                .categoriaGasto(t.getCategoriaGasto())
                .monto(t.getMonto())
                .fechaTransaccion(t.getFechaTransaccion())
                .estado(t.getEstado())
                .observaciones(t.getObservaciones())
                .ventaId(t.getVenta() != null ? t.getVenta().getId() : null)
                .compraId(t.getCompra() != null ? t.getCompra().getId() : null)
                .fechaCreacion(t.getFechaCreacion())
                .build();
    }
}
