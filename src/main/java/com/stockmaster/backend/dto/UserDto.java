package com.stockmaster.backend.dto;

import com.stockmaster.backend.entity.Rol;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public class UserDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserResponse {
        private Long id;
        private String correo;
        private String nombreCompleto;
        private String rol;
        private String estado;
        private String avatarUrl;
        private LocalDateTime fechaCreacion;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateUserRequest {
        private String nombreCompleto;
        private Rol rol;
        private String estado;
        private String password;
        private String avatarUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CambiarRolRequest {
        @NotNull(message = "El rol es obligatorio")
        private Rol rol;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RolInfoResponse {
        private String rol;
        private String descripcion;
        private List<String> permisos;
    }
}
