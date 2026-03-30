package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.visitas.ConfirmarLlegadaDto;
import com.cloud_technological.aura_pos.dto.visitas.CreateVisitaDto;
import com.cloud_technological.aura_pos.dto.visitas.VisitaDto;
import com.cloud_technological.aura_pos.dto.visitas.VisitaTableDto;
import com.cloud_technological.aura_pos.entity.EmpleadoEntity;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.LocalEntity;
import com.cloud_technological.aura_pos.entity.RutaEntity;
import com.cloud_technological.aura_pos.entity.VisitaEntity;
import com.cloud_technological.aura_pos.repositories.locales.LocalJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.EmpleadoJPARepository;
import com.cloud_technological.aura_pos.repositories.rutas.RutaJPARepository;
import com.cloud_technological.aura_pos.repositories.visitas.VisitaJPARepository;
import com.cloud_technological.aura_pos.repositories.visitas.VisitaQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@Service
public class VisitaService {

    @Autowired
    private VisitaJPARepository visitaRepository;

    @Autowired
    private VisitaQueryRepository visitaQueryRepository;

    @Autowired
    private LocalJPARepository localRepository;

    @Autowired
    private EmpleadoJPARepository empleadoRepository;

    @Autowired
    private RutaJPARepository rutaRepository;

    @Autowired
    private SecurityUtils securityUtils;

    @SuppressWarnings("unchecked")
    public PageImpl<VisitaTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        Object params = pageable.getParams();
        Long vendedorId = null;
        LocalDate fechaDesde = null;
        LocalDate fechaHasta = null;
        String estado = null;
        String search = pageable.getSearch();

        if (params != null) {
            if (params instanceof java.util.Map) {
                java.util.Map<String, Object> paramMap = (java.util.Map<String, Object>) params;
                vendedorId = paramMap.get("vendedorId") != null 
                    ? Long.valueOf(paramMap.get("vendedorId").toString()) 
                    : null;
                fechaDesde = paramMap.get("fechaDesde") != null 
                    ? LocalDate.parse(paramMap.get("fechaDesde").toString()) 
                    : null;
                fechaHasta = paramMap.get("fechaHasta") != null 
                    ? LocalDate.parse(paramMap.get("fechaHasta").toString()) 
                    : null;
                estado = paramMap.get("estado") != null 
                    ? paramMap.get("estado").toString() 
                    : null;
            }
        }

        List<VisitaTableDto> visitas = visitaQueryRepository.page(
            empresaId, 
            pageable.getPage().intValue(), 
            pageable.getRows().intValue(), 
            vendedorId, 
            fechaDesde, 
            fechaHasta, 
            estado, 
            search
        );

        int totalRows = visitas.isEmpty() ? 0 : visitas.get(0).getTotalRows();

        return new PageImpl<>(visitas, 
            org.springframework.data.domain.PageRequest.of(
                pageable.getPage().intValue(), 
                pageable.getRows().intValue()
            ), 
            totalRows);
    }

    public List<VisitaTableDto> getVisitasDelDia(Integer empresaId) {
        Long usuarioId = securityUtils.getUsuarioId();

        if (usuarioId == null) {
            throw new GlobalException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }

        List<EmpleadoEntity> empleados = empleadoRepository.findByEmpresaIdAndActivoTrue(empresaId);
        EmpleadoEntity empleado = empleados.stream()
                .filter(e -> e.getId().equals(usuarioId))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empleado no encontrado"));

        return visitaQueryRepository.findByVendedorAndFecha(empresaId.longValue(), empleado.getId(), LocalDate.now());
    }

    public VisitaDto findById(Long id, Integer empresaId) {
        VisitaEntity entity = visitaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada");
        }

        return toDto(entity);
    }

    @Transactional
    public VisitaDto create(CreateVisitaDto dto, Integer empresaId) {
        LocalEntity local = localRepository.findById(dto.getLocalId())
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Local no encontrado"));

        if (local.getVendedorActual() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El local no tiene un vendedor asignado");
        }

        LocalDateTime fechaProgramada = LocalDateTime.parse(dto.getFechaProgramada());

        if (visitaRepository.existsByLocalIdAndFechaProgramadaAndEstadoNot(
                dto.getLocalId(), fechaProgramada, VisitaEntity.Estado.CANCELADA)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe una visita para este local en la fecha especificada");
        }

        EmpresaEntity empresa = new EmpresaEntity();
        empresa.setId(empresaId);

        EmpleadoEntity vendedor = local.getVendedorActual();

        RutaEntity ruta = null;
        if (dto.getRutaId() != null) {
            ruta = rutaRepository.findById(dto.getRutaId())
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Ruta no encontrada"));
        }

        VisitaEntity visita = new VisitaEntity();
        visita.setEmpresa(empresa);
        visita.setLocal(local);
        visita.setVendedor(vendedor);
        visita.setRuta(ruta);
        visita.setFechaProgramada(fechaProgramada);
        visita.setHoraProgramada(dto.getHoraProgramada());
        visita.setEstado(VisitaEntity.Estado.PROGRAMADA);
        visita.setCreatedAt(LocalDateTime.now());

        return toDto(visitaRepository.save(visita));
    }

    @Transactional
    public VisitaDto confirmarLlegada(Long id, ConfirmarLlegadaDto dto, Integer empresaId) {
        VisitaEntity entity = visitaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada");
        }

        if (entity.getEstado() != VisitaEntity.Estado.PROGRAMADA) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La visita no está en estado programada");
        }

        if (dto.getConfirmacionManual() == null || !dto.getConfirmacionManual()) {
            if (dto.getLatitud() != null && dto.getLongitud() != null && entity.getLocal().getLatitud() != null && entity.getLocal().getLongitud() != null) {
                double distancia = calcularDistancia(
                        dto.getLatitud(), dto.getLongitud(),
                        entity.getLocal().getLatitud(), entity.getLocal().getLongitud()
                );

                if (distancia > 100) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST, "Debe estar dentro del local para confirmar la llegada");
                }
            }
        }

        entity.setEstado(VisitaEntity.Estado.COMPLETADA);
        entity.setFechaReal(LocalDateTime.now());
        entity.setLatitudLlegada(dto.getLatitud());
        entity.setLongitudLlegada(dto.getLongitud());
        entity.setObservaciones(dto.getObservaciones());
        entity.setUpdatedAt(LocalDateTime.now());

        return toDto(visitaRepository.save(entity));
    }

    @Transactional
    public void cancelar(Long id, Integer empresaId) {
        VisitaEntity entity = visitaRepository.findById(id)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada"));

        if (!entity.getEmpresa().getId().equals(empresaId.longValue())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Visita no encontrada");
        }

        if (entity.getEstado() == VisitaEntity.Estado.COMPLETADA) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se puede cancelar una visita completada");
        }

        entity.setEstado(VisitaEntity.Estado.CANCELADA);
        entity.setUpdatedAt(LocalDateTime.now());
        visitaRepository.save(entity);
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private VisitaDto toDto(VisitaEntity entity) {
        VisitaDto dto = new VisitaDto();
        dto.setId(entity.getId());
        dto.setEmpresaId(entity.getEmpresa().getId().longValue());
        dto.setLocalId(entity.getLocal().getId());
        dto.setLocalNombre(entity.getLocal().getNombre());
        dto.setLocalDireccion(entity.getLocal().getDireccion());
        dto.setVendedorId(entity.getVendedor().getId());
        dto.setVendedorNombre(entity.getVendedor().getNombres() + " " + entity.getVendedor().getApellidos());

        if (entity.getRuta() != null) {
            dto.setRutaId(entity.getRuta().getId());
            dto.setRutaNombre(entity.getRuta().getNombre());
        }

        dto.setFechaProgramada(entity.getFechaProgramada());
        dto.setHoraProgramada(entity.getHoraProgramada());
        dto.setFechaReal(entity.getFechaReal());
        dto.setLatitudLlegada(entity.getLatitudLlegada());
        dto.setLongitudLlegada(entity.getLongitudLlegada());
        dto.setEstado(entity.getEstado().name());
        dto.setObservaciones(entity.getObservaciones());

        return dto;
    }
}
