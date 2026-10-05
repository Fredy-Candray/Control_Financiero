package com.dev.control_financiero.service;

import com.dev.control_financiero.dto.MenuAdminRequest;
import com.dev.control_financiero.dto.MenuAdminResponse;
import com.dev.control_financiero.dto.MenuResponse;
import com.dev.control_financiero.entity.Menu;
import com.dev.control_financiero.entity.OpcionMenu;
import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.repository.MenuRepository;
import com.dev.control_financiero.repository.RolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private final MenuRepository menuRepository;
    private final RolRepository rolRepository;

    @Transactional(readOnly = true)
    public MenuResponse obtenerMenu(String nombreRol) {
        List<MenuResponse.Seccion> secciones = new ArrayList<>();
        List<String> rutasPermitidas = new ArrayList<>();
        for (Menu menu : menuRepository.findAllByActivoTrueOrderByOrdenAscIdAsc()) {
            List<MenuResponse.Opcion> opciones = menu.getOpciones().stream()
                    .filter(opcion -> Boolean.TRUE.equals(opcion.getActivo()))
                    .filter(opcion -> opcion.getRoles().stream().anyMatch(rol -> rol.getNombre().equals(nombreRol)))
                    .sorted(Comparator.comparing(OpcionMenu::getOrden).thenComparing(OpcionMenu::getId))
                    .map(opcion -> new MenuResponse.Opcion(opcion.getEtiqueta(), opcion.getRuta(), opcion.getIcono()))
                    .toList();
            if (!opciones.isEmpty()) {
                secciones.add(new MenuResponse.Seccion(menu.getTitulo(), opciones));
                opciones.forEach(opcion -> rutasPermitidas.add(opcion.ruta()));
            }
        }
        // El perfil se abre desde el menú de la cuenta y no forma parte del menú lateral.
        rutasPermitidas.add("/perfil");
        return new MenuResponse(List.copyOf(secciones), List.copyOf(rutasPermitidas));
    }

    @Transactional(readOnly = true)
    public MenuAdminResponse obtenerConfiguracion() {
        List<MenuAdminResponse.Seccion> secciones = menuRepository.findAllByOrderByOrdenAscIdAsc().stream()
                .map(menu -> new MenuAdminResponse.Seccion(menu.getId(), menu.getTitulo(), menu.getOrden(), menu.getActivo(),
                        menu.getOpciones().stream()
                                .sorted(Comparator.comparing(OpcionMenu::getOrden).thenComparing(OpcionMenu::getId))
                                .map(opcion -> new MenuAdminResponse.Opcion(opcion.getId(), opcion.getEtiqueta(), opcion.getRuta(),
                                        opcion.getIcono(), opcion.getOrden(), opcion.getActivo(),
                                        opcion.getRoles().stream().map(Rol::getNombre).sorted().toList()))
                                .toList()))
                .toList();
        return new MenuAdminResponse(secciones, rolRepository.findAll().stream().map(Rol::getNombre).sorted().toList());
    }

    @Transactional
    public MenuAdminResponse guardarConfiguracion(MenuAdminRequest request) {
        if (request == null || request.secciones() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista de menús es obligatoria.");
        }

        Set<String> rutas = new HashSet<>();
        boolean adminMenuPresente = false;
        for (MenuAdminRequest.Seccion seccion : request.secciones()) {
            validarTexto(seccion.titulo(), "El título de cada sección es obligatorio.", 80);
            if (seccion.opciones() == null) continue;
            for (MenuAdminRequest.Opcion opcion : seccion.opciones()) {
                validarTexto(opcion.etiqueta(), "El nombre de cada opción es obligatorio.", 80);
                validarTexto(opcion.ruta(), "La ruta de cada opción es obligatoria.", 120);
                validarTexto(opcion.icono(), "El icono de cada opción es obligatorio.", 40);
                if (!opcion.ruta().startsWith("/") || opcion.ruta().contains("..")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las rutas deben ser internas y comenzar con /.");
                }
                if (!rutas.add(opcion.ruta())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se permiten rutas duplicadas en el menú.");
                }
                if ("/administrar-menu".equals(opcion.ruta()) && Boolean.TRUE.equals(seccion.activo())
                        && Boolean.TRUE.equals(opcion.activo()) && opcion.roles() != null && opcion.roles().contains("Administrador")) {
                    adminMenuPresente = true;
                }
            }
        }
        if (!adminMenuPresente) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe mantenerse activa la opción Administrar menú para el rol Administrador.");
        }

        Map<Long, Menu> existentes = menuRepository.findAllByOrderByOrdenAscIdAsc().stream()
                .collect(Collectors.toMap(Menu::getId, menu -> menu));
        Set<Long> seccionesConservadas = new HashSet<>();
        List<Menu> guardar = new ArrayList<>();

        for (MenuAdminRequest.Seccion seccionRequest : request.secciones()) {
            Menu menu;
            if (seccionRequest.id() == null) {
                menu = new Menu();
                menu.setOpciones(new ArrayList<>());
            } else {
                menu = existentes.get(seccionRequest.id());
                if (menu == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró una sección del menú.");
                seccionesConservadas.add(menu.getId());
            }
            menu.setTitulo(seccionRequest.titulo().trim());
            menu.setOrden(seccionRequest.orden() == null ? guardar.size() + 1 : seccionRequest.orden());
            menu.setActivo(Boolean.TRUE.equals(seccionRequest.activo()));

            Map<Long, OpcionMenu> opcionesExistentes = menu.getOpciones().stream()
                    .filter(opcion -> opcion.getId() != null)
                    .collect(Collectors.toMap(OpcionMenu::getId, opcion -> opcion));
            Set<Long> opcionesConservadas = new HashSet<>();
            List<MenuAdminRequest.Opcion> opcionesRequest = seccionRequest.opciones() == null ? List.of() : seccionRequest.opciones();
            for (MenuAdminRequest.Opcion opcionRequest : opcionesRequest) {
                OpcionMenu opcion;
                if (opcionRequest.id() == null) {
                    opcion = new OpcionMenu();
                    opcion.setMenu(menu);
                    menu.getOpciones().add(opcion);
                } else {
                    opcion = opcionesExistentes.get(opcionRequest.id());
                    if (opcion == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró una opción en la sección indicada.");
                    opcionesConservadas.add(opcion.getId());
                }
                opcion.setEtiqueta(opcionRequest.etiqueta().trim());
                opcion.setRuta(opcionRequest.ruta().trim());
                opcion.setIcono(opcionRequest.icono().trim());
                opcion.setOrden(opcionRequest.orden() == null ? opcionesRequest.indexOf(opcionRequest) + 1 : opcionRequest.orden());
                opcion.setActivo(Boolean.TRUE.equals(opcionRequest.activo()));
                opcion.setMenu(menu);
                Set<String> nombresRol = opcionRequest.roles() == null ? Set.of() : new HashSet<>(opcionRequest.roles());
                List<Rol> roles = rolRepository.findAll().stream().filter(rol -> nombresRol.contains(rol.getNombre())).toList();
                if (roles.size() != nombresRol.size()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se indicó un rol que no existe.");
                }
                opcion.setRoles(new LinkedHashSet<>(roles));
            }
            menu.getOpciones().removeIf(opcion -> opcion.getId() != null && !opcionesConservadas.contains(opcion.getId()));
            guardar.add(menu);
        }

        menuRepository.deleteAll(existentes.values().stream()
                .filter(menu -> !seccionesConservadas.contains(menu.getId())
                        && request.secciones().stream().noneMatch(seccion -> Objects.equals(seccion.id(), menu.getId())))
                .toList());
        menuRepository.saveAll(guardar);
        return obtenerConfiguracion();
    }

    private void validarTexto(String valor, String mensaje, int maximo) {
        if (valor == null || valor.isBlank() || valor.trim().length() > maximo) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensaje);
        }
    }
}
