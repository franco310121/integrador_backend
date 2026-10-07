package com.stockmaster.backend.controller;

import com.stockmaster.backend.dto.EmpresaDto;
import com.stockmaster.backend.service.EmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/empresa")
@RequiredArgsConstructor
public class EmpresaController {

    private final EmpresaService empresaService;

    @GetMapping
    public ResponseEntity<EmpresaDto.Response> getEmpresa() {
        EmpresaDto.Response empresa = empresaService.getEmpresa();
        if (empresa == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(empresa);
    }

    @PostMapping
    public ResponseEntity<EmpresaDto.Response> guardarEmpresa(@Valid @RequestBody EmpresaDto.Request request) {
        return ResponseEntity.ok(empresaService.guardarOActualizar(request));
    }

    @PutMapping
    public ResponseEntity<EmpresaDto.Response> actualizarEmpresa(@Valid @RequestBody EmpresaDto.Request request) {
        return ResponseEntity.ok(empresaService.guardarOActualizar(request));
    }
}
