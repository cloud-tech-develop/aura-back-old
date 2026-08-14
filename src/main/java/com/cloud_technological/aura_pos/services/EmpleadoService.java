package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.SaldosInicialesDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface EmpleadoService {
    PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    List<EmpleadoDto> listarVendedores(Integer empresaId);

    /** Empleados con al menos un contrato ACTIVO (para prestaciones/nómina). */
    List<EmpleadoDto> listarConContratoActivo(Integer empresaId);

    /** F7 — saldos iniciales de todos los empleados activos (para la migración). */
    List<SaldosInicialesDto> listarSaldosIniciales(Integer empresaId);

    /** F7 — actualiza los saldos iniciales de un empleado. */
    SaldosInicialesDto actualizarSaldosIniciales(Long id, SaldosInicialesDto dto, Integer empresaId);
    EmpleadoDto obtenerPorId(Long id, Integer empresaId);
    EmpleadoDto crear(CreateEmpleadoDto dto, Integer empresaId);

    /** Alta desde un tercero ya creado (identidad+banco en tercero; contrato aparte). */
    EmpleadoDto crearDesdeTercero(Long terceroId, String cargo, Integer empresaId);

    /** Marca si el empleado requiere control de asistencia (config MIXTA). */
    EmpleadoDto cambiarControlAsistencia(Long id, boolean requiere, Integer empresaId);
    EmpleadoDto actualizar(Long id, CreateEmpleadoDto dto, Integer empresaId);
    void retirar(Long id, Integer empresaId);
    
    /**
     * Sincroniza los datos del empleado con el usuario vinculado.
     * Actualiza el rol basándose en el cargo del empleado.
     * @param id ID del empleado
     * @param empresaId ID de la empresa
     */
    void sincronizarConUsuario(Long id, Integer empresaId);
}
