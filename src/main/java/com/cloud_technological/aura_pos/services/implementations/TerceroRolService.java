package com.cloud_technological.aura_pos.services.implementations;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TerceroRolEntity;
import com.cloud_technological.aura_pos.entity.TerceroRolEntity.Rol;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroRolJPARepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Gestión de roles de tercero durante la migración de booleanos a tabla (V98).
 *
 * <h2>Doble escritura — leer antes de tocar</h2>
 *
 * Mientras dure la migración, los roles viven en DOS lugares:
 * <ul>
 *   <li>Los booleanos {@code es_cliente / es_proveedor / es_empleado / es_banco}
 *       de {@code tercero} — <b>fuente de verdad actual</b>, la que lee el
 *       código existente.</li>
 *   <li>La tabla {@code tercero_rol} — el destino.</li>
 * </ul>
 *
 * Este servicio escribe en ambos. <b>No saltarse ese paso:</b> si el código
 * viejo escribe solo booleanos, {@code tercero_rol} se desincroniza en silencio
 * y el día del corte los roles quedan mal.
 *
 * <h2>Orden de retiro</h2>
 * <ol>
 *   <li>V98: tabla + backfill ✔</li>
 *   <li>Doble escritura (este servicio) ← <b>estás aquí</b></li>
 *   <li>Migrar lecturas: {@code listarPorRol()} en vez de
 *       {@code listarClientes/Proveedores/Bancos}</li>
 *   <li>Quitar los booleanos de los 4 DTOs y de {@link TerceroEntity}</li>
 *   <li>Migración futura: {@code DROP COLUMN es_cliente, ...}</li>
 * </ol>
 */
@Slf4j
@Service
public class TerceroRolService {

    private final TerceroRolJPARepository rolRepository;

    public TerceroRolService(TerceroRolJPARepository rolRepository) {
        this.rolRepository = rolRepository;
    }

    /**
     * Sincroniza los roles de un tercero desde sus booleanos legacy.
     * Llamar después de crear o actualizar un tercero, dentro de la misma
     * transacción.
     */
    @Transactional
    public void sincronizarDesdeBooleanos(TerceroEntity tercero) {
        if (tercero == null || tercero.getId() == null) {
            return;
        }
        aplicar(tercero.getId(), Rol.CLIENTE,   Boolean.TRUE.equals(tercero.getEsCliente()));
        aplicar(tercero.getId(), Rol.PROVEEDOR, Boolean.TRUE.equals(tercero.getEsProveedor()));
        aplicar(tercero.getId(), Rol.EMPLEADO,  Boolean.TRUE.equals(tercero.getEsEmpleado()));
        aplicar(tercero.getId(), Rol.BANCO,     Boolean.TRUE.equals(tercero.getEsBanco()));
    }

    private void aplicar(Long terceroId, String rol, boolean activo) {
        if (activo) {
            rolRepository.agregarRol(terceroId, rol);
        } else {
            rolRepository.quitarRol(terceroId, rol);
        }
    }

    /**
     * Agrega un rol que NO tiene booleano equivalente (EPS, AFP, CCF, ARL).
     * Estos solo viven en la tabla — son la razón de existir de esta migración.
     */
    @Transactional
    public void agregarRol(Long terceroId, String rol) {
        rolRepository.agregarRol(terceroId, rol);
    }

    @Transactional
    public void quitarRol(Long terceroId, String rol) {
        rolRepository.quitarRol(terceroId, rol);
    }

    @Transactional(readOnly = true)
    public Set<String> rolesDe(Long terceroId) {
        return rolRepository.findByTerceroId(terceroId).stream()
                .map(TerceroRolEntity::getRol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional(readOnly = true)
    public boolean tieneRol(Long terceroId, String rol) {
        return rolRepository.existsByTerceroIdAndRol(terceroId, rol);
    }

    /** Roles de seguridad social: no tienen booleano legacy. */
    public static List<String> rolesSeguridadSocial() {
        return List.of(Rol.EPS, Rol.AFP, Rol.CCF, Rol.ARL, Rol.CESANTIAS);
    }
}
