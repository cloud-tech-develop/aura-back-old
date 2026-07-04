package com.cloud_technological.aura_pos.dto.contabilidad;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mapeo concepto contable → cuenta del PUC para una empresa. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CuentaConfigDto {

    /** Nombre del enum ConceptoContable. */
    private String concepto;

    /** Descripción legible del concepto. */
    private String descripcionConcepto;

    /** Cuenta asignada (null si no hay mapeo ni cuenta por defecto disponible). */
    private Long cuentaId;

    private String codigoCuenta;

    private String nombreCuenta;

    /** true si está usando el código por defecto (no hay override de la empresa). */
    private boolean porDefecto;
}
