package com.cloud_technological.aura_pos.event;

/**
 * Se publica cuando una operación simple (gasto o merma) debe generar su asiento
 * contable. El listener lo procesa tras el commit (AFTER_COMMIT).
 */
public class OperacionContabilizableEvent {

    /** "GASTO" o "MERMA". */
    private final String tipo;
    private final Long origenId;
    private final Integer empresaId;
    private final Integer usuarioId;

    public OperacionContabilizableEvent(String tipo, Long origenId, Integer empresaId, Integer usuarioId) {
        this.tipo = tipo;
        this.origenId = origenId;
        this.empresaId = empresaId;
        this.usuarioId = usuarioId;
    }

    public String getTipo() {
        return tipo;
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
