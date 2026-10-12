package com.cloud_technological.aura_pos.controllers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaMesDto;
import com.cloud_technological.aura_pos.dto.nomina.nomina.NominaElectronicaEstadoDto;
import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaJPARepositories.NominaElectronicaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.FactusNominaV2Service;
import com.cloud_technological.aura_pos.services.nomina.FactusNominaV2Builder;
import com.cloud_technological.aura_pos.services.nomina.electronica.NominaElectronicaMes;
import com.cloud_technological.aura_pos.services.nomina.electronica.NominaElectronicaMesService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Disparador de pruebas de <b>nómina electrónica Factus v2</b>.
 *
 * <p>Permite (1) previsualizar el payload que le mandaríamos a Factus para una
 * nómina y (2) enviarlo de verdad al sandbox y devolver la respuesta cruda, para
 * afinar los códigos contra lo que Factus rechace. El {@code numbering_range_id}
 * llega por el body porque es un ULID que se obtiene de
 * {@code GET /v2/numbering-ranges?filter[document]=26}.
 *
 * <p><b>Un documento por empleado y mes</b>: los endpoints reciben una nómina pero
 * trabajan con su MES (todas las nóminas confirmadas del contrato en ese mes). Se
 * envía cuando el mes está completo (con quincenas, al confirmar la 2ª).
 */
@Slf4j
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

    @Autowired
    private NominaElectronicaMesService mesService;

    /** Previsualiza el payload (sin enviarlo a Factus). */
    @GetMapping("/{nominaId}/preview")
    public ResponseEntity<ApiResponse<FactusNominaV2Payload>> preview(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaEntity nomina = cargar(nominaId, empresaId);
        NominaElectronicaMes m = mesService.mesDe(nomina, empresaId);
        // La vista previa incluye la nómina abierta aunque aún no esté confirmada.
        List<NominaEntity> nominas = new ArrayList<>(m.nominas());
        if (nominas.stream().noneMatch(n -> n.getId().equals(nominaId))) {
            nominas.add(nomina);
            nominas.sort(Comparator.comparing(n -> n.getPeriodo().getFechaFin()));
        }
        FactusNominaV2Payload payload = builder.construirMes(nominas, nomina.getContrato(), m.mes(),
                "PRE-" + mesService.referencia(empresaId, nomina.getContrato().getId(), m.mes()),
                rangoNomina(null, nomina));
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Payload generado", false, payload),
                HttpStatus.OK);
    }

    /**
     * El mes de la nómina: qué nóminas confirmadas junta el documento, si ya está
     * completo para enviarlo y el estado de su documento electrónico.
     */
    @GetMapping("/nomina/{nominaId}/mes")
    public ResponseEntity<ApiResponse<NominaElectronicaMesDto>> mes(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        NominaEntity nomina = cargar(nominaId, empresaId);
        return ok("Nómina electrónica del mes", mesService.aDto(mesService.mesDe(nomina, empresaId), empresaId));
    }

    /** Vista por mes: empleados con nóminas confirmadas en el mes y el estado de su documento. */
    @GetMapping("/mes")
    public ResponseEntity<ApiResponse<List<NominaElectronicaMesDto>>> listarMes(
            @RequestParam int anio, @RequestParam int mes) {
        return ok("Nómina electrónica del mes", mesService.listar(securityUtils.getEmpresaId(), YearMonth.of(anio, mes)));
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

        NominaEntity nomina = cargar(nominaId, empresaId);
        // Un documento por empleado y mes: se envía el MES de la nómina, y solo cuando
        // sus nóminas confirmadas lo cubren completo.
        NominaElectronicaMes m = mesService.mesDe(nomina, empresaId);
        mesService.exigirCompleto(m);
        Long contratoId = nomina.getContrato().getId();
        String referenceCode = body.getOrDefault("referenceCode", mesService.referencia(empresaId, contratoId, m.mes()));
        // numbering_range_id: el del body (pruebas) o el de la empresa
        // (empresa.factus_nomina_numbering_range_id). Factus lo exige cuando la cuenta
        // tiene más de un rango activo; si la empresa no tiene, va vacío y Factus usa el único.
        String numberingRangeId = rangoNomina(body.get("numberingRangeId"), nomina);

        // Guarda anti-reenvío: si ya fue ACEPTADA por la DIAN, no se manda de nuevo
        // (crearía un documento duplicado). Para corregir se usa una nota de
        // ajuste/eliminación.
        // Excepción: si Factus ya no la tiene (404 por su referencia, p. ej. el sandbox
        // se reinició o se eliminó allá), la fila local quedó huérfana y se reenvía.
        mesService.documentoDelMes(empresaId, contratoId, m.mes())
                .filter(ne -> NominaElectronicaEntity.Estado.ACEPTADO.equals(ne.getEstado()))
                .filter(ne -> !noExisteEnFactus(empresaId, ne))
                .ifPresent(ne -> { throw new GlobalException(HttpStatus.CONFLICT,
                        "La nómina electrónica de este mes ya fue enviada a la DIAN (CUNE " + ne.getCune()
                        + "). Para corregirla usa una nota de ajuste/eliminación."); });

        FactusNominaV2Payload payload = builder.construirMes(
                m.nominas(), nomina.getContrato(), m.mes(), referenceCode, numberingRangeId);
        FactusNominaV2Service.Respuesta respuesta = factusNominaV2Service.enviar(empresaId, payload);

        // Persistir el resultado: si Factus la aceptó (CUNE), queda en
        // nomina_electronica como ACEPTADO con su consecutivo, referencia y payload.
        String mensaje = "Envío procesado";
        if (respuesta.exitoso() && respuesta.cune() != null) {
            persistir(m, empresaId, referenceCode, respuesta);
            mensaje = avisoDocumentoExistente(m.totalDevengado(), referenceCode, respuesta);
        }
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, respuesta),
                HttpStatus.OK);
    }

    /**
     * Factus no crea un documento nuevo si ya tiene la referencia: responde 201 con el
     * que ya existía e ignora lo enviado (así se perdió un bono que se agregó después
     * del primer envío). Si el total devengado que dice tener Factus no es el de la
     * nómina, se avisa para corregir con nota de ajuste en vez de darla por buena.
     *
     * @return "Envío procesado", o el aviso de que Factus conservó otros valores
     */
    private String avisoDocumentoExistente(BigDecimal devengadoMes, String referenceCode,
                                           FactusNominaV2Service.Respuesta r) {
        String total = factusNominaV2Service.extraer(r.responseBody(), "data/total_accruals",
                "data/payroll/total_accruals");
        if (total == null || devengadoMes == null) return "Envío procesado";
        BigDecimal enFactus;
        try {
            enFactus = new BigDecimal(total);
        } catch (NumberFormatException e) {
            return "Envío procesado";
        }
        if (enFactus.subtract(devengadoMes).abs().compareTo(BigDecimal.ONE) <= 0)
            return "Envío procesado";
        log.warn("[NE] Factus ya tenía {} con devengado {} (el mes tiene {}): no tomó los cambios.",
                referenceCode, enFactus, devengadoMes);
        return "Factus ya tenía el documento " + referenceCode + " con un devengado de " + enFactus.toPlainString()
                + " y el mes tiene " + devengadoMes.toPlainString()
                + ": no tomó los cambios. Para corregirlo use Nota de ajuste y vuelva a enviar.";
    }

    /**
     * Estado local (persistido) de la nómina electrónica de una nómina. Devuelve
     * {@code null} en {@code data} si aún no se ha enviado. Lo usa el front para
     * mostrar el CUNE y ocultar "Enviar" cuando ya fue aceptada.
     */
    @GetMapping("/nomina/{nominaId}")
    public ResponseEntity<ApiResponse<NominaElectronicaEstadoDto>> estadoLocal(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        // Si Factus ya no la tiene, se responde como no enviada para que se pueda reenviar.
        NominaElectronicaEstadoDto dto = documentoDe(nominaId, empresaId)
                .filter(ne -> !NominaElectronicaEntity.Estado.ACEPTADO.equals(ne.getEstado())
                        || !noExisteEnFactus(empresaId, ne))
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
        NominaElectronicaEntity ne = original(nominaId, empresaId);
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
        if (ne.getConsecutivo() != null)
            d.setNumero((ne.getPrefijo() != null ? ne.getPrefijo() : "") + ne.getConsecutivo());
        if (ne.getNominaAjustada() != null) d.setNominaAjustadaId(ne.getNominaAjustada().getId());
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

    /** Ver una nómina electrónica en Factus por su número (prefijo + consecutivo). */
    @GetMapping("/numero/{number}")
    public ResponseEntity<ApiResponse<JsonNode>> verPorNumero(@PathVariable String number) {
        return ok("Nómina electrónica", exigir(factusNominaV2Service.verPorNumero(securityUtils.getEmpresaId(), number)));
    }

    /** PDF de la nómina electrónica (Factus lo devuelve en base64). */
    @GetMapping(value = "/nomina/{nominaId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long nominaId) {
        Integer empresaId = securityUtils.getEmpresaId();
        String number = numero(original(nominaId, empresaId));
        return archivo(factusNominaV2Service.descargarPdf(empresaId, number), "pdf_base_64_encoded",
                "nomina-" + number + ".pdf", MediaType.APPLICATION_PDF);
    }

    /**
     * Elimina en Factus una nómina que la DIAN no validó (para crearla de nuevo
     * corregida) y la marca ANULADA localmente. Si Factus no la elimina, 400 con su
     * mensaje y la local no cambia.
     */
    @DeleteMapping("/reference/{referenceCode}")
    public ResponseEntity<ApiResponse<JsonNode>> eliminar(@PathVariable String referenceCode) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = exigir(factusNominaV2Service.eliminar(empresaId, referenceCode));
        marcarAnulada(empresaId, referenceCode);
        return ok("Nómina electrónica eliminada", data);
    }

    // ── Notas de ajuste (eliminación) ───────────────────────────────────────

    /** Ver una nota de ajuste en Factus por su número. */
    @GetMapping("/ajustes/{number}")
    public ResponseEntity<ApiResponse<JsonNode>> verAjuste(@PathVariable String number) {
        return ok("Nota de ajuste", exigir(factusNominaV2Service.verAjuste(securityUtils.getEmpresaId(), number)));
    }

    /**
     * Elimina en Factus una nota de ajuste que la DIAN no validó: la nota queda
     * ANULADA localmente y la nómina que eliminaba vuelve a ACEPTADO.
     */
    @DeleteMapping("/ajustes/reference/{referenceCode}")
    public ResponseEntity<ApiResponse<JsonNode>> eliminarAjuste(@PathVariable String referenceCode) {
        Integer empresaId = securityUtils.getEmpresaId();
        JsonNode data = exigir(factusNominaV2Service.eliminarAjuste(empresaId, referenceCode));
        neRepo.findByEmpresaIdAndReferenceCode(empresaId, referenceCode)
                .filter(ne -> Boolean.TRUE.equals(ne.getEsAjuste()))
                .ifPresent(nota -> {
                    nota.setEstado(NominaElectronicaEntity.Estado.ANULADO);
                    neRepo.save(nota);
                    NominaElectronicaEntity original = nota.getNominaAjustada();
                    if (original != null) {
                        original.setEstado(NominaElectronicaEntity.Estado.ACEPTADO);
                        neRepo.save(original);
                    }
                });
        return ok("Nota de ajuste eliminada", data);
    }

    /** XML de una nota de ajuste (Factus lo devuelve en base64). */
    @GetMapping(value = "/ajustes/{number}/xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> descargarXmlAjuste(@PathVariable String number) {
        return archivo(factusNominaV2Service.descargarXmlAjuste(securityUtils.getEmpresaId(), number),
                "xml_base_64_encoded", "nota-ajuste-" + number + ".xml", MediaType.APPLICATION_XML);
    }

    /** PDF de una nota de ajuste (Factus lo devuelve en base64). */
    @GetMapping(value = "/ajustes/{number}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarPdfAjuste(@PathVariable String number) {
        return archivo(factusNominaV2Service.descargarPdfAjuste(securityUtils.getEmpresaId(), number),
                "pdf_base_64_encoded", "nota-ajuste-" + number + ".pdf", MediaType.APPLICATION_PDF);
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

        FactusNominaV2Service.RespuestaFactus r =
                factusNominaV2Service.notaEliminacion(empresaId, payrollNumber, refNota, numberingRangeId);
        JsonNode data = exigir(r);
        guardarNotaAjuste(ne, refNota, r);
        ne.setEstado(NominaElectronicaEntity.Estado.ANULADO);
        neRepo.save(ne);
        return ok("Nota de eliminación enviada", data);
    }

    /**
     * Guarda la nota de ajuste como fila propia (es_ajuste) ligada a la nómina que
     * elimina, con el número y el CUNE que asignó Factus, para poder consultarla,
     * descargarla o eliminarla después.
     */
    private void guardarNotaAjuste(NominaElectronicaEntity original, String referenceCode,
                                   FactusNominaV2Service.RespuestaFactus r) {
        String numero = r.texto("data/adjustment_payroll/number", "data/payroll/number", "data/number");
        String prefijo = r.texto("data/numbering_range/prefix", "data/adjustment_payroll/prefix");
        String current = r.texto("data/numbering_range/current");
        if (prefijo == null && numero != null) prefijo = numero.replaceAll("[0-9]+$", "");
        if (current == null && numero != null) current = numero.replaceAll("^.*?([0-9]+)$", "$1");
        long consecutivo = current != null ? parseLong(current, -original.getId()) : -original.getId();

        NominaElectronicaEntity nota = new NominaElectronicaEntity();
        nota.setEmpresaId(original.getEmpresaId());
        nota.setNomina(original.getNomina());
        nota.setContratoId(original.getContratoId());
        nota.setAgno(original.getAgno());
        nota.setMes(original.getMes());
        nota.setEsAjuste(Boolean.TRUE);
        nota.setNominaAjustada(original);
        nota.setReferenceCode(referenceCode);
        nota.setPrefijo(prefijo != null && !prefijo.isBlank() ? prefijo : null);
        nota.setConsecutivo(consecutivo);
        nota.setCune(r.texto("data/adjustment_payroll/cune", "data/payroll/cune", "data/cune"));
        nota.setEstado(NominaElectronicaEntity.Estado.ACEPTADO);
        nota.setFechaEnvio(LocalDateTime.now());
        nota.setFechaRespuesta(LocalDateTime.now());
        nota.setPayloadJson(r.cuerpo() != null ? r.cuerpo().toString() : null);
        neRepo.save(nota);
    }

    /** La nómina electrónica original del mes de la nómina; 404 si no se ha enviado. */
    private NominaElectronicaEntity original(Long nominaId, Integer empresaId) {
        return documentoDe(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "El mes de esta nómina no tiene nómina electrónica enviada"));
    }

    /**
     * Documento electrónico del MES de la nómina (contrato + mes de su período). Las
     * filas viejas, de antes del documento mensual, se encuentran por la nómina.
     */
    private Optional<NominaElectronicaEntity> documentoDe(Long nominaId, Integer empresaId) {
        NominaEntity nomina = cargar(nominaId, empresaId);
        YearMonth mes = YearMonth.from(nomina.getPeriodo().getFechaFin());
        Optional<NominaElectronicaEntity> delMes =
                mesService.documentoDelMes(empresaId, nomina.getContrato().getId(), mes);
        return delMes.isPresent() ? delMes
                : neRepo.findOriginalByNomina(nominaId).filter(x -> empresaId.equals(x.getEmpresaId()));
    }

    /** Número ante Factus (prefijo + consecutivo); 400 si aún no tiene. */
    private static String numero(NominaElectronicaEntity ne) {
        if (ne.getConsecutivo() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La nómina electrónica no tiene número asignado");
        return (ne.getPrefijo() != null ? ne.getPrefijo() : "") + ne.getConsecutivo();
    }

    /** El cuerpo de Factus si respondió bien; si no, 400 con su mensaje. */
    private static JsonNode exigir(FactusNominaV2Service.RespuestaFactus r) {
        if (r.codigoHttp() == 404)
            throw new GlobalException(HttpStatus.BAD_REQUEST, r.error() + ". Factus no tiene ese documento "
                    + "(si es el sandbox, pudo reiniciarse): vuelva a enviar la nómina electrónica.");
        if (!r.exitosa()) throw new GlobalException(HttpStatus.BAD_REQUEST, r.error());
        return r.cuerpo();
    }

    /** Rango de numeración de nómina: el pedido, o el de la empresa; null si no hay ninguno. */
    private static String rangoNomina(String pedido, NominaEntity nomina) {
        if (pedido != null && !pedido.isBlank()) return pedido.trim();
        String deEmpresa = nomina.getEmpresa() != null ? nomina.getEmpresa().getFactusNominaNumberingRangeId() : null;
        return deEmpresa != null && !deEmpresa.isBlank() ? deEmpresa.trim() : null;
    }

    /** ¿Factus ya no tiene la nómina (404 por su referencia)? */
    private boolean noExisteEnFactus(Integer empresaId, NominaElectronicaEntity ne) {
        if (ne.getReferenceCode() == null) return false;
        boolean noExiste = factusNominaV2Service.verPorReferencia(empresaId, ne.getReferenceCode()).codigoHttp() == 404;
        if (noExiste)
            log.warn("[NE] La nómina electrónica {} ({}) no existe en Factus; se reenvía.", ne.getId(),
                    ne.getReferenceCode());
        return noExiste;
    }

    /** Decodifica el archivo en base64 que devuelve Factus y lo entrega como descarga. */
    private static ResponseEntity<byte[]> archivo(FactusNominaV2Service.RespuestaFactus r, String campo,
                                                  String nombrePorDefecto, MediaType tipo) {
        exigir(r);
        String base64 = r.texto("data/" + campo, campo);
        if (base64 == null || base64.isBlank())
            throw new GlobalException(HttpStatus.BAD_GATEWAY, "Factus no devolvió el archivo.");
        byte[] bytes;
        try {
            bytes = java.util.Base64.getMimeDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new GlobalException(HttpStatus.BAD_GATEWAY, "El archivo de Factus no es base64 válido.");
        }
        String nombre = r.texto("data/file_name", "file_name");
        if (nombre == null || nombre.isBlank()) nombre = nombrePorDefecto;
        else if (!nombre.contains(".")) nombre = nombre + nombrePorDefecto.substring(nombrePorDefecto.lastIndexOf('.'));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(tipo);
        h.setContentDispositionFormData("attachment", nombre);
        return new ResponseEntity<>(bytes, h, HttpStatus.OK);
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
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

    private void persistir(NominaElectronicaMes m, Integer empresaId, String referenceCode,
                           FactusNominaV2Service.Respuesta r) {
        NominaEntity nomina = m.ultima();
        // Si la anterior se eliminó con nota de ajuste, la corregida es una fila nueva:
        // la anulada queda como historia y la nota sigue apuntando a ella.
        NominaElectronicaEntity ne = mesService.documentoDelMes(empresaId, m.contrato().getId(), m.mes())
                .filter(x -> !NominaElectronicaEntity.Estado.ANULADO.equals(x.getEstado()))
                .orElseGet(NominaElectronicaEntity::new);
        ne.setNomina(nomina);
        ne.setContratoId(m.contrato().getId());
        ne.setEmpresaId(empresaId);
        ne.setReferenceCode(referenceCode);
        ne.setAgno(m.mes().getYear());
        ne.setMes(m.mes().getMonthValue());

        // El número del documento (p. ej. "NEF12") es el que exigen las descargas; el
        // "current" del rango es su contador y solo sirve de respaldo.
        String numero = factusNominaV2Service.extraer(r.responseBody(), "data/payroll/number", "data/number");
        String prefijo = factusNominaV2Service.extraer(r.responseBody(), "data/numbering_range/prefix");
        String current = factusNominaV2Service.extraer(r.responseBody(), "data/numbering_range/current");
        if (numero != null && numero.matches(".*?[0-9]+$")) {
            prefijo = numero.replaceAll("[0-9]+$", "");
            current = numero.replaceAll("^.*?([0-9]+)$", "$1");
        }
        ne.setPrefijo(prefijo != null && !prefijo.isBlank() ? prefijo : null);
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
