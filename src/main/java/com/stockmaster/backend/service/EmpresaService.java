package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.EmpresaDto;
import com.stockmaster.backend.entity.Empresa;
import com.stockmaster.backend.repository.EmpresaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    @Transactional(readOnly = true)
    public EmpresaDto.Response getEmpresa() {
        Empresa empresa = empresaRepository.findFirstByOrderByIdAsc()
                .orElse(null);
        if (empresa == null) {
            return null;
        }
        return mapToResponse(empresa);
    }

    @Transactional
    public EmpresaDto.Response guardarOActualizar(EmpresaDto.Request request) {
        Empresa empresa = empresaRepository.findFirstByOrderByIdAsc()
                .orElse(new Empresa());

        empresa.setNombreMype(request.getNombreMype());
        empresa.setRuc(request.getRuc());
        empresa.setDireccion(request.getDireccion());
        empresa.setTelefono(request.getTelefono());
        empresa.setCorreo(request.getCorreo());
        empresa.setSectorComercial(request.getSectorComercial());

        Empresa saved = empresaRepository.save(empresa);
        return mapToResponse(saved);
    }

    private EmpresaDto.Response mapToResponse(Empresa e) {
        return EmpresaDto.Response.builder()
                .id(e.getId())
                .nombreMype(e.getNombreMype())
                .ruc(e.getRuc())
                .direccion(e.getDireccion())
                .telefono(e.getTelefono())
                .correo(e.getCorreo())
                .sectorComercial(e.getSectorComercial())
                .fechaCreacion(e.getFechaCreacion())
                .build();
    }
}
