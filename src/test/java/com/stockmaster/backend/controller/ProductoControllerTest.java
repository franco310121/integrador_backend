package com.stockmaster.backend.controller;

import com.stockmaster.backend.dto.ProductoDto;
import com.stockmaster.backend.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ProductoService productoService;

    @InjectMocks
    private ProductoController productoController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productoController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetActiveProducts_Returns200AndList() throws Exception {
        ProductoDto.Response p1 = ProductoDto.Response.builder()
                .id(1L)
                .nombre("Inca Kola")
                .sku("BEB-001")
                .precioVenta(BigDecimal.valueOf(3.50))
                .stock(20)
                .stockLabel("Normal")
                .build();

        when(productoService.findActive()).thenReturn(List.of(p1));

        mockMvc.perform(get("/api/productos")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Inca Kola"))
                .andExpect(jsonPath("$[0].sku").value("BEB-001"));
    }

    @Test
    void testGetProductById_Returns200AndProduct() throws Exception {
        ProductoDto.Response p1 = ProductoDto.Response.builder()
                .id(1L)
                .nombre("Inca Kola")
                .sku("BEB-001")
                .precioVenta(BigDecimal.valueOf(3.50))
                .stock(20)
                .build();

        var principal = User.withUsername("qa@example.test").password("unused").roles("ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(productoService.findById(1L, "qa@example.test")).thenReturn(p1);

        mockMvc.perform(get("/api/productos/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nombre").value("Inca Kola"));
    }
}
