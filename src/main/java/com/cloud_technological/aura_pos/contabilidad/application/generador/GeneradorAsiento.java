package com.cloud_technological.aura_pos.contabilidad.application.generador;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;

/**
 * Estrategia de generación de asientos: una implementación por tipo de
 * documento origen (VENTA, COMPRA, NOMINA…). El generador SOLO decide
 * partidas; idempotencia, período, persistencia y log viven en el caso de
 * uso. Máximo ~150 líneas: si crece, está decidiendo cosas de un resolver.
 */
public interface GeneradorAsiento {

    /** Tipo de origen que este generador sabe contabilizar (p.ej. "VENTA"). */
    String tipoOrigen();

    /** Construye el asiento cuadrado del documento. Dominio puro adentro. */
    Asiento generar(ContextoContabilizacion ctx);

    /**
     * true → el asiento nace SIEMPRE en BORRADOR, sin importar el modo de la
     * empresa (propuestas que el contador debe aprobar: deterioro, causación).
     */
    default boolean siempreBorrador() {
        return false;
    }

    /**
     * true → el asiento nace SIEMPRE CONTABILIZADO, sin importar el modo de
     * la empresa (actos deliberados del contador, como el cierre mensual:
     * provisión de renta, traslado de utilidad, distribución, pago de
     * dividendos). Incompatible con {@link #siempreBorrador()}.
     */
    default boolean siempreContabilizado() {
        return false;
    }
}
