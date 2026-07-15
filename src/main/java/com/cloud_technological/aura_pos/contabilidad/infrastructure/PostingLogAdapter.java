package com.cloud_technological.aura_pos.contabilidad.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.PostingLog;
import com.cloud_technological.aura_pos.entity.ContabilidadPostingLogEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.ContabilidadPostingLogJPARepository;
import com.cloud_technological.aura_pos.services.ErrorLogService;

import lombok.RequiredArgsConstructor;

/**
 * PostingLog sobre la tabla contabilidad_posting_log (E3): vista positiva
 * ("qué se contabilizó y desde dónde") + fallo también al ErrorLog para
 * reproceso. El log JAMÁS revienta el posting: si falla, solo se loguea.
 */
@Component
@RequiredArgsConstructor
public class PostingLogAdapter implements PostingLog {

    private static final Logger log = LoggerFactory.getLogger(PostingLogAdapter.class);

    private final ContabilidadPostingLogJPARepository postingLogRepo;
    private final ErrorLogService errorLogService;

    @Override
    public void exito(ContextoContabilizacion ctx, Long asientoId) {
        log.info("Contabilizado {} #{} (empresa {}) → asiento {}",
                ctx.tipoOrigen(), ctx.origenId(), ctx.empresaId(), asientoId);
        registrar(ctx, asientoId, "EXITO", null);
    }

    @Override
    public void fallo(ContextoContabilizacion ctx, Exception causa) {
        log.error("Fallo el posting de {} #{} (empresa {}): {}",
                ctx.tipoOrigen(), ctx.origenId(), ctx.empresaId(), causa.getMessage());
        registrar(ctx, null, "ERROR",
                causa.getClass().getSimpleName() + ": " + causa.getMessage());
        errorLogService.registrarAsync(
                "EVENT",
                "contabilidad/auto/" + ctx.tipoOrigen().toLowerCase() + "/" + ctx.origenId(),
                500,
                "Fallo al generar asiento automático de " + ctx.tipoOrigen().toLowerCase()
                        + " #" + ctx.origenId(),
                causa.getClass().getSimpleName() + ": " + causa.getMessage(),
                "sistema",
                "-");
    }

    private void registrar(ContextoContabilizacion ctx, Long asientoId, String estado, String error) {
        try {
            postingLogRepo.save(ContabilidadPostingLogEntity.builder()
                    .empresaId(ctx.empresaId())
                    .tipoOrigen(ctx.tipoOrigen())
                    .origenId(ctx.origenId())
                    .asientoId(asientoId)
                    .estado(estado)
                    .error(error != null && error.length() > 500 ? error.substring(0, 500) : error)
                    .usuarioId(ctx.usuarioId() != null ? ctx.usuarioId().longValue() : null)
                    .build());
        } catch (Exception e) {
            log.warn("No se pudo escribir el posting log ({} #{}): {}",
                    ctx.tipoOrigen(), ctx.origenId(), e.getMessage());
        }
    }
}
