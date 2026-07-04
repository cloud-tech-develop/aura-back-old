package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;

/**
 * Validación contable centralizada para asientos.
 *
 * Regla inviolable: todo comprobante debe quedar cuadrado
 * (Σ débitos = Σ créditos) y sin valores negativos. Usado tanto por el
 * registro manual (AsientoContableServiceImpl) como por la generación
 * automática (ContabilidadAutoServiceImpl) para garantizar que ningún
 * asiento descuadrado llegue a la base de datos.
 */
public final class AsientoBalanceValidator {

    private AsientoBalanceValidator() {
    }

    /** Valida que los totales débito/crédito cuadren y no sean negativos. */
    public static void validarCuadre(BigDecimal totalDebito, BigDecimal totalCredito) {
        BigDecimal db = totalDebito != null ? totalDebito : BigDecimal.ZERO;
        BigDecimal cr = totalCredito != null ? totalCredito : BigDecimal.ZERO;

        if (db.signum() < 0 || cr.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El asiento no puede tener totales negativos: débito=" + db + " crédito=" + cr);
        }
        if (db.compareTo(cr) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El asiento no está cuadrado: débito=" + db + " crédito=" + cr);
        }
        if (db.signum() == 0 && cr.signum() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El asiento no tiene movimientos (débito y crédito en cero)");
        }
    }

    /**
     * Suma las líneas, valida que cada una sea no negativa y que el asiento
     * cuadre. Devuelve los totales [débito, crédito] ya calculados.
     */
    public static BigDecimal[] sumarYValidar(List<AsientoDetalleEntity> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El asiento debe tener al menos una línea de movimiento");
        }

        BigDecimal totalDebito = BigDecimal.ZERO;
        BigDecimal totalCredito = BigDecimal.ZERO;
        for (AsientoDetalleEntity d : detalles) {
            BigDecimal db = d.getDebito() != null ? d.getDebito() : BigDecimal.ZERO;
            BigDecimal cr = d.getCredito() != null ? d.getCredito() : BigDecimal.ZERO;
            if (db.signum() < 0 || cr.signum() < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Una línea del asiento tiene valores negativos: débito=" + db + " crédito=" + cr);
            }
            totalDebito = totalDebito.add(db);
            totalCredito = totalCredito.add(cr);
        }

        validarCuadre(totalDebito, totalCredito);
        return new BigDecimal[] { totalDebito, totalCredito };
    }
}
