package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaElectronicaEstadoDto;
import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaJPARepositories.NominaElectronicaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.FactusNominaV2Service;
import com.cloud_technological.aura_pos.services.nomina.FactusNominaV2Builder;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Disparador de pruebas de <b>nómina electrónica Factus v2</b>.
 *
 * <p>Permite (1) previsualizar el payload que le mandaríamos a Factus para una
 * nómina y (2) enviarlo de verdad al sandbox y devolver la respuesta cruda, para
 * afinar los códigos contra lo que Factus rechace. El {@code numbering_range_id}
 * llega por el body porque es un ULID que se obtiene de
 * {@code GET /v2/numbering-ranges?filter[document]=26}.
 */
@RestController
@RequestMapping("/api/nomina-electronica")
public class NominaElectronicaController {

    @Autowired
    private NominaJPARepository nominaRepo;

    @Autowired
    private FactusNominaV2Builder builder;

    @Autowired
    private FactusNominaV2Service factusNominaV2Service;

    @Autowired
    private NominaElectronicaRepo neRepo;

    @Autowired
    private SecurityUtils securityUtils;

    /** Previsualiza el payload (sin enviarlo a Factus). */
    @GetMapping("/{nominaId}/preview")
    public ResponseEntity<ApiResponse<FactusNominaV2Payload>> preview(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaEntity nomina = cargar(nominaId, empresaId);
        FactusNominaV2Payload payload = builder.construir(
                nomina, nomina.getContrato(), "PRE-" + nominaId, null);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Payload generado", false, payload),
                HttpStatus.OK);
    }

    /**
     * Envía la nómina al sandbox de Factus v2 y devuelve la respuesta cruda.
     * Body: {@code { "numberingRangeId": "<ULID>", "referenceCode": "<opcional>" }}.
     */
    @PostMapping("/{nominaId}/enviar")
    public ResponseEntity<ApiResponse<FactusNominaV2Service.Respuesta>> enviar(
            @PathVariable Long nominaId,
            @RequestBody Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();

        // numbering_range_id se envía VACÍO: Factus lo asigna automáticamente al
        // NIT habilitado. El body puede forzar uno solo para pruebas puntuales.
        String numberingRangeId = body.get("numberingRangeId");
        String referenceCode = body.getOrDefault("referenceCode", "NOM-" + nominaId);

        NominaEntity nomina = cargar(nominaId, empresaId);

        // Guarda anti-reenvío: si ya fue ACEPTADA por la DIAN, no se manda de nuevo
        // (crearía un documento duplicado). Para corregir se usa una nota de
        // ajuste/eliminación.
        neRepo.findOriginalByNomina(nominaId)
                .filter(ne -> NominaElectronicaEntity.Estado.ACEPTADO.equals(ne.getEstado()))
                .ifPresent(ne -> { throw new GlobalException(HttpStatus.CONFLICT,
                        "Esta nómina ya fue enviada a la DIAN (CUNE " + ne.getCune()
                        + "). Para corregirla usa una nota de ajuste/eliminación."); });

        FactusNominaV2Payload payload = builder.construir(
                nomina, nomina.getContrato(), referenceCode, numberingRangeId);
        FactusNominaV2Service.Respuesta respuesta = factusNominaV2Service.enviar(empresaId, payload);

        // Persistir el resultado: si Factus la aceptó (CUNE), queda en
        // nomina_electronica como ACEPTADO con su consecutivo, referencia y payload.
        if (respuesta.exitoso() && respuesta.cune() != null) {
            persistir(nomina, empresaId, referenceCode, respuesta);
        }
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Envío procesado", false, respuesta),
                HttpStatus.OK);
    }

    /**
     * Estado local (persistido) de la nómina electrónica de una nómina. Devuelve
     * {@code null} en {@code data} si aún no se ha enviado. Lo usa el front para
     * mostrar el CUNE y ocultar "Enviar" cuando ya fue aceptada.
     */
    @GetMapping("/nomina/{nominaId}")
    public ResponseEntity<ApiResponse<NominaElectronicaEstadoDto>> estadoLocal(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaElectronicaEstadoDto dto = neRepo.findOriginalByNomina(nominaId)
                .filter(ne -> empresaId.equals(ne.getEmpresaId()))
                .map(this::aEstadoDto)
                .orElse(null);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Estado de nómina electrónica", false, dto),
                HttpStatus.OK);
    }

    /**
     * Descarga el XML firmado de la nómina electrónica. El número que exige Factus
     * ({@code GET /v2/payrolls/{number}/download-xml}) se arma con el prefijo +
     * consecutivo persistidos (p.ej. "NEF1"), que no viene como campo suelto en la
     * respuesta de Factus pero sí en {@code numbering_range.prefix/current}.
     */
    @GetMapping(value = "/nomina/{nominaId}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> descargarXml(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaElectronicaEntity ne = neRepo.findOriginalByNomina(nominaId)
                .filter(x -> empresaId.equals(x.getEmpresaId()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "Esta nómina no tiene nómina electrónica enviada"));
        if (ne.getConsecutivo() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina electrónica no tiene número asignado");

        String number = (ne.getPrefijo() != null ? ne.getPrefijo() : "") + ne.getConsecutivo();
        String xml = resolverXml(factusNominaV2Service.descargarXml(empresaId, number));
        if (xml != null && !xml.isBlank()) {   // se guarda para no volver a pedirlo
            ne.setXml(xml);
            neRepo.save(ne);
        }

        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_XML);
        h.setContentDispositionFormData("attachment", "nomina-" + number + ".xml");
        return new ResponseEntity<>(xml, h, HttpStatus.OK);
    }

    /** Factus puede devolver el XML directo o dentro de un JSON (a veces en base64). */
    private String resolverXml(String body) {
        if (body == null) return null;
        if (body.trim().startsWith("<")) return body;   // XML directo
        String candidato = factusNominaV2Service.extraer(body,
                "data/xml_base_64_encoded", "data/xml", "xml_base_64_encoded", "xml");
        if (candidato == null) return body;
        if (candidato.trim().startsWith("<")) return candidato;
        try {
            return new String(java.util.Base64.getDecoder().decode(candidato.trim()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return candidato;
        }
    }

    /** Listado local (persistido) de las nóminas electrónicas de la empresa. */
    @GetMapping("/local")
    public ResponseEntity<ApiResponse<java.util.List<NominaElectronicaEstadoDto>>> listarLocal() {
        Integer empresaId = securityUtils.getEmpresaId();
        java.util.List<NominaElectronicaEstadoDto> data = neRepo.findByEmpresaIdOrderByIdDesc(empresaId)
                .stream().map(this::aEstadoDto).toList();
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nóminas electrónicas", false, data),
                HttpStatus.OK);
    }

    private NominaElectronicaEstadoDto aEstadoDto(NominaElectronicaEntity ne) {
        NominaElectronicaEstadoDto d = new NominaElectronicaEstadoDto();
        d.setId(ne.getId());
        d.setNominaId(ne.getNomina() != null ? ne.getNomina().getId() : null);
        if (ne.getNomina() != null && ne.getNomina().getEmpleado() != null)
            d.setEmpleadoNombre(ne.getNomina().getEmpleado().getNombreCompletoResuelto());
        d.setAgno(ne.getAgno());
        d.setMes(ne.getMes());
        d.setEstado(ne.getEstado());
        d.setCune(ne.getCune());
        d.setReferenceCode(ne.getReferenceCode());
        d.setPrefijo(ne.getPrefijo());
        d.setConsecutivo(ne.getConsecutivo());
        d.setEsAjuste(ne.getEsAjuste());
        d.setFechaEnvio(ne.getFechaEnvio());
        d.setTieneXml(ne.getXml() != null && !ne.getXml().isBlank());
        return d;
    }

    /** Ver una nómina electrónica en Factus por su reference_code. */
    @GetMapping("/reference/{referenceCode}")
    public ResponseEntity<ApiResponse<JsonNode>> ver(@PathVariable String referenceCode) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = factusNominaV2Service.consultar(empresaId, referenceCode);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina electrónica", false, data),
                HttpStatus.OK);
    }

    /** Elimina la nómina electrónica en Factus y la marca ANULADA localmente. */
    @DeleteMapping("/reference/{referenceCode}")
    public ResponseEntity<ApiResponse<JsonNode>> eliminar(@PathVariable String referenceCode) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = factusNominaV2Service.eliminar(empresaId, referenceCode);
        marcarAnulada(empresaId, referenceCode);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nómina electrónica eliminada", false, data),
                HttpStatus.OK);
    }

    /**
     * Nota de eliminación (forma DIAN de anular): emite un documento de eliminación
     * contra la nómina ya aceptada vía {@code POST /v2/adjustment-payrolls} y la
     * marca ANULADA localmente. Body opcional: {@code {referenceCode, numberingRangeId}}.
     */
    @PostMapping("/reference/{referenceCode}/nota-eliminacion")
    public ResponseEntity<ApiResponse<JsonNode>> notaEliminacion(
            @PathVariable String referenceCode,
            @RequestBody(required = false) Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaElectronicaEntity ne = neRepo.findByEmpresaIdAndReferenceCode(empresaId, referenceCode)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "No existe una nómina electrónica local con referencia " + referenceCode));
        if (!NominaElectronicaEntity.Estado.ACEPTADO.equals(ne.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Solo se puede eliminar una nómina ACEPTADA (actual: " + ne.getEstado() + ")");

        String payrollNumber = (ne.getPrefijo() != null ? ne.getPrefijo() : "") + ne.getConsecutivo();
        String refNota = body != null && body.get("referenceCode") != null
                ? body.get("referenceCode")
                : "DEL-" + referenceCode + "-" + System.currentTimeMillis();
        String numberingRangeId = body != null ? body.get("numberingRangeId") : null;

        JsonNode data = factusNominaV2Service.notaEliminacion(empresaId, payrollNumber, refNota, numberingRangeId);
        ne.setEstado(NominaElectronicaEntity.Estado.ANULADO);
        neRepo.save(ne);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Nota de eliminación enviada", false, data),
                HttpStatus.OK);
    }

    /** Lista las nóminas electrónicas de Factus con filtros y paginación. */
    @GetMapping
    public ResponseEntity<ApiResponse<JsonNode>> listar(
            @RequestParam(required = false) String identificationNumber,
            @RequestParam(required = false) String number,
            @RequestParam(required = false) String names,
            @RequestParam(required = false) Integer page) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = factusNominaV2Service.listar(empresaId, identificationNumber, number, names, page);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado de nóminas electrónicas", false, data),
                HttpStatus.OK);
    }

    // ── Persistencia ────────────────────────────────────────────────────────

    private void persistir(NominaEntity nomina, Integer empresaId, String referenceCode,
                           FactusNominaV2Service.Respuesta r) {
        NominaElectronicaEntity ne = neRepo.findOriginalByNomina(nomina.getId())
                .orElseGet(NominaElectronicaEntity::new);
        ne.setNomina(nomina);
        ne.setEmpresaId(empresaId);
        ne.setReferenceCode(referenceCode);
        ne.setAgno(nomina.getPeriodo().getFechaFin().getYear());
        ne.setMes(nomina.getPeriodo().getFechaFin().getMonthValue());

        String prefijo = factusNominaV2Service.extraer(r.responseBody(), "data/numbering_range/prefix");
        String current = factusNominaV2Service.extraer(r.responseBody(), "data/numbering_range/current");
        ne.setPrefijo(prefijo);
        // Consecutivo: el que asignó Factus; si no vino, se usa el id de la nómina
        // (único, evita chocar con uq_ne_consecutivo).
        long consecutivo = current != null ? parseLong(current, nomina.getId()) : nomina.getId();
        ne.setConsecutivo(consecutivo);

        ne.setEstado(NominaElectronicaEntity.Estado.ACEPTADO);
        ne.setCune(r.cune());
        ne.setFechaEnvio(LocalDateTime.now());
        ne.setFechaRespuesta(LocalDateTime.now());
        ne.setPayloadJson(r.requestBody());
        neRepo.save(ne);
    }

    /**
     * Marca ANULADA la nómina electrónica ligada a un reference_code. Ubica por
     * la referencia persistida; para filas viejas sin ella, cae al parseo del id
     * embebido en {@code NOM-{id}}.
     */
    private void marcarAnulada(Integer empresaId, String referenceCode) {
        Optional<NominaElectronicaEntity> encontrada =
                neRepo.findByEmpresaIdAndReferenceCode(empresaId, referenceCode);
        if (encontrada.isEmpty()) {
            Long nominaId = nominaIdDeReferencia(referenceCode);
            if (nominaId != null) encontrada = neRepo.findOriginalByNomina(nominaId);
        }
        encontrada.ifPresent(ne -> {
            ne.setEstado(NominaElectronicaEntity.Estado.ANULADO);
            neRepo.save(ne);
        });
    }

    private Long nominaIdDeReferencia(String referenceCode) {
        if (referenceCode == null) return null;
        try {
            return Long.parseLong(referenceCode.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long parseLong(String s, long fallback) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return fallback; }
    }

    private NominaEntity cargar(Long nominaId, Integer empresaId) {
        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));
        if (nomina.getContrato() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nómina no tiene contrato asociado; no se puede armar la nómina electrónica");
        return nomina;
    }
}
