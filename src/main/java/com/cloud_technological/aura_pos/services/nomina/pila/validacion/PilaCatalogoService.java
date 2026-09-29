package com.cloud_technological.aura_pos.services.nomina.pila.validacion;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.CargaEntidadDto;
import com.cloud_technological.aura_pos.entity.PilaEntidadEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PilaJPARepositories.PilaEntidadRepo;

/**
 * Carga de catálogos oficiales de PILA (P2b). Upsert del catálogo de entidades
 * EPS/AFP/ARL/CCF desde la fuente oficial (no se inventan códigos: el usuario/
 * admin sube el catálogo del período).
 */
@Service
public class PilaCatalogoService {

    private final PilaEntidadRepo entidadRepo;

    public PilaCatalogoService(PilaEntidadRepo entidadRepo) {
        this.entidadRepo = entidadRepo;
    }

    /** Upsert por (tipo, código). Devuelve cuántas filas se cargaron/actualizaron. */
    @Transactional
    public int cargarEntidades(List<CargaEntidadDto> filas) {
        if (filas == null) return 0;
        int n = 0;
        for (CargaEntidadDto f : filas) {
            if (f.getTipo() == null || f.getCodigo() == null || f.getNombre() == null) continue;
            String tipo = f.getTipo().trim().toUpperCase();
            String codigo = f.getCodigo().trim();
            PilaEntidadEntity e = entidadRepo.findByTipoAndCodigo(tipo, codigo)
                    .orElseGet(PilaEntidadEntity::new);
            e.setTipo(tipo);
            e.setCodigo(codigo);
            e.setNombre(f.getNombre().trim());
            e.setVigenciaDesde(parse(f.getVigenciaDesde()));
            e.setVigenciaHasta(parse(f.getVigenciaHasta()));
            e.setActivo(f.getActivo() == null || f.getActivo());
            entidadRepo.save(e);
            n++;
        }
        return n;
    }

    @Transactional(readOnly = true)
    public long totalEntidades() {
        return entidadRepo.count();
    }

    private LocalDate parse(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
