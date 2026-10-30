package com.dev.control_financiero.service;

import com.dev.control_financiero.entity.PrestamoPersonal;
import com.dev.control_financiero.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;

@Service @RequiredArgsConstructor
public class PrestamoPersonalService {
    private final PrestamoPersonalRepository prestamos;
    private final UsuarioRepository users;
    @Transactional(readOnly=true) public List<PrestamoPersonal> listar(Long user) {return prestamos.findByUsuarioIdOrderByIdAsc(user);}
    /** Opening liability only: never credits an account or invents a past income. */
    @Transactional public PrestamoPersonal crear(Long user,String creditor,BigDecimal balance,String reference) {
        if(creditor==null || creditor.isBlank() || creditor.strip().length()>120 || balance==null || balance.signum()<=0 || balance.scale()>2)
            throw new IllegalArgumentException("Indica el acreedor y un saldo pendiente válido con hasta dos decimales.");
        users.findLockedById(user).orElseThrow(()->new IllegalArgumentException("Usuario no encontrado."));
        var prior=prestamos.findByUsuarioIdAndReferenciaCliente(user,reference);
        if(prior.isPresent()) {
            if(!prior.get().getAcreedor().equals(creditor.strip()) || prior.get().getSaldoInicial().compareTo(balance)!=0)
                throw new IllegalArgumentException("Esta referencia ya pertenece a otra deuda registrada.");
            return prior.get();
        }
        return prestamos.save(PrestamoPersonal.builder().usuarioId(user).acreedor(creditor.strip()).saldoInicial(balance)
                .saldoPendiente(balance).tasaInteres(BigDecimal.ZERO).referenciaCliente(reference)
                .fechaRegistro(LocalDateTime.now(ZoneId.of("America/El_Salvador"))).build());
    }
}
