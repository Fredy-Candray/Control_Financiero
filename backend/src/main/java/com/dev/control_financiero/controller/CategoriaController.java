package com.dev.control_financiero.controller;

import com.dev.control_financiero.dto.CrearCategoriaRequest;
import com.dev.control_financiero.entity.Categoria;
import com.dev.control_financiero.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    public Categoria crear(@Valid @RequestBody CrearCategoriaRequest request) {
        return categoriaService.crearCategoria(request);
    }

    @GetMapping
    public List<Categoria> listarCategorias() {
        return categoriaService.listarCategorias();
    }
}
