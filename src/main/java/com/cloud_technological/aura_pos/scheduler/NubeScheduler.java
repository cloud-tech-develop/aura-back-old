package com.cloud_technological.aura_pos.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.nube.NubeAlmacenamiento;
import com.cloud_technological.aura_pos.services.nube.NubeMantenimiento;

/**
 * Tareas de la nube de documentos (docs/PLAN_NUBE_DOCUMENTOS.md F6):
 * liberar carpetas privadas de firmas que ya no atienden a la empresa, y purgar
 * la papelera vencida (30 días).
 */
@Component
public class NubeScheduler {

    private static final Logger logger = LoggerFactory.getLogger(NubeScheduler.class);

    /** Lotes por corrida: hasta 10 000 archivos; lo que quede sale la noche siguiente. */
    private static final int MAX_LOTES = 50;

    private final NubeMantenimiento mantenimiento;
    private final NubeAlmacenamiento almacenamiento;

    public NubeScheduler(NubeMantenimiento mantenimiento, NubeAlmacenamiento almacenamiento) {
        this.mantenimiento = mantenimiento;
        this.almacenamiento = almacenamiento;
    }

    /** Cada hora al minuto 20: el vínculo puede terminar en cualquier momento. */
    @Scheduled(cron = "0 20 * * * *")
    public void liberarPrivadas() {
        try {
            int n = mantenimiento.liberarPrivadasSinVinculo();
            if (n > 0) logger.info(">>> NUBE: {} carpeta(s) privada(s) pasaron a compartidas (fin de vínculo).", n);
        } catch (Exception e) {
            logger.error(">>> NUBE: error liberando carpetas privadas: {}", e.getMessage(), e);
        }
    }

    /** Todos los días a las 03:30. Sin bucket configurado no se borra nada. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgarPapelera() {
        if (!almacenamiento.disponible()) return;
        try {
            int total = 0;
            for (int i = 0; i < MAX_LOTES; i++) {
                int n = mantenimiento.purgarLote();
                total += n;
                if (n < NubeMantenimiento.LOTE) break;
            }
            if (total > 0) logger.info(">>> NUBE: {} archivo(s) vencido(s) borrado(s) de la papelera.", total);
        } catch (Exception e) {
            logger.error(">>> NUBE: error purgando la papelera: {}", e.getMessage(), e);
        }
    }
}
