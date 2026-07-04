package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.AuditoriaNominaAsistenciaEntity;
import com.cloud_technological.aura_pos.repositories.asistencia.AuditoriaNominaAsistenciaJPARepository;
import com.cloud_technological.aura_pos.services.AuditoriaNominaService;

@Service
public class AuditoriaNominaServiceImpl implements AuditoriaNominaService {

    @Autowired
    private AuditoriaNominaAsistenciaJPARepository auditoriaRepo;

    @Override
    @Transactional
    public void registrar(Integer empresaId, String entidad, Long entidadId, String accion,
                          Integer usuarioId, String valorAnterior, String valorNuevo, String motivo) {
        AuditoriaNominaAsistenciaEntity a = new AuditoriaNominaAsistenciaEntity();
        a.setEmpresaId(empresaId);
        a.setEntidad(entidad);
        a.setEntidadId(entidadId);
        a.setAccion(accion);
        a.setUsuarioId(usuarioId);
        a.setValorAnterior(valorAnterior);
        a.setValorNuevo(valorNuevo);
        a.setMotivo(motivo);
        a.setOrigen("SISTEMA");
        a.setFechaHora(LocalDateTime.now());
        auditoriaRepo.save(a);
    }

    @Override
    public List<AuditoriaNominaAsistenciaEntity> listar(Integer empresaId) {
        return auditoriaRepo.findTop200ByEmpresaIdOrderByFechaHoraDesc(empresaId);
    }

    @Override
    public List<AuditoriaNominaAsistenciaEntity> listarPorEntidad(Integer empresaId, String entidad, Long entidadId) {
        return auditoriaRepo.findByEmpresaIdAndEntidadAndEntidadIdOrderByFechaHoraDesc(empresaId, entidad, entidadId);
    }
}
