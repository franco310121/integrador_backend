package com.stockmaster.backend.service;

import com.stockmaster.backend.dto.AuthDto;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.entity.Usuario;
import com.stockmaster.backend.exception.BadRequestException;
import com.stockmaster.backend.repository.UsuarioRepository;
import com.stockmaster.backend.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .correo("admin@stockmaster.com")
                .password("encoded_pass")
                .nombreCompleto("Administrador")
                .rol(Rol.ADMIN)
                .estado("activo")
                .build();
    }

    @Test
    void testLogin_Success() {
        AuthDto.LoginRequest request = new AuthDto.LoginRequest("admin@stockmaster.com", "admin123");
        Authentication auth = mock(Authentication.class);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(tokenProvider.generateToken(auth)).thenReturn("mock_jwt_token");
        when(usuarioRepository.findByCorreo("admin@stockmaster.com")).thenReturn(Optional.of(usuario));

        AuthDto.LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock_jwt_token", response.getToken());
        assertEquals("admin@stockmaster.com", response.getCorreo());
        assertEquals("admin", response.getRol());
    }

    @Test
    void testRegister_DuplicateEmail_ThrowsBadRequestException() {
        AuthDto.RegisterRequest request = AuthDto.RegisterRequest.builder()
                .correo("admin@stockmaster.com")
                .password("password123")
                .nombreCompleto("Admin")
                .rol(Rol.ADMIN)
                .build();

        when(usuarioRepository.existsByCorreo("admin@stockmaster.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void credencialesIncorrectasNoGeneranToken() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Credenciales incorrectas"));
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(new AuthDto.LoginRequest("qa@example.test", "incorrecta")));
        verifyNoInteractions(tokenProvider, usuarioRepository);
    }
}
