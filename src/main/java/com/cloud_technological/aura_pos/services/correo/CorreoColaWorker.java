package com.cloud_technological.aura_pos.services.correo;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.repositories.correo.CorreoQueryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cola de correos masivos (comprobantes de nómina). Cada pocos segundos toma una
 * tanda de pendientes y los envía de a uno, respetando el límite de envíos por
 * segundo de Resend ({@code CORREO_ENVIOS_POR_SEGUNDO}). Si el backend se apaga a
 * mitad, al arrancar los que quedaron tomados vuelven a la cola.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorreoColaWorker {

    private static final int TANDA = 20;
    private static final int MAX_INTENTOS = 3;

    private final CorreoQueryRepository query;
    private final CorreoService correos;
    private final ResendClient resend;

    @Value("${correo.envios-por-segundo:2}")
    private int enviosPorSegundo;

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            int n = query.liberarAtascados();
            if (n > 0) log.info("Correo: {} envíos interrumpidos volvieron a la cola", n);
        } catch (Exception e) {
            log.warn("Correo: no se pudo revisar la cola al arrancar: {}", e.getMessage());
        }
    }

    /** Envía los pendientes; sin llave de Resend no toma nada (quedan en la cola). */
    @Scheduled(fixedDelayString = "${correo.cola.intervalo-ms:5000}", initialDelay = 20000)
    public void procesar() {
        if (!resend.configurado()) return;
        List<Long> ids;
        try {
            ids = query.tomarPendientes(TANDA, MAX_INTENTOS);
        } catch (Exception e) {
            log.warn("Correo: no se pudo leer la cola: {}", e.getMessage());
            return;
        }
        long pausa = 1000L / Math.max(1, enviosPorSegundo);
        for (Long id : ids) {
            try {
                correos.enviarDeCola(id, MAX_INTENTOS);
            } catch (Exception e) {
                log.error("Correo {}: error al enviar: {}", id, e.getMessage());
            }
            try {
                Thread.sleep(pausa);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
