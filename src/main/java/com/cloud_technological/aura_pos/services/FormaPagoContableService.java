package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.contabilidad.FormaPagoContableDto;

/**
 * Formas de pago parametrizables (E2 · pieza 2): mapean cada método de pago
 * a una cuenta del disponible (11xx) sin tocar código.
 */
public interface FormaPagoContableService {

    List<FormaPagoContableDto> listar(Integer empresaId);

    FormaPagoContableDto crear(Integer empresaId, FormaPagoContableDto dto);

    FormaPagoContableDto actualizar(Integer empresaId, Long id, FormaPagoContableDto dto);

    /**
     * Cuenta contable de la forma de pago con ese código, o {@code null} si
     * no existe, está inactiva o no tiene cuenta asignada.
     */
    Long cuentaPara(Integer empresaId, String codigoMetodoPago);

    /**
     * Siembra las formas de pago estándar (EFECTIVO→1105; TRANSFERENCIA,
     * TARJETA, NEQUI, DAVIPLATA→1110), omitiendo las que ya existan.
     */
    void seedDefaults(Integer empresaId);
}
