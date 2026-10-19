package com.dev.control_financiero.config;

import com.dev.control_financiero.entity.Rol;
import com.dev.control_financiero.entity.Usuario;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.mapping.Column;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfileImageMappingTest {
    @Test void keepsSqlServerMaxColumnsAndUsesPostgresText() {
        check("org.hibernate.dialect.SQLServerDialect", "varchar(max)");
        check("org.hibernate.dialect.PostgreSQLDialect", "text");
    }

    private void check(String dialect, String expectedType) {
        var registry=new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", dialect)
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false).build();
        try {
            var metadata=new MetadataSources(registry).addAnnotatedClass(Usuario.class)
                    .addAnnotatedClass(Rol.class).buildMetadata();
            var table=metadata.getEntityBinding(Usuario.class.getName()).getTable();
            for(String name:new String[]{"foto_perfil", "foto_portada", "foto_portada_original"}) {
                assertEquals(expectedType, table.getColumn(new Column(name)).getSqlType(metadata));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
