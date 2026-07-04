package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando una venta queda registrada y debe generar su asiento
 * contable. El listener lo procesa tras el commit de la venta
 * (AFTER_COMMIT), de modo que un fallo contable no revierta la venta.
 */
public class VentaContabilizableEvent {

    private final Long ventaId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public VentaContabilizableEvent(Long ventaId, Integer empresaId, Integer usuarioId) {
        this.ventaId = ventaId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public Long getVentaId() {
        return ventaId;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }
}
