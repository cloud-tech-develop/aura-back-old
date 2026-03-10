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

    private static final String FACTUS_BILL_URL =
            "https://api.factus.com.co/v1/bills/validate";

    private final RestTemplate         restTemplate;
    private final EmpresaJPARepository empresaRepository;
    private final FactusTokenService   factusTokenService;
    private final ObjectMapper         objectMapper;

    public FactusService(RestTemplate restTemplate,
                          EmpresaJPARepository empresaRepository,
                          FactusTokenService factusTokenService) {
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
                FACTUS_BILL_URL,
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

        // reference_code: prefijo de empresa + consecutivo de la venta
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
        customer.setLegalOrganizationId("2");  // 2=Persona Natural
        customer.setTributeId("21");           // 21=No aplica
        dto.setCustomer(customer);

        // ── Items ────────────────────────────────────────────────────
        List<FactusItemDto> items = req.getItems().stream().map(item -> {
            FactusItemDto i = new FactusItemDto();
            i.setCodeReference(item.getSku() != null ? item.getSku() : "SIN-SKU");
            i.setName(item.getNombre());
            i.setQuantity(item.getCantidad());
            i.setPrice(item.getPrecioSinIva());
            i.setDiscountRate(BigDecimal.ZERO);

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

    public FactusBillDto facturaFallback(Integer empresaId,
                                          FacturaElectronicaRequest request,
                                          Throwable ex) {
        log.error("[Factus Bill CB ABIERTO] empresa={} error={}",
                empresaId, ex.getMessage());
        return null;
    }
}