package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.CrearCategoriaRequest;
import com.dev.control_financiero.entity.Categoria;
import com.dev.control_financiero.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public Categoria crearCategoria(CrearCategoriaRequest request) {

        if (categoriaRepository.existsByNombre(request.getNombre())) {
            throw new RuntimeException("La categoría ya existe");
        }

        Categoria categoria = Categoria.builder()
                .nombre(request.getNombre())
                .activa(true)
                .build();

        return categoriaRepository.save(categoria);
    }

    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll();
    }
}