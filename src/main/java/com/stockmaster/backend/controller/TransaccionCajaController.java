package com.stockmaster.backend.controller;

import com.stockmaster.backend.dto.FlujoCajaHistoricoDto;
import com.stockmaster.backend.dto.ImportarFinancieroDto;
import com.stockmaster.backend.dto.TransaccionCajaDto;
import com.stockmaster.backend.service.TransaccionCajaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/caja")
@RequiredArgsConstructor
public class TransaccionCajaController {

    private final TransaccionCajaService transaccionCajaService;

    @PostMapping("/transacciones")
    public ResponseEntity<TransaccionCajaDto.Response> registrarTransaccion(@Valid @RequestBody TransaccionCajaDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transaccionCajaService.registrarTransaccion(request));
    }

    @PostMapping("/importar")
    public ResponseEntity<ImportarFinancieroDto.Response> importarFinanciero(@RequestBody ImportarFinancieroDto.Request request) {
        return ResponseEntity.ok(transaccionCajaService.importarTransacciones(request));
    }

    @GetMapping("/historico")
    public ResponseEntity<FlujoCajaHistoricoDto> getHistorico() {
        return ResponseEntity.ok(transaccionCajaService.getHistoricoFlujoCaja());
    }
}
