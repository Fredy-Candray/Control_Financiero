package com.dev.control_financiero.repository;

import com.dev.control_financiero.entity.Menu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuRepository extends JpaRepository<Menu, Long> {
    List<Menu> findAllByOrderByOrdenAscIdAsc();
    List<Menu> findAllByActivoTrueOrderByOrdenAscIdAsc();
}
