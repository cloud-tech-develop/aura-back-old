package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.electronica.NominaElectronicaPayload;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity.Estado;
import com.cloud_technological.aura_pos.entity.NominaElectronicaLogEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaJPARepositories.NominaElectronicaLogRepo;
import com.cloud_technological.aura_pos.repositories.nomina.NominaElectronicaJPARepositories.NominaElectronicaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.NominaJPARepository;
import com.cloud_technological.aura_pos.services.nomina.NominaElectronicaPayloadBuilder;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Orquesta la nómina electrónica (Fase 5).
 *
 * <h2>Idempotencia — el punto donde más duele equivocarse</h2>
 * <ol>
 *   <li>{@link #prepararDocumento} reserva el consecutivo y persiste la fila en
 *       PENDIENTE. Transacción propia ({@code REQUIRES_NEW}): el consecutivo
 *       queda tomado aunque el envío falle después.</li>
 *   <li>{@link #enviar} usa esa fila. Si falla, la deja en PENDIENTE/RECHAZADO
 *       para reintento — <b>con el mismo consecutivo</b>.</li>
 *   <li>{@code uq_ne_consecutivo} impide duplicar. Un consecutivo repetido ante
 *       la DIAN no se deshace con un DELETE.</li>
 * </ol>
 *
 * <p><b>El envío nunca va dentro del request de aprobación:</b> se encola.
 */
@Slf4j
@Service
public class NominaElectronicaService {

    private final NominaElectronicaRepo neRepo;
    private final NominaElectronicaLogRepo logRepo;
    private final NominaJPARepository nominaRepo;
    private final EmpresaJPARepository empresaRepo;
    private final NominaElectronicaPayloadBuilder payloadBuilder;
    private final FactusNominaService factusNominaService;

    public NominaElectronicaService(NominaElectronicaRepo neRepo,
                                    NominaElectronicaLogRepo logRepo,
                                    NominaJPARepository nominaRepo,
                                    EmpresaJPARepository empresaRepo,
                                    NominaElectronicaPayloadBuilder payloadBuilder,
                                    FactusNominaService factusNominaService) {
        this.neRepo = neRepo;
        this.logRepo = logRepo;
        this.nominaRepo = nominaRepo;
        this.empresaRepo = empresaRepo;
        this.payloadBuilder = payloadBuilder;
        this.factusNominaService = factusNominaService;
    }

    /**
     * Prepara el documento: reserva consecutivo y lo deja en PENDIENTE.
     *
     * <p>Se llama al aprobar la nómina, tras el commit. No envía nada.
     *
     * <p>{@code REQUIRES_NEW}: el consecutivo debe quedar reservado de forma
     * durable antes de intentar el envío. Si compartiera transacción con el
     * envío, un rollback liberaría el número y otro documento podría tomarlo —
     * mientras el primero quizá ya llegó a la DIAN.
     *
     * @return el documento en PENDIENTE, o el existente si ya estaba preparado
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NominaElectronicaEntity prepararDocumento(Long nominaId, Integer empresaId) {
        var existente = neRepo.findOriginalByNomina(nominaId);
        if (existente.isPresent()) {
            log.debug("La nómina {} ya tiene documento electrónico ({})",
                    nominaId, existente.get().getEstado());
            return existente.get();
        }

        NominaEntity nomina = nominaRepo.findByIdAndEmpresaId(nominaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nómina no encontrada"));

        var fin = nomina.getPeriodo().getFechaFin();
        int agno = fin.getYear();

        NominaElectronicaEntity ne = new NominaElectronicaEntity();
        ne.setEmpresaId(empresaId);
        ne.setNomina(nomina);
        ne.setAgno(agno);
        ne.setMes(fin.getMonthValue());
        ne.setConsecutivo(neRepo.reservarConsecutivo(empresaId, agno));
        ne.setEstado(Estado.PENDIENTE);

        return neRepo.save(ne);
    }

    /**
     * Envía un documento pendiente.
     *
     * <p>Lo llama el job, nunca el request. Es reintentable: si Factus falla,
     * la fila queda en PENDIENTE con el mismo consecutivo.
     */
    @Transactional
    public NominaElectronicaEntity enviar(Long nominaElectronicaId) {
        NominaElectronicaEntity ne = neRepo.findById(nominaElectronicaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento no encontrado"));

        if (Estado.ACEPTADO.equals(ne.getEstado())) {
            log.debug("El documento {} ya fue aceptado. No se reenvía.", ne.getId());
            return ne;
        }

        EmpresaEntity empresa = empresaRepo.findById(ne.getEmpresaId())
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        NominaEntity nomina = ne.getNomina();
        NominaElectronicaPayload payload = payloadBuilder.construir(
                nomina, nomina.getContrato(), empresa, ne.getConsecutivo(), ne.getPrefijo());

        ne.setIntentos(ne.getIntentos() + 1);
        ne.setFechaEnvio(LocalDateTime.now());
        ne.setEstado(Estado.ENVIADO);

        try {
            var r = factusNominaService.enviar(ne.getEmpresaId(), payload);

            registrarLog(ne, r.codigoHttp() + "", r.responseBody(), r.requestBody(),
                    r.responseBody(), r.duracionMs());

            if (r.exitoso()) {
                ne.setEstado(Estado.ACEPTADO);
                ne.setCune(r.cune());
                ne.setPayloadJson(r.requestBody());   // snapshot de lo enviado
                if (r.cune() == null) {
                    log.warn("[NE {}] Factus respondió OK pero sin CUNE. "
                            + "Verificar el parseo de la respuesta.", ne.getId());
                }
            } else {
                ne.setEstado(Estado.RECHAZADO);
            }
            ne.setFechaRespuesta(LocalDateTime.now());

        } catch (RuntimeException e) {
            // El envío no llegó o falló: queda PENDIENTE para reintento, con el
            // MISMO consecutivo. Nunca marcar como enviado lo que no se envió.
            log.error("[NE {}] Falló el envío (intento {}): {}",
                    ne.getId(), ne.getIntentos(), e.getMessage());
            ne.setEstado(Estado.PENDIENTE);
            registrarLog(ne, "ERROR", e.getMessage(), null, null, null);
        }

        return neRepo.save(ne);
    }

    /** Pendientes de una empresa. Lo consume el job de reintentos. */
    @Transactional(readOnly = true)
    public List<NominaElectronicaEntity> pendientesDe(Integer empresaId) {
        return neRepo.findPendientes(empresaId);
    }

    private void registrarLog(NominaElectronicaEntity ne, String codigo, String mensaje,
                              String request, String response, Integer duracion) {
        NominaElectronicaLogEntity l = new NominaElectronicaLogEntity();
        l.setNominaElectronica(ne);
        l.setIntento(ne.getIntentos());
        l.setCodigoRespuesta(codigo);
        l.setMensajeRespuesta(mensaje);
        l.setRequestBody(request);
        l.setResponseBody(response);
        l.setDuracionMs(duracion);
        logRepo.save(l);
    }
}
