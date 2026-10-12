package com.cloud_technological.aura_pos.services.firma_contable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.EmpresaFirmaClienteEntity;
import com.cloud_technological.aura_pos.entity.PerfilEntity;
import com.cloud_technological.aura_pos.entity.PerfilPermisoEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.EmpresaFirmaClienteJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.EmpresaDisponible;
import com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.VinculoFila;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PerfilPermisoJPARepository;
import com.cloud_technological.aura_pos.repositories.permisos.PermisoUsuarioQueryRepository;
import com.cloud_technological.aura_pos.services.empresa.ConfiguracionEmpresaService;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.services.permisos.PerfilesSistema;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Firma contable ↔ empresa cliente (docs/PLAN_FIRMA_CONTABLE.md, F2).
 *
 * <p>Un cliente tiene como máximo una firma activa. Cambiar de firma desactiva el
 * vínculo anterior (queda la historia) y crea uno nuevo; las asignaciones de usuarios
 * de la firma vieja dejan de valer porque cuelgan del vínculo.
 */
@Service
@RequiredArgsConstructor
public class FirmaContableService {

    private static final String CLAVE_BITACORA = "caja.usuarios";

    private final EmpresaJPARepository empresaRepo;
    private final EmpresaFirmaClienteJPARepository vinculoRepo;
    private final FirmaContableQueryRepository query;
    private final PerfilJPARepository perfilRepo;
    private final PerfilPermisoJPARepository perfilPermisoRepo;
    private final PermisoUsuarioQueryRepository permisoQuery;
    private final ConfiguracionEmpresaService configuracion;
    private final BitacoraService bitacora;
    private final com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository usuarioRepo;
    private final com.cloud_technological.aura_pos.repositories.firma_contable.UsuarioFirmaClienteJPARepository asignacionRepo;
    private final com.cloud_technological.aura_pos.repositories.firma_contable.SolicitudFirmaClienteJPARepository solicitudRepo;

    /** Caché del filtro de cada petición: se limpia al quitar un acceso para cortarlo ya. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.cloud_technological.aura_pos.security.AccesoFirmaService accesoFirma;

    /** Firmas activas para el buscador de la plataforma. */
    public List<EmpresaDisponible> firmas(String texto) {
        return query.firmas(texto);
    }

    /**
     * Marca o desmarca la empresa como firma. No se desmarca mientras atienda
     * clientes, y una empresa que tiene firma no puede ser firma.
     */
    @Transactional
    public void marcarFirma(EmpresaEntity empresa, boolean esFirma) {
        boolean actual = Boolean.TRUE.equals(empresa.getEsFirmaContable());
        if (actual == esFirma) return;
        if (esFirma) {
            if (query.vinculoActivoDelCliente(empresa.getId()) != null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Esta empresa tiene una firma contable que la atiende: quítela antes de marcarla como firma");
        } else {
            int clientes = query.clientesDeLaFirma(empresa.getId()).size();
            if (clientes > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La firma atiende " + clientes + " cliente(s): quíteles la firma antes de desmarcarla");
        }
        empresa.setEsFirmaContable(esFirma);
        empresaRepo.save(empresa);
    }

    /**
     * Deja a {@code firmaId} como la firma del cliente; null la quita. Si ya es esa
     * firma no hace nada.
     *
     * @param actorId quien hace el cambio (administrador de plataforma o del cliente)
     */
    @Transactional
    public VinculoFila asignarFirma(Integer clienteId, Integer firmaId, Integer actorId) {
        return vincular(clienteId, firmaId, actorId, EmpresaFirmaClienteEntity.MODALIDAD_CLIENTE_PAGA);
    }

    /** Igual, con la modalidad: CLIENTE_PAGA (el cliente usa Aura) o GESTIONADO (la firma lo creó y lo paga). */
    @Transactional
    public VinculoFila vincular(Integer clienteId, Integer firmaId, Integer actorId, String modalidad) {
        EmpresaEntity cliente = empresaRepo.findById(clienteId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        VinculoFila actual = query.vinculoActivoDelCliente(clienteId);
        if (actual != null && Objects.equals(actual.firmaEmpresaId(), firmaId)) return actual;
        if (actual == null && firmaId == null) return null;

        EmpresaEntity firma = null;
        if (firmaId != null) {
            firma = empresaRepo.findById(firmaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Firma contable no encontrada"));
            if (!Boolean.TRUE.equals(firma.getEsFirmaContable()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, firma.getRazonSocial() + " no es una firma contable");
            if (!Boolean.TRUE.equals(firma.getActiva()))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "La firma contable está suspendida");
            if (firmaId.equals(clienteId))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Una firma no puede ser su propia firma contable");
            if (Boolean.TRUE.equals(cliente.getEsFirmaContable()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Una firma contable lleva su propia contabilidad: no se le asigna otra firma");
        }

        if (actual != null) revocar(actual.id(), actorId);

        VinculoFila nuevo = null;
        if (firma != null) {
            EmpresaFirmaClienteEntity v = new EmpresaFirmaClienteEntity();
            v.setFirmaEmpresaId(firma.getId());
            v.setClienteEmpresaId(clienteId);
            v.setPerfilId(asegurarPerfilContador(clienteId));
            v.setActivo(true);
            v.setModalidad(modalidad);
            v.setCreadoPor(actorId);
            vinculoRepo.saveAndFlush(v);
            nuevo = query.vinculoActivoDelCliente(clienteId);
        }

        String descripcion = firma == null
                ? "Quitó el acceso de la firma contable " + actual.firmaNombre()
                : actual == null
                        ? "Dio acceso a la firma contable " + firma.getRazonSocial()
                        : "Cambió la firma contable de " + actual.firmaNombre() + " a " + firma.getRazonSocial();
        bitacora.registrarEnEmpresa(clienteId, CLAVE_BITACORA, "FIRMA_CONTABLE", "empresa", clienteId, descripcion,
                actual != null ? Map.of("firma", actual.firmaNombre(), "nit", actual.firmaNit()) : null,
                nuevo != null ? Map.of("firma", nuevo.firmaNombre(), "nit", nuevo.firmaNit()) : null);
        return nuevo;
    }

    /** Quita la firma del cliente (desde la plataforma o el administrador del cliente). */
    @Transactional
    public void quitarFirma(Integer clienteId, Integer actorId) {
        if (query.vinculoActivoDelCliente(clienteId) == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La empresa no tiene una firma contable con acceso");
        asignarFirma(clienteId, null, actorId);
    }

    /**
     * El perfil "Contador externo" del cliente; lo crea si no existe. Los permisos van
     * contra todo el catálogo de la plantilla: si el cliente activa un módulo después,
     * el contador ya lo ve (el cálculo cruza con lo que la empresa tiene activo).
     */
    @Transactional
    public Long asegurarPerfilContador(Integer empresaId) {
        PerfilesSistema.Definicion d = PerfilesSistema.CONTADOR;
        Long existente = permisoQuery.perfilIdPorCodigo(empresaId, d.codigo());
        if (existente != null) return existente;

        PerfilEntity p = new PerfilEntity();
        p.setEmpresaId(empresaId);
        p.setCodigo(d.codigo());
        p.setNombre(d.nombre());
        p.setDescripcion(d.descripcion());
        p.setAccesoTotal(false);
        p.setEsSistema(true);
        p.setActivo(true);
        p = perfilRepo.save(p);

        for (Integer submoduloId : configuracion.submodulosPorPatron(d.claves())) {
            PerfilPermisoEntity pp = new PerfilPermisoEntity();
            pp.setPerfilId(p.getId());
            pp.setSubmoduloId(submoduloId.longValue());
            pp.setVer(true);
            pp.setCrear(true);
            pp.setEditar(true);
            pp.setAnular(true);
            perfilPermisoRepo.save(pp);
        }
        return p.getId();
    }

    // ── La firma: qué clientes atiende cada usuario (F4) ────────────────────

    /** Clientes activos de la firma. Si la empresa no es firma, lista vacía. */
    public List<VinculoFila> clientesDeLaFirma(Integer firmaId) {
        EmpresaEntity firma = empresaRepo.findById(firmaId).orElse(null);
        if (firma == null || !Boolean.TRUE.equals(firma.getEsFirmaContable())) return List.of();
        return query.clientesDeLaFirma(firmaId);
    }

    /** Vínculos (clientes) que el usuario de la firma tiene asignados. */
    public List<Long> clientesDelUsuario(Integer firmaId, Integer usuarioId) {
        usuarioDeLaFirma(firmaId, usuarioId);
        return query.vinculosDelUsuario(usuarioId);
    }

    /**
     * Deja al usuario de la firma atendiendo exactamente esos clientes ({@code vinculoIds}).
     * Lo que quita se desactiva (sus sesiones en ese cliente se cortan); lo que vuelve a
     * dar reactiva la fila que ya existía.
     */
    @Transactional
    public List<Long> asignarClientes(Integer firmaId, Integer usuarioId, List<Long> vinculoIds, Integer actorId) {
        var usuario = usuarioDeLaFirma(firmaId, usuarioId);
        java.util.Set<Long> validos = new java.util.HashSet<>();
        for (VinculoFila v : clientesDeLaFirma(firmaId)) validos.add(v.id());
        java.util.Set<Long> pedidos = new java.util.LinkedHashSet<>(vinculoIds != null ? vinculoIds : List.of());
        for (Long id : pedidos) {
            if (!validos.contains(id))
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Uno de los clientes ya no es de la firma");
        }

        java.util.Set<Long> actuales = new java.util.HashSet<>(query.vinculosDelUsuario(usuarioId));
        int quitados = 0, dados = 0;
        for (Long id : actuales) {
            if (pedidos.contains(id)) continue;
            Long fila = query.asignacionId(usuarioId, id);
            var a = fila != null ? asignacionRepo.findById(fila).orElse(null) : null;
            if (a != null) {
                a.setActivo(false);
                asignacionRepo.save(a);
                quitados++;
            }
        }
        for (Long id : pedidos) {
            if (actuales.contains(id)) continue;
            Long fila = query.asignacionId(usuarioId, id);
            var a = fila != null ? asignacionRepo.findById(fila).orElse(null) : null;
            if (a == null) {
                a = new com.cloud_technological.aura_pos.entity.UsuarioFirmaClienteEntity();
                a.setUsuarioId(usuarioId);
                a.setVinculoId(id);
                a.setCreatedBy(actorId);
            }
            a.setActivo(true);
            asignacionRepo.save(a);
            dados++;
        }
        if (quitados + dados > 0) {
            bitacora.registrarEnEmpresa(firmaId, CLAVE_BITACORA, "CLIENTES_FIRMA", "usuario", usuarioId,
                    usuario.getUsername() + ": atiende " + pedidos.size() + " cliente(s) (" + dados + " nuevo(s), "
                            + quitados + " quitado(s))", null, null);
            if (accesoFirma != null) accesoFirma.invalidar();
        }
        return query.vinculosDelUsuario(usuarioId);
    }

    private com.cloud_technological.aura_pos.entity.UsuarioEntity usuarioDeLaFirma(Integer firmaId, Integer usuarioId) {
        var u = usuarioRepo.findById(usuarioId)
                .filter(x -> com.cloud_technological.aura_pos.utils.AislamientoEmpresa.es(x.getEmpresa(), firmaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        EmpresaEntity firma = u.getEmpresa();
        if (!Boolean.TRUE.equals(firma.getEsFirmaContable()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La empresa no es una firma contable");
        return u;
    }

    // ── El cliente: quién de la firma tiene acceso (F5) ─────────────────────

    /** La firma con acceso al cliente y los usuarios suyos que entran; null si no tiene. */
    public AccesoDelCliente accesoDelCliente(Integer clienteId) {
        VinculoFila v = query.vinculoActivoDelCliente(clienteId);
        if (v == null) return null;
        return new AccesoDelCliente(v, query.usuariosConAcceso(clienteId));
    }

    public record AccesoDelCliente(VinculoFila firma,
            List<com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.UsuarioConAcceso> usuarios) {
    }

    // ── Solicitudes cliente → firma (V200, §9 del plan) ─────────────────────

    /** La firma activa con ese NIT; 404 si no hay. El NIT se compara sin puntos ni dígito de verificación. */
    public EmpresaDisponible buscarFirmaPorNit(Integer clienteId, String nit) {
        String limpio = nit == null ? "" : nit.replaceAll("[^0-9]", "");
        if (limpio.length() < 5) throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el NIT de la firma");
        EmpresaDisponible f = query.firmaPorNit(limpio);
        if (f == null || f.id().equals(clienteId))
            throw new GlobalException(HttpStatus.NOT_FOUND, "No hay una firma contable en Aura con ese NIT");
        return f;
    }

    /** El cliente pide a una firma que le lleve la contabilidad. Antes tiene que quitar la que tenga. */
    @Transactional
    public com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.SolicitudFila solicitar(
            Integer clienteId, Integer firmaId, Integer actorId) {
        EmpresaEntity cliente = empresaRepo.findById(clienteId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
        if (Boolean.TRUE.equals(cliente.getEsFirmaContable()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Una firma contable lleva su propia contabilidad");
        if (query.vinculoActivoDelCliente(clienteId) != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Su empresa ya tiene una firma contable. Quítele el acceso antes de solicitar otra");
        if (query.solicitudPendienteDelCliente(clienteId) != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya tiene una solicitud pendiente: espere la respuesta o cancélela");
        EmpresaEntity firma = empresaRepo.findById(firmaId)
                .filter(f -> Boolean.TRUE.equals(f.getEsFirmaContable()) && Boolean.TRUE.equals(f.getActiva()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "No hay una firma contable en Aura con ese NIT"));

        var sol = new com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity();
        sol.setClienteEmpresaId(clienteId);
        sol.setFirmaEmpresaId(firma.getId());
        sol.setSolicitadoPor(actorId);
        solicitudRepo.saveAndFlush(sol);
        bitacora.registrarEnEmpresa(clienteId, CLAVE_BITACORA, "SOLICITUD_FIRMA", "empresa", clienteId,
                "Solicitó a " + firma.getRazonSocial() + " que le lleve la contabilidad", null, null);
        return query.solicitudPendienteDelCliente(clienteId);
    }

    public com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.SolicitudFila ultimaSolicitud(
            Integer clienteId) {
        return query.ultimaSolicitudDelCliente(clienteId);
    }

    public List<com.cloud_technological.aura_pos.repositories.firma_contable.FirmaContableQueryRepository.SolicitudFila> solicitudesPendientes(
            Integer firmaId) {
        return query.solicitudesPendientesDeLaFirma(firmaId);
    }

    /** El cliente retira su solicitud pendiente. */
    @Transactional
    public void cancelarSolicitud(Integer clienteId, Integer actorId) {
        var fila = query.solicitudPendienteDelCliente(clienteId);
        if (fila == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "No tiene una solicitud pendiente");
        var sol = solicitudRepo.findById(fila.id()).orElseThrow();
        sol.setEstado(com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity.CANCELADA);
        sol.setRespondidoPor(actorId);
        sol.setRespondidoAt(LocalDateTime.now());
        solicitudRepo.save(sol);
    }

    /**
     * La firma responde. Aceptar crea el vínculo (el cliente paga su licencia) con el
     * perfil "Contador externo"; si mientras tanto el cliente consiguió otra firma, falla.
     */
    @Transactional
    public void responderSolicitud(Integer firmaId, Long solicitudId, boolean acepta, String motivo, Integer actorId) {
        var sol = solicitudRepo.findById(solicitudId)
                .filter(x -> firmaId.equals(x.getFirmaEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity.PENDIENTE.equals(sol.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La solicitud ya fue respondida o cancelada");
        sol.setEstado(acepta ? com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity.ACEPTADA
                : com.cloud_technological.aura_pos.entity.SolicitudFirmaClienteEntity.RECHAZADA);
        sol.setRespondidoPor(actorId);
        sol.setRespondidoAt(LocalDateTime.now());
        sol.setMotivo(motivo != null && !motivo.isBlank() ? motivo.trim() : null);
        solicitudRepo.save(sol);
        if (acepta) {
            if (query.vinculoActivoDelCliente(sol.getClienteEmpresaId()) != null)
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El cliente ya tiene otra firma contable");
            vincular(sol.getClienteEmpresaId(), firmaId, actorId, EmpresaFirmaClienteEntity.MODALIDAD_CLIENTE_PAGA);
        } else {
            bitacora.registrarEnEmpresa(sol.getClienteEmpresaId(), CLAVE_BITACORA, "SOLICITUD_FIRMA", "empresa",
                    sol.getClienteEmpresaId(), "La firma rechazó la solicitud"
                            + (sol.getMotivo() != null ? ": " + sol.getMotivo() : ""), null, null);
        }
    }

    private void revocar(Long vinculoId, Integer actorId) {
        EmpresaFirmaClienteEntity v = vinculoRepo.findById(vinculoId).orElse(null);
        if (v == null || !Boolean.TRUE.equals(v.getActivo())) return;
        v.setActivo(false);
        v.setRevocadoPor(actorId);
        v.setRevocadoAt(LocalDateTime.now());
        // Se escribe ya: el índice único (un vínculo activo por cliente) no deja
        // insertar el nuevo mientras este siga activo en la base.
        vinculoRepo.saveAndFlush(v);
        if (accesoFirma != null) accesoFirma.invalidar();
    }
}
