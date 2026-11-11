package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.Usuario;
import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false"})
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named="COFI_SQL_INTEGRATION",matches="true")
@Transactional
class PasswordAdminSqlIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.dev.control_financiero.migration.CloudMigrationRunner runner;
    @Autowired EntityManager em; @Autowired PasswordAdminService service;
    @Autowired UsuarioRepository users; @Autowired PasswordEncoder encoder;
    private Usuario crear(String rol) {
        String ref=UUID.randomUUID().toString();
        var u=Usuario.builder().nombre("Prueba reversible").username("reset_it_"+ref).correo(ref+"@example.test")
                .password(encoder.encode("Anterior123")).activo(true).fechaCreacion(LocalDateTime.now())
                .rol(em.createQuery("select r from Rol r where r.nombre=:nombre",Rol.class).setParameter("nombre",rol).getSingleResult()).build();
        em.persist(u); em.flush(); return u;
    }
    @Test void restablecimientoYCambioPersonalPersistenConHashYSeReviertenAlTerminar() {
        var admin=crear("Administrador"); var u=crear("Usuario");
        service.restablecer(admin.getId(),u.getId()); em.flush(); em.clear();
        u=users.findById(u.getId()).orElseThrow();
        assertTrue(encoder.matches("cofi",u.getPassword())); assertTrue(u.getCambioPasswordPendiente());
        service.cambiarInicial(u.getId(),1L,"Clave123","Clave123"); em.flush(); em.clear();
        u=users.findById(u.getId()).orElseThrow();
        assertFalse(u.getCambioPasswordPendiente()); assertEquals(2L,u.getVersionAcceso());
        assertTrue(encoder.matches("Clave123",u.getPassword())); assertFalse(encoder.matches("cofi",u.getPassword()));
    }
}
