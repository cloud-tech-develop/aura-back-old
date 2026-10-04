package com.cloud_technological.aura_pos.services.implementations;

import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.CreateSubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloDto;
import com.cloud_technological.aura_pos.dto.permisos.SubmoduloTableDto;
import com.cloud_technological.aura_pos.dto.permisos.UpdateSubmoduloDto;
import com.cloud_technological.aura_pos.entity.ModuloEntity;
import com.cloud_technological.aura_pos.entity.SubmoduloEntity;
import com.cloud_technological.aura_pos.repositories.platform.ModuloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.SubmoduloJPARepository;
import com.cloud_technological.aura_pos.repositories.platform.ModuloQueryRepository;
import com.cloud_technological.aura_pos.services.SubmoduloService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubmoduloServiceImpl implements SubmoduloService {

    private final SubmoduloJPARepository submoduloJPARepository;
    private final ModuloJPARepository moduloJPARepository;
    private final ModuloQueryRepository moduloQueryRepository;

    @Override
    @Transactional
    public SubmoduloTableDto crear(CreateSubmoduloDto dto) {
        ModuloEntity modulo = moduloJPARepository.findById(dto.getModuloId())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));

        // El código es único dentro del módulo ("ventas" existe en Ventas y en Reportes).
        if (moduloQueryRepository.codigoSubmoduloEnUso(modulo.getId(), dto.getCodigo(), null)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un submódulo con este código en el módulo");
        }

        SubmoduloEntity entity = new SubmoduloEntity();
        entity.setModulo(modulo);
        entity.setNombre(dto.getNombre());
        entity.setCodigo(dto.getCodigo());
        entity.setDescripcion(dto.getDescripcion());
        entity.setOrden(dto.getOrden() != null ? dto.getOrden() : 0);
        entity.setActivo(true);
        entity.setPadreId(validarPadre(dto.getPadreId(), modulo.getId(), null));

        SubmoduloEntity saved = submoduloJPARepository.save(entity);
        return toTableDto(saved);
    }

    @Override
    @Transactional
    public SubmoduloTableDto actualizar(Integer id, UpdateSubmoduloDto dto) {
        SubmoduloEntity entity = submoduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado"));

        boolean cambioModulo = false;
        if (dto.getModuloId() != null && !dto.getModuloId().equals(entity.getModulo().getId())) {
            ModuloEntity modulo = moduloJPARepository.findById(dto.getModuloId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Módulo no encontrado"));
            entity.setModulo(modulo);
            cambioModulo = true;
        }

        if (dto.getCodigo() != null && (cambioModulo || !dto.getCodigo().equals(entity.getCodigo()))) {
            if (moduloQueryRepository.codigoSubmoduloEnUso(entity.getModulo().getId(), dto.getCodigo(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un submódulo con este código en el módulo");
            }
            entity.setCodigo(dto.getCodigo());
        }

        // Grupo padre: sinPadre lo saca del grupo; padreId lo mueve. Si cambió de
        // módulo y no se dice nada, el grupo del módulo anterior ya no aplica.
        if (Boolean.TRUE.equals(dto.getSinPadre())) {
            entity.setPadreId(null);
        } else if (dto.getPadreId() != null) {
            entity.setPadreId(validarPadre(dto.getPadreId(), entity.getModulo().getId(), id));
        } else if (cambioModulo) {
            entity.setPadreId(null);
        }

        if (dto.getNombre() != null) entity.setNombre(dto.getNombre());
        if (dto.getDescripcion() != null) entity.setDescripcion(dto.getDescripcion());
        if (dto.getOrden() != null) entity.setOrden(dto.getOrden());
        if (dto.getActivo() != null) entity.setActivo(dto.getActivo());

        SubmoduloEntity saved = submoduloJPARepository.save(entity);
        return toTableDto(saved);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        if (!submoduloJPARepository.existsById(id)) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado");
        }
        if (moduloQueryRepository.hijosDeSubmodulo(id) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Es un grupo con pantallas dentro: muévalas a otro grupo o elimínelas primero");
        }
        submoduloJPARepository.deleteById(id);
    }

    @Override
    public SubmoduloDto obtenerPorId(Integer id) {
        SubmoduloEntity entity = submoduloJPARepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Submódulo no encontrado"));
        return toDto(entity);
    }

    @Override
    public List<SubmoduloTableDto> listarPorModulo(Integer moduloId) {
        return moduloQueryRepository.listarSubmodulosPorModulo(moduloId);
    }

    @Override
    public PageImpl<SubmoduloTableDto> paginar(Integer moduloId, String search, int page, int size) {
        return moduloQueryRepository.paginarSubmodulos(moduloId, search, page, size);
    }

    /**
     * Un grupo padre es un submódulo del mismo módulo que no cuelga de otro
     * (solo hay un nivel de grupos). Un submódulo con hijos no puede tener padre.
     */
    private Long validarPadre(Long padreId, Integer moduloId, Integer propioId) {
        if (padreId == null) return null;
        if (propioId != null && padreId.intValue() == propioId) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un submódulo no puede ser su propio grupo");
        }
        SubmoduloEntity padre = submoduloJPARepository.findById(padreId.intValue())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Grupo no encontrado"));
        if (!padre.getModulo().getId().equals(moduloId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El grupo debe ser del mismo módulo");
        }
        if (padre.getPadreId() != null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "'" + padre.getNombre() + "' ya está dentro de un grupo: solo hay un nivel de grupos");
        }
        if (propioId != null && moduloQueryRepository.hijosDeSubmodulo(propioId) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Este submódulo es un grupo con pantallas: no puede quedar dentro de otro grupo");
        }
        return padreId;
    }

    private SubmoduloTableDto toTableDto(SubmoduloEntity entity) {
        SubmoduloTableDto dto = new SubmoduloTableDto();
        dto.setId(entity.getId());
        dto.setModuloId(entity.getModulo().getId());
        dto.setModuloNombre(entity.getModulo().getNombre());
        dto.setNombre(entity.getNombre());
        dto.setCodigo(entity.getCodigo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        dto.setOrden(entity.getOrden());
        dto.setPadreId(entity.getPadreId());
        return dto;
    }

    private SubmoduloDto toDto(SubmoduloEntity entity) {
        SubmoduloDto dto = new SubmoduloDto();
        dto.setId(entity.getId());
        dto.setModuloId(entity.getModulo().getId());
        dto.setModuloNombre(entity.getModulo().getNombre());
        dto.setNombre(entity.getNombre());
        dto.setCodigo(entity.getCodigo());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        dto.setOrden(entity.getOrden());
        dto.setPadreId(entity.getPadreId());
        if (entity.getPadreId() != null) {
            submoduloJPARepository.findById(entity.getPadreId().intValue())
                    .ifPresent(p -> dto.setPadreNombre(p.getNombre()));
        }
        return dto;
    }
}
