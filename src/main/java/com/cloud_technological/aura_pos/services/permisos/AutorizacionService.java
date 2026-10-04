package com.cloud_technological.aura_pos.services.permisos;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.AutorizacionDada;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.Exceso;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.CodigoGenerado;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.EstadoSolicitud;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.SolicitarAutorizacion;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.SolicitudPendiente;
import com.cloud_technological.aura_pos.dto.permisos.BitacoraDtos.UsarCodigo;
import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDetalleDto;
import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.entity.AutorizacionEntity;
import com.cloud_technological.aura_pos.repositories.auditoria.AutorizacionJPARepository;
import com.cloud_technological.aura_pos.repositories.auditoria.BitacoraQueryRepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Límites de descuento y de rebaja de precio, y autorización de un supervisor para
 * pasarlos (fase P8 de docs/PLAN_PERMISOS.md).
 *
 * <p>Flujo: el POS calcula el exceso con {@link #exceso}; si pasa el límite hay dos
 * caminos, y en ninguno la clave del supervisor se escribe en el equipo del cajero:
 * <ul>
 * <li>{@link #generarCodigo}: el supervisor genera en SU sesión un código de 6
 * dígitos (2 minutos, un uso) y se lo dicta; el cajero lo usa con {@link #usarCodigo}.</li>
 * <li>{@link #solicitar}: el cajero pide aprobación y el supervisor la aprueba en su
 * sesión ({@link #aprobar}); el POS espera con {@link #estadoSolicitud}.</li>
 * </ul>
 * Cualquiera de los dos deja una autorización VIGENTE por 10 minutos que la venta
 * trae y {@link #validarParaVenta} / {@link #consumir} revisan otra vez en el back.
 *
 * <p>Los límites son opcionales (null = sin límite) y se aplican siempre que estén
 * puestos, sin importar el modo del control de permisos.
 */
@Service
@RequiredArgsConstructor
public class AutorizacionService {

    public static final String ESPECIAL_AUTORIZAR = "ventas.ventas:AUTORIZAR_DESCUENTO";
    private static final int MINUTOS_VIGENCIA = 10;
    private static final int MINUTOS_CODIGO = 2;
    private static final int MINUTOS_SOLICITUD = 15;
    private static final java.security.SecureRandom AZAR = new java.security.SecureRandom();
    /** Tolerancia de redondeo para la rebaja de precio (el POS redondea a 2 decimales). */
    private static final BigDecimal TOLERANCIA_PCT = new BigDecimal("0.5");
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final int MAX_FALLOS = 5;
    private static final long VENTANA_FALLOS_MS = 10 * 60 * 1000L;

    private final PermisoUsuarioService permisos;
    private final BitacoraQueryRepository query;
    private final AutorizacionJPARepository repo;
    private final BitacoraService bitacora;

    /** Intentos fallidos por solicitante: un cajero no puede adivinar códigos. */
    private final Map<Integer, long[]> fallos = new ConcurrentHashMap<>();

    // ── Cálculo ──────────────────────────────────────────────────────────────

    /** Cuánto descuento y rebaja de precio trae la venta, contra los límites del usuario. */
    public Exceso exceso(CreateVentaDto dto, Integer usuarioId, Integer empresaId) {
        PermisosUsuarioDto p = permisos.efectivos(usuarioId);
        Exceso x = new Exceso();
        x.setDescuentoMaxPct(p.getDescuentoMaxPct());
        x.setRebajaPrecioMaxPct(p.getRebajaPrecioMaxPct());
        if (dto == null || dto.getDetalles() == null) return x;
        boolean mideDescuento = p.getDescuentoMaxPct() != null;
        boolean mideRebaja = p.getRebajaPrecioMaxPct() != null;
        if (!mideDescuento && !mideRebaja) return x;

        // Descuento general como % de la base después de los descuentos de línea.
        BigDecimal baseNeta = BigDecimal.ZERO;
        for (CreateVentaDetalleDto d : dto.getDetalles()) {
            baseNeta = baseNeta.add(bruto(d).subtract(nz(d.getDescuentoValor())));
        }
        BigDecimal general = pct(nz(dto.getDescuentoGeneral()), baseNeta);
        BigDecimal factorGeneral = BigDecimal.ONE.subtract(general.divide(CIEN, 8, RoundingMode.HALF_UP));

        int n = 0;
        for (CreateVentaDetalleDto d : dto.getDetalles()) {
            n++;
            BigDecimal bruto = bruto(d);
            if (mideDescuento) {
                // Las reglas de descuento automáticas no cuentan contra el límite del usuario.
                BigDecimal linea = d.getReglaDescuentoId() != null ? BigDecimal.ZERO : pct(nz(d.getDescuentoValor()), bruto);
                // Descuento de línea y general se acumulan: 10% + 10% = 19%.
                BigDecimal combinado = CIEN.subtract(CIEN.subtract(linea).multiply(factorGeneral))
                        .setScale(2, RoundingMode.HALF_UP);
                if (combinado.compareTo(x.getDescuentoPct()) > 0) x.setDescuentoPct(combinado);
                if (combinado.compareTo(p.getDescuentoMaxPct()) > 0) {
                    x.getDetalle().add("Línea " + n + ": descuento " + combinado.stripTrailingZeros().toPlainString()
                            + "% (su límite es " + p.getDescuentoMaxPct().stripTrailingZeros().toPlainString() + "%)");
                }
            }
            if (mideRebaja && d.getProductoId() != null && d.getPrecioUnitario() != null) {
                BigDecimal ref = query.precioMinimoSinIva(empresaId, d.getProductoId(), d.getProductoPresentacionId());
                if (ref != null && ref.signum() > 0 && d.getPrecioUnitario().compareTo(ref) < 0) {
                    BigDecimal rebaja = pct(ref.subtract(d.getPrecioUnitario()), ref);
                    if (rebaja.compareTo(TOLERANCIA_PCT) > 0) {
                        if (rebaja.compareTo(x.getRebajaPct()) > 0) x.setRebajaPct(rebaja);
                        if (rebaja.compareTo(p.getRebajaPrecioMaxPct()) > 0) {
                            x.getDetalle().add("Línea " + n + ": precio " + rebaja.stripTrailingZeros().toPlainString()
                                    + "% por debajo del menor precio del producto (su límite es "
                                    + p.getRebajaPrecioMaxPct().stripTrailingZeros().toPlainString() + "%)");
                        }
                    }
                }
            }
        }
        if (mideDescuento && general.compareTo(p.getDescuentoMaxPct()) > 0) {
            x.getDetalle().add("Descuento general " + general.stripTrailingZeros().toPlainString() + "%");
        }
        return x;
    }

    // ── Código de un solo uso ────────────────────────────────────────────────

    /**
     * El supervisor, en SU sesión, genera un código de 6 dígitos para dictarlo.
     * Solo se guarda su huella; vence en {@value #MINUTOS_CODIGO} minutos.
     */
    @Transactional
    public CodigoGenerado generarCodigo(Integer supervisorId, Integer empresaId) {
        exigirAutorizador(supervisorId);
        String codigo = String.format("%06d", AZAR.nextInt(1_000_000));
        AutorizacionEntity e = new AutorizacionEntity();
        e.setEmpresaId(empresaId);
        e.setAutorizadorId(supervisorId);
        e.setTipo(AutorizacionEntity.TIPO_DESCUENTO);
        e.setEstado(AutorizacionEntity.CODIGO);
        e.setCodigoHash(huella(empresaId, codigo));
        e.setExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_CODIGO));
        repo.save(e);

        PermisosUsuarioDto suyo = permisos.efectivos(supervisorId);
        CodigoGenerado r = new CodigoGenerado();
        r.setCodigo(codigo);
        r.setExpiraEn(e.getExpiraEn());
        r.setDescuentoMaxPct(suyo.getDescuentoMaxPct());
        r.setRebajaPrecioMaxPct(suyo.getRebajaPrecioMaxPct());
        return r;
    }

    /**
     * El cajero usa el código que le dictaron. Verlo en las herramientas del
     * navegador no sirve: ya quedó usado y vencía en minutos.
     */
    @Transactional
    public AutorizacionDada usarCodigo(UsarCodigo req, Integer solicitanteId, Integer empresaId) {
        String codigo = req != null && req.getCodigo() != null ? req.getCodigo().replaceAll("\\s", "") : "";
        if (!codigo.matches("\\d{6}")) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el código de 6 dígitos que le dio el supervisor");
        }
        frenarSiAdivina(solicitanteId);
        Long id = query.autorizacionPorCodigo(empresaId, huella(empresaId, codigo));
        AutorizacionEntity a = id != null ? repo.findById(id).orElse(null) : null;
        if (a == null) {
            fallo(solicitanteId);
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El código no es válido o ya venció: pida otro");
        }
        fallos.remove(solicitanteId);
        if (a.getAutorizadorId().equals(solicitanteId)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La autorización la da otra persona: usted ya está en el límite de su perfil");
        }
        BigDecimal desc = nz(req.getDescuentoPct());
        BigDecimal rebaja = nz(req.getRebajaPct());
        exigirAutorizador(a.getAutorizadorId());
        exigirCubre(a.getAutorizadorId(), desc, rebaja);

        a.setSolicitanteId(solicitanteId);
        a.setDescuentoPct(desc);
        a.setRebajaPct(rebaja);
        a.setMotivo(recortarMotivo(req.getMotivo()));
        a.setEstado(AutorizacionEntity.VIGENTE);
        a.setExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_VIGENCIA));
        repo.save(a);
        return dada(a, "con código");
    }

    // ── Aprobación remota ────────────────────────────────────────────────────

    /** El cajero pide aprobación; el supervisor la verá en su sesión. */
    @Transactional
    public EstadoSolicitud solicitar(SolicitarAutorizacion req, Integer solicitanteId, Integer empresaId) {
        AutorizacionEntity e = new AutorizacionEntity();
        e.setEmpresaId(empresaId);
        e.setSolicitanteId(solicitanteId);
        e.setTipo(AutorizacionEntity.TIPO_DESCUENTO);
        e.setEstado(AutorizacionEntity.SOLICITADA);
        e.setDescuentoPct(nz(req != null ? req.getDescuentoPct() : null));
        e.setRebajaPct(nz(req != null ? req.getRebajaPct() : null));
        e.setDetalle(req != null && req.getDetalle() != null ? String.join("\n", req.getDetalle()) : null);
        e.setMotivo(recortarMotivo(req != null ? req.getMotivo() : null));
        e.setExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_SOLICITUD));
        repo.save(e);
        return estado(e);
    }

    /** El POS pregunta cómo va su solicitud. */
    public EstadoSolicitud estadoSolicitud(Long id, Integer solicitanteId, Integer empresaId) {
        AutorizacionEntity a = repo.findById(id).orElse(null);
        if (a == null || !empresaId.equals(a.getEmpresaId()) || !solicitanteId.equals(a.getSolicitanteId())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        return estado(a);
    }

    /** Bandeja del supervisor. */
    public List<SolicitudPendiente> pendientes(Integer supervisorId, Integer empresaId) {
        if (!permisos.puedeEspecial(supervisorId, ESPECIAL_AUTORIZAR)) return List.of();
        return query.solicitudesPendientes(empresaId);
    }

    @Transactional
    public EstadoSolicitud aprobar(Long id, Integer supervisorId, Integer empresaId) {
        AutorizacionEntity a = solicitudPendiente(id, empresaId);
        exigirAutorizador(supervisorId);
        if (supervisorId.equals(a.getSolicitanteId())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No puede aprobar su propia solicitud");
        }
        exigirCubre(supervisorId, nz(a.getDescuentoPct()), nz(a.getRebajaPct()));
        a.setAutorizadorId(supervisorId);
        a.setEstado(AutorizacionEntity.VIGENTE);
        a.setExpiraEn(LocalDateTime.now().plusMinutes(MINUTOS_VIGENCIA));
        repo.save(a);
        dada(a, "remota");
        return estado(a);
    }

    @Transactional
    public EstadoSolicitud rechazar(Long id, Integer supervisorId, Integer empresaId) {
        AutorizacionEntity a = solicitudPendiente(id, empresaId);
        exigirAutorizador(supervisorId);
        a.setAutorizadorId(supervisorId);
        a.setEstado(AutorizacionEntity.RECHAZADA);
        repo.save(a);
        bitacora.registrar("ventas.ventas", "RECHAZAR_DESCUENTO", "autorizacion", a.getId(),
                query.username(supervisorId) + " rechazó la solicitud de " + query.username(a.getSolicitanteId()),
                null, null, supervisorId);
        return estado(a);
    }

    // ── Internos de autorización ─────────────────────────────────────────────

    /** Siempre, sin importar el modo del control: autorizar es dar más de lo que el perfil permite. */
    private void exigirAutorizador(Integer usuarioId) {
        if (!permisos.puedeEspecial(usuarioId, ESPECIAL_AUTORIZAR)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permiso para autorizar descuentos y precios");
        }
    }

    /** Nadie autoriza más de lo que él mismo puede dar. */
    private void exigirCubre(Integer supervisorId, BigDecimal desc, BigDecimal rebaja) {
        PermisosUsuarioDto suyo = permisos.efectivos(supervisorId);
        String nombre = query.username(supervisorId);
        if (suyo.getDescuentoMaxPct() != null && desc.compareTo(suyo.getDescuentoMaxPct()) > 0) {
            throw new GlobalException(HttpStatus.FORBIDDEN, nombre + " solo puede autorizar hasta "
                    + suyo.getDescuentoMaxPct().stripTrailingZeros().toPlainString() + "% de descuento");
        }
        if (suyo.getRebajaPrecioMaxPct() != null && rebaja.compareTo(suyo.getRebajaPrecioMaxPct()) > 0) {
            throw new GlobalException(HttpStatus.FORBIDDEN, nombre + " solo puede autorizar rebajas de precio hasta "
                    + suyo.getRebajaPrecioMaxPct().stripTrailingZeros().toPlainString() + "%");
        }
    }

    private AutorizacionEntity solicitudPendiente(Long id, Integer empresaId) {
        AutorizacionEntity a = repo.findById(id).orElse(null);
        if (a == null || !empresaId.equals(a.getEmpresaId())) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada");
        }
        if (!AutorizacionEntity.SOLICITADA.equals(a.getEstado()) || a.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new GlobalException(HttpStatus.CONFLICT, "La solicitud ya fue respondida o venció");
        }
        return a;
    }

    /** Deja la autorización en la bitácora y la respuesta para el POS. */
    private AutorizacionDada dada(AutorizacionEntity a, String como) {
        String quien = query.username(a.getAutorizadorId());
        bitacora.registrar("ventas.ventas", "AUTORIZAR", "autorizacion", a.getId(),
                quien + " autorizó (" + como + ") a " + query.username(a.getSolicitanteId()) + ": descuento "
                        + nz(a.getDescuentoPct()).stripTrailingZeros().toPlainString() + "% / rebaja "
                        + nz(a.getRebajaPct()).stripTrailingZeros().toPlainString() + "%"
                        + (a.getMotivo() != null ? " — " + a.getMotivo() : ""),
                null, null, a.getAutorizadorId());
        AutorizacionDada r = new AutorizacionDada();
        r.setAutorizacionId(a.getId());
        r.setAutorizador(quien);
        r.setExpiraEn(a.getExpiraEn());
        return r;
    }

    private EstadoSolicitud estado(AutorizacionEntity a) {
        EstadoSolicitud s = new EstadoSolicitud();
        s.setId(a.getId());
        boolean vencida = (AutorizacionEntity.SOLICITADA.equals(a.getEstado())
                || AutorizacionEntity.VIGENTE.equals(a.getEstado())) && a.getExpiraEn().isBefore(LocalDateTime.now());
        s.setEstado(vencida ? "VENCIDA" : a.getEstado());
        s.setAutorizador(query.username(a.getAutorizadorId()));
        s.setExpiraEn(a.getExpiraEn());
        return s;
    }

    /** SHA-256 de empresa + código: la base nunca guarda el código. */
    static String huella(Integer empresaId, String codigo) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((empresaId + ":" + codigo).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String recortarMotivo(String m) {
        if (m == null || m.isBlank()) return null;
        String t = m.trim();
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    /**
     * Revisa que la venta pueda pasar: sin exceso no hace nada; con exceso exige
     * una autorización vigente del mismo usuario que lo cubra. Devuelve la
     * autorización (para consumirla cuando la venta exista) o null.
     */
    public AutorizacionEntity validarParaVenta(Exceso exceso, Long autorizacionId, Integer usuarioId, Integer empresaId) {
        if (!exceso.requiereAutorizacion()) return null;
        String detalle = String.join("; ", exceso.getDetalle());
        if (autorizacionId == null) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "La venta pasa su límite y necesita autorización de un supervisor: "
                    + detalle);
        }
        AutorizacionEntity a = repo.findById(autorizacionId).orElse(null);
        if (a == null || !empresaId.equals(a.getEmpresaId()) || !usuarioId.equals(a.getSolicitanteId())) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "La autorización no es válida para esta venta");
        }
        if (!AutorizacionEntity.VIGENTE.equals(a.getEstado())) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "La autorización ya se usó: pida una nueva");
        }
        if (a.getExpiraEn().isBefore(LocalDateTime.now())) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "La autorización venció: pida una nueva");
        }
        if (exceso.getDescuentoPct().compareTo(nz(a.getDescuentoPct())) > 0
                || exceso.getRebajaPct().compareTo(nz(a.getRebajaPct())) > 0) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "La venta cambió y ya no la cubre la autorización: pida una nueva (" + detalle + ")");
        }
        return a;
    }

    /** La deja usada y amarrada al documento; queda en la bitácora con quien autorizó. */
    public void consumir(AutorizacionEntity a, String documentoTipo, Long documentoId, Exceso exceso) {
        if (a == null) return;
        a.setEstado(AutorizacionEntity.USADA);
        a.setUsadaEn(LocalDateTime.now());
        a.setDocumentoTipo(documentoTipo);
        a.setDocumentoId(documentoId);
        repo.save(a);
        bitacora.registrar("ventas.ventas", "DESCUENTO_AUTORIZADO", documentoTipo.toLowerCase(), documentoId,
                "Venta con " + String.join("; ", exceso.getDetalle()), null, exceso, a.getAutorizadorId());
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private void frenarSiAdivina(Integer solicitanteId) {
        long[] f = fallos.get(solicitanteId);
        if (f != null && f[0] >= MAX_FALLOS && System.currentTimeMillis() - f[1] < VENTANA_FALLOS_MS) {
            throw new GlobalException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos de autorización. Espere unos minutos");
        }
    }

    private void fallo(Integer solicitanteId) {
        fallos.compute(solicitanteId, (k, f) -> {
            long ahora = System.currentTimeMillis();
            if (f == null || ahora - f[1] >= VENTANA_FALLOS_MS) return new long[] { 1, ahora };
            return new long[] { f[0] + 1, f[1] };
        });
    }

    private static BigDecimal bruto(CreateVentaDetalleDto d) {
        if (d.getPrecioUnitario() == null || d.getCantidad() == null) return BigDecimal.ZERO;
        return d.getPrecioUnitario().multiply(d.getCantidad());
    }

    /** parte / total × 100, 2 decimales; 0 si el total no es positivo. */
    private static BigDecimal pct(BigDecimal parte, BigDecimal total) {
        if (total == null || total.signum() <= 0 || parte == null || parte.signum() <= 0) return BigDecimal.ZERO;
        return parte.multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Vender a crédito: basta con tenerla en Ventas o en el Punto de Venta. */
    public static final List<String> CLAVES_CREDITO = List.of("ventas.ventas:VENDER_A_CREDITO",
            "principal.punto-de-venta:VENDER_A_CREDITO");
}
