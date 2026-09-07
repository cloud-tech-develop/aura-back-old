package com.cloud_technological.aura_pos.dto.compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GastoDto {
    private Long id;
    private Integer empresaId;
    private Long sucursalId;
    private String sucursalNombre;
    private Integer usuarioId;
    private String usuarioNombre;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private LocalDate fecha;
    private Boolean deducible;
    private String estado;
    private LocalDateTime createdAt;
    // Origen de fondos (V142): de dónde salió la plata.
    private String formaPago;
    private String metodoPago;
    private Long cuentaBancariaId;
    /** Cuenta contable acreditada (crédito); distinta de cuentaContableId. */
    private Long cuentaPagoId;

    /**
     * El documento se registró declarando que la plata ya había salido del
     * cajón otro día. Necesario al editar: sin esto el formulario lo
     * reconstruiría como un pago de caja normal y generaría un movimiento que
     * no debe existir.
     */
    private Boolean salidaCajaOtroDia;

    /**
     * De dónde salió la plata, en los mismos términos en que se preguntó:
     * CREDITO, CAJA, BANCO, CUENTA o CAJA_OTRO_DIA.
     *
     * <p>Es derivado, no una columna. Las piezas que sí se guardan son las que
     * necesita el asiento (forma de pago, cuenta bancaria, cuenta acreditada),
     * y reconstruir la pregunta a partir de ellas no es directo: para un pago
     * de caja normal `cuenta_pago_id` también queda lleno — con la cuenta de
     * CAJA que resolvió el sistema, no con una que alguien haya elegido. El
     * formulario leía eso como "otra cuenta" y reabría cualquier gasto en
     * efectivo con el origen equivocado.
     *
     * <p>Se calcula aquí, con la misma prioridad que usa el resolutor al
     * guardar, para que haya una sola definición y no dos que se desincronicen.
     */
    private String origenFondos;

    // Campos tributarios (V54)
    private Long terceroId;
    private String terceroNombre;
    private Long cuentaContableId;
    private String cuentaContableNombre;
    private Long centroCostoId;
    private Long periodoContableId;
    private BigDecimal baseIva;
    private BigDecimal tarifaIva;
    private BigDecimal valorIva;
    private BigDecimal baseRetefuente;
    private BigDecimal tarifaRetefuente;
    private BigDecimal valorRetefuente;
    private BigDecimal baseReteica;
    private BigDecimal tarifaReteica;
    private BigDecimal valorReteica;
    private String tipoDocSoporte;
    private String numeroDocSoporte;
}
