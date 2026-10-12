package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateCuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CreateFamiliarDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.CuentaBancariaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.ExperienciaDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoHojaVidaDtos.FamiliarDto;
import com.cloud_technological.aura_pos.entity.EmpleadoCuentaBancariaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoExperienciaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoFamiliarEntity;
import com.cloud_technological.aura_pos.mappers.EmpleadoHojaVidaMapper;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoHojaVidaJPARepositories.CuentaBancariaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoHojaVidaJPARepositories.ExperienciaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoHojaVidaJPARepositories.FamiliarRepo;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoHojaVidaQueryRepository;
import com.cloud_technological.aura_pos.services.EmpleadoHojaVidaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link EmpleadoHojaVidaService}. */
@Service
public class EmpleadoHojaVidaServiceImpl implements EmpleadoHojaVidaService {

    private final EmpleadoHojaVidaQueryRepository queryRepository;
    private final CuentaBancariaRepo cuentaRepository;
    private final FamiliarRepo familiarRepository;
    private final ExperienciaRepo experienciaRepository;
    private final EmpleadoHojaVidaMapper mapper;

    public EmpleadoHojaVidaServiceImpl(EmpleadoHojaVidaQueryRepository queryRepository,
            CuentaBancariaRepo cuentaRepository,
            FamiliarRepo familiarRepository,
            ExperienciaRepo experienciaRepository,
            EmpleadoHojaVidaMapper mapper) {
        this.queryRepository = queryRepository;
        this.cuentaRepository = cuentaRepository;
        this.familiarRepository = familiarRepository;
        this.experienciaRepository = experienciaRepository;
        this.mapper = mapper;
    }

    // ── Cuentas bancarias ────────────────────────────────────────────────────

    @Override
    public List<CuentaBancariaDto> cuentas(Long empleadoId, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        return queryRepository.cuentas(empleadoId);
    }

    @Override
    @Transactional
    public List<CuentaBancariaDto> crearCuenta(Long empleadoId, CreateCuentaBancariaDto dto, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        exigirBanco(dto.getBancoTerceroId(), empresaId);
        EmpleadoCuentaBancariaEntity c = new EmpleadoCuentaBancariaEntity();
        mapper.actualizarCuenta(dto, c);
        c.setEmpleadoId(empleadoId);
        c.setNumeroCuenta(dto.getNumeroCuenta().trim());
        c.setActiva(dto.getActiva() == null || dto.getActiva());
        c.setPrincipal(Boolean.TRUE.equals(dto.getPrincipal()));
        c.setCreatedAt(LocalDateTime.now());
        return guardarCuenta(empleadoId, cuentaRepository.save(c));
    }

    @Override
    @Transactional
    public List<CuentaBancariaDto> actualizarCuenta(Long empleadoId, Long id, CreateCuentaBancariaDto dto,
            Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        exigirBanco(dto.getBancoTerceroId(), empresaId);
        EmpleadoCuentaBancariaEntity c = cuenta(empleadoId, id);
        mapper.actualizarCuenta(dto, c);
        c.setNumeroCuenta(dto.getNumeroCuenta().trim());
        c.setActiva(dto.getActiva() == null || dto.getActiva());
        // Una cuenta inactiva no puede ser la de pago.
        c.setPrincipal(Boolean.TRUE.equals(dto.getPrincipal()) && c.getActiva());
        c.setUpdatedAt(LocalDateTime.now());
        return guardarCuenta(empleadoId, cuentaRepository.save(c));
    }

    @Override
    @Transactional
    public List<CuentaBancariaDto> eliminarCuenta(Long empleadoId, Long id, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        cuentaRepository.delete(cuenta(empleadoId, id));
        cuentaRepository.flush();
        queryRepository.asegurarPrincipal(empleadoId);
        queryRepository.copiarPrincipalAlTercero(empleadoId);
        return queryRepository.cuentas(empleadoId);
    }

    /** Deja una sola principal, asegura que exista una y la copia al tercero. */
    private List<CuentaBancariaDto> guardarCuenta(Long empleadoId, EmpleadoCuentaBancariaEntity guardada) {
        cuentaRepository.flush();
        if (Boolean.TRUE.equals(guardada.getPrincipal())) queryRepository.marcarUnicaPrincipal(empleadoId, guardada.getId());
        queryRepository.asegurarPrincipal(empleadoId);
        queryRepository.copiarPrincipalAlTercero(empleadoId);
        return queryRepository.cuentas(empleadoId);
    }

    private void exigirBanco(Long bancoTerceroId, Integer empresaId) {
        if (queryRepository.nombreBanco(bancoTerceroId, empresaId).isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El banco no es válido: elija un tercero de tipo banco.");
    }

    private EmpleadoCuentaBancariaEntity cuenta(Long empleadoId, Long id) {
        return cuentaRepository.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }

    // ── Familiares ───────────────────────────────────────────────────────────

    @Override
    public List<FamiliarDto> familiares(Long empleadoId, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        return queryRepository.familiares(empleadoId);
    }

    @Override
    @Transactional
    public List<FamiliarDto> crearFamiliar(Long empleadoId, CreateFamiliarDto dto, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        EmpleadoFamiliarEntity f = new EmpleadoFamiliarEntity();
        mapper.actualizarFamiliar(dto, f);
        f.setEmpleadoId(empleadoId);
        normalizar(f);
        f.setCreatedAt(LocalDateTime.now());
        familiarRepository.save(f);
        return queryRepository.familiares(empleadoId);
    }

    @Override
    @Transactional
    public List<FamiliarDto> actualizarFamiliar(Long empleadoId, Long id, CreateFamiliarDto dto, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        EmpleadoFamiliarEntity f = familiarRepository.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Familiar no encontrado"));
        mapper.actualizarFamiliar(dto, f);
        normalizar(f);
        f.setUpdatedAt(LocalDateTime.now());
        familiarRepository.save(f);
        return queryRepository.familiares(empleadoId);
    }

    @Override
    @Transactional
    public List<FamiliarDto> eliminarFamiliar(Long empleadoId, Long id, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        familiarRepository.delete(familiarRepository.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Familiar no encontrado")));
        return queryRepository.familiares(empleadoId);
    }

    /** Nombre limpio y banderas nunca nulas. */
    private static void normalizar(EmpleadoFamiliarEntity f) {
        f.setNombre(f.getNombre().trim());
        f.setConvive(Boolean.TRUE.equals(f.getConvive()));
        f.setDependeEconomicamente(Boolean.TRUE.equals(f.getDependeEconomicamente()));
        f.setBeneficiarioCaja(Boolean.TRUE.equals(f.getBeneficiarioCaja()));
        f.setBeneficiarioSalud(Boolean.TRUE.equals(f.getBeneficiarioSalud()));
        f.setContactoEmergencia(Boolean.TRUE.equals(f.getContactoEmergencia()));
    }

    // ── Historial laboral ────────────────────────────────────────────────────

    @Override
    public List<ExperienciaDto> experiencia(Long empleadoId, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        return queryRepository.experiencia(empleadoId);
    }

    @Override
    @Transactional
    public List<ExperienciaDto> crearExperiencia(Long empleadoId, CreateExperienciaDto dto, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        validarFechas(dto);
        EmpleadoExperienciaEntity x = new EmpleadoExperienciaEntity();
        mapper.actualizarExperiencia(dto, x);
        x.setEmpleadoId(empleadoId);
        x.setCreatedAt(LocalDateTime.now());
        experienciaRepository.save(x);
        return queryRepository.experiencia(empleadoId);
    }

    @Override
    @Transactional
    public List<ExperienciaDto> actualizarExperiencia(Long empleadoId, Long id, CreateExperienciaDto dto,
            Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        validarFechas(dto);
        EmpleadoExperienciaEntity x = experienciaRepository.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Experiencia no encontrada"));
        mapper.actualizarExperiencia(dto, x);
        x.setUpdatedAt(LocalDateTime.now());
        experienciaRepository.save(x);
        return queryRepository.experiencia(empleadoId);
    }

    @Override
    @Transactional
    public List<ExperienciaDto> eliminarExperiencia(Long empleadoId, Long id, Integer empresaId) {
        exigirEmpleado(empleadoId, empresaId);
        experienciaRepository.delete(experienciaRepository.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Experiencia no encontrada")));
        return queryRepository.experiencia(empleadoId);
    }

    private static void validarFechas(CreateExperienciaDto dto) {
        if (dto.getFechaFin() != null && dto.getFechaFin().isBefore(dto.getFechaInicio()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha de retiro no puede ser anterior a la de ingreso.");
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private void exigirEmpleado(Long empleadoId, Integer empresaId) {
        if (!queryRepository.empleadoDeLaEmpresa(empleadoId, empresaId))
            throw new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado");
    }
}
