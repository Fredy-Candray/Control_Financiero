package com.dev.control_financiero.config;

import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.repository.RolRepository;
import com.dev.control_financiero.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.annotation.Order;

import java.util.List;

@Component
@RequiredArgsConstructor
@Order(1)
public class RolDataInitializer implements ApplicationRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Rol administrador = obtenerOCrear("Administrador");
        Rol usuario = obtenerOCrear("Usuario");

        List<Usuario> usuarios = usuarioRepository.findAllByOrderByIdAsc();
        for (int i = 0; i < usuarios.size(); i++) {
            Usuario existente = usuarios.get(i);
            if (existente.getRol() == null) {
                existente.setRol(i == 0 ? administrador : usuario);
            }
        }
        usuarioRepository.saveAll(usuarios);
    }

    private Rol obtenerOCrear(String nombre) {
        return rolRepository.findByNombre(nombre)
                .orElseGet(() -> rolRepository.save(Rol.builder().nombre(nombre).build()));
    }
}
