package com.cloud_technological.aura_pos.contabilidad.application.port;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;

/**
 * Rastro de auditoría del posting automático: qué se contabilizó, desde
 * dónde y qué falló. En E1 el adapter escribe al log de aplicación; la
 * tabla {@code contabilidad_posting_log} llega en E3.
 */
public interface PostingLog {

    void exito(ContextoContabilizacion ctx, Long asientoId);

    void fallo(ContextoContabilizacion ctx, Exception causa);
}
