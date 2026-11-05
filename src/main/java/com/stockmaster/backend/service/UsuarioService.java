package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.AuthDto;
import com.stockmaster.backend.dto.UserDto;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.entity.Usuario;
import com.stockmaster.backend.exception.BadRequestException;
import com.stockmaster.backend.exception.ResourceNotFoundException;
import com.stockmaster.backend.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public List<UserDto.UserResponse> findAll() {
        return usuarioRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    public UserDto.UserResponse findById(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        return mapToDto(usuario);
    }

    public Usuario findEntityById(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    public Usuario findEntityByCorreo(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con correo: " + correo));
    }

    @Transactional
    public UserDto.UserResponse create(AuthDto.RegisterRequest request) {
        if (usuarioRepository.existsByCorreo(request.getCorreo())) {
            throw new BadRequestException("El correo ya está registrado: " + request.getCorreo());
        }

        Usuario usuario = Usuario.builder()
                .correo(request.getCorreo())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombreCompleto(request.getNombreCompleto())
                .rol(request.getRol() != null ? request.getRol() : Rol.VENDEDOR)
                .estado("activo")
                .build();

        return mapToDto(usuarioRepository.save(usuario));
    }

    @Transactional
    public UserDto.UserResponse update(Long id, UserDto.UpdateUserRequest request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        if (request.getNombreCompleto() != null && !request.getNombreCompleto().isBlank()) {
            usuario.setNombreCompleto(request.getNombreCompleto());
        }
        if (request.getRol() != null) {
            usuario.setRol(request.getRol());
        }
        if (request.getEstado() != null && !request.getEstado().isBlank()) {
            usuario.setEstado(request.getEstado());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        if (request.getAvatarUrl() != null) {
            usuario.setAvatarUrl(request.getAvatarUrl());
        }

        return mapToDto(usuarioRepository.save(usuario));
    }

    @Transactional
    public void toggleStatus(Long id, String estado) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        usuario.setEstado(estado);
        usuarioRepository.save(usuario);
    }

    @Transactional
    public UserDto.UserResponse cambiarRol(Long id, Rol nuevoRol) {
        if (nuevoRol == null) {
            throw new BadRequestException("El rol proporcionado no puede ser nulo");
        }
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        usuario.setRol(nuevoRol);
        return mapToDto(usuarioRepository.save(usuario));
    }

    public List<UserDto.RolInfoResponse> listarRolesDisponibles() {
        return List.of(
                UserDto.RolInfoResponse.builder()
                        .rol(Rol.ADMIN.name().toLowerCase())
                        .descripcion("Acceso total a la administración del sistema, usuarios, inventario, auditoría y validación de IA.")
                        .permisos(List.of("GESTIONAR_USUARIOS", "GESTIONAR_ROLES", "VALIDAR_MODELO_IA", "SIMULAR_ESCENARIOS", "GESTIONAR_INVENTARIO", "VER_AUDITORIA"))
                        .build(),
                UserDto.RolInfoResponse.builder()
                        .rol(Rol.VENDEDOR.name().toLowerCase())
                        .descripcion("Acceso operativo a ventas, devoluciones, clientes, flujo de caja y consulta de recomendaciones.")
                        .permisos(List.of("REGISTRAR_VENTAS", "GESTIONAR_CLIENTES", "CONSULTAR_FLUJO_CAJA", "CONSULTAR_RECOMENDACIONES", "SIMULAR_ESCENARIOS"))
                        .build()
        );
    }

    public UserDto.UserResponse mapToDto(Usuario usuario) {
        return UserDto.UserResponse.builder()
                .id(usuario.getId())
                .correo(usuario.getCorreo())
                .nombreCompleto(usuario.getNombreCompleto())
                .rol(usuario.getRol().name().toLowerCase())
                .estado(usuario.getEstado())
                .avatarUrl(usuario.getAvatarUrl())
                .fechaCreacion(usuario.getFechaCreacion())
                .build();
    }
}
