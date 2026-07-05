package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.proyecto.AsignarTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.CreateFrenteDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTableDto;
import com.cloud_technological.aura_pos.dto.proyecto.FrenteTrabajadorDto;
import com.cloud_technological.aura_pos.dto.proyecto.UpdateFrenteDto;
import com.cloud_technological.aura_pos.entity.ProyectoEntity;
import com.cloud_technological.aura_pos.entity.ProyectoFrenteEntity;
import com.cloud_technological.aura_pos.entity.ProyectoFrenteTrabajadorEntity;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoFrenteTrabajadorJPARepository;
import com.cloud_technological.aura_pos.repositories.proyecto.ProyectoJPARepository;
import com.cloud_technological.aura_pos.services.ProyectoFrenteService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import jakarta.transaction.Transactional;

@Service
public class ProyectoFrenteServiceImpl implements ProyectoFrenteService {

    @Autowired private ProyectoJPARepository proyectoRepo;
    @Autowired private ProyectoFrenteJPARepository frenteRepo;
    @Autowired private ProyectoFrenteTrabajadorJPARepository trabajadorRepo;
    @Autowired private ProyectoFrenteQueryRepository queryRepo;

    @Override
    public List<FrenteTableDto> listarPorProyecto(Long proyectoId, Integer empresaId) {
        return queryRepo.listarPorProyecto(proyectoId, empresaId);
    }

    @Override
    public FrenteTableDto obtenerFrente(Long id, Integer empresaId) {
        return toTableDto(getFrente(id, empresaId));
    }

    @Override
    @Transactional
    public FrenteTableDto crearFrente(Long proyectoId, CreateFrenteDto dto, Integer empresaId, Long usuarioId) {
        ProyectoEntity proyecto = proyectoRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(proyectoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Proyecto no encontrado"));
        if ("FINALIZADO".equals(proyecto.getEstado()) || "ANULADO".equals(proyecto.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se pueden crear frentes en un proyecto finalizado o anulado");
        }

        String codigo = dto.getCodigo().trim().toUpperCase();
        if (frenteRepo.existsByCodigoAndProyectoIdAndDeletedAtIsNull(codigo, proyectoId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Ya existe un frente con el código: " + codigo);
        }

        ProyectoFrenteEntity e = new ProyectoFrenteEntity();
        e.setEmpresaId(empresaId);
        e.setProyectoId(proyectoId);
        e.setCodigo(codigo);
        e.setNombre(dto.getNombre().trim());
        e.setDescripcion(dto.getDescripcion());
        e.setUbicacion(dto.getUbicacion());
        e.setLiderId(dto.getLiderId());
        e.setFechaInicio(dto.getFechaInicio());
        e.setFechaFin(dto.getFechaFin());
        e.setEstado(dto.getEstado() != null ? dto.getEstado() : "ACTIVO");
        e.setObservacion(dto.getObservacion());
        e.setCreatedBy(usuarioId);
        ProyectoFrenteEntity saved = frenteRepo.save(e);

        if (dto.getTrabajadorIds() != null) {
            for (Long empId : dto.getTrabajadorIds()) {
                asignar(saved, empId, LocalDate.now(), null, usuarioId);
            }
        }

        return toTableDto(saved);
    }

    @Override
    @Transactional
    public FrenteTableDto actualizarFrente(Long id, UpdateFrenteDto dto, Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity e = getFrente(id, empresaId);

        String codigo = dto.getCodigo().trim().toUpperCase();
        if (!e.getCodigo().equalsIgnoreCase(codigo)
                && frenteRepo.existsByCodigoAndProyectoIdAndIdNotAndDeletedAtIsNull(codigo, e.getProyectoId(), id)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código ya está en uso por otro frente");
        }

        e.setCodigo(codigo);
        e.setNombre(dto.getNombre().trim());
        e.setDescripcion(dto.getDescripcion());
        e.setUbicacion(dto.getUbicacion());
        e.setLiderId(dto.getLiderId());
        e.setFechaInicio(dto.getFechaInicio());
        e.setFechaFin(dto.getFechaFin());
        if (dto.getEstado() != null) e.setEstado(dto.getEstado());
        e.setObservacion(dto.getObservacion());
        e.setUpdatedBy(usuarioId);

        return toTableDto(frenteRepo.save(e));
    }

    @Override
    @Transactional
    public void eliminarFrente(Long id, Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity e = getFrente(id, empresaId);
        e.setDeletedAt(LocalDateTime.now());
        e.setDeletedBy(usuarioId);
        e.setEstado("ANULADO");
        frenteRepo.save(e);
    }

    @Override
    public List<FrenteTrabajadorDto> listarTrabajadores(Long frenteId, Integer empresaId) {
        return queryRepo.listarTrabajadores(frenteId, empresaId);
    }

    @Override
    @Transactional
    public void asignarTrabajador(Long frenteId, AsignarTrabajadorDto dto, Integer empresaId, Long usuarioId) {
        ProyectoFrenteEntity frente = getFrente(frenteId, empresaId);
        if ("FINALIZADO".equals(frente.getEstado()) || "ANULADO".equals(frente.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se pueden asignar trabajadores a un frente finalizado o anulado");
        }
        if (trabajadorRepo.existsByFrenteIdAndEmpleadoIdAndEstadoAndDeletedAtIsNull(frenteId, dto.getEmpleadoId(), "ACTIVO")) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El trabajador ya está asignado a este frente");
        }
        LocalDate fecha = dto.getFechaInicio() != null ? dto.getFechaInicio() : LocalDate.now();
        asignar(frente, dto.getEmpleadoId(), fecha, dto.getObservacion(), usuarioId);
    }

    @Override
    @Transactional
    public void retirarTrabajador(Long frenteId, Long empleadoId, Integer empresaId, Long usuarioId) {
        getFrente(frenteId, empresaId); // valida pertenencia a la empresa
        ProyectoFrenteTrabajadorEntity t = trabajadorRepo
                .findByFrenteIdAndEmpleadoIdAndDeletedAtIsNull(frenteId, empleadoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "El trabajador no está asignado a este frente"));
        t.setEstado("RETIRADO");
        t.setFechaFin(LocalDate.now());
        t.setUpdatedBy(usuarioId);
        trabajadorRepo.save(t);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private ProyectoFrenteEntity getFrente(Long id, Integer empresaId) {
        return frenteRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Frente no encontrado"));
    }

    private void asignar(ProyectoFrenteEntity frente, Long empleadoId, LocalDate fechaInicio,
            String observacion, Long usuarioId) {
        ProyectoFrenteTrabajadorEntity t = new ProyectoFrenteTrabajadorEntity();
        t.setEmpresaId(frente.getEmpresaId());
        t.setProyectoId(frente.getProyectoId());
        t.setFrenteId(frente.getId());
        t.setEmpleadoId(empleadoId);
        t.setFechaInicio(fechaInicio);
        t.setEstado("ACTIVO");
        t.setObservacion(observacion);
        t.setCreatedBy(usuarioId);
        trabajadorRepo.save(t);
    }

    private FrenteTableDto toTableDto(ProyectoFrenteEntity e) {
        FrenteTableDto d = new FrenteTableDto();
        d.setId(e.getId());
        d.setProyectoId(e.getProyectoId());
        d.setCodigo(e.getCodigo());
        d.setNombre(e.getNombre());
        d.setDescripcion(e.getDescripcion());
        d.setUbicacion(e.getUbicacion());
        d.setLiderId(e.getLiderId());
        d.setFechaInicio(e.getFechaInicio());
        d.setFechaFin(e.getFechaFin());
        d.setEstado(e.getEstado());
        d.setObservacion(e.getObservacion());
        return d;
    }
}
