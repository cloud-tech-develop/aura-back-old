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
import com.cloud_technological.aura_pos.utils.SecurityUtils;

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
 * <p>Se guarda en caché unos minutos por usuario y empresa de la sesión: lo
 * consultan el menú (P2) y cada petición al API (P3). Quien cambie perfiles,
 * excepciones o módulos de la empresa debe llamar a {@link #invalidarUsuario} o
 * {@link #invalidarTodo}.
 *
 * <p>La empresa es la del token, no la del usuario: un usuario de una firma
 * contable trabaja en empresas cliente (docs/PLAN_FIRMA_CONTABLE.md): ahí valen el
 * perfil del vínculo ("Contador externo"), sin las excepciones ni los límites que el
 * usuario tenga en su firma. Sin acceso vigente, una sesión en otra empresa no tiene permisos.
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
    private final SecurityUtils securityUtils;
    private final com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository firmaQuery;

    /** Llave "usuarioId:empresaId" (empresa de la sesión; "-" fuera de una petición). */
    private final Map<String, Entrada> cache = new ConcurrentHashMap<>();

    public PermisosUsuarioDto efectivos(Integer usuarioId) {
        if (usuarioId == null) return new PermisosUsuarioDto();
        Integer empresaSesion = empresaDeLaSesion();
        String llave = usuarioId + ":" + (empresaSesion != null ? empresaSesion : "-");
        Entrada e = cache.get(llave);
        if (e != null && e.expira() > System.currentTimeMillis()) return e.permisos();
        PermisosUsuarioDto calculado = calcular(usuarioId, empresaSesion);
        cache.put(llave, new Entrada(calculado, System.currentTimeMillis() + TTL_MS));
        return calculado;
    }

    /** La empresa del token; null fuera de una petición (jobs, eventos) o si no se puede leer. */
    private Integer empresaDeLaSesion() {
        try {
            return securityUtils.getEmpresaId();
        } catch (Exception ex) {
            return null;
        }
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
        if (usuarioId == null) return;
        String prefijo = usuarioId + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefijo));
    }

    public void invalidarTodo() {
        cache.clear();
    }

    private PermisosUsuarioDto calcular(Integer usuarioId, Integer empresaSesion) {
        PermisosUsuarioDto dto = new PermisosUsuarioDto();
        dto.setUsuarioId(usuarioId);
        UsuarioPerfil u = query.usuario(usuarioId);
        if (u == null) return dto;
        dto.setRol(u.rol());
        // Sesión en una empresa que no es la del usuario: solo con el vínculo de su firma.
        if (empresaSesion != null && u.empresaId() != null && !empresaSesion.equals(u.empresaId())
                && !PoliticaRoles.PLATFORM_ADMIN.equals(u.rol())) {
            return comoContadorExterno(dto, usuarioId, empresaSesion);
        }

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

        llenar(dto, perfil, u.empresaId(), query.excepcionesUsuario(usuarioId),
                PoliticaRoles.SUPER_ADMIN.equals(u.rol()));
        especiales(dto, perfil, usuarioId, u.empresaId());
        limites(dto, perfil, usuarioId);
        return dto;
    }

    /**
     * Usuario de una firma contable en un cliente: el perfil del vínculo y nada más
     * (sus excepciones, especiales y límites son de la firma). Todas las sedes: el
     * cliente no le asigna sedes. Rol {@code CONTADOR_EXTERNO}.
     */
    private PermisosUsuarioDto comoContadorExterno(PermisosUsuarioDto dto, Integer usuarioId, Integer empresaId) {
        var acceso = firmaQuery.acceso(usuarioId, empresaId);
        if (acceso == null) return dto;
        // Cliente gestionado (V200): los administradores de la firma lo administran con el
        // perfil Administrador del cliente. Los demás, con el del vínculo ("Contador externo").
        Long perfilId = acceso.administraElCliente() ? perfilAdministrador(empresaId) : acceso.perfilId();
        if (perfilId == null) return dto;
        Perfil perfil = query.perfil(perfilId);
        if (perfil == null || !perfil.activo() || !empresaId.equals(perfil.empresaId())) return dto;

        dto.setRol(acceso.administraElCliente() ? "ADMIN" : PerfilesSistema.CONTADOR_EXTERNO);
        dto.setPerfilId(perfil.id());
        dto.setPerfilNombre(perfil.nombre());
        dto.setAccesoTotal(perfil.accesoTotal());
        llenar(dto, perfil, empresaId, Map.of(), false);
        especiales(dto, perfil, null, empresaId);
        LimitesPerfil lp = query.limitesPerfil(perfil.id());
        dto.setDescuentoMaxPct(perfil.accesoTotal() || lp == null ? null : lp.descuentoMaxPct());
        dto.setRebajaPrecioMaxPct(perfil.accesoTotal() || lp == null ? null : lp.rebajaPrecioMaxPct());
        dto.setTodasLasSedes(true);
        return dto;
    }

    /** El perfil Administrador (de sistema) de la empresa; se crea si falta. */
    private Long perfilAdministrador(Integer empresaId) {
        Long id = query.perfilIdPorCodigo(empresaId, PerfilesSistema.ADMINISTRADOR);
        if (id == null) {
            try {
                perfilesSistema.asegurar(empresaId);
            } catch (DataIntegrityViolationException carrera) {
                // Otra petición los creó al mismo tiempo: basta con volver a leer.
            }
            id = query.perfilIdPorCodigo(empresaId, PerfilesSistema.ADMINISTRADOR);
        }
        return id;
    }

    /** Acciones por submódulo: perfil (o acceso total) ± excepciones, sobre lo que la empresa tiene activo. */
    private void llenar(PermisosUsuarioDto dto, Perfil perfil, Integer empresaId, Map<Long, Boolean[]> excepciones,
            boolean superAdmin) {
        Map<Long, Boolean[]> base = perfil != null && !perfil.accesoTotal()
                ? query.permisosPerfil(perfil.id()) : Map.of();
        excepciones = nulo(excepciones);

        for (SubmoduloHabilitado s : query.habilitadosEmpresa(empresaId)) {
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
            if (superAdmin && CLAVES_SUPER_ADMIN.contains(s.clave())) {
                acciones = TODAS;
            }
            if (!acciones.isEmpty()) dto.getPermisos().put(s.clave(), List.copyOf(acciones));
        }
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
        // usuarioId null = sin excepciones de usuario (contador externo en un cliente).
        Map<Long, Boolean> delUsuario = usuarioId != null ? nulo(query.especialesUsuario(usuarioId)) : Map.of();
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
