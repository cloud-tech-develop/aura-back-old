package com.cloud_technological.aura_pos.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.rutas.CreateRutaDto;
import com.cloud_technological.aura_pos.dto.rutas.RutaDto;
import com.cloud_technological.aura_pos.dto.rutas.RutaLocalDto;
import com.cloud_technological.aura_pos.dto.rutas.RutaTableDto;
import com.cloud_technological.aura_pos.dto.rutas.UpdateRutaDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.LocalEntity;
import com.cloud_technological.aura_pos.entity.RutaEntity;
import com.cloud_technological.aura_pos.entity.RutaLocalEntity;
import com.cloud_technological.aura_pos.repositories.locales.LocalJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.rutas.RutaJPARepository;
import com.cloud_technological.aura_pos.repositories.rutas.RutaLocalJPARepository;
import com.cloud_technological.aura_pos.repositories.rutas.RutaQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

@Service
public class RutaService {

    @Autowired
    private RutaJPARepository rutaRepository;

    @Autowired
    private RutaLocalJPARepository rutaLocalRepository;

    @Autowired
    private RutaQueryRepository rutaQueryRepository;

    @Autowired
    private EmpleadoJPARepository empleadoRepository;

    @Autowired
    private LocalJPARepository localRepository;

    @SuppressWarnings("unchecked")
    public PageImpl<RutaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        Object params = pageable.getParams();
        Long vendedorId = null;
        String search = pageable.getSearch();

        if (params != null) {
            if (params instanceof java.util.Map) {
                java.util.Map<String, Object> paramMap = (java.util.Map<String, Object>) params;
                vendedorId = paramMap.get("vendedorId") != null
                        ? Long.valueOf(paramMap.get("vendedorId").toString())
                        : null;
            }
        }

        List<RutaTableDto> rutas = rutaQueryRepository.page(
                empresaId,
                pageable.getPage().intValue(),
                pageable.getRows().intValue(),
                vendedorId,
                search
        );

        int totalRows = rutas.isEmpty() ? 0 : rutas.get(0).getTotalRows();

        return new PageImpl<>(rutas,
                org.springframework.data.domain.PageRequest.of(
                        pageable.getPage().intValue(),
                        pageable.getRows().intValue()
                ),
                totalRows);
    }

    public RutaDto findById(Long id, Integer empresaId) {
        RutaEntity entity = rutaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada");
        }

        return toDto(entity);
    }

    public RutaDto findByIdWithLocales(Long id, Integer empresaId) {
        return findById(id, empresaId);
    }

    @Transactional
    public RutaDto create(CreateRutaDto dto, Integer empresaId) {
        if (rutaRepository.existsByEmpresaIdAndNombreAndActivoTrue(empresaId.longValue(), dto.getNombre())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una ruta con este nombre");
        }

        if (dto.getLocales() == null || dto.getLocales().isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe agregar al menos un local a la ruta");
        }

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        EmpleadoEntity vendedor = empleadoRepository.findByIdAndEmpresaId(dto.getVendedorId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor no encontrado"));

        RutaEntity ruta = new RutaEntity();
        ruta.setEmpresa(empresa);
        ruta.setVendedor(vendedor);
        ruta.setNombre(dto.getNombre());
        ruta.setDescripcion(dto.getDescripcion());
        ruta.setActivo(true);
        ruta.setCreatedAt(LocalDateTime.now());

        ruta = rutaRepository.save(ruta);

        List<RutaLocalEntity> locales = new ArrayList<>();
        for (RutaLocalDto localDto : dto.getLocales()) {
            LocalEntity local = localRepository.findById(localDto.getLocalId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Local no encontrado"));

            RutaLocalEntity rutaLocal = new RutaLocalEntity();
            rutaLocal.setRuta(ruta);
            rutaLocal.setLocal(local);
            rutaLocal.setOrden(localDto.getOrden());
            rutaLocal.setCreatedAt(LocalDateTime.now());
            locales.add(rutaLocal);
        }

        rutaLocalRepository.saveAll(locales);

        return toDto(ruta);
    }

    @Transactional
    public RutaDto update(Long id, UpdateRutaDto dto, Integer empresaId) {
        RutaEntity entity = rutaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada");
        }

        if (dto.getNombre() != null && !dto.getNombre().equals(entity.getNombre())) {
            if (rutaRepository.existsByEmpresaIdAndNombreAndActivoTrueAndIdNot(empresaId.longValue(), dto.getNombre(), id)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una ruta con este nombre");
            }
            entity.setNombre(dto.getNombre());
        }

        if (dto.getDescripcion() != null) {
            entity.setDescripcion(dto.getDescripcion());
        }
        if (dto.getActivo() != null) {
            entity.setActivo(dto.getActivo());
        }

        if (dto.getVendedorId() != null) {
            EmpleadoEntity vendedor = empleadoRepository.findByIdAndEmpresaId(dto.getVendedorId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Vendedor no encontrado"));
            entity.setVendedor(vendedor);
        }

        if (dto.getLocales() != null) {
            rutaLocalRepository.deleteByRutaId(id);

            List<RutaLocalEntity> locales = new ArrayList<>();
            for (RutaLocalDto localDto : dto.getLocales()) {
                LocalEntity local = localRepository.findById(localDto.getLocalId())
                        .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Local no encontrado"));

                RutaLocalEntity rutaLocal = new RutaLocalEntity();
                rutaLocal.setRuta(entity);
                rutaLocal.setLocal(local);
                rutaLocal.setOrden(localDto.getOrden());
                rutaLocal.setCreatedAt(LocalDateTime.now());
                locales.add(rutaLocal);
            }

            rutaLocalRepository.saveAll(locales);
        }

        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(rutaRepository.save(entity));
    }

    @Transactional
    public void delete(Long id, Integer empresaId) {
        RutaEntity entity = rutaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Ruta no encontrada");
        }

        if (rutaQueryRepository.tieneVisitasAsociadas(id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede eliminar, la ruta tiene visitas asociadas");
        }

        entity.setActivo(false);
        entity.setUpdatedAt(LocalDateTime.now());
        rutaRepository.save(entity);
    }

    private RutaDto toDto(RutaEntity entity) {
        RutaDto dto = new RutaDto();
        dto.setId(entity.getId());
        dto.setEmpresaId(entity.getEmpresa().getId().longValue());
        dto.setVendedorId(entity.getVendedor().getId());
        dto.setVendedorNombre(entity.getVendedor().getNombres() + " " + entity.getVendedor().getApellidos());
        dto.setNombre(entity.getNombre());
        dto.setDescripcion(entity.getDescripcion());
        dto.setActivo(entity.getActivo());
        return dto;
    }
}
