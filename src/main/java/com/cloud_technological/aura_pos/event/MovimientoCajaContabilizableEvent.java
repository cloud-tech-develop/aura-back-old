package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando se registra un movimiento de caja manual con concepto, para
 * que genere su asiento contable tras el commit (AFTER_COMMIT).
 */
public class MovimientoCajaContabilizableEvent {

    private final Long movimientoId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public MovimientoCajaContabilizableEvent(Long movimientoId, Integer empresaId, Integer usuarioId) {
        this.movimientoId = movimientoId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public Long getMovimientoId() { return movimientoId; }
    public Integer getEmpresaId() { return empresaId; }
    public Integer getUsuarioId() { return usuarioId; }
}
