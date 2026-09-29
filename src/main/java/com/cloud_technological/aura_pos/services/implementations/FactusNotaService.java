package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.dto.factus.CrearNotaCreditoDto;
import com.cloud_technological.aura_pos.entity.NotaElectronicaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.NotaElectronicaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente Factus v1 para notas crédito/débito y búsqueda de facturas.
 *
 * <p>La validación de la nota es una API pura ({@code /v1/credit-notes/validate}
 * sobre {@code factus.api.base-url}); la búsqueda de facturas es un endpoint del
 * panel admin ({@code /bills/get-bills} sobre {@code factus.admin.base-url}).
 */
@Slf4j
@Service
public class FactusNotaService {

    private final String apiBaseUrl;
    private final String creditNoteUrl;
    private final String debitNoteUrl;
    private final String getBillsUrl;
    private final String getBillDetailUrl;
    private final RestTemplate restTemplate;
    private final FactusTokenService tokenService;
    private final NotaElectronicaJPARepository notaRepo;
    private final com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository ventaRepo;
    private final com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository ventaDetalleRepo;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final ObjectMapper mapper;

    public FactusNotaService(
            @Value("${factus.api.base-url}") String apiBaseUrl,
            @Value("${factus.admin.base-url:https://app.factus.com.co/administrador}") String adminBaseUrl,
            RestTemplate restTemplate,
            FactusTokenService tokenService,
            NotaElectronicaJPARepository notaRepo,
            com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository ventaRepo,
            com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository ventaDetalleRepo,
            org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.apiBaseUrl = apiBaseUrl;
        this.creditNoteUrl = apiBaseUrl + "/v1/credit-notes/validate";
        this.debitNoteUrl = apiBaseUrl + "/v1/debit-notes/validate";
        // Listado de facturas por la API (usa el bearer). El panel admin
        // (app.factus.com.co/administrador/bills/get-bills) requiere sesión, no token.
        this.getBillsUrl = apiBaseUrl + "/v1/bills";
        this.getBillDetailUrl = adminBaseUrl + "/bills/get-bill";
        this.restTemplate = restTemplate;
        this.tokenService = tokenService;
        this.notaRepo = notaRepo;
        this.ventaRepo = ventaRepo;
        this.ventaDetalleRepo = ventaDetalleRepo;
        this.eventPublisher = eventPublisher;
        this.mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Prefill de la nota: intenta el DETALLE de Factus ({@code get-bill?bill_id},
     * que trae products con IVA + CUFE); si el token no autoriza ese host o no
     * devuelve productos, cae a la venta LOCAL por número. Así siempre hay datos.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto prefill(
            Integer empresaId, Long billId, String numero) {
        if (billId != null) {
            try {
                var desdeFactus = billDetalleFactus(empresaId, billId);
                if (desdeFactus != null && desdeFactus.getItems() != null && !desdeFactus.getItems().isEmpty())
                    return desdeFactus;
            } catch (Exception e) {
                log.warn("[Factus Notas] Detalle Factus del bill {} no disponible ({}); uso venta local.",
                        billId, e.getMessage());
            }
        }
        return facturaLocal(empresaId, numero);
    }

    /** Detalle de una factura desde Factus ({@code get-bill?bill_id}). */
    private com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto billDetalleFactus(
            Integer empresaId, Long billId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));
        String url = getBillDetailUrl + "?bill_id=" + billId;
        JsonNode root;
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            root = parse(raw.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Notas] GET {} -> HTTP {}", url, e.getStatusCode());
            return null;
        }
        JsonNode bill = root.get("bill");
        JsonNode products = root.get("products");
        if (bill == null || products == null || !products.isArray() || products.isEmpty()) return null;

        var dto = new com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto();
        dto.setNumero(txt(bill, "number"));
        dto.setCufe(txt(bill, "cufe"));
        String total = txt(bill, "total");
        if (total != null) try { dto.setTotal(new BigDecimal(total)); } catch (NumberFormatException ignored) { }

        java.util.List<com.cloud_technological.aura_pos.dto.factus.FactusItemDto> items = new java.util.ArrayList<>();
        for (JsonNode p : products) {
            var it = new com.cloud_technological.aura_pos.dto.factus.FactusItemDto();
            it.setCodeReference(txtOr(p, "code_reference", "SIN-REF"));
            it.setName(txt(p, "name"));
            it.setQuantity(bd(txtOr(p, "quantity", "1")));
            it.setDiscountRate(bd(txtOr(p, "discount_rate", "0")));
            it.setPrice(bd(txtOr(p, "price", "0")));   // Factus lo entrega CON IVA (lo que pide la nota)
            it.setTaxRate(nestedText(p, "tax_rate", "rate", "0.00"));
            it.setTributeId(nestedInt(p, "tribute", "id", 1));
            it.setUnitMeasureId(nestedInt(p, "unit_measure", "id", 70));
            it.setStandardCodeId(nestedInt(p, "standard_code", "id", 1));
            it.setIsExcluded(p.get("is_excluded") != null ? p.get("is_excluded").asInt() : 0);
            items.add(it);
        }
        dto.setItems(items);
        return dto;
    }

    private String txt(JsonNode n, String f) {
        JsonNode v = n != null ? n.get(f) : null;
        return v != null && !v.isNull() ? v.asText() : null;
    }
    private String txtOr(JsonNode n, String f, String def) {
        String v = txt(n, f);
        return v != null && !v.isBlank() ? v : def;
    }
    private String nestedText(JsonNode n, String obj, String f, String def) {
        JsonNode o = n != null ? n.get(obj) : null;
        String v = txt(o, f);
        return v != null ? v : def;
    }
    private int nestedInt(JsonNode n, String obj, String f, int def) {
        JsonNode o = n != null ? n.get(obj) : null;
        JsonNode v = o != null ? o.get(f) : null;
        return v != null && v.isNumber() ? v.asInt() : def;
    }
    private BigDecimal bd(String s) {
        try { return new BigDecimal(s); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    /**
     * Prefill de la nota desde la venta LOCAL (por número de factura Factus): CUFE
     * e ítems con su IVA, tomados de la BD (no de Factus, que no los devuelve en el
     * listado).
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto facturaLocal(Integer empresaId, String numero) {
        var venta = ventaRepo.findByEmpresaIdAndFactusNumero(empresaId, numero)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "No se encontró la venta local de la factura " + numero));

        var dto = new com.cloud_technological.aura_pos.dto.factus.FacturaLocalDto();
        dto.setVentaId(venta.getId());
        dto.setNumero(venta.getFactusNumero());
        dto.setCufe(venta.getCufe());
        dto.setTotal(venta.getTotalPagar());

        java.util.List<com.cloud_technological.aura_pos.dto.factus.FactusItemDto> items = new java.util.ArrayList<>();
        for (var d : ventaDetalleRepo.findByVentaId(venta.getId())) {
            var it = new com.cloud_technological.aura_pos.dto.factus.FactusItemDto();
            if (d.getProducto() != null) {
                it.setCodeReference(d.getProducto().getSku() != null
                        ? d.getProducto().getSku() : String.valueOf(d.getProducto().getId()));
                it.setName(d.getProducto().getNombre());
            } else {
                it.setCodeReference("SIN-REF");
                it.setName("Producto");
            }
            BigDecimal cantidad = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ONE;
            BigDecimal impuesto = d.getImpuestoValor() != null ? d.getImpuestoValor() : BigDecimal.ZERO;
            BigDecimal conIva = d.getSubtotalLinea() != null ? d.getSubtotalLinea() : BigDecimal.ZERO;
            BigDecimal base = conIva.subtract(impuesto);   // sin IVA (gotcha: subtotalLinea incluye IVA)

            it.setQuantity(cantidad);
            it.setDiscountRate(BigDecimal.ZERO);   // el neto ya refleja el descuento
            // Factus/nota crédito esperan el precio CON IVA (subtotalLinea ya lo incluye).
            BigDecimal precioUnit = cantidad.signum() > 0
                    ? conIva.divide(cantidad, 2, java.math.RoundingMode.HALF_UP) : conIva;
            it.setPrice(precioUnit);

            boolean tieneIva = impuesto.signum() > 0 && base.signum() > 0;
            int pct = tieneIva
                    ? impuesto.multiply(new BigDecimal("100")).divide(base, 0, java.math.RoundingMode.HALF_UP).intValue()
                    : 0;
            it.setTaxRate(String.format("%d.00", pct));
            it.setTributeId(tieneIva ? 1 : 3);
            it.setUnitMeasureId(70);
            it.setStandardCodeId(1);
            it.setIsExcluded(tieneIva ? 0 : 1);
            items.add(it);
        }
        dto.setItems(items);
        return dto;
    }

    /** Busca facturas emitidas para referenciarlas en una nota. */
    public JsonNode buscarFacturas(Integer empresaId, String search) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));

        // La API /v1/bills filtra por campo: si el término son solo dígitos se
        // asume identificación del cliente; si trae prefijo/letras, número de factura.
        String s = search != null ? search.trim() : "";
        String filtro = s.matches("\\d+") ? "filter[identification]" : "filter[number]";
        String url = getBillsUrl + "?" + filtro + "="
                + java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
            return parse(raw.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Notas] GET {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            return parse(e.getResponseBodyAsString());
        }
    }

    /**
     * Crea y valida una nota crédito, y persiste el resultado.
     *
     * @return la respuesta cruda de Factus (para que el front muestre CUDE, número, etc.).
     */
    @org.springframework.transaction.annotation.Transactional
    public JsonNode crearNotaCredito(Integer empresaId, CrearNotaCreditoDto dto) {
        return enviar(empresaId, dto, creditNoteUrl, NotaElectronicaEntity.Tipo.CREDITO, "credit_note");
    }

    @org.springframework.transaction.annotation.Transactional
    public JsonNode crearNotaDebito(Integer empresaId, CrearNotaCreditoDto dto) {
        return enviar(empresaId, dto, debitNoteUrl, NotaElectronicaEntity.Tipo.DEBITO, "debit_note");
    }

    /** credit-notes | debit-notes según el tipo de la nota. */
    private String recurso(String tipo) {
        return NotaElectronicaEntity.Tipo.CREDITO.equals(tipo) ? "credit-notes" : "debit-notes";
    }

    private NotaElectronicaEntity cargar(Integer empresaId, Long id) {
        return notaRepo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nota no encontrada"));
    }

    private HttpHeaders authHeaders(Integer empresaId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));
        return headers;
    }

    /** PDF de la nota (Base64) desde Factus: GET /v1/{recurso}/download-pdf/{number}. */
    public String descargarPdf(Integer empresaId, Long id) {
        NotaElectronicaEntity ne = cargar(empresaId, id);
        if (ne.getNumero() == null || ne.getNumero().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nota no tiene número (no fue aceptada por la DIAN).");
        String url = apiBaseUrl + "/v1/" + recurso(ne.getTipo()) + "/download-pdf/" + ne.getNumero();
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(empresaId)), String.class);
            String pdf = extraer(raw.getBody(), "data/pdf_base_64_encoded");
            if (pdf == null || pdf.isBlank())
                throw new GlobalException(HttpStatus.BAD_GATEWAY, "Factus no devolvió el PDF.");
            return pdf;
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Notas] PDF {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener el PDF de la nota en Factus.");
        }
    }

    /**
     * Elimina la nota en Factus (DELETE /v1/{recurso}/reference/{reference_code}) y,
     * si Factus la borra, quita el registro local. Factus solo permite borrar notas
     * NO validadas por la DIAN; una nota aceptada devolverá error.
     */
    @org.springframework.transaction.annotation.Transactional
    public void eliminar(Integer empresaId, Long id) {
        NotaElectronicaEntity ne = cargar(empresaId, id);
        String url = apiBaseUrl + "/v1/" + recurso(ne.getTipo()) + "/reference/" + ne.getReferenceCode();
        try {
            restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders(empresaId)), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Notas] DELETE {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            String msg = extraer(e.getResponseBodyAsString(), "message");
            throw new GlobalException(HttpStatus.CONFLICT, msg != null ? msg
                    : "Factus no permitió eliminar la nota (¿ya validada por la DIAN?).");
        }
        notaRepo.delete(ne);
    }

    /**
     * Reenvía la nota por correo (POST /v1/{recurso}/send-email/{number}). Factus ya
     * la envía al validar; esto es para reenviar. Si no se indica correo, usa el del
     * cliente de la respuesta de Factus.
     */
    public void reenviarCorreo(Integer empresaId, Long id, String email) {
        NotaElectronicaEntity ne = cargar(empresaId, id);
        if (ne.getNumero() == null || ne.getNumero().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nota no tiene número (no fue aceptada por la DIAN).");
        String destino = (email != null && !email.isBlank())
                ? email.trim()
                : extraer(ne.getResponseJson(), "data/customer/email");
        if (destino == null || destino.isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay correo del cliente; indica uno.");
        String url = apiBaseUrl + "/v1/" + recurso(ne.getTipo()) + "/send-email/" + ne.getNumero();
        try {
            String body = mapper.writeValueAsString(java.util.Map.of("email", destino));
            restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(body, authHeaders(empresaId)), String.class);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.warn("[Factus Notas] send-email {} -> HTTP {} | {}", url, e.getStatusCode(), e.getResponseBodyAsString());
            throw new GlobalException(HttpStatus.BAD_GATEWAY, "Factus no pudo enviar el correo.");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR, "Error serializando el correo.");
        }
    }

    /**
     * Envía la nota (crédito o débito) a Factus y persiste el resultado. Crédito y
     * débito comparten estructura de payload; solo cambian la URL, el tipo y la
     * clave del nodo de respuesta ({@code credit_note} / {@code debit_note}).
     */
    private JsonNode enviar(Integer empresaId, CrearNotaCreditoDto dto, String url,
                            String tipo, String responseKey) {
        if (dto.getReferenceCode() == null || dto.getReferenceCode().isBlank())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nota necesita un reference_code");

        // Guarda anti-reenvío: no duplicar una nota ya emitida.
        if (notaRepo.existsByEmpresaIdAndReferenceCode(empresaId, dto.getReferenceCode()))
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Ya existe una nota con la referencia " + dto.getReferenceCode());

        String requestBody;
        try {
            requestBody = mapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Error serializando la nota: " + e.getMessage());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(tokenService.obtenerToken(empresaId));

        String responseBody;
        boolean exitoso;
        try {
            ResponseEntity<String> raw = restTemplate.exchange(url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
            responseBody = raw.getBody();
            exitoso = raw.getStatusCode().is2xxSuccessful();
            log.info("[Factus Notas] {} HTTP {} | {}", tipo, raw.getStatusCode(), responseBody);
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            responseBody = e.getResponseBodyAsString();
            exitoso = false;
            log.warn("[Factus Notas] {} HTTP {} | {}", tipo, e.getStatusCode(), responseBody);
        }

        NotaElectronicaEntity ne = persistir(empresaId, dto, requestBody, responseBody, exitoso, tipo, responseKey);

        // F5: contabilizar la nota aceptada (reversa de ingreso). El listener corre
        // AFTER_COMMIT: un fallo del posting NO tumba la emisión de la nota.
        if (NotaElectronicaEntity.Estado.ACEPTADO.equals(ne.getEstado())) {
            String tipoOrigen = NotaElectronicaEntity.Tipo.CREDITO.equals(tipo)
                    ? "NOTA_CREDITO" : "NOTA_DEBITO";
            eventPublisher.publishEvent(
                    new com.cloud_technological.aura_pos.contabilidad.infrastructure.event.DocumentoContabilizableEvent(
                            tipoOrigen, ne.getId(), empresaId, null));
        }
        return parse(responseBody);
    }

    private NotaElectronicaEntity persistir(Integer empresaId, CrearNotaCreditoDto dto, String requestBody,
                           String responseBody, boolean exitoso, String tipo, String responseKey) {
        NotaElectronicaEntity ne = notaRepo
                .findByEmpresaIdAndReferenceCode(empresaId, dto.getReferenceCode())
                .orElseGet(NotaElectronicaEntity::new);
        ne.setEmpresaId(empresaId);
        ne.setTipo(tipo);
        ne.setReferenceCode(dto.getReferenceCode());
        ne.setBillId(dto.getBillId());
        ne.setCustomizationId(dto.getCustomizationId());
        ne.setCorrectionConceptCode(dto.getCorrectionConceptCode());
        ne.setPayloadJson(requestBody);
        ne.setResponseJson(responseBody);

        ne.setNumero(extraer(responseBody, "data/" + responseKey + "/number"));
        ne.setCude(extraer(responseBody, "data/" + responseKey + "/cude"));
        String total = extraer(responseBody, "data/" + responseKey + "/total");
        if (total != null) {
            try { ne.setTotal(new BigDecimal(total)); } catch (NumberFormatException ignored) { }
        }

        // Base gravable + IVA para contabilizar (F5): se calculan de los items
        // enviados a Factus (price sin IVA, descuento y tarifa por línea).
        calcularBaseEIva(dto, ne);

        ne.setEstado(exitoso && ne.getCude() != null
                ? NotaElectronicaEntity.Estado.ACEPTADO
                : NotaElectronicaEntity.Estado.RECHAZADO);
        ne.setUpdatedAt(LocalDateTime.now());
        return notaRepo.save(ne);
    }

    /** Suma base e IVA por línea: base = price·cant·(1−desc%); iva = base·tarifa%. */
    private void calcularBaseEIva(CrearNotaCreditoDto dto, NotaElectronicaEntity ne) {
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal iva = BigDecimal.ZERO;
        if (dto.getItems() != null) {
            for (var it : dto.getItems()) {
                BigDecimal price = it.getPrice() != null ? it.getPrice() : BigDecimal.ZERO;
                BigDecimal cant = it.getQuantity() != null ? it.getQuantity() : BigDecimal.ONE;
                BigDecimal descPct = it.getDiscountRate() != null ? it.getDiscountRate() : BigDecimal.ZERO;
                BigDecimal bruto = price.multiply(cant);
                BigDecimal baseLinea = bruto.subtract(bruto.multiply(descPct).movePointLeft(2));
                BigDecimal tarifa = parseTarifa(it.getTaxRate());
                base = base.add(baseLinea);
                iva = iva.add(baseLinea.multiply(tarifa).movePointLeft(2));
            }
        }
        ne.setBaseGravable(base.setScale(2, java.math.RoundingMode.HALF_UP));
        ne.setIva(iva.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    private BigDecimal parseTarifa(String taxRate) {
        if (taxRate == null || taxRate.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(taxRate.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private JsonNode parse(String body) {
        try {
            return body != null ? mapper.readTree(body) : mapper.nullNode();
        } catch (Exception e) {
            return mapper.nullNode();
        }
    }

    /** Primer texto no nulo de una ruta 'a/b/c' del JSON. */
    private String extraer(String body, String ruta) {
        if (body == null) return null;
        try {
            JsonNode node = mapper.readTree(body);
            for (String parte : ruta.split("/")) {
                if (node == null) return null;
                node = node.get(parte);
            }
            return node != null && !node.isNull() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
