package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando una compra queda registrada y debe generar su asiento
 * contable. El listener lo procesa tras el commit de la compra (AFTER_COMMIT),
 * de modo que un fallo contable no revierta la compra.
 */
public class CompraContabilizableEvent {

    private final Long compraId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public CompraContabilizableEvent(Long compraId, Integer empresaId, Integer usuarioId) {
        this.compraId = compraId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public Long getCompraId() {
        return compraId;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }
}
