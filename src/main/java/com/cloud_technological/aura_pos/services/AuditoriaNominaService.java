package com.cloud_technological.aura_pos.services;

import java.util.List;

import com.cloud_technological.aura_pos.entity.AuditoriaNominaAsistenciaEntity;

public interface AuditoriaNominaService {
    void registrar(Integer empresaId, String entidad, Long entidadId, String accion,
                   Integer usuarioId, String valorAnterior, String valorNuevo, String motivo);
    List<AuditoriaNominaAsistenciaEntity> listar(Integer empresaId);
    List<AuditoriaNominaAsistenciaEntity> listarPorEntidad(Integer empresaId, String entidad, Long entidadId);
}
