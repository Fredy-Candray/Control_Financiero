package com.dev.control_financiero.dto;

import java.util.List;

public record MenuAdminRequest(List<Seccion> secciones) {
    public record Seccion(Long id, String titulo, Integer orden, Boolean activo, List<Opcion> opciones) {}
    public record Opcion(Long id, String etiqueta, String ruta, String icono, Integer orden,
                         Boolean activo, List<String> roles) {}
}
