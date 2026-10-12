package com.cloud_technological.aura_pos.utils;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;

/**
 * Un registro que llega por ID desde la petición tiene que ser de la empresa de
 * la sesión. Se usa con {@code findById(id).filter(x -> AislamientoEmpresa.es(x.getEmpresa(), empresaId))}:
 * el de otra empresa responde "no encontrado", igual que si no existiera, para no
 * revelar que existe (docs/PLAN_FIRMA_CONTABLE.md, F0).
 */
public final class AislamientoEmpresa {

    private AislamientoEmpresa() {
    }

    public static boolean es(EmpresaEntity empresa, Integer empresaId) {
        return empresa != null && empresaId != null && empresaId.equals(empresa.getId());
    }
}
