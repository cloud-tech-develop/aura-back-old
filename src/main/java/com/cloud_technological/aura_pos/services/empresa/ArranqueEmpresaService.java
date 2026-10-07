package com.cloud_technological.aura_pos.services.empresa;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.PlanCuentasService;

/**
 * Siembra lo que necesitan las líneas de uso de una empresa (PUC, configuración
 * contable…) y deja constancia del resultado en {@code empresa.configuracion}.
 *
 * <p>Cada paso corre en su propia transacción: si falla, la empresa ya creada no se
 * deshace; el paso queda en ERROR con el motivo y se puede reintentar desde el
 * panel. Todos los pasos son idempotentes.
 */
@Service
public class ArranqueEmpresaService {

    private static final Logger log = LoggerFactory.getLogger(ArranqueEmpresaService.class);
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final PlanCuentasService planCuentasService;
    private final EmpresaJPARepository empresaRepo;
    private final TransactionTemplate nueva;

    public ArranqueEmpresaService(PlanCuentasService planCuentasService, EmpresaJPARepository empresaRepo,
            PlatformTransactionManager txManager) {
        this.planCuentasService = planCuentasService;
        this.empresaRepo = empresaRepo;
        this.nueva = new TransactionTemplate(txManager);
        this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Pasos que piden esas líneas, sin repetir. */
    public static Set<PasoArranque> pasosDe(Collection<LineaUso> lineas) {
        Set<PasoArranque> pasos = new LinkedHashSet<>();
        for (LineaUso l : lineas) pasos.addAll(l.getPasos());
        return pasos;
    }

    /**
     * Corre los pasos cuando la transacción actual confirme (la empresa ya existe
     * de verdad); sin transacción, los corre ya. Así un fallo en la siembra nunca
     * tumba la creación de la empresa.
     */
    public void programar(Integer empresaId, Collection<PasoArranque> pasos) {
        if (pasos.isEmpty()) return;
        Set<PasoArranque> copia = new LinkedHashSet<>(pasos);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ejecutar(empresaId, copia);
                }
            });
        } else {
            ejecutar(empresaId, copia);
        }
    }

    /** Corre los pasos ya y anota cada resultado. Nunca lanza: el error queda guardado. */
    public void ejecutar(Integer empresaId, Collection<PasoArranque> pasos) {
        for (PasoArranque paso : pasos) {
            String resultado;
            String detalle;
            try {
                detalle = nueva.execute(tx -> correr(empresaId, paso));
                resultado = ConfiguracionEmpresaJson.OK;
            } catch (RuntimeException e) {
                log.error("Arranque {} de la empresa {} falló", paso, empresaId, e);
                resultado = ConfiguracionEmpresaJson.ERROR;
                detalle = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
            anotar(empresaId, paso, resultado, detalle);
        }
    }

    private String correr(Integer empresaId, PasoArranque paso) {
        return switch (paso) {
            case CONTABLE -> {
                int nuevas = planCuentasService.seedPUC(empresaId);
                yield nuevas > 0
                        ? "Plan de cuentas cargado (" + nuevas + " cuentas nuevas) y configuración contable por defecto"
                        : "El plan de cuentas ya estaba; se completó la configuración contable por defecto";
            }
        };
    }

    private void anotar(Integer empresaId, PasoArranque paso, String resultado, String detalle) {
        try {
            nueva.executeWithoutResult(tx -> {
                EmpresaEntity e = empresaRepo.findById(empresaId).orElse(null);
                if (e == null) return;
                Map<String, Object> cfg = ConfiguracionEmpresaJson.leer(e.getConfiguracion());
                ConfiguracionEmpresaJson.escribirPaso(cfg, paso, resultado, ahora(), recortar(detalle, 500));
                e.setConfiguracion(ConfiguracionEmpresaJson.aColumna(cfg));
                empresaRepo.save(e);
            });
        } catch (RuntimeException ex) {
            log.error("No se pudo anotar el arranque {} de la empresa {}", paso, empresaId, ex);
        }
    }

    static String ahora() {
        return OffsetDateTime.now(BOGOTA).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
