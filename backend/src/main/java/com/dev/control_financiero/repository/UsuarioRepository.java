package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Usuario u where u.id = :id")
    java.util.Optional<Usuario> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    List<Usuario> findAllByOrderByIdAsc();

    long countByRol_Nombre(String nombre);

    boolean existsByIdAndActivoTrueAndRol_Nombre(Long id, String nombre);

    Optional<Usuario> findByUsername(String username);

    Optional<Usuario> findByCorreo(String correo);

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByUsername(String username);

    boolean existsByCorreo(String correo);

}
