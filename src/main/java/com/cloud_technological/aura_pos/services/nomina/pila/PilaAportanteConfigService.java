package com.cloud_technological.aura_pos.services.nomina.pila;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.PilaAportanteConfigEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaAportanteConfigRepo;

/** Configuración del aportante para PILA (P4b): datos del encabezado por empresa. */
@Service
public class PilaAportanteConfigService {

    private final PilaAportanteConfigRepo repo;

    public PilaAportanteConfigService(PilaAportanteConfigRepo repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public PilaAportanteConfigEntity obtener(Integer empresaId) {
        return repo.findById(empresaId).orElseGet(() -> {
            PilaAportanteConfigEntity e = new PilaAportanteConfigEntity();
            e.setEmpresaId(empresaId);
            return e;
        });
    }

    @Transactional
    public PilaAportanteConfigEntity guardar(Integer empresaId, PilaAportanteConfigEntity dto) {
        PilaAportanteConfigEntity e = repo.findById(empresaId).orElseGet(PilaAportanteConfigEntity::new);
        e.setEmpresaId(empresaId);
        e.setTipoAportante(dto.getTipoAportante());
        e.setClaseAportante(dto.getClaseAportante());
        e.setNaturalezaAportante(dto.getNaturalezaAportante());
        e.setCodActividadEconomica(dto.getCodActividadEconomica());
        e.setCodOperador(dto.getCodOperador());
        e.setFormaPresentacion(dto.getFormaPresentacion());
        e.setRepLegalTipoDocumento(dto.getRepLegalTipoDocumento());
        e.setRepLegalDocumento(dto.getRepLegalDocumento());
        e.setRepLegalApellido1(dto.getRepLegalApellido1());
        e.setRepLegalApellido2(dto.getRepLegalApellido2());
        e.setRepLegalNombre1(dto.getRepLegalNombre1());
        e.setRepLegalNombre2(dto.getRepLegalNombre2());
        return repo.save(e);
    }
}
