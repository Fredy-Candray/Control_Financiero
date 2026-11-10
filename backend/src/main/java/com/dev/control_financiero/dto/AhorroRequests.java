package com.dev.control_financiero.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class AhorroRequests {
    public record Meta(@NotBlank @Size(max=100) String nombre,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal objetivo,
        LocalDate fechaObjetivo,@DecimalMin("0") @Digits(integer=16,fraction=2) BigDecimal aporteMensual,
        @PositiveOrZero Long version,@NotBlank @Size(max=36) String referenciaCliente) {}
    public record Reserva(@NotNull @Positive Long cuentaId,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,
        @NotNull @PositiveOrZero Long version,@NotBlank @Size(max=36) String referenciaCliente) {}
    public record Fijo(@NotBlank @Size(max=100) String nombre,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,
        @Positive Long categoriaId,@Min(1) @Max(31) int diaPago,
        @NotBlank @Pattern(regexp="[0-9]{4}-(0[1-9]|1[0-2])") String inicio,
        @PositiveOrZero Long version,@NotBlank @Size(max=36) String referenciaCliente) {}
    public record Presupuesto(@NotBlank @Size(max=100) String nombre,
        @NotBlank @Pattern(regexp="[0-9]{4}-(0[1-9]|1[0-2])") String mes,
        @Positive Long categoriaId,@NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal limite,
        boolean alertaAnticipada,@Min(1) @Max(99) int umbral,
        @PositiveOrZero Long version,@NotBlank @Size(max=36) String referenciaCliente) {}
    public record Pago(@NotNull @Positive Long movimientoId,
        @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,
        @NotNull @PositiveOrZero Long version,@NotBlank @Size(max=36) String referenciaCliente) {}
    public record Revision(@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=255) String nota) {}
    public record CambioCompromiso(@NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal monto,
        @NotNull @PositiveOrZero Long version,@NotBlank @Size(max=255) String nota) {}
}
