package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.*;
import com.dev.control_financiero.enums.*;
import com.dev.control_financiero.dto.CrearMovimientoRequest;
import com.dev.control_financiero.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit local SQL Server check; all fixtures roll back and scheduling is disabled. */
@SpringBootTest(properties={"spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false"})
@ActiveProfiles("migration")
@EnabledIfEnvironmentVariable(named="COFI_SQL_INTEGRATION",matches="true")
@Transactional
class ControlFinancieroSqlIntegrationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.dev.control_financiero.migration.CloudMigrationRunner migrationRunner;
    @Autowired ControlFinancieroService service;
    @Autowired MovimientoService movementService;
    @Autowired UsuarioRepository users;
    @Autowired RolRepository roles;
    @Autowired CuentaRepository accounts;
    @Autowired MovimientoRepository movements;
    @Autowired BankEmailImportRepository notices;
    @Autowired EntityManager em;
    @Test void realSchemaSupportsManualLoansLinkedMovementsRepaymentsAndReconciliation() {
        String nonce=UUID.randomUUID().toString();
        var user=users.save(Usuario.builder().nombre("Integration fixture").correo(nonce+"@example.invalid").username("it-"+nonce)
            .password("test-not-a-login-credential").rol(roles.findByNombre("Usuario").orElseThrow()).activo(true).fechaCreacion(LocalDateTime.now()).build());
        var cash=accounts.save(Cuenta.builder().usuario(user).nombre("Test cash").tipo(TipoCuenta.EFECTIVO)
            .saldoActual(new BigDecimal("1000.00")).activa(true).fechaCreacion(LocalDateTime.now()).build());
        var loan=service.prestar(user.getId(),"Test borrower",new BigDecimal("100.00"),cash.getId(),null,null,null,"new-loan");em.flush();
        assertEquals(new BigDecimal("900.00"),cash.getSaldoActual());
        assertEquals(TipoMovimiento.PRESTAMO_OTORGADO,movements.findById(loan.getMovimientoOrigenId()).orElseThrow().getTipo());
        service.cobrar(user.getId(),loan.getId(),new BigDecimal("40.00"),cash.getId(),null,"repayment");em.flush();
        service.cobrar(user.getId(),loan.getId(),new BigDecimal("40.00"),cash.getId(),null,"repayment");em.flush();
        assertEquals(new BigDecimal("940.00"),cash.getSaldoActual());assertEquals(new BigDecimal("60.00"),loan.getPendiente());
        var r=new CrearMovimientoRequest();r.setUsuarioId(user.getId());r.setTipo(TipoMovimiento.GASTO);r.setCuentaOrigenId(cash.getId());r.setMonto(new BigDecimal("20.00"));
        var debit=movementService.crearMovimiento(r);em.flush();
        var linked=service.prestar(user.getId(),"Other borrower",null,null,debit.getId(),null,null,"linked-loan");em.flush();
        assertEquals(new BigDecimal("920.00"),cash.getSaldoActual());assertEquals(new BigDecimal("20.00"),linked.getPendiente());
        r.setTipo(TipoMovimiento.INGRESO);r.setMonto(new BigDecimal("5.00"));var income=movementService.crearMovimiento(r);em.flush();
        service.cobrar(user.getId(),linked.getId(),null,null,income.getId(),"linked-return");em.flush();
        assertEquals(new BigDecimal("925.00"),cash.getSaldoActual());assertEquals(new BigDecimal("15.00"),linked.getPendiente());
        var review=service.conciliar(user.getId(),cash.getId(),new BigDecimal("924.00"),"Test", "comparison");em.flush();
        assertEquals(new BigDecimal("-1.00"),review.getDiferencia());assertEquals(new BigDecimal("925.00"),cash.getSaldoActual());
        var notice=new BankEmailImport();notice.setUsuarioId(user.getId());notice.setGmailMessageId(nonce);notice.setEstado("PENDIENTE");notice.setMonto(new BigDecimal("20.00"));notice.setDetalle("Original fixture");notice=notices.save(notice);
        service.resolver(user.getId(),notice.getId(),"VINCULAR","Already recorded",debit.getId());em.flush();
        assertEquals("Original fixture",notice.getDetalle());assertEquals("IMPORTADO",notice.getEstado());
        assertEquals(2,service.historialPrestamo(user.getId(),linked.getId()).size());
        var opening=service.registrarPendiente(user.getId(),"Existing debtor",new BigDecimal("700.00"),java.time.LocalDate.of(2026,9,1),null,"opening");em.flush();
        assertTrue(opening.isRegistroInicial());assertNull(opening.getMovimientoOrigenId());assertEquals(new BigDecimal("925.00"),cash.getSaldoActual());
        service.cobrar(user.getId(),opening.getId(),new BigDecimal("50.00"),cash.getId(),null,"opening-repayment");em.flush();
        assertEquals(new BigDecimal("975.00"),cash.getSaldoActual());assertEquals(new BigDecimal("650.00"),opening.getPendiente());
    }
}
