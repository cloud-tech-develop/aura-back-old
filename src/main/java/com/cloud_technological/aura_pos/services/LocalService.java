package com.cloud_technological.aura_pos.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.locales.CreateLocalDto;
import com.cloud_technological.aura_pos.dto.locales.LocalDto;
import com.cloud_technological.aura_pos.dto.locales.LocalTableDto;
import com.cloud_technological.aura_pos.dto.locales.UpdateLocalDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.LocalEntity;
import com.cloud_technological.aura_pos.repositories.locales.LocalJPARepository;
import com.cloud_technological.aura_pos.repositories.locales.LocalQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class LocalService {

    @Autowired
    private LocalJPARepository localRepository;

    @Autowired
    private LocalQueryRepository localQueryRepository;

    @Autowired
    private EmpleadoJPARepository empleadoRepository;

    @SuppressWarnings("unchecked")
    public PageImpl<LocalTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        // Extraer parámetros del pageable
        Object params = pageable.getParams();
        Long vendedorActualId = null;
        Long vendedorAnteriorId = null;
        String search = pageable.getSearch();

        if (params != null) {
            if (params instanceof java.util.Map) {
                java.util.Map<String, Object> paramMap = (java.util.Map<String, Object>) params;
                vendedorActualId = paramMap.get("vendedorActualId") != null
                        ? Long.valueOf(paramMap.get("vendedorActualId").toString())
                        : null;
                vendedorAnteriorId = paramMap.get("vendedorAnteriorId") != null
                        ? Long.valueOf(paramMap.get("vendedorAnteriorId").toString())
                        : null;
            }
        }

        List<LocalTableDto> locales = localQueryRepository.page(
                empresaId,
                pageable.getPage().intValue(),
                pageable.getRows().intValue(),
                vendedorActualId,
                vendedorAnteriorId,
                search
        );

        // Calcular total de páginas
        int totalRows = locales.isEmpty() ? 0 : locales.get(0).getTotalRows();
        int totalPages = (int) Math.ceil((double) totalRows / pageable.getRows().intValue());

        return new PageImpl<>(locales,
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPage().intValue(),
                        pageable.getRows().intValue()
                ),
                totalRows);
    }

    public LocalDto findById(Long id, Integer empresaId) {
        LocalEntity entity = localRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado");
        }

        return toDto(entity);
    }

    @Transactional
    public LocalDto create(CreateLocalDto dto, Integer empresaId) {
        if (localRepository.existsByEmpresaIdAndNombreAndActivoTrue(empresaId.longValue(), dto.getNombre())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un local con este nombre");
        }

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        LocalEntity entity = new LocalEntity();
        entity.setEmpresa(empresa);
        entity.setNombre(dto.getNombre());
        entity.setDireccion(dto.getDireccion());
        entity.setCiudad(dto.getCiudad());
        entity.setCiudadId(dto.getCiudadId());
        entity.setBarrio(dto.getBarrio());
        entity.setLatitud(dto.getLatitud());
        entity.setLongitud(dto.getLongitud());
        entity.setImagenFachada(dto.getImagenFachada());
        entity.setHorarioJson(dto.getHorarioJson());
        entity.setPreferenciaDiasJson(dto.getPreferenciaDiasJson());
        entity.setActivo(true);
        entity.setCreatedAt(LocalDateTime.now());

        if (dto.getVendedorActualId() != null) {
            EmpleadoEntity vendedorActual = empleadoRepository.findByIdAndEmpresaId(dto.getVendedorActualId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor actual no encontrado"));
            entity.setVendedorActual(vendedorActual);
        }

        return toDto(localRepository.save(entity));
    }

    @Transactional
    public LocalDto update(Long id, UpdateLocalDto dto, Integer empresaId) {
        LocalEntity entity = localRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado");
        }

        if (dto.getNombre() != null && !dto.getNombre().equals(entity.getNombre())) {
            if (localRepository.existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(empresaId.longValue(), dto.getNombre(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un local con este nombre");
            }
            entity.setNombre(dto.getNombre());
        }

        if (dto.getDireccion() != null) {
            entity.setDireccion(dto.getDireccion());
        }
        if (dto.getLatitud() != null) {
            entity.setLatitud(dto.getLatitud());
        }
        if (dto.getLongitud() != null) {
            entity.setLongitud(dto.getLongitud());
        }
        if (dto.getImagenFachada() != null) {
            entity.setImagenFachada(dto.getImagenFachada());
        }
        if (dto.getHorarioJson() != null) {
            entity.setHorarioJson(dto.getHorarioJson());
        }
        if (dto.getPreferenciaDiasJson() != null) {
            entity.setPreferenciaDiasJson(dto.getPreferenciaDiasJson());
        }
        if (dto.getActivo() != null) {
            entity.setActivo(dto.getActivo());
        }

        // Manejar reasignación de vendedor
        if (dto.getVendedorActualId() != null && !dto.getVendedorActualId().equals(entity.getVendedorActual() != null ? entity.getVendedorActual().getId() : null)) {
            // Mover vendedor actual a anterior
            if (entity.getVendedorActual() != null) {
                entity.setVendedorAnterior(entity.getVendedorActual());
            }

            EmpleadoEntity nuevoVendedor = empleadoRepository.findByIdAndEmpresaId(dto.getVendedorActualId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor no encontrado"));
            entity.setVendedorActual(nuevoVendedor);
        }

        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(localRepository.save(entity));
    }

    @Transactional
    public void delete(Long id, Integer empresaId) {
        LocalEntity entity = localRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado");
        }

        if (localQueryRepository.tieneVisitasAsociadas(id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede eliminar, el local tiene visitas asociadas");
        }

        entity.setActivo(false);
        entity.setUpdatedAt(LocalDateTime.now());
        localRepository.save(entity);
    }

    @Transactional
    public LocalDto asignarVendedor(Long localId, Long vendedorId, Integer empresaId) {
        LocalEntity entity = localRepository.findById(localId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Local no encontrado"));

        if (!entity.getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Local no permitido");
        }

        // Si ya tiene vendedor actual, moverlo a anterior
        if (entity.getVendedorActual() != null) {
            entity.setVendedorAnterior(entity.getVendedorActual());
        }

        // Buscar y asignar el nuevo vendedor
        EmpleadoEntity nuevoVendedor = empleadoRepository.findByIdAndEmpresaId(vendedorId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor no encontrado"));

        entity.setVendedorActual(nuevoVendedor);
        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(localRepository.save(entity));
    }

    public List<LocalDto> findAllActivosByEmpresa(Integer empresaId) {
        return localRepository.findByEmpresaIdAndActivoTrueOrderByNombre(empresaId.longValue())
                .stream()
                .map(this::toDto)
                .collect(java.util.stream.Collectors.toList());
    }

    private LocalDto toDto(LocalEntity entity) {
        LocalDto dto = new LocalDto();
        dto.setId(entity.getId());
        dto.setEmpresaId(entity.getEmpresa().getId().longValue());
        dto.setNombre(entity.getNombre());
        dto.setDireccion(entity.getDireccion());
        dto.setCiudad(entity.getCiudad());
        dto.setCiudadId(entity.getCiudadId());
        dto.setBarrio(entity.getBarrio());
        dto.setLatitud(entity.getLatitud());
        dto.setLongitud(entity.getLongitud());
        dto.setImagenFachada(entity.getImagenFachada());
        dto.setHorarioJson(entity.getHorarioJson());
        dto.setPreferenciaDiasJson(entity.getPreferenciaDiasJson());
        dto.setActivo(entity.getActivo());

        if (entity.getVendedorActual() != null) {
            dto.setVendedorActualId(entity.getVendedorActual().getId());
            dto.setVendedorActualNombre(entity.getVendedorActual().getNombres() + " " + entity.getVendedorActual().getApellidos());
        }

        if (entity.getVendedorAnterior() != null) {
            dto.setVendedorAnteriorId(entity.getVendedorAnterior().getId());
            dto.setVendedorAnteriorNombre(entity.getVendedorAnterior().getNombres() + " " + entity.getVendedorAnterior().getApellidos());
        }

        return dto;
    }
}
