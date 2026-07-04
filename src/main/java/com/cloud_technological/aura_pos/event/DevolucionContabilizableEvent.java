package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando una devolución de venta queda registrada y debe generar su
 * asiento contable. El listener lo procesa tras el commit (AFTER_COMMIT).
 */
public class DevolucionContabilizableEvent {

    private final Long devolucionId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public DevolucionContabilizableEvent(Long devolucionId, Integer empresaId, Integer usuarioId) {
        this.devolucionId = devolucionId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public Long getDevolucionId() {
        return devolucionId;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }
}
