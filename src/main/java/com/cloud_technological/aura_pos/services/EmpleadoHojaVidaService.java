package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateFamiliarDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.ExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.FamiliarDto;

/**
 * Pestañas de hoja de vida del empleado: cuentas bancarias, familiares e
 * historial laboral. Cada operación devuelve la lista actualizada. 404 si el
 * empleado no es de la empresa.
 */
public interface EmpleadoHojaVidaService {

    List<CuentaBancariaDto> cuentas(Long empleadoId, Integer empresaId);

    /**
     * Agrega una cuenta. La primera, o la marcada como principal, queda como la
     * única principal y se copia al tercero (de ahí se paga la nómina).
     */
    List<CuentaBancariaDto> crearCuenta(Long empleadoId, CreateCuentaBancariaDto dto, Integer empresaId);

    List<CuentaBancariaDto> actualizarCuenta(Long empleadoId, Long id, CreateCuentaBancariaDto dto, Integer empresaId);

    /** Quita la cuenta; si era la principal, la siguiente activa pasa a serlo. */
    List<CuentaBancariaDto> eliminarCuenta(Long empleadoId, Long id, Integer empresaId);

    List<FamiliarDto> familiares(Long empleadoId, Integer empresaId);

    List<FamiliarDto> crearFamiliar(Long empleadoId, CreateFamiliarDto dto, Integer empresaId);

    List<FamiliarDto> actualizarFamiliar(Long empleadoId, Long id, CreateFamiliarDto dto, Integer empresaId);

    List<FamiliarDto> eliminarFamiliar(Long empleadoId, Long id, Integer empresaId);

    List<ExperienciaDto> experiencia(Long empleadoId, Integer empresaId);

    /** Agrega una experiencia; 400 si la fecha de fin es anterior a la de inicio. */
    List<ExperienciaDto> crearExperiencia(Long empleadoId, CreateExperienciaDto dto, Integer empresaId);

    List<ExperienciaDto> actualizarExperiencia(Long empleadoId, Long id, CreateExperienciaDto dto, Integer empresaId);

    List<ExperienciaDto> eliminarExperiencia(Long empleadoId, Long id, Integer empresaId);
}
