package com.cloud_technological.aura_pos.services.correo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.correo.CorreoDtos;
import com.cloud_technological.aura_pos.entity.CorreoEnvioEntity;
import com.cloud_technological.aura_pos.entity.FacturaVentaEntity;
import com.cloud_technological.aura_pos.repositories.correo.CorreoEnvioJPARepository;
import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository;
import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository.DocumentoCorreo;
import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository.EmpresaCorreo;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Envío de documentos por correo (docs/PLAN_CUENTA_COBRO_CORREO.md): factura, cuenta
 * de cobro y cotización uno a uno (se envía en el momento y se ve el resultado), y
 * comprobantes de nómina en masa (van a la cola de {@link CorreoColaWorker}). El PDF
 * lo genera la pantalla, así el adjunto es el mismo que se descarga.
 *
 * <p>Sale de la dirección de Aura (CORREO_REMITENTE) con el nombre de la empresa, y
 * las respuestas le llegan al correo de la empresa (Reply-To).
 */
@Service
@RequiredArgsConstructor
public class CorreoService {

    public static final String FACTURA = "FACTURA";
    public static final String CUENTA_COBRO = "CUENTA_COBRO";
    public static final String COTIZACION = "COTIZACION";
    public static final String NOMINA = "NOMINA";
    /** Cobro de cartera: documento_id = el cliente (tercero). */
    public static final String COBRO = "COBRO";
    /** Acceso inicial o reenviado del administrador: documento_id = la empresa. */
    public static final String ACCESO = "ACCESO";

    /** Tamaño máximo del PDF (Resend acepta hasta 40 MB por correo). */
    private static final int MAX_ADJUNTO = 10 * 1024 * 1024;
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final CorreoEnvioJPARepository repo;
    private final CorreoQueryRepository query;
    private final ResendClient resend;

    // ── Sugerencias ─────────────────────────────────────────────────────────

    /** Para, asunto y mensaje propuestos para una factura o cuenta de cobro. */
    public CorreoDtos.Sugerencia sugerenciaFactura(Integer empresaId, Long facturaId) {
        DocumentoCorreo d = factura(empresaId, facturaId);
        String empresa = nombreEmpresa(empresaId);
        boolean cc = FacturaVentaEntity.TIPO_CUENTA_COBRO.equals(d.tipo());
        String doc = (cc ? "Cuenta de cobro" : "Factura de venta") + (d.numero() != null ? " " + d.numero() : "");
        String mensaje = "Hola " + saludo(d.nombre()) + ",\n\nAdjuntamos la "
                + (cc ? "cuenta de cobro" : "factura de venta") + (d.numero() != null ? " " + d.numero() : "")
                + ". Quedamos atentos a cualquier inquietud.\n\nCordialmente,\n" + empresa;
        return new CorreoDtos.Sugerencia(lista(d.correo()), doc + " - " + empresa, mensaje, resend.configurado());
    }

    public CorreoDtos.Sugerencia sugerenciaCotizacion(Integer empresaId, Long cotizacionId) {
        DocumentoCorreo d = cotizacion(empresaId, cotizacionId);
        String empresa = nombreEmpresa(empresaId);
        String doc = "Cotización" + (d.numero() != null ? " " + d.numero() : "");
        String mensaje = "Hola " + saludo(d.nombre()) + ",\n\nAdjuntamos la cotización que nos solicitó. "
                + "Si tiene preguntas o quiere ajustar algo, responda este correo.\n\nCordialmente,\n" + empresa;
        return new CorreoDtos.Sugerencia(lista(d.correo()), doc + " - " + empresa, mensaje, resend.configurado());
    }

    /** Asunto y mensaje del lote de comprobantes (el "para" es el correo de cada empleado). */
    public CorreoDtos.Sugerencia sugerenciaNomina(Integer empresaId) {
        String empresa = nombreEmpresa(empresaId);
        String mensaje = "Hola,\n\nAdjuntamos su comprobante de pago de nómina. Si encuentra alguna diferencia, "
                + "responda este correo.\n\nCordialmente,\n" + empresa;
        return new CorreoDtos.Sugerencia(List.of(), "Comprobante de pago de nómina - " + empresa, mensaje,
                resend.configurado());
    }

    // ── Envío uno a uno ─────────────────────────────────────────────────────

    /**
     * Envía la factura o cuenta de cobro ya. Devuelve la fila con su estado: ENVIADO,
     * o FALLIDO con el motivo (también queda en el historial).
     */
    @Transactional
    public CorreoDtos.Fila enviarFactura(Integer empresaId, Long facturaId, CorreoDtos.Enviar dto, Integer usuarioId) {
        exigirAdjunto(dto);
        DocumentoCorreo d = factura(empresaId, facturaId);
        String tipo = FacturaVentaEntity.TIPO_CUENTA_COBRO.equals(d.tipo()) ? CUENTA_COBRO : FACTURA;
        return enviarAhora(empresaId, tipo, facturaId, dto, usuarioId);
    }

    @Transactional
    public CorreoDtos.Fila enviarCotizacion(Integer empresaId, Long cotizacionId, CorreoDtos.Enviar dto,
            Integer usuarioId) {
        exigirAdjunto(dto);
        cotizacion(empresaId, cotizacionId);
        return enviarAhora(empresaId, COTIZACION, cotizacionId, dto, usuarioId);
    }

    /** Correos de una factura o cuenta de cobro. */
    public List<CorreoDtos.Fila> historialFactura(Integer empresaId, Long facturaId) {
        factura(empresaId, facturaId);
        return query.historial(empresaId, List.of(FACTURA, CUENTA_COBRO), facturaId);
    }

    public List<CorreoDtos.Fila> historialCotizacion(Integer empresaId, Long cotizacionId) {
        cotizacion(empresaId, cotizacionId);
        return query.historial(empresaId, List.of(COTIZACION), cotizacionId);
    }

    // ── Para otros servicios (cobro de cartera, accesos) ────────────────────

    /**
     * Envía ya un documento cuyo PDF armó el servidor (p. ej. el estado de cuenta).
     * Devuelve la fila con su estado (ENVIADO o FALLIDO con el motivo).
     */
    @Transactional
    public CorreoDtos.Fila enviarConPdf(Integer empresaId, String tipo, Long documentoId, CorreoDtos.Enviar dto,
            String adjuntoNombre, byte[] pdf, Integer usuarioId) {
        exigirConfigurado();
        CorreoEnvioEntity c = nuevoSinAdjunto(empresaId, tipo, documentoId, dto.getPara(), dto.getCc(), dto.getAsunto(),
                dto.getMensaje(), usuarioId);
        c.setAdjuntoNombre(nombreArchivo(adjuntoNombre));
        c.setAdjunto(pdf);
        c = repo.save(c);
        c.setIntentos(1);
        registrar(c, despachar(c));
        repo.saveAndFlush(c);
        Long id = c.getId();
        return query.historial(empresaId, List.of(tipo), documentoId).stream()
                .filter(f -> f.getId().equals(id)).findFirst().orElse(null);
    }

    /** Pone en la cola de envío un correo con un PDF que armó el servidor. */
    @Transactional
    public void encolarConPdf(Integer empresaId, String loteId, String tipo, Long documentoId, String para,
            String asunto, String mensaje, String adjuntoNombre, byte[] pdf, Integer usuarioId) {
        exigirConfigurado();
        CorreoEnvioEntity c = nuevoSinAdjunto(empresaId, tipo, documentoId, List.of(para), List.of(), asunto, mensaje,
                usuarioId);
        c.setLoteId(loteId);
        c.setAdjuntoNombre(nombreArchivo(adjuntoNombre));
        c.setAdjunto(pdf);
        repo.save(c);
    }

    /**
     * Correo del sistema (acceso del administrador): sale como "Aura", sin Reply-To y
     * con un botón. El HTML lleva datos que no se guardan (la clave temporal): en el
     * historial queda solo {@code registro}.
     */
    @Transactional
    public ResendClient.Resultado enviarSistema(Integer empresaId, String tipo, Long documentoId, String para,
            String asunto, String mensaje, String registro, String botonTexto, String botonUrl, Integer usuarioId) {
        if (!resend.configurado())
            return new ResendClient.Resultado(false, null, "El envío de correos no está configurado", false);
        CorreoEnvioEntity c = nuevoSinAdjunto(empresaId, tipo, documentoId, List.of(para), List.of(), asunto, registro,
                usuarioId);
        c = repo.save(c);
        c.setIntentos(1);
        String html = CorreoPlantilla.html("Aura", null, mensaje, null, null, botonTexto, botonUrl);
        ResendClient.Resultado r = resend.enviar(new ResendClient.Mensaje("Aura", List.of(c.getDestinatario()), List.of(),
                null, c.getAsunto(), html, null, null, "correo-" + c.getId()));
        registrar(c, r);
        repo.save(c);
        return r;
    }

    // ── Comprobantes de nómina en masa ──────────────────────────────────────

    /**
     * Encola una tanda de comprobantes; cada uno va al correo del empleado. Los que
     * no tienen correo no se encolan y vuelven en {@code sinCorreo}.
     */
    @Transactional
    public CorreoDtos.LoteEncolado encolarNomina(Integer empresaId, CorreoDtos.Lote dto, Integer usuarioId) {
        exigirConfigurado();
        String loteId = dto.getLoteId() != null && !dto.getLoteId().isBlank() ? dto.getLoteId().trim()
                : UUID.randomUUID().toString();
        List<Long> ids = dto.getItems().stream().map(CorreoDtos.ItemLote::getNominaId).distinct().toList();
        Map<Long, DocumentoCorreo> nominas = query.nominas(ids, empresaId).stream()
                .collect(Collectors.toMap(DocumentoCorreo::id, Function.identity()));
        List<String> sinCorreo = new ArrayList<>();
        int encolados = 0;
        for (CorreoDtos.ItemLote item : dto.getItems()) {
            DocumentoCorreo n = nominas.get(item.getNominaId());
            if (n == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada: " + item.getNominaId());
            if (n.correo() == null || !CORREO.matcher(n.correo()).matches()) {
                sinCorreo.add(n.nombre());
                continue;
            }
            CorreoEnvioEntity c = nuevo(empresaId, NOMINA, n.id(), List.of(n.correo()), List.of(), dto.getAsunto(),
                    dto.getMensaje(), item.getAdjunto(), usuarioId);
            c.setLoteId(loteId);
            repo.save(c);
            encolados++;
        }
        return new CorreoDtos.LoteEncolado(loteId, encolados, sinCorreo);
    }

    /** Avance del lote: cuántos van, cuántos fallaron y a quién. */
    public CorreoDtos.ResumenLote resumenLote(Integer empresaId, String loteId) {
        CorreoDtos.ResumenLote r = new CorreoDtos.ResumenLote();
        r.setLoteId(loteId);
        r.setFilas(query.lote(empresaId, loteId));
        r.setTotal(r.getFilas().size());
        for (CorreoDtos.Fila f : r.getFilas()) {
            switch (f.getEstado()) {
                case CorreoEnvioEntity.ENVIADO -> r.setEnviados(r.getEnviados() + 1);
                case CorreoEnvioEntity.FALLIDO -> r.setFallidos(r.getFallidos() + 1);
                default -> r.setPendientes(r.getPendientes() + 1);
            }
        }
        return r;
    }

    // ── Cola (la llama CorreoColaWorker) ────────────────────────────────────

    /**
     * Envía un correo de la cola. Un fallo pasajero (límite de Resend, red) vuelve a
     * PENDIENTE hasta {@code maxIntentos}; los demás quedan FALLIDO con el motivo.
     */
    @Transactional
    public void enviarDeCola(Long id, int maxIntentos) {
        CorreoEnvioEntity c = repo.findById(id).orElse(null);
        if (c == null || !CorreoEnvioEntity.ENVIANDO.equals(c.getEstado())) return;
        ResendClient.Resultado r = despachar(c);
        c.setIntentos(c.getIntentos() + 1);
        if (!r.ok() && r.reintentar() && c.getIntentos() < maxIntentos) {
            c.setEstado(CorreoEnvioEntity.PENDIENTE);
            c.setError(r.error());
        } else {
            registrar(c, r);
        }
        repo.save(c);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private CorreoDtos.Fila enviarAhora(Integer empresaId, String tipo, Long documentoId, CorreoDtos.Enviar dto,
            Integer usuarioId) {
        exigirConfigurado();
        CorreoEnvioEntity c = repo.save(nuevo(empresaId, tipo, documentoId, dto.getPara(), dto.getCc(),
                dto.getAsunto(), dto.getMensaje(), dto.getAdjunto(), usuarioId));
        c.setIntentos(1);
        registrar(c, despachar(c));
        // Un fallo no se lanza: quedaría deshecho y no aparecería en el historial con su motivo.
        repo.saveAndFlush(c);
        return query.historial(empresaId, List.of(tipo), documentoId).stream()
                .filter(f -> f.getId().equals(c.getId())).findFirst().orElse(null);
    }

    private ResendClient.Resultado despachar(CorreoEnvioEntity c) {
        EmpresaCorreo e = query.empresa(c.getEmpresaId());
        String nombre = e != null ? e.nombre() : "Aura";
        String html = CorreoPlantilla.html(nombre, e != null ? e.logoUrl() : null, c.getMensaje(), c.getAdjuntoNombre(),
                e != null ? e.correo() : null, null, null);
        return resend.enviar(new ResendClient.Mensaje(nombre, separar(c.getDestinatario()), separar(c.getCc()),
                e != null ? e.correo() : null, c.getAsunto(), html, c.getAdjuntoNombre(), c.getAdjunto(),
                "correo-" + c.getId()));
    }

    /** Deja el resultado final; el PDF ya no hace falta y se borra. */
    private static void registrar(CorreoEnvioEntity c, ResendClient.Resultado r) {
        if (r.ok()) {
            c.setEstado(CorreoEnvioEntity.ENVIADO);
            c.setProveedorId(r.id());
            c.setError(null);
            c.setEnviadoAt(LocalDateTime.now());
        } else {
            c.setEstado(CorreoEnvioEntity.FALLIDO);
            c.setError(r.error());
        }
        c.setAdjunto(null);
    }

    private CorreoEnvioEntity nuevo(Integer empresaId, String tipo, Long documentoId, List<String> para,
            List<String> cc, String asunto, String mensaje, CorreoDtos.Adjunto adjunto, Integer usuarioId) {
        CorreoEnvioEntity c = nuevoSinAdjunto(empresaId, tipo, documentoId, para, cc, asunto, mensaje, usuarioId);
        c.setAdjuntoNombre(nombreArchivo(adjunto.getNombre()));
        c.setAdjunto(decodificar(adjunto.getBase64()));
        return c;
    }

    private CorreoEnvioEntity nuevoSinAdjunto(Integer empresaId, String tipo, Long documentoId, List<String> para,
            List<String> cc, String asunto, String mensaje, Integer usuarioId) {
        List<String> destinos = validar(para, "destinatario");
        List<String> copias = validar(cc, "correo en copia");
        if (destinos.isEmpty()) throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba al menos un destinatario");
        CorreoEnvioEntity c = new CorreoEnvioEntity();
        c.setEmpresaId(empresaId);
        c.setTipo(tipo);
        c.setDocumentoId(documentoId);
        c.setDestinatario(String.join(",", destinos));
        c.setCc(copias.isEmpty() ? null : String.join(",", copias));
        c.setAsunto(asunto.trim());
        c.setMensaje(mensaje);
        c.setUsuarioId(usuarioId);
        return c;
    }

    private static List<String> validar(List<String> correos, String que) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : correos != null ? correos : List.<String>of()) {
            if (s == null || s.isBlank()) continue;
            String t = s.trim().toLowerCase();
            if (!CORREO.matcher(t).matches())
                throw new GlobalException(HttpStatus.BAD_REQUEST, "El " + que + " \"" + s.trim() + "\" no es un correo válido");
            out.add(t);
        }
        return new ArrayList<>(out);
    }

    private static byte[] decodificar(String base64) {
        String b = base64.contains(",") && base64.startsWith("data:") ? base64.substring(base64.indexOf(',') + 1)
                : base64;
        byte[] pdf;
        try {
            pdf = Base64.getDecoder().decode(b.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El PDF adjunto llegó dañado. Vuelva a intentarlo");
        }
        if (pdf.length == 0) throw new GlobalException(HttpStatus.BAD_REQUEST, "El PDF adjunto está vacío");
        if (pdf.length > MAX_ADJUNTO)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El PDF supera 10 MB: no se puede enviar por correo");
        return pdf;
    }

    /** Sin rutas ni caracteres raros y siempre con .pdf. */
    static String nombreArchivo(String nombre) {
        String n = nombre == null ? "documento" : nombre.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "").trim();
        if (n.isEmpty()) n = "documento";
        return n.toLowerCase().endsWith(".pdf") ? n : n + ".pdf";
    }

    private static void exigirAdjunto(CorreoDtos.Enviar dto) {
        if (dto.getAdjunto() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Falta el PDF del documento");
    }

    /** Historial de correos de un documento (cualquier tipo). */
    public List<CorreoDtos.Fila> historial(Integer empresaId, String tipo, Long documentoId) {
        return query.historial(empresaId, List.of(tipo), documentoId);
    }

    /** 503 si falta RESEND_API_KEY o CORREO_REMITENTE. */
    public void exigirConfigurado() {
        if (!resend.configurado())
            throw new GlobalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El envío de correos no está configurado. Comuníquese con Aura");
    }

    private DocumentoCorreo factura(Integer empresaId, Long id) {
        DocumentoCorreo d = query.factura(id, empresaId);
        if (d == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Factura no encontrada");
        return d;
    }

    private DocumentoCorreo cotizacion(Integer empresaId, Long id) {
        DocumentoCorreo d = query.cotizacion(id, empresaId);
        if (d == null) throw new GlobalException(HttpStatus.NOT_FOUND, "Cotización no encontrada");
        return d;
    }

    /** Nombre que ve el cliente como remitente: el comercial o la razón social. */
    public String nombreEmpresa(Integer empresaId) {
        EmpresaCorreo e = query.empresa(empresaId);
        return e != null && e.nombre() != null ? e.nombre() : "Aura";
    }

    private static String saludo(String nombre) {
        return nombre == null || nombre.isBlank() ? "" : nombre.trim();
    }

    private static List<String> lista(String correo) {
        return correo == null || correo.isBlank() ? List.of() : List.of(correo.trim());
    }

    private static List<String> separar(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return List.of(csv.split(","));
    }
}
