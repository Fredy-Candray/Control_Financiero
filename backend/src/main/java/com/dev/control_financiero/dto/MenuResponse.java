package com.dev.control_financiero.dto;

import java.util.List;

public record MenuResponse(List<Seccion> secciones, List<String> rutasPermitidas) {
    public record Seccion(String titulo, List<Opcion> opciones) {}
    public record Opcion(String etiqueta, String ruta, String icono) {}
}
