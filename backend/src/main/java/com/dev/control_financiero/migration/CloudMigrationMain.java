package com.dev.control_financiero.migration;

import com.dev.control_financiero.ControlFinancieroApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import java.util.Map;

/** Runs only in a temporary private container, with no web server. */
public final class CloudMigrationMain {
    public static void main(String[] args) {
        try {
            try(var connection=CloudArchiveTool.postgres(); var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS \""+CloudArchiveTool.schema()+"\"");
            }
            var app=new SpringApplication(ControlFinancieroApplication.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setLogStartupInfo(false);
            app.setDefaultProperties(Map.of("logging.level.root","OFF","spring.main.banner-mode","off"));
            try(var context=app.run("--spring.main.web-application-type=none","--spring.jpa.hibernate.ddl-auto=update",
                    "--logging.level.root=OFF", "--spring.main.banner-mode=off")) { }
            System.exit(0);
        } catch(Exception error) {
            // Never put financial records, credentials or JDBC error details in logs.
            System.err.println("MIGRACION_FALLIDA: "+error.getClass().getSimpleName());
            for(Throwable cause=error.getCause(); cause!=null; cause=cause.getCause()) {
                System.err.println("MIGRACION_CAUSA: "+cause.getClass().getSimpleName()
                        +(cause instanceof java.sql.SQLException sql ? " SQLSTATE="+sql.getSQLState() : ""));
            }
            System.exit(1);
        }
    }
}
