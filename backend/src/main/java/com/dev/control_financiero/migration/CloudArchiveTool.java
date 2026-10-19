package com.dev.control_financiero.migration;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.zip.*;

/** Offline export / private one-off import. Never exposes an HTTP endpoint. */
public final class CloudArchiveTool {
    static final ObjectMapper JSON=new ObjectMapper();
    static final List<String> TABLES=List.of("roles","categorias","usuarios","menus","opciones_menu",
            "opciones_menu_roles","cuentas","movimientos","eventos_calendario","planes_reporte",
            "gmail_connections","bank_email_imports","tokens_recuperacion_password","entregas_recordatorio");
    static final Set<String> SEED_TABLES=Set.of("roles","menus","opciones_menu","opciones_menu_roles");
    private CloudArchiveTool() {}

    public static void main(String[] args) throws Exception {
        if(args.length!=3 || !"export".equals(args[0])) throw new IllegalArgumentException("export <archive> <local-properties>");
        var props=new Properties(); try(var reader=Files.newBufferedReader(Path.of(args[2]))) { props.load(reader); }
        String url=props.getProperty("spring.datasource.url",
                "jdbc:sqlserver://localhost:63646;databaseName=ControlFinanciero;encrypt=true;trustServerCertificate=true");
        if(!url.startsWith("jdbc:sqlserver://localhost:") || !url.contains("databaseName=ControlFinanciero"))
            throw new IllegalStateException("El origen debe ser la base local de COFI.");
        var root=JSON.createObjectNode(); root.put("format",1); root.put("createdAt",Instant.now().toString());
        var tables=root.putArray("tables");
        try(var source=DriverManager.getConnection(url,props.getProperty("spring.datasource.username","control_financiero"),
                props.getProperty("spring.datasource.password"))) {
            source.setAutoCommit(false); source.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            try {
                for(String table:TABLES) {
                    var item=tables.addObject(); item.put("name",table);
                    var columns=item.putArray("columns"); var types=item.putArray("types"); var rows=item.putArray("rows");
                    try(var statement=source.createStatement(); var rs=statement.executeQuery("SELECT * FROM dbo.["+table+"]")) {
                        var md=rs.getMetaData(); int count=md.getColumnCount();
                        for(int i=1;i<=count;i++) { columns.add(md.getColumnName(i)); types.add(md.getColumnType(i)); }
                        while(rs.next()) {
                            var row=rows.addArray();
                            for(int i=1;i<=count;i++) {
                                String value=canonical(rs,i,md.getColumnType(i));
                                if("usuarios".equals(table) && "password".equals(md.getColumnName(i)) && value!=null
                                        && !value.matches("^\\$2[aby]\\$.*")) value=new BCryptPasswordEncoder().encode(value);
                                if(value==null) row.addNull(); else row.add(value);
                            }
                        }
                    }
                    System.out.println("Exportado "+table+": "+rows.size()+" filas");
                }
                writeArchive(root,Path.of(args[1]),key()); source.commit();
            } catch(Exception error) { source.rollback(); throw error; }
        }
        System.out.println("Respaldo AES-GCM creado. No se modificaron los datos del origen.");
    }

    static byte[] key() {
        byte[] key=Base64.getDecoder().decode(Objects.requireNonNull(System.getenv("COFI_ARCHIVE_KEY")));
        if(key.length!=32) throw new IllegalArgumentException("La clave de archivo debe tener 32 bytes.");
        return key;
    }
    static void writeArchive(JsonNode root,Path file,byte[] key) throws Exception {
        byte[] nonce=new byte[12]; new SecureRandom().nextBytes(nonce);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        try(var out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)) {
            out.write(nonce);
            try(var encrypted=new CipherOutputStream(out,cipher); var compressed=new GZIPOutputStream(encrypted)) {
                JSON.writeValue(compressed,root);
            }
        }
    }
    static JsonNode readArchive(Path file,byte[] key) throws Exception {
        try(var in=Files.newInputStream(file)) {
            byte[] nonce=in.readNBytes(12); if(nonce.length!=12) throw new IOException("Archivo incompleto.");
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            // Authenticate the COMPLETE ciphertext before using any decrypted data.
            byte[] clear=cipher.doFinal(in.readAllBytes());
            try(var compressed=new GZIPInputStream(new ByteArrayInputStream(clear))) { return JSON.readTree(compressed); }
        }
    }

    public static Connection postgres() throws Exception {
        String host=Objects.requireNonNull(System.getenv("PGHOST"));
        if(!host.endsWith(".railway.internal")) throw new IllegalStateException("Solo se admite la red privada Railway.");
        return DriverManager.getConnection("jdbc:postgresql://"+host+":"+System.getenv().getOrDefault("PGPORT","5432")
                +"/"+System.getenv("PGDATABASE")+"?sslmode=require",System.getenv("PGUSER"),System.getenv("PGPASSWORD"));
    }
    public static String schema() {
        String value=System.getenv().getOrDefault("DB_SCHEMA","cofi_live");
        if(!value.matches("cofi_live(?:_[a-z0-9]+)*")) throw new IllegalArgumentException("Esquema de migración inválido.");
        return value;
    }
    public static void importArchive(Connection target,Path archive) throws Exception {
        var root=readArchive(archive,key());
        if(root.path("format").asInt()!=1 || root.path("tables").size()!=TABLES.size()) throw new IOException("Formato inválido.");
        target.setSchema(schema()); target.setAutoCommit(false);
        try {
            for(String table:TABLES) {
                if(SEED_TABLES.contains(table)) continue;
                try(var st=target.createStatement(); var rs=st.executeQuery("SELECT COUNT(*) FROM \""+table+"\"")) {
                    rs.next(); if(rs.getLong(1)!=0) throw new IllegalStateException("Destino no vacío: "+table);
                }
            }
            var reverse=new ArrayList<>(TABLES); Collections.reverse(reverse);
            for(String table:reverse) try(var st=target.createStatement()) { st.executeUpdate("DELETE FROM \""+table+"\""); }
            for(int t=0;t<TABLES.size();t++) {
                var item=root.path("tables").get(t); String table=TABLES.get(t);
                if(!table.equals(item.path("name").asText())) throw new IOException("Orden de tablas inválido.");
                var columns=new ArrayList<String>(); var types=new ArrayList<Integer>();
                for(var column:item.path("columns")) {
                    String name=column.asText(); if(!name.matches("[a-z_]+")) throw new IOException("Columna inválida."); columns.add(name);
                }
                for(var type:item.path("types")) types.add(type.asInt());
                if(columns.size()!=types.size()) throw new IOException("Tipos inválidos.");
                String names=String.join(",",columns.stream().map(c->"\""+c+"\"").toList());
                String sql="INSERT INTO \""+table+"\" ("+names+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")";
                try(var insert=target.prepareStatement(sql)) {
                    for(var row:item.path("rows")) {
                        if(row.size()!=columns.size()) throw new IOException("Fila inválida.");
                        for(int c=0;c<columns.size();c++) bind(insert,c+1,types.get(c),row.get(c));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                verify(target,table,columns,types,item.path("rows"));
                if(columns.contains("id") && types.get(columns.indexOf("id"))==Types.BIGINT) {
                    try(var reset=target.createStatement()) {
                        reset.execute("SELECT setval(pg_get_serial_sequence('"+schema()+"."+table+"','id'),"
                                +"COALESCE((SELECT MAX(id) FROM \""+table+"\"),1), EXISTS(SELECT 1 FROM \""+table+"\"))");
                    }
                }
                System.out.println("Verificado "+table+": "+item.path("rows").size()+" filas");
            }
            target.commit(); System.out.println("MIGRACION_VERIFICADA: filas y contenido coinciden; saldos sin recalcular.");
        } catch(Exception error) { target.rollback(); throw error; }
    }
    static void verify(Connection target,String table,List<String> columns,List<Integer> types,JsonNode rows) throws Exception {
        var expected=new ArrayList<String>(); var actual=new ArrayList<String>();
        for(var row:rows) expected.add(digest(row.toString()));
        String names=String.join(",",columns.stream().map(c->"\""+c+"\"").toList());
        try(var st=target.createStatement(); var rs=st.executeQuery("SELECT "+names+" FROM \""+table+"\"")) {
            while(rs.next()) {
                var row=JSON.createArrayNode();
                for(int c=0;c<columns.size();c++) { String value=canonical(rs,c+1,types.get(c)); if(value==null) row.addNull(); else row.add(value); }
                actual.add(digest(row.toString()));
            }
        }
        Collections.sort(expected); Collections.sort(actual);
        if(!expected.equals(actual)) throw new IllegalStateException("Verificación de contenido fallida: "+table);
    }
    static String digest(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    static String canonical(ResultSet rs,int column,int type) throws Exception {
        if(rs.getObject(column)==null) return null;
        return switch(type) {
            case Types.BIT, Types.BOOLEAN -> Boolean.toString(rs.getBoolean(column));
            case Types.NUMERIC, Types.DECIMAL -> rs.getBigDecimal(column).stripTrailingZeros().toPlainString();
            case Types.TIMESTAMP -> rs.getTimestamp(column).toLocalDateTime().truncatedTo(ChronoUnit.MICROS).toString();
            case Types.TIMESTAMP_WITH_TIMEZONE, -155 -> rs.getObject(column,OffsetDateTime.class).toInstant().truncatedTo(ChronoUnit.MICROS).toString();
            case Types.TIME -> rs.getObject(column,LocalTime.class).truncatedTo(ChronoUnit.MICROS).toString();
            case Types.DATE -> rs.getObject(column,LocalDate.class).toString();
            default -> rs.getString(column);
        };
    }
    static void bind(PreparedStatement ps,int index,int type,JsonNode node) throws Exception {
        if(node.isNull()) { ps.setObject(index,null); return; }
        String value=node.asText();
        switch(type) {
            case Types.BIT, Types.BOOLEAN -> ps.setBoolean(index,Boolean.parseBoolean(value));
            case Types.BIGINT -> ps.setLong(index,Long.parseLong(value));
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> ps.setInt(index,Integer.parseInt(value));
            case Types.NUMERIC, Types.DECIMAL -> ps.setBigDecimal(index,new java.math.BigDecimal(value));
            case Types.TIMESTAMP -> ps.setObject(index,LocalDateTime.parse(value));
            case Types.TIMESTAMP_WITH_TIMEZONE, -155 -> ps.setObject(index,OffsetDateTime.parse(value));
            case Types.TIME -> ps.setObject(index,LocalTime.parse(value));
            case Types.DATE -> ps.setObject(index,LocalDate.parse(value));
            default -> ps.setString(index,value);
        }
    }
}
