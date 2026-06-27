package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando una venta o compra se anula y su asiento contable debe
 * reversarse. El listener genera el contraasiento tras el commit de la
 * anulación (AFTER_COMMIT).
 */
public class ContabilidadReversaEvent {

    /** "VENTA" o "COMPRA". */
    private final String origenTipo;
    private final Long origenId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public ContabilidadReversaEvent(String origenTipo, Long origenId,
            Integer empresaId, Integer usuarioId) {
        this.origenTipo = origenTipo;
        this.origenId = origenId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public String getOrigenTipo() {
        return origenTipo;
    }

    public Long getOrigenId() {
        return origenId;
    }

    public Integer getEmpresaId() {
        return empresaId;
    }

    public Integer getUsuarioId() {
        return usuarioId;
    }
}
