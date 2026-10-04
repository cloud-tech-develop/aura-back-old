package com.cloud_technological.aura_pos.services.permisos;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.PerfilEntity;
import com.cloud_technological.aura_pos.entity.PerfilPermisoEntity;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Crea los perfiles de sistema de una empresa que todavía no los tiene (las que se
 * crearon después de V190). Va aparte del cálculo de permisos para que
 * {@code @Transactional} aplique (no se llama a sí mismo).
 */
@Service
@RequiredArgsConstructor
public class PerfilesSistemaService {

    private final PerfilJPARepository perfilRepo;
    private final PerfilPermisoJPARepository permisoRepo;
    private final PermisoUsuarioQueryRepository query;

    @Transactional
    public void asegurar(Integer empresaId) {
        for (PerfilesSistema.Definicion d : PerfilesSistema.TODOS) {
            if (query.perfilIdPorCodigo(empresaId, d.codigo()) != null) continue;

            PerfilEntity p = new PerfilEntity();
            p.setEmpresaId(empresaId);
            p.setCodigo(d.codigo());
            p.setNombre(d.nombre());
            p.setDescripcion(d.descripcion());
            p.setAccesoTotal(d.accesoTotal());
            p.setEsSistema(true);
            p.setActivo(true);
            p = perfilRepo.save(p);

            // Igual que la migración: lo que el rol veía, con todas las acciones.
            for (Map.Entry<String, Long> sub : query.submodulosPorClave(d.claves()).entrySet()) {
                PerfilPermisoEntity pp = new PerfilPermisoEntity();
                pp.setPerfilId(p.getId());
                pp.setSubmoduloId(sub.getValue());
                pp.setVer(true);
                pp.setCrear(true);
                pp.setEditar(true);
                pp.setAnular(true);
                permisoRepo.save(pp);
            }
        }
    }
}
