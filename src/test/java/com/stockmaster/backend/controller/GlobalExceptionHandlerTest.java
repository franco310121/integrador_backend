package com.stockmaster.backend.controller;

import com.stockmaster.backend.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {
    final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/qa");

    void verifyResponse(ResponseEntity<ErrorResponse> response, int status, String message) {
        assertEquals(status, response.getStatusCode().value());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.getStatus());
        assertEquals(message, body.getMessage());
        assertEquals("/api/qa", body.getPath());
        assertNotNull(body.getTimestamp());
    }
    @Test void recursoNoEncontrado() {
        verifyResponse(handler.handleNotFound(new ResourceNotFoundException("Recurso ausente"), request),404,"Recurso ausente");
    }
    @Test void stockInsuficiente() {
        verifyResponse(handler.handleInsufficientStock(new InsufficientStockException("Stock insuficiente"), request),400,"Stock insuficiente");
    }
    @Test void solicitudInvalida() {
        verifyResponse(handler.handleBadRequest(new BadRequestException("Solicitud incorrecta"), request),400,"Solicitud incorrecta");
    }
    @Test void credencialesInvalidas() {
        verifyResponse(handler.handleBadCredentials(new BadCredentialsException("Detalle interno"), request),401,"Credenciales inválidas");
    }
    @Test void accesoDenegado() {
        verifyResponse(handler.handleAccessDenied(new AccessDeniedException("Detalle interno"), request),403,"No tienes permiso para acceder a este recurso");
    }
    @Test void erroresDeCampos() {
        var ex = mock(MethodArgumentNotValidException.class);
        var binding = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(binding);
        when(binding.getFieldErrors()).thenReturn(List.of(new FieldError("request","nombre","Obligatorio"),new FieldError("request","monto","Debe ser positivo")));
        var response = handler.handleValidationErrors(ex,request);
        verifyResponse(response,400,"Error de validación en los campos enviados");
        assertEquals("Obligatorio",response.getBody().getValidationErrors().get("nombre"));
        assertEquals("Debe ser positivo",response.getBody().getValidationErrors().get("monto"));
        assertEquals(2,response.getBody().getValidationErrors().size());
    }
    @Test void errorInterno() {
        var response = handler.handleGlobalException(new IllegalStateException("Error de prueba"),request);
        assertEquals(500,response.getStatusCode().value());
        assertEquals("Internal Server Error",response.getBody().getError());
        assertEquals("/api/qa",response.getBody().getPath());
    }
}
