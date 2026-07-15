package com.cloud_technological.aura_pos.contabilidad.infrastructure.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.cloud_technological.aura_pos.contabilidad.application.ContabilizarDocumentoUseCase;
import com.cloud_technological.aura_pos.contabilidad.application.ContextoContabilizacion;
import com.cloud_technological.aura_pos.contabilidad.application.port.PostingLog;

import lombok.RequiredArgsConstructor;

/**
 * EL listener único de contabilización. Corre AFTER_COMMIT: un fallo del
 * posting JAMÁS tumba la operación de negocio — queda en el PostingLog
 * (y ErrorLog) para reproceso.
 */
@Component
@RequiredArgsConstructor
public class ContabilizacionListener {

    private final ContabilizarDocumentoUseCase contabilizar;
    private final PostingLog postingLog;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentoContabilizable(DocumentoContabilizableEvent event) {
        ContextoContabilizacion ctx = new ContextoContabilizacion(
                event.tipoOrigen(), event.origenId(), event.empresaId(), event.usuarioId());
        try {
            contabilizar.ejecutar(ctx);
        } catch (Exception ex) {
            postingLog.fallo(ctx, ex);
        }
    }
}
