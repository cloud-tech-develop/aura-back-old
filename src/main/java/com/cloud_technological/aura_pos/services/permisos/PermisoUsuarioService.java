package com.cloud_technological.aura_pos.services.permisos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.AccionEspecial;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.LimitesPerfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.LimitesUsuario;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.Perfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.SubmoduloHabilitado;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.UsuarioPerfil;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;

import lombok.RequiredArgsConstructor;

/**
 * Permisos efectivos de un usuario (docs/PLAN_PERMISOS.md):
 *
 * <pre>empresa (lo que tiene activo)  ⊇  perfil  ⊇  usuario (perfil ± excepciones)</pre>
 *
 * <p>Un usuario sin perfil asignado usa el perfil de sistema de su rol. El
 * SUPER_ADMIN conserva siempre "Usuarios" y "Perfiles y Permisos" para que la
 * empresa no quede sin nadie que pueda arreglar los permisos. PLATFORM_ADMIN no
 * depende de perfiles.
 *
 * <p>Se guarda en caché unos minutos por usuario: lo consultan el menú (P2) y,
 * más adelante, cada petición al API (P3). Quien cambie perfiles, excepciones o
 * módulos de la empresa debe llamar a {@link #invalidarUsuario} o {@link #invalidarTodo}.
 */
@Service
@RequiredArgsConstructor
public class PermisoUsuarioService {

    /** Submódulos que el SUPER_ADMIN nunca pierde. */
    static final java.util.Set<String> CLAVES_SUPER_ADMIN = java.util.Set.of("caja.usuarios", "caja.perfiles");
    private static final long TTL_MS = 5 * 60 * 1000L;
    private static final List<String> TODAS = List.of(
            AccionPermiso.VER.name(), AccionPermiso.CREAR.name(),
            AccionPermiso.EDITAR.name(), AccionPermiso.ANULAR.name());

    private final PermisoUsuarioQueryRepository query;
    private final PerfilesSistemaService perfilesSistema;

    private final Map<Integer, Entrada> cache = new ConcurrentHashMap<>();

    public PermisosUsuarioDto efectivos(Integer usuarioId) {
        if (usuarioId == null) return new PermisosUsuarioDto();
        Entrada e = cache.get(usuarioId);
        if (e != null && e.expira() > System.currentTimeMillis()) return e.permisos();
        PermisosUsuarioDto calculado = calcular(usuarioId);
        cache.put(usuarioId, new Entrada(calculado, System.currentTimeMillis() + TTL_MS));
        return calculado;
    }

    /** ¿Puede el usuario hacer esta acción en el submódulo {@code modulo.submodulo}? */
    public boolean puede(Integer usuarioId, String clave, AccionPermiso accion) {
        PermisosUsuarioDto p = efectivos(usuarioId);
        List<String> acciones = p.getPermisos().get(clave);
        return acciones != null && acciones.contains(accion.name());
    }

    /**
     * ¿Tiene la acción en alguna de las claves? Una clave {@code modulo.*} vale
     * por cualquier submódulo de ese módulo (pantallas que muestran varios).
     */
    public boolean puedeAlguna(Integer usuarioId, List<String> claves, AccionPermiso accion) {
        PermisosUsuarioDto p = efectivos(usuarioId);
        for (String clave : claves) {
            if (clave.endsWith(".*")) {
                String modulo = clave.substring(0, clave.length() - 1);
                for (Map.Entry<String, List<String>> e : p.getPermisos().entrySet()) {
                    if (e.getKey().startsWith(modulo) && e.getValue().contains(accion.name())) return true;
                }
            } else {
                List<String> acciones = p.getPermisos().get(clave);
                if (acciones != null && acciones.contains(accion.name())) return true;
            }
        }
        return false;
    }

    /**
     * ¿Tiene la acción especial? {@code claveCompleta} = {@code modulo.submodulo:CODIGO}
     * (V192). Una clave {@code modulo.*:CODIGO} vale por cualquier submódulo del módulo.
     */
    public boolean puedeEspecial(Integer usuarioId, String claveCompleta) {
        PermisosUsuarioDto p = efectivos(usuarioId);
        if (claveCompleta == null) return false;
        int dos = claveCompleta.indexOf(':');
        if (dos > 0 && claveCompleta.substring(0, dos).endsWith(".*")) {
            String modulo = claveCompleta.substring(0, dos - 1);
            String codigo = claveCompleta.substring(dos);
            return p.getEspeciales().stream().anyMatch(e -> e.startsWith(modulo) && e.endsWith(codigo));
        }
        return p.getEspeciales().contains(claveCompleta);
    }

    /**
     * La sede que puede usar. {@code pedida} null = la de su sesión si no tiene todas
     * (o null = todas si las tiene). Una sede que no es suya → 403.
     */
    public Long sedePermitida(Integer usuarioId, Long pedida, Long deLaSesion) {
        PermisosUsuarioDto p = efectivos(usuarioId);
        if (p.isTodasLasSedes()) return pedida;
        if (pedida == null) return deLaSesion;
        if (!p.getSucursales().contains(pedida)) {
            throw new com.cloud_technological.aura_pos.utils.GlobalException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "Su perfil solo le permite trabajar en sus sedes asignadas");
        }
        return pedida;
    }

    public void invalidarUsuario(Integer usuarioId) {
        if (usuarioId != null) cache.remove(usuarioId);
    }

    public void invalidarTodo() {
        cache.clear();
    }

    private PermisosUsuarioDto calcular(Integer usuarioId) {
        PermisosUsuarioDto dto = new PermisosUsuarioDto();
        dto.setUsuarioId(usuarioId);
        UsuarioPerfil u = query.usuario(usuarioId);
        if (u == null) return dto;
        dto.setRol(u.rol());

        if (PoliticaRoles.PLATFORM_ADMIN.equals(u.rol())) {
            dto.setAccesoTotal(true);
            for (SubmoduloHabilitado s : query.todosActivos()) dto.getPermisos().put(s.clave(), TODAS);
            return dto;
        }
        dto.setSucursales(new ArrayList<>(nulo(query.sucursalesUsuario(usuarioId))));
        if (u.empresaId() == null) return dto;

        Perfil perfil = resolverPerfil(u);
        if (perfil != null) {
            dto.setPerfilId(perfil.id());
            dto.setPerfilNombre(perfil.nombre());
            dto.setAccesoTotal(perfil.accesoTotal());
        }

        Map<Long, Boolean[]> base = perfil != null && !perfil.accesoTotal()
                ? query.permisosPerfil(perfil.id()) : Map.of();
        Map<Long, Boolean[]> excepciones = query.excepcionesUsuario(usuarioId);

        for (SubmoduloHabilitado s : query.habilitadosEmpresa(u.empresaId())) {
            Boolean[] delPerfil = perfil == null ? null
                    : perfil.accesoTotal() ? new Boolean[] { true, true, true, true } : base.get(s.id());
            Boolean[] exc = excepciones.get(s.id());
            List<String> acciones = new ArrayList<>(4);
            AccionPermiso[] orden = AccionPermiso.values();
            for (int i = 0; i < orden.length; i++) {
                boolean v = exc != null && exc[i] != null ? exc[i]
                        : delPerfil != null && Boolean.TRUE.equals(delPerfil[i]);
                if (v) acciones.add(orden[i].name());
            }
            if (PoliticaRoles.SUPER_ADMIN.equals(u.rol()) && CLAVES_SUPER_ADMIN.contains(s.clave())) {
                acciones = TODAS;
            }
            if (!acciones.isEmpty()) dto.getPermisos().put(s.clave(), List.copyOf(acciones));
        }
        especiales(dto, perfil, usuarioId, u.empresaId());
        limites(dto, perfil, usuarioId);
        return dto;
    }

    /**
     * Acciones especiales (V192): excepción del usuario → fila explícita del perfil →
     * acceso total → hereda de la acción base ya calculada. Nunca sin ver el submódulo.
     */
    private void especiales(PermisosUsuarioDto dto, Perfil perfil, Integer usuarioId, Integer empresaId) {
        List<AccionEspecial> catalogo = nulo(query.especialesEmpresa(empresaId));
        if (catalogo.isEmpty()) return;
        Map<Long, Boolean> delPerfil = perfil != null && !perfil.accesoTotal()
                ? nulo(query.especialesPerfil(perfil.id())) : Map.of();
        Map<Long, Boolean> delUsuario = nulo(query.especialesUsuario(usuarioId));
        for (AccionEspecial a : catalogo) {
            List<String> base = dto.getPermisos().get(a.clave());
            if (base == null) continue;
            boolean v;
            if (delUsuario.containsKey(a.id())) v = delUsuario.get(a.id());
            else if (delPerfil.containsKey(a.id())) v = delPerfil.get(a.id());
            else if (perfil != null && perfil.accesoTotal()) v = true;
            else v = a.heredaDe() != null && base.contains(a.heredaDe());
            if (v) dto.getEspeciales().add(a.claveCompleta());
        }
    }

    /** Límites de descuento/precio (el del usuario reemplaza al del perfil) y alcance por sede. */
    private void limites(PermisosUsuarioDto dto, Perfil perfil, Integer usuarioId) {
        LimitesPerfil lp = perfil != null ? query.limitesPerfil(perfil.id()) : null;
        LimitesUsuario lu = query.limitesUsuario(usuarioId);
        // Acceso total: sin límites y todas las sedes, como el Administrador de siempre.
        boolean total = perfil != null && perfil.accesoTotal();
        dto.setDescuentoMaxPct(lu != null && lu.descuentoMaxPct() != null ? lu.descuentoMaxPct()
                : total || lp == null ? null : lp.descuentoMaxPct());
        dto.setRebajaPrecioMaxPct(lu != null && lu.rebajaPrecioMaxPct() != null ? lu.rebajaPrecioMaxPct()
                : total || lp == null ? null : lp.rebajaPrecioMaxPct());
        dto.setTodasLasSedes(total || lp == null || lp.todasSedes());
    }

    private static <T> List<T> nulo(List<T> l) {
        return l != null ? l : List.of();
    }

    private static <K, V> Map<K, V> nulo(Map<K, V> m) {
        return m != null ? m : Map.of();
    }

    /**
     * El perfil asignado si es de su empresa y está activo; si no tiene, el de
     * sistema de su rol (se crea si la empresa es posterior a V190). Un perfil
     * asignado pero desactivado deja al usuario sin permisos: desactivarlo es
     * justamente para eso.
     */
    private Perfil resolverPerfil(UsuarioPerfil u) {
        if (u.perfilId() != null) {
            Perfil p = query.perfil(u.perfilId());
            return p != null && p.activo() && u.empresaId().equals(p.empresaId()) ? p : null;
        }
        String codigo = PerfilesSistema.codigoPorRol(u.rol());
        if (codigo == null) return null;
        Long id = query.perfilIdPorCodigo(u.empresaId(), codigo);
        if (id == null) {
            try {
                perfilesSistema.asegurar(u.empresaId());
            } catch (DataIntegrityViolationException carrera) {
                // Otra petición los creó al mismo tiempo: basta con volver a leer.
            }
            id = query.perfilIdPorCodigo(u.empresaId(), codigo);
        }
        return id != null ? query.perfil(id) : null;
    }

    private record Entrada(PermisosUsuarioDto permisos, long expira) {
    }
}
