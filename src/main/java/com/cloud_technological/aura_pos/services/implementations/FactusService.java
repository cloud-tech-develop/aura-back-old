package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaRequest;
import com.cloud_technological.aura_pos.dto.factus.FactusBillDto;
import com.cloud_technological.aura_pos.dto.factus.FactusBillResponseDto;
import com.cloud_technological.aura_pos.dto.factus.FactusCreateBillRequestDto;
import com.cloud_technological.aura_pos.dto.factus.FactusCustomerDto;
import com.cloud_technological.aura_pos.dto.factus.FactusItemDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FactusService {

    private final String               factusBillUrl;
    private final RestTemplate         restTemplate;
    private final EmpresaJPARepository empresaRepository;
    private final FactusTokenService   factusTokenService;
    private final ObjectMapper         objectMapper;

    public FactusService(
            @org.springframework.beans.factory.annotation.Value("${factus.api.base-url}") String factusBaseUrl,
            RestTemplate restTemplate,
            EmpresaJPARepository empresaRepository,
            FactusTokenService factusTokenService) {
        this.factusBillUrl     = factusBaseUrl + "/v1/bills/validate";
        this.restTemplate      = restTemplate;
        this.empresaRepository = empresaRepository;
        this.factusTokenService = factusTokenService;
        this.objectMapper      = new ObjectMapper();
        this.objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @CircuitBreaker(name = "factus-bill", fallbackMethod = "facturaFallback")
    @Retry(name = "factus-bill")
    public FactusBillDto generarFactura(Integer empresaId,
                                         FacturaElectronicaRequest request) {

        String token = factusTokenService.obtenerToken(empresaId);

        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(
                        HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        FactusCreateBillRequestDto dto = buildFactusRequest(empresa, request);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        // Llamar como String para ver respuesta cruda en logs
        ResponseEntity<String> rawResponse = restTemplate.exchange(
                factusBillUrl,
                HttpMethod.POST,
                new HttpEntity<>(dto, headers),
                String.class);

        log.info("[Factus Bill] HTTP {} | Body: {}",
                rawResponse.getStatusCode(),
                rawResponse.getBody());

        // Deserializar manualmente — más control sobre errores
        FactusBillResponseDto body;
        try {
            body = objectMapper.readValue(rawResponse.getBody(), FactusBillResponseDto.class);
        } catch (Exception e) {
            log.error("[Factus Bill] Error deserializando: {}", e.getMessage());
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "Error procesando respuesta de Factus: " + e.getMessage());
        }

        // Validar que llegaron los datos del bill
        if (body == null || body.getData() == null || body.getData().getBill() == null) {
            log.error("[Factus Bill] bill es null. status={} message={}",
                    body != null ? body.getStatus() : "null",
                    body != null ? body.getMessage() : "null");
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "Factus no retornó datos de la factura. " +
                    (body != null ? body.getMessage() : "Sin respuesta"));
        }

        FactusBillDto bill = body.getData().getBill();
        log.info("[Factus Bill] ✅ Factura={} | CUFE={} | URL={}",
                bill.getNumber(), bill.getCufe(), bill.getPublicUrl());

        return bill;
    }

    private FactusCreateBillRequestDto buildFactusRequest(
            EmpresaEntity empresa, FacturaElectronicaRequest req) {

        FactusCreateBillRequestDto dto = new FactusCreateBillRequestDto();
        dto.setNumberingRangeId(empresa.getFactusNumberingRangeId());

        // reference_code: prefijo de empresa + identificador de la venta. Es la
        // llave con la que Factus rechaza duplicados, así que tiene que ser única
        // en la empresa y estable entre reintentos (ver VentaFacturaService).
        String prefijo = empresa.getFactusPrefijo() != null
                ? empresa.getFactusPrefijo() : "POS";
        dto.setReferenceCode(prefijo + "-" + req.getNumeroVenta());

        dto.setObservation(req.getObservacion() != null ? req.getObservacion() : "");
        dto.setPaymentMethodCode(req.getMetodoPago());

        // payment_form: "2"=Crédito si hay fecha distinta a hoy, "1"=Contado
        String hoy = LocalDate.now().toString(); // "YYYY-MM-DD"
        String vencimiento = req.getFechaVencimiento() != null
                ? req.getFechaVencimiento() : hoy;
        dto.setPaymentDueDate(vencimiento);
        dto.setPaymentForm(hoy.equals(vencimiento) ? "1" : "2");
        dto.setOperationType(10); // 10 = Estándar

        // ── Customer ────────────────────────────────────────────────
        FactusCustomerDto customer = new FactusCustomerDto();
        customer.setIdentification(req.getClienteDocumento());
        customer.setDv(req.getClienteDv());
        customer.setNames(req.getClienteNombre());
        customer.setEmail(req.getClienteEmail());
        customer.setPhone(req.getClienteTelefono());
        customer.setAddress(req.getClienteDireccion() != null
                ? req.getClienteDireccion() : "Sin dirección");
        // identification_document_id como String (Factus lo espera así)
        customer.setIdentificationDocumentId(
                String.valueOf(req.getClienteTipoDocumentoFactusId()));
        // municipality_id — default Bogotá (149) si no hay
        customer.setMunicipalityId(req.getClienteMunicipioId() != null
                ? String.valueOf(req.getClienteMunicipioId()) : "511");
        // legal_organization_id y tribute_id: persona natural por defecto
        // Antes siempre persona natural / no aplica: un NIT de empresa salía
        // como persona natural. Ahora viene del tercero (por defecto, natural).
        customer.setLegalOrganizationId(req.getClienteOrganizacionLegalId() != null
                ? req.getClienteOrganizacionLegalId() : "2");
        customer.setTributeId(req.getClienteTributoId() != null
                ? req.getClienteTributoId() : "21");
        dto.setCustomer(customer);

        // ── Items ────────────────────────────────────────────────────
        List<FactusItemDto> items = req.getItems().stream().map(item -> {
            FactusItemDto i = new FactusItemDto();
            i.setCodeReference(item.getSku() != null ? item.getSku() : "SIN-SKU");
            i.setName(item.getNombre());
            i.setQuantity(item.getCantidad());
            i.setPrice(item.getPrecioSinIva());
            i.setDiscountRate(item.getDescuentoPct() != null ? item.getDescuentoPct() : BigDecimal.ZERO);

            // tribute_id: 1=IVA (cualquier %), 3=No aplica (exento)
            String ivaPct = item.getIvaPorcentaje(); // "19.00", "5.00", "0.00"
            boolean tieneIva = ivaPct != null
                    && !ivaPct.trim().equals("0.00")
                    && !ivaPct.trim().equals("0");
            i.setTributeId(tieneIva ? 1 : 3);
            i.setTaxRate(ivaPct != null ? ivaPct : "0.00");
            i.setUnitMeasureId(70);   // 70=Unidad
            i.setStandardCodeId(1);
            i.setIsExcluded(0);
            return i;
        }).collect(Collectors.toList());
        dto.setItems(items);

        return dto;
    }

    /**
     * Antes devolvía null ante CUALQUIER error: un rechazo de validación de
     * Factus salía como "servicio no disponible" y un timeout (la factura pudo
     * haber llegado a la DIAN) no se distinguía de una conexión caída. Ahora:
     * <ul>
     *   <li>error de negocio o rechazo HTTP de Factus → se muestra tal cual;</li>
     *   <li>se envió y no llegó respuesta (read timeout) → estado incierto;</li>
     *   <li>no se pudo conectar o el circuito está abierto → no disponible (null).</li>
     * </ul>
     */
    public FactusBillDto facturaFallback(Integer empresaId,
                                          FacturaElectronicaRequest request,
                                          Throwable ex) {
        if (ex instanceof GlobalException ge) throw ge;
        if (ex instanceof org.springframework.web.client.HttpStatusCodeException he) {
            log.error("[Factus Bill] rechazo HTTP {} empresa={} body={}",
                    he.getStatusCode(), empresaId, he.getResponseBodyAsString());
            throw new GlobalException(HttpStatus.BAD_GATEWAY,
                    "Factus rechazó la factura: " + he.getResponseBodyAsString());
        }
        if (sinRespuestaTrasEnviar(ex)) {
            log.error("[Factus Bill] sin respuesta tras enviar, empresa={} ref={}",
                    empresaId, request != null ? request.getNumeroVenta() : null);
            throw new com.cloud_technological.aura_pos.utils.FacturaEstadoInciertoException(
                    "Factus no respondió a tiempo y pudo haber recibido la factura. Verifique en Factus"
                            + " antes de reenviarla: un reenvío con la misma referencia será rechazado si ya existe.");
        }
        log.error("[Factus Bill no disponible] empresa={} error={}", empresaId, ex.getMessage());
        return null;
    }

    /** Read timeout: la petición salió y no volvió la respuesta. */
    private static boolean sinRespuestaTrasEnviar(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException
                    && t.getMessage() != null
                    && t.getMessage().toLowerCase().contains("read")) {
                return true;
            }
        }
        return false;
    }
}