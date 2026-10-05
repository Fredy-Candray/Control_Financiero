package com.dev.control_financiero.config;

import com.dev.control_financiero.entity.Menu;
import com.dev.control_financiero.entity.OpcionMenu;
import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.repository.MenuRepository;
import com.dev.control_financiero.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Order(2)
public class MenuDataInitializer implements ApplicationRunner {
    private final MenuRepository menuRepository;
    private final RolRepository rolRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (menuRepository.count() > 0) return;
        Rol admin = rolRepository.findByNombre("Administrador").orElseThrow();
        Rol usuario = rolRepository.findByNombre("Usuario").orElseThrow();

        Menu principal = crearSeccion("Principal", 1);
        agregar(principal, "Dashboard", "/dashboard", "dashboard", 1, admin, usuario);

        Menu finanzas = crearSeccion("Finanzas", 2);
        agregar(finanzas, "Cuentas", "/cuentas", "cuentas", 1, admin, usuario);
        agregar(finanzas, "Movimientos", "/movimientos", "movimientos", 2, admin, usuario);
        agregar(finanzas, "Calendario", "/calendario", "calendario", 3, admin, usuario);

        Menu sistema = crearSeccion("Sistema", 3);
        agregar(sistema, "Usuarios", "/usuarios", "usuarios", 1, admin);
        agregar(sistema, "Administrar menú", "/administrar-menu", "configuracion", 2, admin);

        menuRepository.saveAll(List.of(principal, finanzas, sistema));
    }

    private Menu crearSeccion(String titulo, int orden) {
        return Menu.builder().titulo(titulo).orden(orden).activo(true).opciones(new ArrayList<>()).build();
    }

    private void agregar(Menu menu, String etiqueta, String ruta, String icono, int orden, Rol... roles) {
        OpcionMenu opcion = OpcionMenu.builder().etiqueta(etiqueta).ruta(ruta).icono(icono).orden(orden)
                .activo(true).menu(menu).roles(new LinkedHashSet<>(List.of(roles))).build();
        menu.getOpciones().add(opcion);
    }
}
