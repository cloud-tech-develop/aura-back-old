package com.cloud_technological.aura_pos.services.nomina.job;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.NominaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity.Tipo;
import com.cloud_technological.aura_pos.services.implementations.NominaElectronicaService;
import com.cloud_technological.aura_pos.services.implementations.ProcesoNominaService;

import lombok.extern.slf4j.Slf4j;

/**
 * Envía a la DIAN los documentos pendientes (Fase 7).
 *
 * <p>Cierra el ciclo que la Fase 5 dejó abierto: {@code aprobar()} prepara el
 * documento (reserva el consecutivo, queda en PENDIENTE) pero nadie lo enviaba.
 *
 * <h2>🚧 En pausa junto con la Fase 5</h2>
 * Los clientes actuales no usan nómina electrónica. Este job existe pero
 * <b>no está agendado</b>: hay que invocarlo a mano o agregarle
 * {@code @Scheduled} cuando se retome. Ver el TODO de {@code FactusNominaService}.
 *
 * <h2>Por qué no es una transacción grande</h2>
 * Cada documento se envía y persiste por separado. Si el número 300 falla, los
 * 299 anteriores ya están confirmados — y sus consecutivos ya se quemaron ante
 * la DIAN. Un rollback masivo dejaría la BD diciendo que no se enviaron
 * documentos que sí llegaron.
 */
@Slf4j
@Component
public class NominaElectronicaJob {

    private final NominaElectronicaService neService;
    private final ProcesoNominaService procesoService;

    public NominaElectronicaJob(NominaElectronicaService neService,
                                ProcesoNominaService procesoService) {
        this.neService = neService;
        this.procesoService = procesoService;
    }

    /**
     * Envía los pendientes de una empresa.
     *
     * <p>Reintentable: los que fallen quedan en PENDIENTE con su consecutivo
     * y entran en la próxima corrida.
     *
     * @return id del proceso, para hacer polling
     */
    @Async("nominaExecutor")
    public void enviarPendientes(Integer empresaId, Long usuarioId) {
        List<NominaElectronicaEntity> pendientes = neService.pendientesDe(empresaId);
        if (pendientes.isEmpty()) {
            log.debug("No hay nóminas electrónicas pendientes para la empresa {}", empresaId);
            return;
        }

        var proceso = procesoService.crear(empresaId, Tipo.NOMINA_ELECTRONICA,
                null, usuarioId, pendientes.size());
        Long procesoId = proceso.getId();

        procesoService.progreso(procesoId, 0,
                "Enviando " + pendientes.size() + " documento(s) a la DIAN");

        for (NominaElectronicaEntity ne : pendientes) {
            try {
                // Transacción por documento: un fallo tardío no puede botar lo
                // que ya llegó a la DIAN.
                NominaElectronicaEntity r = neService.enviar(ne.getId());

                if (NominaElectronicaEntity.Estado.ACEPTADO.equals(r.getEstado())) {
                    procesoService.itemOk(procesoId);
                } else {
                    procesoService.itemError(procesoId,
                            "consecutivo " + ne.getConsecutivo(),
                            "Estado " + r.getEstado() + " tras el envío");
                }
            } catch (RuntimeException e) {
                // No abortar el lote: los demás documentos deben intentarse.
                procesoService.itemError(procesoId,
                        "consecutivo " + ne.getConsecutivo(), e.getMessage());
            }
        }

        procesoService.finalizar(procesoId);
    }
}
