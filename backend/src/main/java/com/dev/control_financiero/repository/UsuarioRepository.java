package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    List<Usuario> findAllByOrderByIdAsc();

    long countByRol_Nombre(String nombre);

    boolean existsByIdAndActivoTrueAndRol_Nombre(Long id, String nombre);

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByUsername(String username);

    boolean existsByCorreo(String correo);

}
