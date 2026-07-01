package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.conceptos_caja.ConceptoCajaDto;
import com.cloud_technological.aura_pos.dto.conceptos_caja.CreateConceptoCajaDto;
import com.cloud_technological.aura_pos.entity.ConceptoCajaEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.conceptos_caja.ConceptoCajaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.ConceptoCajaService;

@Service
public class ConceptoCajaServiceImpl implements ConceptoCajaService {

    @Autowired private ConceptoCajaJPARepository repo;
    @Autowired private PlanCuentaJPARepository planRepo;

    @Override
    public List<ConceptoCajaDto> listar(Integer empresaId, String tipo) {
        List<ConceptoCajaEntity> conceptos = (tipo != null && !tipo.isBlank())
                ? repo.findByEmpresaIdAndTipoAndActivoTrueOrderByNombreAsc(empresaId, tipo.trim().toUpperCase())
                : repo.findByEmpresaIdAndActivoTrueOrderByNombreAsc(empresaId);
        return conceptos.stream().map(c -> toDto(c, empresaId)).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ConceptoCajaDto crear(CreateConceptoCajaDto dto, Integer empresaId) {
        String tipo = dto.getTipo().trim().toUpperCase();
        String nombre = dto.getNombre().trim();

        if (repo.existsByEmpresaIdAndTipoAndNombreIgnoreCaseAndActivoTrue(empresaId, tipo, nombre)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un concepto de " + tipo.toLowerCase() + " con el nombre: " + nombre);
        }
        validarCuenta(dto.getCuentaContableId(), empresaId);

        ConceptoCajaEntity entity = ConceptoCajaEntity.builder()
                .empresaId(empresaId)
                .nombre(nombre)
                .tipo(tipo)
                .cuentaContableId(dto.getCuentaContableId())
                .activo(true)
                .build();
        return toDto(repo.save(entity), empresaId);
    }

    @Override
    @Transactional
    public ConceptoCajaDto actualizar(Long id, CreateConceptoCajaDto dto, Integer empresaId) {
        ConceptoCajaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concepto de caja no encontrado"));
        validarCuenta(dto.getCuentaContableId(), empresaId);

        entity.setNombre(dto.getNombre().trim());
        entity.setTipo(dto.getTipo().trim().toUpperCase());
        entity.setCuentaContableId(dto.getCuentaContableId());
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());
        return toDto(repo.save(entity), empresaId);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        ConceptoCajaEntity entity = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Concepto de caja no encontrado"));
        entity.setActivo(false);
        repo.save(entity);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void validarCuenta(Long cuentaId, Integer empresaId) {
        PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cuenta contable seleccionada no existe"));
        if (!Boolean.TRUE.equals(cuenta.getActiva())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cuenta contable está inactiva");
        }
    }

    private ConceptoCajaDto toDto(ConceptoCajaEntity e, Integer empresaId) {
        ConceptoCajaDto dto = new ConceptoCajaDto();
        dto.setId(e.getId());
        dto.setNombre(e.getNombre());
        dto.setTipo(e.getTipo());
        dto.setCuentaContableId(e.getCuentaContableId());
        dto.setActivo(e.getActivo());
        planRepo.findByIdAndEmpresaId(e.getCuentaContableId(), empresaId).ifPresent(c -> {
            dto.setCuentaCodigo(c.getCodigo());
            dto.setCuentaNombre(c.getNombre());
        });
        return dto;
    }
}
