package com.cloud_technological.aura_pos.services.nomina.job;

import java.util.List;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.PeriodoNominaEntity;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity.Tipo;
import com.cloud_technological.aura_pos.repositories.nomina.PeriodoNominaJPARepository;
import com.cloud_technological.aura_pos.services.NominaService;
import com.cloud_technological.aura_pos.services.implementations.ContratoLaboralService;
import com.cloud_technological.aura_pos.services.implementations.ProcesoNominaService;

import lombok.extern.slf4j.Slf4j;

/**
 * Liquidación de un período completo, asíncrona (Fase 7).
 *
 * <p>{@code liquidarPeriodoCompleto()} corría dentro del request. Con 30
 * empleados va bien; con 500 y provisiones, timeout — y el usuario no sabe si
 * quedó a medias.
 *
 * <h2>Commit por contrato, no un lote grande</h2>
 * Cada contrato se liquida en su propia transacción ({@code liquidarContrato}
 * es {@code @Transactional}). Si el número 400 falla, los 399 anteriores quedan
 * liquidados y el usuario ve exactamente cuál falló y por qué.
 *
 * <p>Una transacción de 500 empleados haría lo contrario: un error tardío bota
 * todo el trabajo, y con 500 filas de detalle por nómina el rollback es caro.
 */
@Slf4j
@Component
public class LiquidacionJob {

    private final NominaService nominaService;
    private final ContratoLaboralService contratoService;
    private final PeriodoNominaJPARepository periodoRepo;
    private final ProcesoNominaService procesoService;

    public LiquidacionJob(NominaService nominaService,
                          ContratoLaboralService contratoService,
                          PeriodoNominaJPARepository periodoRepo,
                          ProcesoNominaService procesoService) {
        this.nominaService = nominaService;
        this.contratoService = contratoService;
        this.periodoRepo = periodoRepo;
        this.procesoService = procesoService;
    }

    /**
     * Lanza la liquidación de un período.
     *
     * <p>El endpoint debe: crear el proceso, devolver su id con 202 Accepted, y
     * llamar esto. El front hace polling a {@code GET /proceso/{id}}.
     *
     * @param procesoId el proceso ya creado por el endpoint
     */
    @Async("nominaExecutor")
    public void liquidarPeriodo(Long procesoId, Long periodoId, Integer empresaId) {
        try {
            PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                    .orElse(null);
            if (periodo == null) {
                procesoService.fallar(procesoId, "Período no encontrado");
                return;
            }

            List<ContratoLaboralEntity> contratos = contratoService.vigentesEnPeriodo(
                    empresaId, periodo.getFechaInicio(), periodo.getFechaFin());

            procesoService.progreso(procesoId, 0,
                    "Liquidando " + contratos.size() + " contrato(s)");

            for (ContratoLaboralEntity c : contratos) {
                String quien = c.getEmpleado() != null
                        ? c.getEmpleado().getNombreCompletoResuelto()
                        : ("contrato " + c.getId());
                try {
                    // Transacción por contrato. Un fallo aquí no bota el resto.
                    nominaService.liquidarContrato(periodoId, c.getId(), empresaId);
                    procesoService.itemOk(procesoId);
                } catch (RuntimeException e) {
                    // El lote sigue: el usuario resuelve los que fallaron.
                    procesoService.itemError(procesoId, quien, e.getMessage());
                }
            }

            procesoService.finalizar(procesoId);

        } catch (RuntimeException e) {
            // Solo llega aquí lo que no es de un contrato puntual.
            log.error("[proceso {}] la liquidación del período {} falló: {}",
                    procesoId, periodoId, e.getMessage(), e);
            procesoService.fallar(procesoId, e.getMessage());
        }
    }

    /** Crea el proceso y lanza. Atajo para el controller. */
    public Long lanzar(Long periodoId, Integer empresaId, Long usuarioId) {
        PeriodoNominaEntity periodo = periodoRepo.findByIdAndEmpresaId(periodoId, empresaId)
                .orElseThrow(() -> new com.cloud_technological.aura_pos.utils.GlobalException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Período no encontrado"));

        int total = contratoService.vigentesEnPeriodo(
                empresaId, periodo.getFechaInicio(), periodo.getFechaFin()).size();

        // crear() falla si ya hay uno corriendo para este período: dos
        // liquidaciones simultáneas se pisarían y el resultado dependería de
        // quién termine antes.
        var proceso = procesoService.crear(empresaId, Tipo.LIQUIDACION_PERIODO,
                periodoId, usuarioId, total);

        liquidarPeriodo(proceso.getId(), periodoId, empresaId);
        return proceso.getId();
    }
}
