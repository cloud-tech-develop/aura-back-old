package com.cloud_technological.aura_pos.services;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.dto.empresas.UpdateEmpresaContactoDto;
import com.cloud_technological.aura_pos.dto.empresas.UpdateEmpresaDatosDto;

public interface IEmpresaService {
    EmpresaDto obtenerEmpresaActual(Integer empresaId, Long sucursalId, Long usuarioId);

    /**
     * Actualiza los datos de contacto de la empresa. El telefono, el correo y
     * la direccion viven en el TERCERO de la empresa (el que tiene su NIT), no
     * en la tabla empresa: es el mismo dato que sale en la factura.
     */
    EmpresaDto actualizarContacto(UpdateEmpresaContactoDto dto, Integer empresaId,
            Long sucursalId, Long usuarioId);

    /**
     * Datos de la empresa que edita su administrador: nombre comercial, logo,
     * contacto, resolución de facturación y rangos de Factus. Exige la acción
     * especial caja.usuarios:EDITAR_EMPRESA (se valida en el controlador).
     */
    EmpresaDto actualizarDatos(UpdateEmpresaDatosDto dto, Integer empresaId,
            Long sucursalId, Long usuarioId);
}
