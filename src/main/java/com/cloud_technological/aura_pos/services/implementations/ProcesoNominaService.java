package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity;
import com.cloud_technological.aura_pos.entity.ProcesoNominaEntity.Estado;
import com.cloud_technological.aura_pos.repositories.nomina.ProcesoNominaJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Seguimiento de procesos asíncronos (Fase 7).
 *
 * <h2>Flujo</h2>
 * <ol>
 *   <li>El endpoint crea el proceso en PENDIENTE y devuelve su id (202 Accepted).</li>
 *   <li>El job lo toma → EN_PROCESO, va actualizando progreso.</li>
 *   <li>El front hace polling a {@code GET /proceso/{id}}.</li>
 *   <li>Al terminar → COMPLETADO / COMPLETADO_CON_ERRORES / FALLIDO.</li>
 * </ol>
 *
 * <h2>Dos decisiones que importan</h2>
 * <b>Los updates de progreso van en transacción propia</b> ({@code REQUIRES_NEW}):
 * si compartieran la del job, el front no vería nada hasta el commit final —
 * que es justo cuando ya no sirve.
 *
 * <p><b>Al arrancar se marcan como FALLIDO los que quedaron colgados.</b> Si el
 * servicio se reinicia mientras un job corría, su fila quedaría en EN_PROCESO
 * para siempre, bloqueando relanzarlo.
 */
@Slf4j
@Service
public class ProcesoNominaService {

    private final ProcesoNominaJPARepository procesoRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProcesoNominaService(ProcesoNominaJPARepository procesoRepo) {
        this.procesoRepo = procesoRepo;
    }

    /**
     * Limpia los procesos colgados por un reinicio.
     *
     * <p>Sin esto, un reinicio a mitad de una liquidación deja el período
     * bloqueado: {@code yaHayActivo()} lo vería corriendo para siempre.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void limpiarColgadosAlArrancar() {
        int n = procesoRepo.marcarColgadosComoFallidos();
        if (n > 0) {
            log.warn("{} procesos de nómina quedaron colgados por un reinicio anterior. "
                    + "Marcados como FALLIDO; se pueden relanzar.", n);
        }
    }

    /**
     * Crea un proceso.
     *
     * <p>Transacción propia: la fila debe existir antes de que el job arranque,
     * aunque el request que lo pidió falle después.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcesoNominaEntity crear(Integer empresaId, String tipo, Long referenciaId,
                                     Long usuarioId, int totalItems) {
        // No lanzar dos liquidaciones del mismo período a la vez: la segunda
        // pisaría a la primera y el resultado dependería de quién termine antes.
        if (!procesoRepo.findActivos(empresaId, tipo, referenciaId).isEmpty()) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Ya hay un proceso de tipo " + tipo + " corriendo para esta referencia. "
                    + "Espere a que termine.");
        }

        ProcesoNominaEntity p = new ProcesoNominaEntity();
        p.setEmpresaId(empresaId);
        p.setTipo(tipo);
        p.setReferenciaId(referenciaId);
        p.setUsuarioId(usuarioId);
        p.setTotalItems(totalItems);
        p.setEstado(Estado.PENDIENTE);
        return procesoRepo.save(p);
    }

    /**
     * Actualiza el progreso.
     *
     * <p>{@code REQUIRES_NEW}: el front tiene que verlo mientras el job corre,
     * no al final.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void progreso(Long procesoId, int progreso, String mensaje) {
        procesoRepo.findById(procesoId).ifPresent(p -> {
            p.setEstado(Estado.EN_PROCESO);
            p.setProgreso(Math.min(Math.max(progreso, 0), 100));
            p.setMensaje(mensaje);
            procesoRepo.save(p);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void itemOk(Long procesoId) {
        procesoRepo.findById(procesoId).ifPresent(p -> {
            p.setItemsOk(p.getItemsOk() + 1);
            p.setProgreso(porcentaje(p));
            procesoRepo.save(p);
        });
    }

    /**
     * Registra el fallo de un item <b>sin abortar el lote</b>.
     *
     * <p>En 500 empleados, que 3 fallen no debe botar los 497 buenos.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void itemError(Long procesoId, String referencia, String error) {
        procesoRepo.findById(procesoId).ifPresent(p -> {
            p.setItemsError(p.getItemsError() + 1);
            p.setProgreso(porcentaje(p));
            p.setErrores(agregarError(p.getErrores(), referencia, error));
            procesoRepo.save(p);
            log.warn("[proceso {}] item '{}' falló: {}", procesoId, referencia, error);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalizar(Long procesoId) {
        procesoRepo.findById(procesoId).ifPresent(p -> {
            p.setEstado(p.getItemsError() > 0 ? Estado.COMPLETADO_CON_ERRORES : Estado.COMPLETADO);
            p.setProgreso(100);
            p.setFinalizadoAt(LocalDateTime.now());
            if (p.getItemsError() > 0) {
                p.setMensaje("Completado con " + p.getItemsError() + " error(es) de "
                        + p.getTotalItems());
            }
            procesoRepo.save(p);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fallar(Long procesoId, String motivo) {
        procesoRepo.findById(procesoId).ifPresent(p -> {
            p.setEstado(Estado.FALLIDO);
            p.setMensaje(motivo != null && motivo.length() > 300 ? motivo.substring(0, 300) : motivo);
            p.setFinalizadoAt(LocalDateTime.now());
            procesoRepo.save(p);
        });
    }

    @Transactional(readOnly = true)
    public ProcesoNominaEntity consultar(Long procesoId, Integer empresaId) {
        return procesoRepo.findByIdAndEmpresaId(procesoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Proceso no encontrado"));
    }

    private int porcentaje(ProcesoNominaEntity p) {
        if (p.getTotalItems() == null || p.getTotalItems() == 0) return 0;
        int hechos = p.getItemsOk() + p.getItemsError();
        return Math.min(100, hechos * 100 / p.getTotalItems());
    }

    private String agregarError(String json, String referencia, String error) {
        try {
            List<Object> lista = new ArrayList<>();
            if (json != null && !json.isBlank()) {
                lista.addAll(objectMapper.readValue(json, List.class));
            }
            lista.add(java.util.Map.of(
                    "referencia", referencia != null ? referencia : "?",
                    "error", error != null ? error : "?"));
            return objectMapper.writeValueAsString(lista);
        } catch (Exception e) {
            log.error("No se pudo serializar el error del proceso: {}", e.getMessage());
            return json;
        }
    }
}
