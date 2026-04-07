package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CentroCostoTableDto;
import com.cloud_technological.aura_pos.dto.centros_costos.CreateCentroCostoDto;
import com.cloud_technological.aura_pos.dto.centros_costos.UpdateCentroCostoDto;
import com.cloud_technological.aura_pos.entity.CentroCostoEntity;
import com.cloud_technological.aura_pos.repositories.centros_costos.CentroCostoJPARepository;
import com.cloud_technological.aura_pos.repositories.centros_costos.CentroCostoQueryRepository;
import com.cloud_technological.aura_pos.services.CentroCostoService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class CentroCostoServiceImpl implements CentroCostoService {

    @Autowired private CentroCostoJPARepository jpaRepo;
    @Autowired private CentroCostoQueryRepository queryRepo;

    @Override
    public PageImpl<CentroCostoTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return queryRepo.listar(pageable, empresaId);
    }

    @Override
    public CentroCostoTableDto obtenerPorId(Long id, Integer empresaId) {
        jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Centro de costo no encontrado"));
        // Retornamos via query para incluir nombre del padre
        return queryRepo.listar(new PageableDto<>(), empresaId)
                .getContent().stream()
                .filter(cc -> cc.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Centro de costo no encontrado"));
    }

    @Override
    @Transactional
    public CentroCostoTableDto crear(CreateCentroCostoDto dto, Integer empresaId, Long usuarioId) {
        if (jpaRepo.existsByCodigoAndEmpresaIdAndDeletedAtIsNull(dto.getCodigo().trim(), empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un centro de costo con el código: " + dto.getCodigo());
        }
        if (jpaRepo.existsByNombreAndEmpresaIdAndDeletedAtIsNull(dto.getNombre().trim(), empresaId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un centro de costo con el nombre: " + dto.getNombre());
        }

        CentroCostoEntity entity = new CentroCostoEntity();
        entity.setEmpresaId(empresaId);
        entity.setCodigo(dto.getCodigo().trim().toUpperCase());
        entity.setNombre(dto.getNombre().trim());
        entity.setDescripcion(dto.getDescripcion());
        entity.setTipo(dto.getTipo());
        entity.setSucursalId(dto.getSucursalId());
        entity.setResponsableId(dto.getResponsableId());
        entity.setPresupuestoAsignado(dto.getPresupuestoAsignado());
        entity.setUsuarioCreacion(usuarioId);

        Boolean permiteMovimientos = dto.getPermiteMovimientos() != null ? dto.getPermiteMovimientos() : Boolean.TRUE;
        entity.setPermiteMovimientos(permiteMovimientos);

        if (dto.getPadreCentroCostoId() != null) {
            CentroCostoEntity padre = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getPadreCentroCostoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El centro de costo padre no existe"));
            entity.setPadre(padre);
            entity.setNivel(padre.getNivel() != null ? padre.getNivel() + 1 : 1);
        } else {
            entity.setNivel(0);
        }

        CentroCostoEntity saved = jpaRepo.save(entity);
        return toTableDto(saved);
    }

    @Override
    @Transactional
    public CentroCostoTableDto actualizar(Long id, UpdateCentroCostoDto dto, Integer empresaId, Long usuarioId) {
        CentroCostoEntity entity = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Centro de costo no encontrado"));

        String nuevoCodigo = dto.getCodigo().trim().toUpperCase();
        if (!entity.getCodigo().equalsIgnoreCase(nuevoCodigo) &&
                jpaRepo.existsByCodigoAndEmpresaIdAndIdNotAndDeletedAtIsNull(nuevoCodigo, empresaId, id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código ya está en uso por otro centro de costo");
        }
        if (!entity.getNombre().equalsIgnoreCase(dto.getNombre().trim()) &&
                jpaRepo.existsByNombreAndEmpresaIdAndIdNotAndDeletedAtIsNull(dto.getNombre().trim(), empresaId, id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre ya está en uso por otro centro de costo");
        }
        if (dto.getPadreCentroCostoId() != null && dto.getPadreCentroCostoId().equals(id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un centro de costo no puede ser su propio padre");
        }

        entity.setCodigo(nuevoCodigo);
        entity.setNombre(dto.getNombre().trim());
        entity.setDescripcion(dto.getDescripcion());
        entity.setTipo(dto.getTipo());
        entity.setSucursalId(dto.getSucursalId());
        entity.setResponsableId(dto.getResponsableId());
        entity.setPresupuestoAsignado(dto.getPresupuestoAsignado());
        entity.setUsuarioModificacion(usuarioId);
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());
        if (dto.getPermiteMovimientos() != null) entity.setPermiteMovimientos(dto.getPermiteMovimientos());

        if (dto.getPadreCentroCostoId() != null) {
            CentroCostoEntity padre = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getPadreCentroCostoId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "El centro de costo padre no existe"));
            entity.setPadre(padre);
            entity.setNivel(padre.getNivel() != null ? padre.getNivel() + 1 : 1);
        } else {
            entity.setPadre(null);
            entity.setNivel(0);
        }

        return toTableDto(jpaRepo.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        CentroCostoEntity entity = jpaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Centro de costo no encontrado"));

        if (jpaRepo.existsByPadreIdAndDeletedAtIsNull(id)) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "No se puede eliminar este centro de costo porque tiene centros de costo hijos asociados");
        }

        entity.setDeletedAt(LocalDateTime.now());
        entity.setActivo(false);
        jpaRepo.save(entity);
    }

    @Override
    public List<CentroCostoDto> list(Integer empresaId) {
        return queryRepo.list(empresaId);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private CentroCostoTableDto toTableDto(CentroCostoEntity e) {
        CentroCostoTableDto dto = new CentroCostoTableDto();
        dto.setId(e.getId());
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setDescripcion(e.getDescripcion());
        dto.setTipo(e.getTipo());
        dto.setNivel(e.getNivel());
        dto.setPermiteMovimientos(e.getPermiteMovimientos());
        dto.setPresupuestoAsignado(e.getPresupuestoAsignado());
        dto.setActivo(e.getActivo());
        if (e.getPadre() != null) {
            dto.setPadreId(e.getPadre().getId());
            dto.setNombrePadre(e.getPadre().getNombre());
        }
        return dto;
    }
}
