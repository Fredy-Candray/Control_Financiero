package com.dev.control_financiero.dto;

import java.util.List;

public record MenuAdminResponse(List<Seccion> secciones, List<String> roles) {
    public record Seccion(Long id, String titulo, Integer orden, Boolean activo, List<Opcion> opciones) {}
    public record Opcion(Long id, String etiqueta, String ruta, String icono, Integer orden,
                         Boolean activo, List<String> roles) {}
}
