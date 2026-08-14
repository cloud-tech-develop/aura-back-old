package com.cloud_technological.aura_pos.contabilidad.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Proyección de solo lectura de una nota crédito/débito electrónica para
 * contabilizarla. El generador no toca entities JPA: el adapter arma este
 * snapshot desde {@code nota_electronica}.
 */
public interface LectorNota {

    NotaContable cargar(Long notaId, Integer empresaId);

    /**
     * @param fecha     fecha de emisión de la nota
     * @param tipo      CREDITO | DEBITO
     * @param documento etiqueta del documento (" — NC 123") o vacía
     * @param base      base gravable (reversa de ingreso)
     * @param iva       IVA de la nota
     * @param total     total (base + iva)
     * @param clienteId tercero cliente; null si no se resolvió
     */
    record NotaContable(
            LocalDate fecha,
            String tipo,
            String documento,
            BigDecimal base,
            BigDecimal iva,
            BigDecimal total,
            Long clienteId) {
    }
}
