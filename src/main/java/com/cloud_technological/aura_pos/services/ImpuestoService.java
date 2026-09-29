package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.ImpuestoDto;

/** Impuestos parametrizables (E5): reglas de % + cuentas por impuesto. */
public interface ImpuestoService {

    List<ImpuestoDto> listar(Integer empresaId);

    ImpuestoDto crear(Integer empresaId, ImpuestoDto dto);

    ImpuestoDto actualizar(Integer empresaId, Long id, ImpuestoDto dto);

    /**
     * Siembra IVA 19/5, INC 8, Excluido y Exento con las cuentas
     * 240801/240802 si existen. Idempotente por nombre.
     */
    void seedDefaults(Integer empresaId);
}
