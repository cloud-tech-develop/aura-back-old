package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;

import lombok.RequiredArgsConstructor;

/**
 * Cuenta de cobro (docs/PLAN_CUENTA_COBRO_CORREO.md). Decisión del 2026-10-19: la
 * puede emitir cualquier empresa. Lo único que cambia según la empresa es con qué
 * tipo nace un borrador nuevo: la persona natural no responsable de IVA que no
 * factura electrónicamente arranca en cuenta de cobro; las demás, en factura.
 */
@Component
@RequiredArgsConstructor
public class CuentaCobroReglas {

    /** Prefijo del consecutivo propio de las cuentas de cobro: se muestra CT-001. */
    public static final String PREFIJO = "CT";

    /**
     * Número de una venta para mostrar en SQL: la cuenta de cobro como CT-001 (tres
     * dígitos como mínimo; LPAD solo rellena, nunca corta), las demás como prefijo + consecutivo. {@code v} = alias de venta.
     */
    public static final String SQL_NUMERO = """
            CASE WHEN v.tipo_documento = 'CUENTA_COBRO'
                 THEN COALESCE(v.prefijo, 'CT') || '-' || LPAD(v.consecutivo::text, GREATEST(3, LENGTH(v.consecutivo::text)), '0')
                 ELSE COALESCE(v.prefijo, '') || v.consecutivo END""";
    /** {@code venta.tipo_documento} de una cuenta de cobro emitida. */
    public static final String TIPO_VENTA = "CUENTA_COBRO";

    private final EmpresaJPARepository empresaRepo;
    private final TerceroJPARepository terceroRepo;

    /** Cualquier empresa puede emitir cuentas de cobro. */
    public boolean puedeEmitir(Integer empresaId) {
        return true;
    }

    /**
     * Tipo con el que nace un borrador cuando no se pide uno: cuenta de cobro si la
     * empresa es persona natural no responsable de IVA y no factura electrónicamente;
     * si no, factura.
     */
    public String tipoPorDefecto(Integer empresaId) {
        EmpresaEntity e = empresaRepo.findById(empresaId).orElse(null);
        if (e == null || e.isFacturaElectronica() || !"NATURAL".equalsIgnoreCase(e.getTipoPersona()))
            return FacturaVentaEntity.TIPO_FACTURA;
        TerceroEntity t = e.getNit() != null ? terceroRepo.findEmpresaTerceroByNit(empresaId, e.getNit()).orElse(null)
                : null;
        return t != null && "RESPONSABLE_IVA".equalsIgnoreCase(t.getRegimen()) ? FacturaVentaEntity.TIPO_FACTURA
                : FacturaVentaEntity.TIPO_CUENTA_COBRO;
    }

    /** Se conserva para los llamadores: hoy ninguna empresa está excluida. */
    public void exigir(Integer empresaId) {
        // Sin restricción (decisión del usuario 2026-10-19).
    }
}
