package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.documento_soporte.DocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.EmitirDocumentoSoporteDto;
import com.cloud_technological.aura_pos.dto.documento_soporte.PreviaDocumentoSoporteDto;
import com.cloud_technological.aura_pos.entity.CompraDetalleEntity;
import com.cloud_technological.aura_pos.entity.CompraEntity;
import com.cloud_technological.aura_pos.entity.CompraPagoEntity;
import com.cloud_technological.aura_pos.entity.DocumentoSoporteEntity;
import com.cloud_technological.aura_pos.entity.GastoEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.compras.CompraJPARepository;
import com.cloud_technological.aura_pos.repositories.compras.CompraPagoJPARepository;
import com.cloud_technological.aura_pos.repositories.detalle_compras.CompraDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.documento_soporte.DocumentoSoporteJPARepository;
import com.cloud_technological.aura_pos.repositories.gastos.GastoJPARepository;
import com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.DocumentoSoporteService;
import com.cloud_technological.aura_pos.services.documento_soporte.DocumentoSoporteArmador;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Documento soporte electrónico de una compra o un gasto a un proveedor no
 * obligado a facturar.
 *
 * <p>No genera asiento: la compra o el gasto ya se contabilizaron al
 * registrarse. Esto es el soporte fiscal que los vuelve deducibles. Va por la
 * v1 de Factus, la misma con la que la cuenta ya factura.
 */
@Service
@RequiredArgsConstructor
public class DocumentoSoporteServiceImpl implements DocumentoSoporteService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final DocumentoSoporteJPARepository repo;
    private final CompraJPARepository compraRepo;
    private final CompraDetalleJPARepository compraDetalleRepo;
    private final CompraPagoJPARepository compraPagoRepo;
    private final GastoJPARepository gastoRepo;
    private final TerceroJPARepository terceroRepo;
    private final MunicipioQueryRepository municipioRepo;
    private final FactusDocumentoSoporteClient factus;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Todo lo necesario para armar el documento, leído del origen. */
    private record Origen(String numero, TerceroEntity tercero,
            DocumentoSoporteArmador.Proveedor proveedor, List<DocumentoSoporteArmador.Item> items,
            DocumentoSoporteArmador.Retenciones retenciones, DocumentoSoporteArmador.Pago pago,
            String municipioNombre) {
    }

    // ── Previa ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PreviaDocumentoSoporteDto previa(Integer empresaId, String origenTipo, Long origenId) {
        String tipo = normalizarTipo(origenTipo);
        Origen o = cargarOrigen(empresaId, tipo, origenId);
        DocumentoSoporteArmador.Resultado r = DocumentoSoporteArmador.armar(
                "PREVIA", null, null, o.proveedor(), o.items(), o.retenciones(), o.pago());

        PreviaDocumentoSoporteDto dto = new PreviaDocumentoSoporteDto();
        dto.setOrigenTipo(tipo);
        dto.setOrigenId(origenId);
        dto.setOrigenNumero(o.numero());
        if (o.tercero() != null) {
            dto.setTerceroId(o.tercero().getId());
            dto.setProveedorDocumento(o.proveedor().numeroDocumento()
                    + (o.proveedor().dv() != null && !o.proveedor().dv().isBlank()
                            ? "-" + o.proveedor().dv() : ""));
            dto.setProveedorNombre(o.proveedor().nombre());
            dto.setProveedorDireccion(o.proveedor().direccion());
            dto.setProveedorMunicipio(o.municipioNombre());
        }
        for (DocumentoSoporteArmador.Item it : o.items()) {
            PreviaDocumentoSoporteDto.Item i = new PreviaDocumentoSoporteDto.Item();
            i.setCodigo(it.codigo());
            i.setNombre(it.nombre());
            i.setCantidad(it.cantidad());
            i.setPrecio(it.precio());
            i.setDescuentoPct(it.descuentoPct());
            i.setIvaPct(it.ivaPct());
            dto.getItems().add(i);
        }
        dto.setBase(r.total().subtract(r.iva()));
        dto.setIva(r.iva());
        dto.setTotal(r.total());
        dto.setRetenciones(r.retenciones());
        dto.setNetoAPagar(r.netoAPagar());
        dto.setFormaPago(o.pago().credito() ? "CREDITO" : "CONTADO");
        dto.getFaltantes().addAll(r.faltantes());
        dto.getAdvertencias().addAll(r.advertencias());

        for (DocumentoSoporteEntity e : repo.findByEmpresaIdAndOrigenTipoAndOrigenIdOrderByIdDesc(
                empresaId, tipo, origenId)) {
            if (DocumentoSoporteEntity.ESTADO_ACEPTADO.equals(e.getEstado())) {
                dto.setAceptado(toDto(e, o.proveedor().nombre()));
            } else {
                dto.getIntentos().add(toDto(e, o.proveedor().nombre()));
            }
        }
        dto.setPuedeEmitirse(r.puedeEmitirse() && dto.getAceptado() == null);
        return dto;
    }

    // ── Emitir ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DocumentoSoporteDto emitir(Integer empresaId, Integer usuarioId, EmitirDocumentoSoporteDto req) {
        String tipo = normalizarTipo(req.getOrigenTipo());
        if (repo.existsByEmpresaIdAndOrigenTipoAndOrigenIdAndEstado(empresaId, tipo,
                req.getOrigenId(), DocumentoSoporteEntity.ESTADO_ACEPTADO)) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Este documento ya tiene un documento soporte aceptado por la DIAN");
        }
        Origen o = cargarOrigen(empresaId, tipo, req.getOrigenId());

        // Cada intento lleva su propia referencia: Factus rechaza repetirla, y
        // un intento rechazado puede haber quedado guardado allá sin validar.
        String reference = "DS-" + tipo.charAt(0) + req.getOrigenId() + "-"
                + (System.currentTimeMillis() / 1000);
        DocumentoSoporteArmador.Resultado r = DocumentoSoporteArmador.armar(reference,
                req.getNumberingRangeId(), req.getObservacion(), o.proveedor(), o.items(),
                o.retenciones(), o.pago());
        if (!r.puedeEmitirse()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Faltan datos para emitir el documento soporte: " + String.join("; ", r.faltantes()));
        }

        String body = r.payload().toString();
        FactusDocumentoSoporteClient.Respuesta resp = factus.validar(empresaId, body);

        DocumentoSoporteEntity e = new DocumentoSoporteEntity();
        e.setEmpresaId(empresaId);
        e.setOrigenTipo(tipo);
        e.setOrigenId(req.getOrigenId());
        e.setTerceroId(o.tercero() != null ? o.tercero().getId() : null);
        e.setReferenceCode(reference);
        e.setNumberingRangeId(req.getNumberingRangeId());
        e.setTotal(r.total());
        e.setRetenciones(r.retenciones());
        e.setPayloadJson(body);
        e.setResponseJson(resp.body());
        e.setUsuarioId(usuarioId);
        e.setNumero(extraer(resp.body(), "data/support_document/number", "data/number",
                "data/data/number", "number"));
        // En el documento soporte el código único se llama CUDS.
        e.setCude(extraer(resp.body(), "data/support_document/cuds",
                "data/support_document/cude", "data/cuds", "data/cude"));

        boolean aceptado = resp.exitoso() && (e.getCude() != null || e.getNumero() != null);
        e.setEstado(aceptado ? DocumentoSoporteEntity.ESTADO_ACEPTADO
                : DocumentoSoporteEntity.ESTADO_RECHAZADO);
        if (!aceptado) {
            e.setMensajeError(mensajeError(resp));
        }
        e.setUpdatedAt(LocalDateTime.now());
        e = repo.save(e);

        // El gasto ya tenía dónde anotar su soporte: se llena solo.
        if (aceptado && DocumentoSoporteEntity.ORIGEN_GASTO.equals(tipo)) {
            GastoEntity g = gastoRepo.findByIdAndEmpresaId(req.getOrigenId(), empresaId).orElse(null);
            if (g != null) {
                g.setTipoDocSoporte("DOCUMENTO_SOPORTE");
                g.setNumeroDocSoporte(e.getNumero());
                gastoRepo.save(g);
            }
        }
        return toDto(e, o.proveedor().nombre());
    }

    // ── Consultas y acciones ────────────────────────────────────────────────

    @Override
    public List<DocumentoSoporteDto> listar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        List<DocumentoSoporteDto> out = new ArrayList<>();
        for (DocumentoSoporteEntity e : repo.findByEmpresaIdAndCreatedAtBetweenOrderByIdDesc(
                empresaId, d.atStartOfDay(), h.plusDays(1).atStartOfDay())) {
            String nombre = e.getTerceroId() != null
                    ? terceroRepo.findByIdAndEmpresaId(e.getTerceroId(), empresaId)
                            .map(DocumentoSoporteServiceImpl::nombre).orElse(null)
                    : null;
            out.add(toDto(e, nombre));
        }
        return out;
    }

    @Override
    public String pdf(Integer empresaId, Long id) {
        DocumentoSoporteEntity e = cargar(empresaId, id);
        if (!DocumentoSoporteEntity.ESTADO_ACEPTADO.equals(e.getEstado()) || e.getNumero() == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo los documentos soporte aceptados por la DIAN tienen PDF");
        }
        return factus.pdf(empresaId, e.getNumero());
    }

    /**
     * Descarta un intento rechazado. En Factus se borra si quedó guardado sin
     * validar (así no estorba en su listado); aquí queda como ELIMINADO, nunca
     * se borra la fila.
     */
    @Override
    @Transactional
    public DocumentoSoporteDto descartar(Integer empresaId, Long id) {
        DocumentoSoporteEntity e = cargar(empresaId, id);
        if (DocumentoSoporteEntity.ESTADO_ACEPTADO.equals(e.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un documento soporte aceptado por la DIAN no se elimina: se corrige con "
                            + "una nota de ajuste");
        }
        factus.eliminar(empresaId, e.getReferenceCode());
        e.setEstado(DocumentoSoporteEntity.ESTADO_ELIMINADO);
        e.setUpdatedAt(LocalDateTime.now());
        return toDto(repo.save(e), null);
    }

    // ── Lectura del origen ──────────────────────────────────────────────────

    private Origen cargarOrigen(Integer empresaId, String tipo, Long origenId) {
        return DocumentoSoporteEntity.ORIGEN_COMPRA.equals(tipo)
                ? desdeCompra(empresaId, origenId)
                : desdeGasto(empresaId, origenId);
    }

    private Origen desdeCompra(Integer empresaId, Long compraId) {
        CompraEntity c = compraRepo.findByIdAndEmpresaId(compraId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Compra no encontrada"));
        if ("ANULADA".equals(c.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La compra está anulada: no lleva documento soporte");
        }
        if ("NOTA_CREDITO".equals(c.getTipoDocumento())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Una nota crédito de compra no lleva documento soporte: se corrige con una "
                            + "nota de ajuste al documento soporte de la compra original");
        }

        List<DocumentoSoporteArmador.Item> items = new ArrayList<>();
        for (CompraDetalleEntity d : compraDetalleRepo.findByCompraId(compraId)) {
            BigDecimal cantidad = nz(d.getCantidad()).abs();
            BigDecimal neto = nz(d.getSubtotalLinea()).abs();
            BigDecimal descuento = nz(d.getDescuentoValor()).abs();
            // Precio unitario antes del descuento: con presentaciones el costo
            // unitario guardado es el de la unidad pequeña, así que se deduce
            // de la línea en vez de confiar en él.
            BigDecimal precio = cantidad.signum() > 0
                    ? neto.add(descuento).divide(cantidad, 2, RoundingMode.HALF_UP)
                    : nz(d.getCostoUnitario());
            BigDecimal impuesto = nz(d.getImpuestoValor()).abs();
            BigDecimal ivaPct = neto.signum() > 0
                    ? impuesto.multiply(CIEN).divide(neto, 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            String codigo = d.getProducto() != null
                    ? (d.getProducto().getSku() != null ? d.getProducto().getSku()
                            : String.valueOf(d.getProducto().getId()))
                    : "SIN-REF";
            String nombre = d.getProducto() != null ? d.getProducto().getNombre() : "Producto";
            items.add(new DocumentoSoporteArmador.Item(codigo, nombre, cantidad, precio,
                    nz(d.getDescuentoPct()), ivaPct));
        }

        boolean credito = "CREDITO".equalsIgnoreCase(c.getFormaPago());
        String metodo = null;
        if (!credito) {
            List<CompraPagoEntity> pagos = compraPagoRepo.findByCompraIdAndActivoTrue(compraId);
            metodo = pagos.isEmpty() ? null : pagos.get(0).getMetodoPago();
        }
        TerceroEntity t = c.getProveedor();
        return new Origen(c.getNumeroCompra() != null ? c.getNumeroCompra() : "Compra #" + compraId,
                t, proveedor(t), items,
                new DocumentoSoporteArmador.Retenciones(c.getRetefuentePct(), c.getReteivaPct(),
                        c.getTotalRetenciones()),
                new DocumentoSoporteArmador.Pago(credito, metodo),
                municipio(t));
    }

    private Origen desdeGasto(Integer empresaId, Long gastoId) {
        GastoEntity g = gastoRepo.findByIdAndEmpresaId(gastoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Gasto no encontrado"));
        if (!"ACTIVO".equals(g.getEstado())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El gasto no está activo: no lleva documento soporte");
        }
        TerceroEntity t = g.getTerceroId() != null
                ? terceroRepo.findByIdAndEmpresaId(g.getTerceroId(), empresaId).orElse(null)
                : null;

        // La base del IVA es el valor del servicio; si no se registró, el monto.
        BigDecimal base = nz(g.getBaseIva()).signum() > 0 ? g.getBaseIva() : nz(g.getMonto());
        String nombre = g.getDescripcion() != null && !g.getDescripcion().isBlank()
                ? g.getDescripcion() : (g.getCategoria() != null ? g.getCategoria() : "Gasto");
        List<DocumentoSoporteArmador.Item> items = List.of(new DocumentoSoporteArmador.Item(
                "GASTO-" + gastoId, nombre, BigDecimal.ONE, base, BigDecimal.ZERO, nz(g.getTarifaIva())));

        BigDecimal retenciones = nz(g.getValorRetefuente()).add(nz(g.getValorReteica()));
        boolean credito = "CREDITO".equalsIgnoreCase(g.getFormaPago());
        return new Origen("Gasto #" + gastoId, t, t != null ? proveedor(t) : null, items,
                new DocumentoSoporteArmador.Retenciones(g.getTarifaRetefuente(), null, retenciones),
                new DocumentoSoporteArmador.Pago(credito, g.getMetodoPago()),
                t != null ? municipio(t) : null);
    }

    /** El municipio de Aura ya es el ID de Factus: la tabla se sembró de ahí. */
    private DocumentoSoporteArmador.Proveedor proveedor(TerceroEntity t) {
        if (t == null) return null;
        return new DocumentoSoporteArmador.Proveedor(t.getTipoDocumento(), t.getNumeroDocumento(),
                t.getDv(), nombre(t), t.getNombreComercial(), t.getDireccion(), t.getCodigoPais(),
                t.getMunicipioId(), t.getEmailFe() != null && !t.getEmailFe().isBlank()
                        ? t.getEmailFe() : t.getEmail(),
                t.getTelefono(), t.getRegimen());
    }

    private String municipio(TerceroEntity t) {
        if (t == null || t.getMunicipioId() == null) return t != null ? t.getMunicipio() : null;
        var m = municipioRepo.findById(t.getMunicipioId());
        return m != null ? m.getNombre() : t.getMunicipio();
    }

    private static String nombre(TerceroEntity t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial().trim();
        String n = ((t.getNombres() != null ? t.getNombres() : "") + " "
                + (t.getApellidos() != null ? t.getApellidos() : "")).trim();
        return n.isEmpty() ? null : n;
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private DocumentoSoporteEntity cargar(Integer empresaId, Long id) {
        return repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "Documento soporte no encontrado"));
    }

    private static String normalizarTipo(String tipo) {
        String t = tipo != null ? tipo.trim().toUpperCase() : "";
        if (!DocumentoSoporteEntity.ORIGEN_COMPRA.equals(t) && !DocumentoSoporteEntity.ORIGEN_GASTO.equals(t)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El origen debe ser COMPRA o GASTO");
        }
        return t;
    }

    /**
     * Lo que dijo Factus/DIAN, legible: el mensaje general y, si vienen, los
     * errores por campo ({@code data.errors} o {@code errors}).
     */
    private String mensajeError(FactusDocumentoSoporteClient.Respuesta resp) {
        List<String> partes = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(resp.body() != null ? resp.body() : "{}");
            if (root.hasNonNull("message")) partes.add(root.get("message").asText());
            for (JsonNode errores : List.of(root.path("data").path("errors"), root.path("errors"))) {
                if (errores.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> it = errores.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> f = it.next();
                        partes.add(f.getKey() + ": " + (f.getValue().isArray() && !f.getValue().isEmpty()
                                ? f.getValue().get(0).asText() : f.getValue().asText()));
                    }
                } else if (errores.isArray()) {
                    errores.forEach(x -> partes.add(x.asText()));
                }
            }
        } catch (Exception ignored) {
            // cuerpo no JSON: se reporta el código HTTP abajo
        }
        if (partes.isEmpty()) partes.add("Factus respondió HTTP " + resp.status());
        String msg = String.join(" | ", partes);
        return msg.length() > 2000 ? msg.substring(0, 2000) : msg;
    }

    private String extraer(String body, String... rutas) {
        if (body == null) return null;
        try {
            JsonNode root = mapper.readTree(body);
            for (String ruta : rutas) {
                JsonNode n = root;
                for (String p : ruta.split("/")) {
                    n = n != null ? n.get(p) : null;
                }
                if (n != null && !n.isNull() && !n.asText().isBlank()) return n.asText();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private DocumentoSoporteDto toDto(DocumentoSoporteEntity e, String terceroNombre) {
        DocumentoSoporteDto d = new DocumentoSoporteDto();
        d.setId(e.getId());
        d.setOrigenTipo(e.getOrigenTipo());
        d.setOrigenId(e.getOrigenId());
        d.setTerceroId(e.getTerceroId());
        d.setTerceroNombre(terceroNombre);
        d.setReferenceCode(e.getReferenceCode());
        d.setNumero(e.getNumero());
        d.setCude(e.getCude());
        d.setEstado(e.getEstado());
        d.setTotal(e.getTotal());
        d.setRetenciones(e.getRetenciones());
        d.setMensajeError(e.getMensajeError());
        d.setCreatedAt(e.getCreatedAt());
        return d;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
