package com.cloud_technological.aura_pos.services.permisos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.AccionEspecialDto;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.BloqueoLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.EspecialValor;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.CambioLog;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.GuardarExcepciones;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.GuardarPerfil;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.NodoArbol;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PerfilDetalle;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PerfilFila;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.Permiso;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PermisoExcepcion;
import com.cloud_technological.aura_pos.dto.permisos.PerfilesDtos.PermisosDeUsuario;
import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.entity.PerfilAccionEspecialEntity;
import com.cloud_technological.aura_pos.entity.PerfilEntity;
import com.cloud_technological.aura_pos.entity.PerfilPermisoEntity;
import com.cloud_technological.aura_pos.entity.PermisoCambioLogEntity;
import com.cloud_technological.aura_pos.entity.UsuarioAccionEspecialEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.UsuarioPermisoEntity;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilAccionEspecialJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.UsuarioAccionEspecialJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.AccionEspecial;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoCambioLogJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.Perfil;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository.UsuarioPerfil;
import com.cloud_technological.aura_pos.repositories.permisos.UsuarioPermisoJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;

import lombok.RequiredArgsConstructor;

/**
 * Perfiles de permisos de la empresa y excepciones por usuario (fase P1 de
 * docs/PLAN_PERMISOS.md).
 *
 * <p>Reglas:
 * <ul>
 * <li><b>Nadie da más de lo que tiene</b>: quien edita solo puede otorgar
 * acciones que él mismo tiene; un perfil de acceso total solo lo maneja quien
 * tiene acceso total.</li>
 * <li>El perfil Administrador no se edita (da todo): para limitarlo, se duplica.</li>
 * <li>Los perfiles de sistema no se borran ni se desactivan.</li>
 * <li>Solo un SUPER_ADMIN toca los permisos de otro SUPER_ADMIN.</li>
 * <li>Crear, editar o anular implica ver.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private static final List<String> ACCIONES = List.of("VER", "CREAR", "EDITAR", "ANULAR");

    private final PermisoUsuarioQueryRepository query;
    private final PerfilJPARepository perfilRepo;
    private final PerfilPermisoJPARepository perfilPermisoRepo;
    private final UsuarioPermisoJPARepository usuarioPermisoRepo;
    private final PermisoCambioLogJPARepository logRepo;
    private final PermisoUsuarioService permisos;
    private final PerfilesSistemaService perfilesSistema;
    private final PerfilAccionEspecialJPARepository perfilEspecialRepo;
    private final UsuarioAccionEspecialJPARepository usuarioEspecialRepo;
    private final UsuarioJPARepository usuarioRepo;

    // ── Consultas ────────────────────────────────────────────────────────────

    public List<PerfilFila> listar(Integer empresaId) {
        perfilesSistema.asegurar(empresaId);
        return query.perfilesEmpresa(empresaId);
    }

    public List<NodoArbol> arbol(Integer empresaId) {
        return query.arbolEmpresa(empresaId);
    }

    public PerfilDetalle detalle(Long perfilId, Integer empresaId) {
        PerfilFila fila = filaDeLaEmpresa(perfilId, empresaId);
        PerfilDetalle d = new PerfilDetalle();
        d.setPerfil(fila);
        if (!fila.isAccesoTotal()) {
            d.setPermisos(aLista(query.permisosPerfil(perfilId)));
            d.setEspeciales(valores(query.especialesPerfil(perfilId)));
        }
        return d;
    }

    /** Acciones especiales de los submódulos que la empresa tiene (V192), para la matriz. */
    public List<AccionEspecialDto> especiales(Integer empresaId) {
        List<AccionEspecialDto> out = new ArrayList<>();
        for (AccionEspecial a : query.especialesEmpresa(empresaId)) {
            AccionEspecialDto x = new AccionEspecialDto();
            x.setId(a.id());
            x.setSubmoduloId(a.submoduloId());
            x.setClave(a.clave());
            x.setCodigo(a.codigo());
            x.setNombre(a.nombre());
            x.setDescripcion(a.descripcion());
            x.setHeredaDe(a.heredaDe());
            out.add(x);
        }
        return out;
    }

    public List<CambioLog> historial(Integer empresaId) {
        return query.historial(empresaId, 300);
    }

    public List<BloqueoLog> bloqueos(Integer empresaId, Integer dias) {
        int d = dias != null && dias > 0 && dias <= 90 ? dias : 7;
        return query.bloqueos(empresaId, LocalDate.now().minusDays(d));
    }

    // ── Perfiles ─────────────────────────────────────────────────────────────

    @Transactional
    public PerfilDetalle crear(GuardarPerfil req, Integer empresaId, Integer editorId) {
        String nombre = nombreValido(req.getNombre(), empresaId, null);
        boolean total = Boolean.TRUE.equals(req.getAccesoTotal());
        PermisosUsuarioDto editor = permisos.efectivos(editorId);
        if (total) exigirAccesoTotal(editor, "crear un perfil de acceso total");

        PerfilEntity p = new PerfilEntity();
        p.setEmpresaId(empresaId);
        p.setNombre(nombre);
        p.setDescripcion(limpiar(req.getDescripcion(), 300));
        p.setAccesoTotal(total);
        p.setEsSistema(false);
        p.setActivo(req.getActivo() == null || req.getActivo());
        aplicarLimites(p, req, editor, new ArrayList<>());
        p = perfilRepo.save(p);

        int filas = total ? 0 : reemplazarPermisos(p.getId(), req.getPermisos(), empresaId, editor);
        if (!total) reemplazarEspeciales(p.getId(), req.getEspeciales(), empresaId, editorId);
        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_PERFIL, p.getId(), null,
                "Creó el perfil '" + nombre + "'" + (total ? " con acceso total" : " con " + filas + " submódulos"));
        permisos.invalidarTodo();
        return detalle(p.getId(), empresaId);
    }

    @Transactional
    public PerfilDetalle actualizar(Long perfilId, GuardarPerfil req, Integer empresaId, Integer editorId) {
        PerfilEntity p = entidadDeLaEmpresa(perfilId, empresaId);
        if (PerfilesSistema.ADMINISTRADOR.equals(p.getCodigo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El perfil Administrador da todo lo que la empresa tiene y no se edita. "
                            + "Para uno con menos acceso, duplíquelo y quite lo que no quiera");
        }
        PermisosUsuarioDto editor = permisos.efectivos(editorId);
        boolean total = Boolean.TRUE.equals(req.getAccesoTotal());
        if (total || Boolean.TRUE.equals(p.getAccesoTotal())) {
            exigirAccesoTotal(editor, "modificar un perfil de acceso total");
        }
        if (Boolean.TRUE.equals(p.getEsSistema()) && Boolean.FALSE.equals(req.getActivo())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un perfil del sistema no se puede desactivar");
        }

        List<String> cambios = new ArrayList<>();
        String nombre = Boolean.TRUE.equals(p.getEsSistema()) ? p.getNombre()
                : nombreValido(req.getNombre(), empresaId, perfilId);
        if (!nombre.equals(p.getNombre())) cambios.add("nombre '" + p.getNombre() + "' → '" + nombre + "'");
        p.setNombre(nombre);
        p.setDescripcion(limpiar(req.getDescripcion(), 300));
        if (total != Boolean.TRUE.equals(p.getAccesoTotal())) cambios.add(total ? "pasó a acceso total" : "dejó de ser de acceso total");
        p.setAccesoTotal(total);
        if (req.getActivo() != null && !req.getActivo().equals(p.getActivo())) {
            cambios.add(req.getActivo() ? "activado" : "desactivado");
            p.setActivo(req.getActivo());
        }
        aplicarLimites(p, req, editor, cambios);
        perfilRepo.save(p);

        Map<Long, Boolean[]> antes = query.permisosPerfil(perfilId);
        int filas = reemplazarPermisos(perfilId, total ? List.of() : req.getPermisos(), empresaId, editor);
        if (!total) cambios.add(resumenCambio(antes, query.permisosPerfil(perfilId), empresaId) + " (" + filas + " submódulos)");
        Map<Long, Boolean> especialesAntes = query.especialesPerfil(perfilId);
        reemplazarEspeciales(perfilId, total ? List.of() : req.getEspeciales(), empresaId, editorId);
        String esp = resumenEspeciales(especialesAntes, query.especialesPerfil(perfilId), empresaId);
        if (esp != null) cambios.add(esp);

        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_PERFIL, perfilId, null,
                "Editó el perfil '" + nombre + "': " + String.join("; ", cambios));
        permisos.invalidarTodo();
        return detalle(perfilId, empresaId);
    }

    /** Copia un perfil para ajustarlo. Duplicar el Administrador da un perfil con todo marcado, editable. */
    @Transactional
    public PerfilDetalle duplicar(Long perfilId, Integer empresaId, Integer editorId) {
        PerfilEntity origen = entidadDeLaEmpresa(perfilId, empresaId);
        PermisosUsuarioDto editor = permisos.efectivos(editorId);

        String base = origen.getNombre() + " (copia)";
        String nombre = base;
        for (int i = 2; query.nombreEnUso(empresaId, nombre, null); i++) nombre = base + " " + i;

        PerfilEntity copia = new PerfilEntity();
        copia.setEmpresaId(empresaId);
        copia.setNombre(nombre.length() > 80 ? nombre.substring(0, 80) : nombre);
        copia.setDescripcion(origen.getDescripcion());
        copia.setAccesoTotal(false);
        copia.setEsSistema(false);
        copia.setActivo(true);
        copia.setDescuentoMaxPct(origen.getDescuentoMaxPct());
        copia.setRebajaPrecioMaxPct(origen.getRebajaPrecioMaxPct());
        copia.setTodasSedes(!Boolean.FALSE.equals(origen.getTodasSedes()));
        copia = perfilRepo.save(copia);

        List<Permiso> lista;
        if (Boolean.TRUE.equals(origen.getAccesoTotal())) {
            lista = new ArrayList<>();
            for (NodoArbol n : query.arbolEmpresa(empresaId)) lista.add(todo(n.getSubmoduloId()));
        } else {
            lista = aLista(query.permisosPerfil(perfilId));
        }
        reemplazarPermisos(copia.getId(), lista, empresaId, editor);
        List<EspecialValor> especiales = new ArrayList<>();
        if (Boolean.TRUE.equals(origen.getAccesoTotal())) {
            for (AccionEspecial a : query.especialesEmpresa(empresaId)) especiales.add(valor(a.id(), true));
        } else {
            especiales = valores(query.especialesPerfil(perfilId));
        }
        reemplazarEspeciales(copia.getId(), especiales, empresaId, editorId);
        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_PERFIL, copia.getId(), null,
                "Duplicó '" + origen.getNombre() + "' como '" + copia.getNombre() + "'");
        return detalle(copia.getId(), empresaId);
    }

    @Transactional
    public void eliminar(Long perfilId, Integer empresaId, Integer editorId) {
        PerfilFila fila = filaDeLaEmpresa(perfilId, empresaId);
        if (fila.isEsSistema()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un perfil del sistema no se puede eliminar");
        }
        if (fila.getUsuarios() > 0) {
            throw new GlobalException(HttpStatus.CONFLICT, "El perfil lo tienen " + fila.getUsuarios()
                    + " usuario(s): asígneles otro perfil antes de eliminarlo");
        }
        if (fila.isAccesoTotal()) exigirAccesoTotal(permisos.efectivos(editorId), "eliminar un perfil de acceso total");
        for (Long id : query.filasPerfil(perfilId).values()) perfilPermisoRepo.deleteById(id);
        perfilRepo.deleteById(perfilId);
        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_PERFIL, null, null,
                "Eliminó el perfil '" + fila.getNombre() + "'");
        permisos.invalidarTodo();
    }

    // ── Asignación a usuarios ────────────────────────────────────────────────

    /**
     * Perfil a guardar en un usuario que se crea o se edita.
     *
     * @param solicitado  perfil elegido en el formulario; null = el que corresponda
     * @param rolNuevo    rol con el que queda el usuario
     * @param rolAnterior rol que tenía (null si se está creando)
     * @param actual      perfil que tenía (null si se está creando)
     */
    public Long perfilParaAsignar(Integer empresaId, Integer editorId, Long solicitado, String rolNuevo,
            String rolAnterior, Long actual) {
        if (PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(rolNuevo)) return null;
        if (solicitado != null) {
            if (solicitado.equals(actual)) return actual;
            Perfil p = query.perfil(solicitado);
            if (p == null || !empresaId.equals(p.empresaId())) {
                throw new GlobalException(HttpStatus.NOT_FOUND, "Perfil no encontrado");
            }
            if (!p.activo()) throw new GlobalException(HttpStatus.BAD_REQUEST, "El perfil '" + p.nombre() + "' está inactivo");
            PermisosUsuarioDto editor = permisos.efectivos(editorId);
            if (p.accesoTotal()) {
                exigirAccesoTotal(editor, "asignar un perfil de acceso total");
            } else {
                exigirTecho(editor, aLista(query.permisosPerfil(p.id())), empresaId);
            }
            return p.id();
        }
        // Sin elegir: al crear, el de sistema del rol; al cambiar de rol, si tenía
        // el de sistema del rol anterior, pasa al del rol nuevo. Si no, se conserva.
        boolean cambioRol = rolAnterior != null && !rolAnterior.equalsIgnoreCase(rolNuevo);
        if (actual != null && !(cambioRol && esDeSistemaDelRol(actual, empresaId, rolAnterior))) return actual;
        String codigo = PerfilesSistema.codigoPorRol(rolNuevo);
        if (codigo == null) return null;
        perfilesSistema.asegurar(empresaId);
        return query.perfilIdPorCodigo(empresaId, codigo);
    }

    /** Deja constancia del cambio de perfil de un usuario y refresca su caché. */
    public void registrarAsignacion(Integer empresaId, Integer editorId, Integer usuarioId, Long anterior, Long nuevo) {
        permisos.invalidarUsuario(usuarioId);
        if (nuevo == null ? anterior == null : nuevo.equals(anterior)) return;
        String de = anterior != null && query.perfil(anterior) != null ? query.perfil(anterior).nombre() : "ninguno";
        String a = nuevo != null && query.perfil(nuevo) != null ? query.perfil(nuevo).nombre() : "ninguno";
        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_USUARIO, nuevo, usuarioId,
                "Perfil: " + de + " → " + a);
    }

    // ── Excepciones por usuario ──────────────────────────────────────────────

    public PermisosDeUsuario permisosDeUsuario(Integer usuarioId, Integer empresaId) {
        UsuarioPerfil u = usuarioDeLaEmpresa(usuarioId, empresaId);
        PermisosDeUsuario r = new PermisosDeUsuario();
        r.setUsuarioId(usuarioId);
        r.setUsername(query.username(usuarioId));
        r.setRol(u.rol());
        PermisosUsuarioDto efectivos = permisos.efectivos(usuarioId);
        r.setPerfilId(efectivos.getPerfilId());
        r.setPerfilNombre(efectivos.getPerfilNombre());
        r.setPerfilAccesoTotal(efectivos.isAccesoTotal());
        if (efectivos.getPerfilId() != null && !efectivos.isAccesoTotal()) {
            r.setDelPerfil(aLista(query.permisosPerfil(efectivos.getPerfilId())));
        }
        for (Map.Entry<Long, Boolean[]> e : query.excepcionesUsuario(usuarioId).entrySet()) {
            PermisoExcepcion x = new PermisoExcepcion();
            x.setSubmoduloId(e.getKey());
            x.setVer(e.getValue()[0]);
            x.setCrear(e.getValue()[1]);
            x.setEditar(e.getValue()[2]);
            x.setAnular(e.getValue()[3]);
            r.getExcepciones().add(x);
        }
        r.setEspecialesEfectivos(efectivos.getEspeciales());
        r.setEspecialesDelPerfil(especialesDelPerfil(efectivos, usuarioId, empresaId));
        r.setExcepcionesEspeciales(valores(query.especialesUsuario(usuarioId)));
        if (efectivos.getPerfilId() != null && !efectivos.isAccesoTotal()) {
            PermisoUsuarioQueryRepository.LimitesPerfil lp = query.limitesPerfil(efectivos.getPerfilId());
            if (lp != null) {
                r.setPerfilDescuentoMaxPct(lp.descuentoMaxPct());
                r.setPerfilRebajaPrecioMaxPct(lp.rebajaPrecioMaxPct());
                r.setPerfilTodasSedes(lp.todasSedes());
            }
        }
        PermisoUsuarioQueryRepository.LimitesUsuario lu = query.limitesUsuario(usuarioId);
        if (lu != null) {
            r.setDescuentoMaxPct(lu.descuentoMaxPct());
            r.setRebajaPrecioMaxPct(lu.rebajaPrecioMaxPct());
        }
        return r;
    }

    /** Lo que le daría el perfil sin las excepciones de acciones especiales del usuario. */
    private List<String> especialesDelPerfil(PermisosUsuarioDto efectivos, Integer usuarioId, Integer empresaId) {
        Map<Long, Boolean> exc = query.especialesUsuario(usuarioId);
        if (exc.isEmpty()) return efectivos.getEspeciales();
        List<String> out = new ArrayList<>(efectivos.getEspeciales());
        Map<Long, Boolean> delPerfil = efectivos.getPerfilId() != null && !efectivos.isAccesoTotal()
                ? query.especialesPerfil(efectivos.getPerfilId()) : Map.of();
        for (AccionEspecial a : query.especialesEmpresa(empresaId)) {
            if (!exc.containsKey(a.id())) continue;
            List<String> base = efectivos.getPermisos().get(a.clave());
            boolean v = base != null && (delPerfil.containsKey(a.id()) ? delPerfil.get(a.id())
                    : efectivos.isAccesoTotal() || (a.heredaDe() != null && base.contains(a.heredaDe())));
            out.remove(a.claveCompleta());
            if (v) out.add(a.claveCompleta());
        }
        return out;
    }

    @Transactional
    public PermisosDeUsuario guardarExcepciones(Integer usuarioId, GuardarExcepciones req, Integer empresaId,
            Integer editorId, String rolEditor) {
        UsuarioPerfil u = usuarioDeLaEmpresa(usuarioId, empresaId);
        if (PoliticaRoles.SUPER_ADMIN.equalsIgnoreCase(u.rol()) && !PoliticaRoles.esPrivilegiado(rolEditor)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "Solo un SUPER_ADMIN cambia los permisos de otro SUPER_ADMIN");
        }
        Set<Long> habilitados = idsHabilitados(empresaId);
        PermisosUsuarioDto editor = permisos.efectivos(editorId);

        // Se valida antes de tocar nada: solo lo que se da (true) tiene techo.
        List<Permiso> otorgados = new ArrayList<>();
        Map<Long, PermisoExcepcion> limpias = new HashMap<>();
        for (PermisoExcepcion x : req.getExcepciones() != null ? req.getExcepciones() : List.<PermisoExcepcion>of()) {
            if (x.getSubmoduloId() == null || !habilitados.contains(x.getSubmoduloId())) continue;
            if (x.getVer() == null && x.getCrear() == null && x.getEditar() == null && x.getAnular() == null) continue;
            // Dar crear/editar/anular implica ver; quitar ver quita todo.
            if (Boolean.TRUE.equals(x.getCrear()) || Boolean.TRUE.equals(x.getEditar()) || Boolean.TRUE.equals(x.getAnular())) {
                if (x.getVer() == null) x.setVer(true);
            }
            if (Boolean.FALSE.equals(x.getVer())) {
                x.setCrear(false);
                x.setEditar(false);
                x.setAnular(false);
            }
            limpias.put(x.getSubmoduloId(), x);
            Permiso dado = new Permiso();
            dado.setSubmoduloId(x.getSubmoduloId());
            dado.setVer(Boolean.TRUE.equals(x.getVer()));
            dado.setCrear(Boolean.TRUE.equals(x.getCrear()));
            dado.setEditar(Boolean.TRUE.equals(x.getEditar()));
            dado.setAnular(Boolean.TRUE.equals(x.getAnular()));
            otorgados.add(dado);
        }
        exigirTecho(editor, otorgados, empresaId);

        Map<Long, Long> filas = query.filasExcepciones(usuarioId);
        for (Map.Entry<Long, Long> f : filas.entrySet()) {
            if (!limpias.containsKey(f.getKey())) usuarioPermisoRepo.deleteById(f.getValue());
        }
        for (PermisoExcepcion x : limpias.values()) {
            Long id = filas.get(x.getSubmoduloId());
            UsuarioPermisoEntity e = id != null ? usuarioPermisoRepo.findById(id).orElse(new UsuarioPermisoEntity())
                    : new UsuarioPermisoEntity();
            e.setUsuarioId(usuarioId);
            e.setSubmoduloId(x.getSubmoduloId());
            e.setVer(x.getVer());
            e.setCrear(x.getCrear());
            e.setEditar(x.getEditar());
            e.setAnular(x.getAnular());
            if (e.getId() == null) e.setCreatedBy(editorId);
            usuarioPermisoRepo.save(e);
        }
        int especiales = guardarEspecialesUsuario(usuarioId, req.getEspeciales(), empresaId, editorId);
        String limites = guardarLimitesUsuario(usuarioId, req, editor);
        log(empresaId, editorId, PermisoCambioLogEntity.TIPO_USUARIO, null, usuarioId,
                "Excepciones: " + limpias.size() + " submódulo(s) y " + especiales
                        + " acción(es) especial(es) con ajustes sobre su perfil" + (limites != null ? "; " + limites : ""));
        permisos.invalidarUsuario(usuarioId);
        return permisosDeUsuario(usuarioId, empresaId);
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    /** Borra y vuelve a escribir los permisos del perfil. Devuelve cuántos submódulos quedaron. */
    private int reemplazarPermisos(Long perfilId, List<Permiso> lista, Integer empresaId, PermisosUsuarioDto editor) {
        Set<Long> habilitados = idsHabilitados(empresaId);
        Map<Long, Permiso> limpios = new HashMap<>();
        for (Permiso x : lista != null ? lista : List.<Permiso>of()) {
            if (x.getSubmoduloId() == null || !habilitados.contains(x.getSubmoduloId())) continue;
            if (x.isCrear() || x.isEditar() || x.isAnular()) x.setVer(true);
            if (!x.isVer()) continue;
            limpios.put(x.getSubmoduloId(), x);
        }
        exigirTecho(editor, new ArrayList<>(limpios.values()), empresaId);

        Map<Long, Long> filas = query.filasPerfil(perfilId);
        for (Map.Entry<Long, Long> f : filas.entrySet()) {
            if (!limpios.containsKey(f.getKey())) perfilPermisoRepo.deleteById(f.getValue());
        }
        for (Permiso x : limpios.values()) {
            Long id = filas.get(x.getSubmoduloId());
            PerfilPermisoEntity e = id != null ? perfilPermisoRepo.findById(id).orElse(new PerfilPermisoEntity())
                    : new PerfilPermisoEntity();
            e.setPerfilId(perfilId);
            e.setSubmoduloId(x.getSubmoduloId());
            e.setVer(true);
            e.setCrear(x.isCrear());
            e.setEditar(x.isEditar());
            e.setAnular(x.isAnular());
            perfilPermisoRepo.save(e);
        }
        return limpios.size();
    }

    // ── Acciones especiales y límites (V192) ─────────────────────────────────

    /** Escribe lo explícito del perfil; {@code permitido} null = heredar (se borra la fila). */
    private void reemplazarEspeciales(Long perfilId, List<EspecialValor> lista, Integer empresaId, Integer editorId) {
        Map<Long, Boolean> pedidos = pedidosValidos(lista, empresaId, editorId);
        Map<Long, Long> filas = query.filasEspecialesPerfil(perfilId);
        for (Map.Entry<Long, Long> f : filas.entrySet()) {
            if (!pedidos.containsKey(f.getKey())) perfilEspecialRepo.deleteById(f.getValue());
        }
        for (Map.Entry<Long, Boolean> v : pedidos.entrySet()) {
            Long id = filas.get(v.getKey());
            PerfilAccionEspecialEntity e = id != null
                    ? perfilEspecialRepo.findById(id).orElse(new PerfilAccionEspecialEntity())
                    : new PerfilAccionEspecialEntity();
            e.setPerfilId(perfilId);
            e.setAccionId(v.getKey());
            e.setPermitido(v.getValue());
            perfilEspecialRepo.save(e);
        }
    }

    private int guardarEspecialesUsuario(Integer usuarioId, List<EspecialValor> lista, Integer empresaId,
            Integer editorId) {
        Map<Long, Boolean> pedidos = pedidosValidos(lista, empresaId, editorId);
        Map<Long, Long> filas = query.filasEspecialesUsuario(usuarioId);
        for (Map.Entry<Long, Long> f : filas.entrySet()) {
            if (!pedidos.containsKey(f.getKey())) usuarioEspecialRepo.deleteById(f.getValue());
        }
        for (Map.Entry<Long, Boolean> v : pedidos.entrySet()) {
            Long id = filas.get(v.getKey());
            UsuarioAccionEspecialEntity e = id != null
                    ? usuarioEspecialRepo.findById(id).orElse(new UsuarioAccionEspecialEntity())
                    : new UsuarioAccionEspecialEntity();
            e.setUsuarioId(usuarioId);
            e.setAccionId(v.getKey());
            e.setPermitido(v.getValue());
            if (e.getId() == null) e.setCreatedBy(editorId);
            usuarioEspecialRepo.save(e);
        }
        return pedidos.size();
    }

    /** Solo acciones del catálogo de la empresa; dar una exige tenerla (nadie da más de lo que tiene). */
    private Map<Long, Boolean> pedidosValidos(List<EspecialValor> lista, Integer empresaId, Integer editorId) {
        Map<Long, AccionEspecial> catalogo = catalogo(empresaId);
        Map<Long, Boolean> pedidos = new HashMap<>();
        for (EspecialValor v : lista != null ? lista : List.<EspecialValor>of()) {
            if (v.getAccionId() == null || v.getPermitido() == null) continue;
            AccionEspecial a = catalogo.get(v.getAccionId());
            if (a == null) continue;
            if (v.getPermitido() && !permisos.puedeEspecial(editorId, a.claveCompleta())) {
                throw new GlobalException(HttpStatus.FORBIDDEN, "No puede dar '" + a.nombre() + "': usted no la tiene");
            }
            pedidos.put(v.getAccionId(), v.getPermitido());
        }
        return pedidos;
    }

    /** Límites y sedes del perfil, con techo: nadie da más de lo que tiene. */
    private void aplicarLimites(PerfilEntity p, GuardarPerfil req, PermisosUsuarioDto editor, List<String> cambios) {
        BigDecimal desc = porcentaje(req.getDescuentoMaxPct(), "descuento máximo");
        BigDecimal rebaja = porcentaje(req.getRebajaPrecioMaxPct(), "rebaja máxima de precio");
        boolean todas = req.getTodasSedes() == null || req.getTodasSedes();
        if (Boolean.TRUE.equals(req.getAccesoTotal())) {
            // Acceso total = sin límites y todas las sedes, como el Administrador.
            desc = null;
            rebaja = null;
            todas = true;
        }
        exigirTechoLimite(editor.getDescuentoMaxPct(), desc, "descuento");
        exigirTechoLimite(editor.getRebajaPrecioMaxPct(), rebaja, "rebaja de precio");
        boolean antes = !Boolean.FALSE.equals(p.getTodasSedes());
        if (todas && !editor.isTodasLasSedes() && (p.getId() == null || !antes)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No puede dar todas las sedes: usted no las tiene");
        }
        if (!igual(desc, p.getDescuentoMaxPct())) {
            cambios.add("descuento máximo " + txt(p.getDescuentoMaxPct()) + " → " + txt(desc));
        }
        if (!igual(rebaja, p.getRebajaPrecioMaxPct())) {
            cambios.add("rebaja máxima " + txt(p.getRebajaPrecioMaxPct()) + " → " + txt(rebaja));
        }
        if (p.getId() != null && todas != antes) cambios.add(todas ? "pasó a todas las sedes" : "solo sus sedes");
        p.setDescuentoMaxPct(desc);
        p.setRebajaPrecioMaxPct(rebaja);
        p.setTodasSedes(todas);
    }

    /** Límites propios del usuario (null = los del perfil); devuelve el resumen del cambio o null. */
    private String guardarLimitesUsuario(Integer usuarioId, GuardarExcepciones req, PermisosUsuarioDto editor) {
        UsuarioEntity u = usuarioRepo.findById(usuarioId).orElse(null);
        if (u == null) return null;
        BigDecimal desc = porcentaje(req.getDescuentoMaxPct(), "descuento máximo");
        BigDecimal rebaja = porcentaje(req.getRebajaPrecioMaxPct(), "rebaja máxima de precio");
        if (desc != null) exigirTechoLimite(editor.getDescuentoMaxPct(), desc, "descuento");
        if (rebaja != null) exigirTechoLimite(editor.getRebajaPrecioMaxPct(), rebaja, "rebaja de precio");
        List<String> cambios = new ArrayList<>();
        if (!igual(desc, u.getDescuentoMaxPct())) {
            cambios.add("descuento máximo propio " + txt(u.getDescuentoMaxPct()) + " → " + txt(desc));
        }
        if (!igual(rebaja, u.getRebajaPrecioMaxPct())) {
            cambios.add("rebaja máxima propia " + txt(u.getRebajaPrecioMaxPct()) + " → " + txt(rebaja));
        }
        if (cambios.isEmpty()) return null;
        u.setDescuentoMaxPct(desc);
        u.setRebajaPrecioMaxPct(rebaja);
        usuarioRepo.save(u);
        return String.join("; ", cambios);
    }

    /** Quien edita con límite no puede dar uno mayor ni dejarlo sin límite. */
    private static void exigirTechoLimite(BigDecimal delEditor, BigDecimal dado, String que) {
        if (delEditor == null) return;
        if (dado == null || dado.compareTo(delEditor) > 0) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No puede dar un límite de " + que
                    + " mayor que el suyo (" + delEditor.stripTrailingZeros().toPlainString() + "%)");
        }
    }

    private static BigDecimal porcentaje(BigDecimal v, String que) {
        if (v == null) return null;
        if (v.signum() < 0 || v.compareTo(new BigDecimal("100")) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El " + que + " va de 0 a 100%");
        }
        return v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static boolean igual(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String txt(BigDecimal v) {
        return v == null ? "sin límite" : v.stripTrailingZeros().toPlainString() + "%";
    }

    private Map<Long, AccionEspecial> catalogo(Integer empresaId) {
        Map<Long, AccionEspecial> m = new HashMap<>();
        for (AccionEspecial a : query.especialesEmpresa(empresaId)) m.put(a.id(), a);
        return m;
    }

    private String resumenEspeciales(Map<Long, Boolean> antes, Map<Long, Boolean> despues, Integer empresaId) {
        Map<Long, AccionEspecial> catalogo = catalogo(empresaId);
        List<String> l = new ArrayList<>();
        Set<Long> ids = new HashSet<>(antes.keySet());
        ids.addAll(despues.keySet());
        for (Long id : ids) {
            Boolean a = antes.get(id);
            Boolean d = despues.get(id);
            if (a == null ? d == null : a.equals(d)) continue;
            String nombre = catalogo.containsKey(id) ? catalogo.get(id).nombre() : "#" + id;
            l.add(nombre + ": " + (d == null ? "hereda" : d ? "sí" : "no"));
        }
        return l.isEmpty() ? null : "acciones especiales " + recortar(l);
    }

    private static List<EspecialValor> valores(Map<Long, Boolean> m) {
        List<EspecialValor> l = new ArrayList<>();
        for (Map.Entry<Long, Boolean> e : m.entrySet()) l.add(valor(e.getKey(), e.getValue()));
        return l;
    }

    private static EspecialValor valor(Long accionId, Boolean permitido) {
        EspecialValor v = new EspecialValor();
        v.setAccionId(accionId);
        v.setPermitido(permitido);
        return v;
    }

    /** Quien edita solo puede otorgar lo que él mismo tiene. */
    private void exigirTecho(PermisosUsuarioDto editor, List<Permiso> otorgados, Integer empresaId) {
        if (editor.isAccesoTotal() || otorgados.isEmpty()) return;
        Map<Long, String> claves = new HashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        for (NodoArbol n : query.arbolEmpresa(empresaId)) {
            claves.put(n.getSubmoduloId(), n.getClave());
            nombres.put(n.getSubmoduloId(), n.getModuloNombre() + " › " + n.getNombre());
        }
        for (Permiso x : otorgados) {
            List<String> propias = editor.getPermisos().getOrDefault(claves.get(x.getSubmoduloId()), List.of());
            boolean[] pide = { x.isVer(), x.isCrear(), x.isEditar(), x.isAnular() };
            for (int i = 0; i < pide.length; i++) {
                if (pide[i] && !propias.contains(ACCIONES.get(i))) {
                    throw new GlobalException(HttpStatus.FORBIDDEN, "No puede dar " + ACCIONES.get(i).toLowerCase()
                            + " en " + nombres.getOrDefault(x.getSubmoduloId(), "un submódulo") + ": usted no lo tiene");
                }
            }
        }
    }

    private void exigirAccesoTotal(PermisosUsuarioDto editor, String que) {
        if (!editor.isAccesoTotal()) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "Solo un usuario con acceso total puede " + que);
        }
    }

    private boolean esDeSistemaDelRol(Long perfilId, Integer empresaId, String rol) {
        String codigo = PerfilesSistema.codigoPorRol(rol);
        return codigo != null && perfilId.equals(query.perfilIdPorCodigo(empresaId, codigo));
    }

    private String resumenCambio(Map<Long, Boolean[]> antes, Map<Long, Boolean[]> despues, Integer empresaId) {
        Map<Long, String> nombres = new HashMap<>();
        for (NodoArbol n : query.arbolEmpresa(empresaId)) nombres.put(n.getSubmoduloId(), n.getModuloNombre() + " › " + n.getNombre());
        List<String> dio = new ArrayList<>();
        List<String> quito = new ArrayList<>();
        Set<Long> ids = new HashSet<>(antes.keySet());
        ids.addAll(despues.keySet());
        for (Long id : ids) {
            Boolean[] a = antes.get(id);
            Boolean[] d = despues.get(id);
            for (int i = 0; i < ACCIONES.size(); i++) {
                boolean va = a != null && Boolean.TRUE.equals(a[i]);
                boolean vd = d != null && Boolean.TRUE.equals(d[i]);
                String txt = nombres.getOrDefault(id, "#" + id) + " (" + ACCIONES.get(i).toLowerCase() + ")";
                if (vd && !va) dio.add(txt);
                if (va && !vd) quito.add(txt);
            }
        }
        if (dio.isEmpty() && quito.isEmpty()) return "sin cambios de permisos";
        StringBuilder sb = new StringBuilder();
        if (!dio.isEmpty()) sb.append("dio ").append(recortar(dio));
        if (!quito.isEmpty()) sb.append(sb.length() > 0 ? "; " : "").append("quitó ").append(recortar(quito));
        return sb.toString();
    }

    private static String recortar(List<String> l) {
        return l.size() <= 8 ? String.join(", ", l) : String.join(", ", l.subList(0, 8)) + " y " + (l.size() - 8) + " más";
    }

    private Set<Long> idsHabilitados(Integer empresaId) {
        Set<Long> s = new HashSet<>();
        for (PermisoUsuarioQueryRepository.SubmoduloHabilitado h : query.habilitadosEmpresa(empresaId)) s.add(h.id());
        return s;
    }

    private PerfilFila filaDeLaEmpresa(Long perfilId, Integer empresaId) {
        PerfilFila f = query.perfilFila(perfilId, empresaId);
        if (f == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Perfil no encontrado");
        return f;
    }

    private PerfilEntity entidadDeLaEmpresa(Long perfilId, Integer empresaId) {
        PerfilEntity p = perfilRepo.findById(perfilId).orElse(null);
        if (p == null || !empresaId.equals(p.getEmpresaId())) throw new GlobalException(HttpStatus.NOT_FOUND, "Perfil no encontrado");
        return p;
    }

    private UsuarioPerfil usuarioDeLaEmpresa(Integer usuarioId, Integer empresaId) {
        UsuarioPerfil u = query.usuario(usuarioId);
        if (u == null || !empresaId.equals(u.empresaId()) || PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(u.rol())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
        }
        return u;
    }

    private String nombreValido(String nombre, Integer empresaId, Long exceptoId) {
        String n = limpiar(nombre, 80);
        if (n == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "El nombre del perfil es obligatorio");
        if (query.nombreEnUso(empresaId, n, exceptoId)) {
            throw new GlobalException(HttpStatus.CONFLICT, "Ya existe un perfil llamado '" + n + "'");
        }
        return n;
    }

    private static String limpiar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static List<Permiso> aLista(Map<Long, Boolean[]> m) {
        List<Permiso> l = new ArrayList<>();
        for (Map.Entry<Long, Boolean[]> e : m.entrySet()) {
            Permiso p = new Permiso();
            p.setSubmoduloId(e.getKey());
            p.setVer(Boolean.TRUE.equals(e.getValue()[0]));
            p.setCrear(Boolean.TRUE.equals(e.getValue()[1]));
            p.setEditar(Boolean.TRUE.equals(e.getValue()[2]));
            p.setAnular(Boolean.TRUE.equals(e.getValue()[3]));
            l.add(p);
        }
        return l;
    }

    private static Permiso todo(Long submoduloId) {
        Permiso p = new Permiso();
        p.setSubmoduloId(submoduloId);
        p.setVer(true);
        p.setCrear(true);
        p.setEditar(true);
        p.setAnular(true);
        return p;
    }

    private void log(Integer empresaId, Integer editorId, String tipo, Long perfilId, Integer usuarioAfectado, String detalle) {
        PermisoCambioLogEntity l = new PermisoCambioLogEntity();
        l.setEmpresaId(empresaId);
        l.setUsuarioId(editorId);
        l.setTipo(tipo);
        l.setPerfilId(perfilId);
        l.setUsuarioAfectadoId(usuarioAfectado);
        l.setDetalle(detalle);
        logRepo.save(l);
    }
}
