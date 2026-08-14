package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.nomina.job.LiquidacionJob;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Orquesta la liquidación asíncrona de un período (Fase 7).
 *
 * <p>Valida, crea el proceso y dispara {@link LiquidacionJob}. Devuelve el
 * proceso ya creado para que el endpoint responda 202 con su id; el front hace
 * polling desde ahí.
 *
 * <p><b>Por qué esta clase y no llamar al job directo desde el controller:</b>
 * el método del job es {@code @Async}. Invocarlo desde su propia clase saltaría
 * el proxy de Spring y correría síncrono. Al llamarlo desde este bean distinto,
 * el proxy se respeta y el trabajo va al pool {@code nominaExecutor}.
 */
@Service
public class LiquidacionAsyncService {

    private final PeriodoNominaJPARepository periodoRepo;
    private final ContratoLaboralService contratoLaboralService;
    private final ProcesoNominaService procesoService;
    private final LiquidacionJob job;

    public LiquidacionAsyncService(PeriodoNominaJPARepository periodoRepo,
                                   ContratoLaboralService contratoLaboralService,
                                   ProcesoNominaService procesoService,
                                   LiquidacionJob job) {
        this.periodoRepo = periodoRepo;
        this.contratoLaboralService = contratoLaboralService;
        this.procesoService = procesoService;
        this.job = job;
    }

    /**
     * Lanza la liquidación del período en segundo plano.
     *
     * @return el proceso recién creado (estado PENDIENTE), para el 202.
     * @throws GlobalException 404 si el período no existe; 400 si está anulado o
     *         no tiene contratos que liquidar; 409 si ya hay un proceso corriendo
     *         para este período (lo valida {@code ProcesoNominaService.crear}).
     */
    public ProcesoNominaEntity lanzarLiquidacionPeriodo(Long periodoId, Integer empresaId, Long usuarioId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Período no encontrado"));

        if ("ANULADO".equals(periodo.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El período está anulado");

        int total = contratoLaboralService
                .vigentesEnPeriodo(empresaId, periodo.getFechaInicio(), periodo.getFechaFin())
                .size();

        if (total == 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay contratos vigentes en el período para liquidar");

        // crear() responde 409 si ya hay un proceso activo para este período, y
        // commitea en su propia transacción: la fila existe antes de que el job
        // arranque, aunque este método falle después.
        ProcesoNominaEntity proceso = procesoService.crear(
                empresaId, ProcesoNominaEntity.Tipo.LIQUIDACION_PERIODO,
                periodoId, usuarioId, total);

        // Cross-bean: el @Async del job sí se respeta desde aquí.
        job.liquidarPeriodo(proceso.getId(), periodoId, empresaId);
        return proceso;
    }
}
