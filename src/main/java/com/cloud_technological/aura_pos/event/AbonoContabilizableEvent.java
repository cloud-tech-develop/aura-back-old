package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando se registra un abono de cartera (cobro) o de cuenta por
 * pagar (pago a proveedor) y debe generar su asiento contable. El listener lo
 * procesa tras el commit (AFTER_COMMIT).
 *
 * @deprecated E2 (ADR-003): los abonos publican el evento único
 *             {@code DocumentoContabilizableEvent("ABONO_COBRAR"|"ABONO_PAGAR", …)}
 *             procesado por {@code AbonoCobroGenerador}/{@code AbonoPagoGenerador}.
 *             Nadie publica este evento; se elimina al cerrar E11.
 */
@Deprecated
public class AbonoContabilizableEvent {

    /** "COBRO" o "PAGO". */
    private final String tipo;
    private final Long abonoId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public AbonoContabilizableEvent(String tipo, Long abonoId, Integer empresaId, Integer usuarioId) {
        this.tipo = tipo;
        this.abonoId = abonoId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getAbonoId() {
        return abonoId;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }
}
