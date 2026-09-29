package com.cloud_technological.aura_pos.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.services.NotaDiarioPlantillaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pasada nocturna de las plantillas recurrentes: cuando llega su día del mes,
 * deja la nota en BORRADOR para que el contador la revise y la contabilice.
 * Nunca contabiliza sola.
 *
 * <p>Cada plantilla va en su propia transacción: una que falle (mes cerrado,
 * cuenta desactivada) no frena a las demás, y se reintenta al día siguiente
 * porque su último mes generado no avanzó.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotaDiarioPlantillaScheduler {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final NotaDiarioPlantillaService plantillaService;

    @Scheduled(cron = "0 40 0 * * *", zone = "America/Bogota")
    public void generarRecurrentes() {
        LocalDate hoy = LocalDate.now(BOGOTA);
        int generadas = 0;
        for (Long id : plantillaService.recurrentesActivas()) {
            try {
                if (plantillaService.generarProgramada(id, hoy)) generadas++;
            } catch (Exception e) {
                log.warn(">>> NOTAS RECURRENTES: la plantilla {} no se pudo generar: {}", id, e.getMessage());
            }
        }
        if (generadas > 0) log.info(">>> NOTAS RECURRENTES: {} borradores generados", generadas);
    }
}
