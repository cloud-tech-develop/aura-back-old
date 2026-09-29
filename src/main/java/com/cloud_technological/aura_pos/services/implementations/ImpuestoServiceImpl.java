package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.ImpuestoDto;
import com.cloud_technological.aura_pos.entity.ImpuestoEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ImpuestoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.ImpuestoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ImpuestoServiceImpl implements ImpuestoService {

    private static final List<String> TIPOS = List.of("IVA", "INC", "EXCLUIDO", "EXENTO");

    private final ImpuestoJPARepository repo;
    private final PlanCuentaJPARepository planRepo;

    @Override
    public List<ImpuestoDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .map(e -> toDto(empresaId, e))
                .toList();
    }

    @Override
    @Transactional
    public ImpuestoDto crear(Integer empresaId, ImpuestoDto dto) {
        if (repo.findByEmpresaIdAndNombre(empresaId, dto.getNombre().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un impuesto llamado " + dto.getNombre());
        }
        ImpuestoEntity e = ImpuestoEntity.builder()
                .empresaId(empresaId)
                .nombre(dto.getNombre().trim())
                .tipo(normalizarTipo(dto.getTipo()))
                .porcentaje(dto.getPorcentaje() != null ? dto.getPorcentaje() : BigDecimal.ZERO)
                .vigenteDesde(dto.getVigenteDesde())
                .vigenteHasta(dto.getVigenteHasta())
                .activo(dto.getActivo() == null || dto.getActivo())
                .build();
        aplicarCuentas(empresaId, e, dto);
        return toDto(empresaId, repo.save(e));
    }

    @Override
    @Transactional
    public ImpuestoDto actualizar(Integer empresaId, Long id, ImpuestoDto dto) {
        ImpuestoEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Impuesto no encontrado"));
        e.setNombre(dto.getNombre().trim());
        e.setTipo(normalizarTipo(dto.getTipo()));
        if (dto.getPorcentaje() != null) {
            e.setPorcentaje(dto.getPorcentaje());
        }
        e.setVigenteDesde(dto.getVigenteDesde());
        e.setVigenteHasta(dto.getVigenteHasta());
        if (dto.getActivo() != null) {
            e.setActivo(dto.getActivo());
        }
        aplicarCuentas(empresaId, e, dto);
        return toDto(empresaId, repo.save(e));
    }

    @Override
    @Transactional
    public void seedDefaults(Integer empresaId) {
        Long generado = idCuenta(empresaId, "240801");
        Long descontable = idCuenta(empresaId, "240802");
        seedUno(empresaId, "IVA 19%", "IVA", new BigDecimal("19"), generado, descontable);
        seedUno(empresaId, "IVA 5%", "IVA", new BigDecimal("5"), generado, descontable);
        seedUno(empresaId, "INC 8%", "INC", new BigDecimal("8"), generado, descontable);
        seedUno(empresaId, "Excluido", "EXCLUIDO", BigDecimal.ZERO, null, null);
        seedUno(empresaId, "Exento", "EXENTO", BigDecimal.ZERO, null, null);
    }

    private void seedUno(Integer empresaId, String nombre, String tipo, BigDecimal pct,
            Long generado, Long descontable) {
        if (repo.findByEmpresaIdAndNombre(empresaId, nombre).isPresent()) {
            return;
        }
        repo.save(ImpuestoEntity.builder()
                .empresaId(empresaId)
                .nombre(nombre)
                .tipo(tipo)
                .porcentaje(pct)
                .cuentaGeneradoId(generado)
                .cuentaDescontableId(descontable)
                .activo(true)
                .build());
    }

    /** Guardarraíl (ADR-006): las cuentas del impuesto son 24xx auxiliares. */
    private void aplicarCuentas(Integer empresaId, ImpuestoEntity e, ImpuestoDto dto) {
        e.setCuentaGeneradoId(validar(empresaId, dto.getCuentaGeneradoId(), "generado"));
        e.setCuentaDescontableId(validar(empresaId, dto.getCuentaDescontableId(), "descontable"));
    }

    private Long validar(Integer empresaId, Long cuentaId, String rol) {
        if (cuentaId == null) {
            return null;
        }
        PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cuenta de impuesto " + rol + " no existe."));
        if (!Boolean.TRUE.equals(cuenta.getActiva()) || !Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de impuesto " + rol + " debe ser una auxiliar activa.");
        }
        if (cuenta.getCodigo() == null || !cuenta.getCodigo().startsWith("24")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta de impuesto " + rol + " debe ser 24xx; la cuenta "
                            + cuenta.getCodigo() + " no aplica.");
        }
        return cuentaId;
    }

    private String normalizarTipo(String tipo) {
        String t = tipo != null ? tipo.trim().toUpperCase() : "IVA";
        if (!TIPOS.contains(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo de impuesto inválido: use IVA, INC, EXCLUIDO o EXENTO.");
        }
        return t;
    }

    private Long idCuenta(Integer empresaId, String codigo) {
        return planRepo.findByEmpresaIdAndCodigo(empresaId, codigo)
                .map(PlanCuentaEntity::getId).orElse(null);
    }

    private String etiqueta(Integer empresaId, Long cuentaId) {
        if (cuentaId == null) {
            return null;
        }
        return planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .map(c -> c.getCodigo() + " - " + c.getNombre()).orElse("#" + cuentaId);
    }

    private ImpuestoDto toDto(Integer empresaId, ImpuestoEntity e) {
        return ImpuestoDto.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .tipo(e.getTipo())
                .porcentaje(e.getPorcentaje())
                .cuentaGeneradoId(e.getCuentaGeneradoId())
                .cuentaGenerado(etiqueta(empresaId, e.getCuentaGeneradoId()))
                .cuentaDescontableId(e.getCuentaDescontableId())
                .cuentaDescontable(etiqueta(empresaId, e.getCuentaDescontableId()))
                .vigenteDesde(e.getVigenteDesde())
                .vigenteHasta(e.getVigenteHasta())
                .activo(e.getActivo())
                .build();
    }
}
