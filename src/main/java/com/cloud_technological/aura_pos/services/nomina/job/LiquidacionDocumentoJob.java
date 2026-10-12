package com.cloud_technological.aura_pos.services.nomina.job;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoLiquidacionQueryRepository.FilaALiquidar;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.services.implementations.ProcesoNominaService;

/**
 * Liquida en segundo plano las filas de una prenómina (rediseño R3.2c).
 *
 * <p>Una transacción por fila: si un empleado falla, su fila queda "no liquidada"
 * con la causa y el lote sigue. Al final se recalculan los totales del encabezado.
 * Se invoca desde otro bean para que el {@code @Async} respete el proxy.
 */
@Component
public class LiquidacionDocumentoJob {

    private static final Logger log = LoggerFactory.getLogger(LiquidacionDocumentoJob.class);

    private final NominaService nominaService;
    private final NominaDocumentoLiquidacionQueryRepository queryRepository;
    private final ProcesoNominaService procesoService;

    public LiquidacionDocumentoJob(NominaService nominaService,
            NominaDocumentoLiquidacionQueryRepository queryRepository,
            ProcesoNominaService procesoService) {
        this.nominaService = nominaService;
        this.queryRepository = queryRepository;
        this.procesoService = procesoService;
    }

    /**
     * Liquida las filas dadas con la fecha de liquidación del documento.
     *
     * @param procesoId proceso ya creado (lo consulta el front con polling)
     */
    @Async("nominaExecutor")
    public void liquidar(Long procesoId, Long documentoId, List<FilaALiquidar> filas, LocalDate fechaLiquidacion,
            Integer empresaId) {
        try {
            procesoService.progreso(procesoId, 0, "Liquidando " + filas.size() + " empleado(s)");
            for (FilaALiquidar f : filas) {
                try {
                    nominaService.liquidarFilaDocumento(f.nominaId(), fechaLiquidacion, empresaId);
                    procesoService.itemOk(procesoId);
                } catch (RuntimeException e) {
                    // La fila queda no liquidada con su causa; el lote sigue.
                    queryRepository.marcarError(f.nominaId(), e.getMessage());
                    procesoService.itemError(procesoId, f.empleado(), e.getMessage());
                }
            }
            queryRepository.recalcularTotales(documentoId);
            procesoService.finalizar(procesoId);
        } catch (RuntimeException e) {
            log.error("[proceso {}] la liquidación del documento {} falló: {}",
                    procesoId, documentoId, e.getMessage(), e);
            procesoService.fallar(procesoId, e.getMessage());
        }
    }
}
