package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.entity.EmpleadoArlEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.TipoEmpleadoEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoQueryRepository;
import com.cloud_technological.aura_pos.repositories.tipo_empleado.TipoEmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.EmpleadoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class EmpleadoServiceImpl implements EmpleadoService {

    @Autowired
    private EmpleadoJPARepository empleadoRepo;

    @Autowired
    private EmpleadoQueryRepository empleadoQueryRepo;

    @Autowired
    private UsuarioJPARepository usuarioRepo;

    @Autowired
    private TipoEmpleadoJPARepository tipoEmpleadoRepo;

    @Override
    public PageImpl<EmpleadoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return empleadoQueryRepo.listar(pageable, empresaId);
    }

    @Override
    public List<EmpleadoDto> listarVendedores(Integer empresaId) {
        List<EmpleadoEntity> vendedores = empleadoRepo.findByEmpresaIdAndActivoTrueAndCargoIgnoreCase(empresaId, "VENDEDOR");
        return vendedores.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public EmpleadoDto obtenerPorId(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public EmpleadoDto crear(CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = new EmpleadoEntity();
        mapFromDto(dto, entity);

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);
        entity.setEmpresa(empresa);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        entity = empleadoRepo.save(entity);

        EmpleadoArlEntity arl = new EmpleadoArlEntity();
        arl.setEmpleado(entity);
        arl.setEmpresa(empresa);
        arl.setNivelRiesgo(dto.getNivelRiesgoArl() != null ? dto.getNivelRiesgoArl() : 1);
        arl.setPorcentaje(resolverPorcentajeArl(arl.getNivelRiesgo()));
        entity.setArl(arl);

        return toDto(empleadoRepo.save(entity));
    }

    @Override
    @Transactional
    public EmpleadoDto actualizar(Long id, CreateEmpleadoDto dto, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        
        // Guardar el cargo anterior para comparar
        String cargoAnterior = entity.getCargo();
        
        mapFromDto(dto, entity);
        entity.setUpdatedAt(LocalDateTime.now());

        if (dto.getNivelRiesgoArl() != null) {
            EmpleadoArlEntity arl = entity.getArl();
            if (arl == null) {
                arl = new EmpleadoArlEntity();
                arl.setEmpleado(entity);
                EmpresaEntity empresa = new EmpresaEntity();
                empresa.setId(empresaId);
                arl.setEmpresa(empresa);
                entity.setArl(arl);
            }
            arl.setNivelRiesgo(dto.getNivelRiesgoArl());
            arl.setPorcentaje(resolverPorcentajeArl(dto.getNivelRiesgoArl()));
        }

        EmpleadoDto result = toDto(empleadoRepo.save(entity));

        // Sincronizar con usuario si el cargo cambió
        if (cargoAnterior == null || !cargoAnterior.equals(dto.getCargo())) {
            sincronizarConUsuario(id, empresaId);
        }

        return result;
    }

    @Override
    @Transactional
    public void retirar(Long id, Integer empresaId) {
        EmpleadoEntity entity = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));
        entity.setActivo(false);
        entity.setFechaRetiro(LocalDate.now());
        entity.setUpdatedAt(LocalDateTime.now());
        empleadoRepo.save(entity);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void mapFromDto(CreateEmpleadoDto dto, EmpleadoEntity entity) {
        entity.setNombres(dto.getNombres());
        entity.setApellidos(dto.getApellidos());
        entity.setTipoDocumento(dto.getTipoDocumento());
        entity.setNumeroDocumento(dto.getNumeroDocumento());
        entity.setCargo(dto.getCargo());
        entity.setFechaIngreso(dto.getFechaIngreso());
        entity.setSalarioBase(dto.getSalarioBase());
        entity.setTipoContrato(dto.getTipoContrato());
        entity.setBanco(dto.getBanco());
        entity.setNumeroCuenta(dto.getNumeroCuenta());
        entity.setTipoCuenta(dto.getTipoCuenta());
    }

    private EmpleadoDto toDto(EmpleadoEntity entity) {
        EmpleadoDto dto = new EmpleadoDto();
        dto.setId(entity.getId());
        dto.setNombres(entity.getNombres());
        dto.setApellidos(entity.getApellidos());
        dto.setTipoDocumento(entity.getTipoDocumento());
        dto.setNumeroDocumento(entity.getNumeroDocumento());
        dto.setCargo(entity.getCargo());
        dto.setFechaIngreso(entity.getFechaIngreso());
        dto.setFechaRetiro(entity.getFechaRetiro());
        dto.setSalarioBase(entity.getSalarioBase());
        dto.setTipoContrato(entity.getTipoContrato());
        dto.setBanco(entity.getBanco());
        dto.setNumeroCuenta(entity.getNumeroCuenta());
        dto.setTipoCuenta(entity.getTipoCuenta());
        dto.setActivo(entity.getActivo());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        if (entity.getArl() != null) {
            dto.setNivelRiesgoArl(entity.getArl().getNivelRiesgo());
            dto.setPorcentajeArl(entity.getArl().getPorcentaje());
        }
        return dto;
    }

    private BigDecimal resolverPorcentajeArl(int nivel) {
        return switch (nivel) {
            case 1 -> new BigDecimal("0.522");
            case 2 -> new BigDecimal("1.044");
            case 3 -> new BigDecimal("2.436");
            case 4 -> new BigDecimal("4.350");
            case 5 -> new BigDecimal("6.960");
            default -> new BigDecimal("0.522");
        };
    }

    @Override
    @Transactional
    public void sincronizarConUsuario(Long id, Integer empresaId) {
        // 1. Obtener el empleado
        EmpleadoEntity empleado = empleadoRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        // 2. Buscar si existe un usuario vinculado a este empleado
        UsuarioEntity usuario = usuarioRepo.findByEmpleadoId(id).orElse(null);
        
        if (usuario == null) {
            // No hay usuario vinculado, no hay nada que sincronizar
            return;
        }

        // 3. Buscar el tipo de empleado por el cargo
        String cargoEmpleado = empleado.getCargo();
        TipoEmpleadoEntity tipoEmpleado = null;
        
        // Buscar en tipos de empleado de la empresa
        List<TipoEmpleadoEntity> tiposEmpresa = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue((long) empresaId);
        for (TipoEmpleadoEntity te : tiposEmpresa) {
            if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                tipoEmpleado = te;
                break;
            }
        }
        
        // Si no se encontró, buscar en los tipos generales (empresa_id = 1)
        if (tipoEmpleado == null) {
            List<TipoEmpleadoEntity> tiposGenerales = tipoEmpleadoRepo.findByEmpresaIdAndActivoTrue(1L);
            for (TipoEmpleadoEntity te : tiposGenerales) {
                if (te.getNombre().equalsIgnoreCase(cargoEmpleado)) {
                    tipoEmpleado = te;
                    break;
                }
            }
        }

        // 4. Actualizar el usuario con los nuevos datos del empleado
        usuario.setTipoEmpleado(tipoEmpleado);
        // El rol se actualiza con el nombre del cargo o del tipo de empleado
        String nuevoRol = tipoEmpleado != null ? tipoEmpleado.getNombre() : cargoEmpleado;
        usuario.setRol(nuevoRol);

        // 5. Guardar los cambios
        usuarioRepo.save(usuario);
    }
}
