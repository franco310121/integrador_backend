package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.AuthDto;
import com.stockmaster.backend.dto.UserDto;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.entity.Usuario;
import com.stockmaster.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UsuarioService usuarioService;

    @Test
    void testCrearUsuarioYAsignarRol() {
        when(usuarioRepository.existsByCorreo("vendedor@mype.pe")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        AuthDto.RegisterRequest request = AuthDto.RegisterRequest.builder()
                .correo("vendedor@mype.pe")
                .password("secret123")
                .nombreCompleto("Carlos Vendedor")
                .rol(Rol.VENDEDOR)
                .build();

        UserDto.UserResponse created = usuarioService.create(request);

        assertEquals(10L, created.getId());
        assertEquals("vendedor", created.getRol());
        assertEquals("activo", created.getEstado());
    }

    @Test
    void testCambiarRolUsuario_ActualizaCorrectamente() {
        Usuario u = Usuario.builder()
                .id(10L)
                .correo("vendedor@mype.pe")
                .nombreCompleto("Carlos Vendedor")
                .rol(Rol.VENDEDOR)
                .estado("activo")
                .build();

        when(usuarioRepository.findById(10L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDto.UserResponse updated = usuarioService.cambiarRol(10L, Rol.ADMIN);

        assertEquals("admin", updated.getRol());
    }

    @Test
    void testListarRolesDisponibles_RetornaRolesYPermisos() {
        List<UserDto.RolInfoResponse> roles = usuarioService.listarRolesDisponibles();
        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(r -> "admin".equals(r.getRol())));
        assertTrue(roles.stream().anyMatch(r -> "vendedor".equals(r.getRol())));
    }
}

