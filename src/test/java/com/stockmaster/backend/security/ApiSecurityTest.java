package com.stockmaster.backend.security;

import com.stockmaster.backend.dto.AuthDto;
import com.stockmaster.backend.entity.Rol;
import com.stockmaster.backend.repository.UsuarioRepository;
import com.stockmaster.backend.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:finvora_security_qa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@ActiveProfiles("dev")
@Transactional
class ApiSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String crearUsuario(Rol rol) {
        String correo = "qa-" + java.util.UUID.randomUUID() + "@example.test";
        usuarioService.create(AuthDto.RegisterRequest.builder().correo(correo)
                .nombreCompleto("Usuario QA").password("Prueba-local-123!").rol(rol).build());
        return correo;
    }

    @Test
    void accesoSinAutenticacionEsRechazado() throws Exception {
        mvc().perform(get("/api/usuarios")).andExpect(status().is4xxClientError());
    }

    @Test
    void tokenMalformadoEsRechazado() throws Exception {
        mvc().perform(get("/api/usuarios").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void administradorPuedeConsultarUsuarios() throws Exception {
        String correo = crearUsuario(Rol.ADMIN);
        mvc().perform(get("/api/usuarios").header("Authorization",
                "Bearer " + jwtTokenProvider.generateTokenFromUsername(correo)))
                .andExpect(status().isOk());
    }

    @Test
    void vendedorNoPuedeConsultarUsuarios() throws Exception {
        String correo = crearUsuario(Rol.VENDEDOR);
        mvc().perform(get("/api/usuarios").header("Authorization",
                "Bearer " + jwtTokenProvider.generateTokenFromUsername(correo)))
                .andExpect(status().isForbidden());
    }

    @Test
    void contrasenaSeAlmacenaComoHash() {
        String correo = crearUsuario(Rol.VENDEDOR);
        String hash = usuarioRepository.findByCorreo(correo).orElseThrow().getPassword();
        assertNotEquals("Prueba-local-123!", hash);
        assertTrue(passwordEncoder.matches("Prueba-local-123!", hash));
        assertFalse(passwordEncoder.matches("incorrecta", hash));
    }

    @Test
    void respuestaDeUsuariosNoIncluyeContrasenas() throws Exception {
        String correo = crearUsuario(Rol.ADMIN);
        mvc().perform(get("/api/usuarios").header("Authorization",
                "Bearer " + jwtTokenProvider.generateTokenFromUsername(correo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }
}
