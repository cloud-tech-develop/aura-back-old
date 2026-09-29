package com.cloud_technological.aura_pos.contabilidad.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.contabilidad.application.generador.GeneradorRegistry;
import com.cloud_technological.aura_pos.contabilidad.application.port.AsientoRepositorio;
import com.cloud_technological.aura_pos.contabilidad.application.port.PeriodoContablePort;
import com.cloud_technological.aura_pos.contabilidad.application.port.PostingLog;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;

import lombok.RequiredArgsConstructor;

/**
 * Ciclo transversal de contabilización de cualquier documento, UNA sola vez:
 * idempotencia → generador (estrategia) → período abierto → persistir → log.
 * Los generadores solo deciden partidas; todo lo repetido vive aquí.
 *
 * REQUIRES_NEW: el posting se aísla de la transacción del documento origen
 * (que además ya hizo commit cuando el listener dispara este caso de uso).
 */
@Service
@RequiredArgsConstructor
public class ContabilizarDocumentoUseCase {

    private final GeneradorRegistry registry;
    private final AsientoRepositorio asientos;
    private final PeriodoContablePort periodos;
    private final PostingLog postingLog;
    private final com.cloud_technological.aura_pos.contabilidad.application.port.ConfigContabilizacion config;

    /**
     * Contabiliza el documento del contexto. Devuelve el id del asiento
     * creado, o {@code null} si ya existía (reproceso idempotente).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long ejecutar(ContextoContabilizacion ctx) {
        if (asientos.existePorOrigen(ctx.origen(), ctx.empresaId())) {
            return null;
        }
        var generador = registry.para(ctx.tipoOrigen());
        Asiento asiento = generador.generar(ctx)
                .conEstado(generador.siempreBorrador()
                        ? com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento.BORRADOR
                        : generador.siempreContabilizado()
                        ? com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento.CONTABILIZADO
                        : config.estadoInicial(ctx.empresaId()));    // E3: modo revisión
        Long periodoId = periodos.abiertoPara(ctx.empresaId(), asiento.fecha());
        Long asientoId = asientos.guardar(asiento, ctx.empresaId(), ctx.usuarioId(), periodoId);
        postingLog.exito(ctx, asientoId);
        return asientoId;
    }
}
