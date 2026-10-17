package com.cloud_technological.aura_pos.sync.local;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncLocalServiceImpl implements SyncLocalService {

    private final SyncProperties props;
    private final SyncOutboxQueryRepository outbox;
    private final EstadoNodoLocal estadoNodo;
    private final SincronizadorLocal sincronizador;

    /** {@inheritDoc} */
    @Override
    public SyncDtos.EstadoLocal estado() {
        SyncDtos.EstadoLocal e = new SyncDtos.EstadoLocal();
        e.setModo(props.esNodoLocal() ? SyncProperties.LOCAL : SyncProperties.NUBE);
        if (!props.esNodoLocal()) {
            e.setEnLinea(true);
            return e;
        }
        e.setNubeConfigurada(props.nubeConfigurada());
        e.setEnLinea("true".equals(outbox.leerEstado(EstadoNodoLocal.EN_LINEA).orElse("false")));
        e.setPendientes(outbox.contarPorEnviar());
        e.setConError(outbox.contarConError());
        e.setUltimoEnvio(outbox.leerEstado(EstadoNodoLocal.ULTIMO_ENVIO).orElse(null));
        e.setUltimaBajada(outbox.leerEstado(EstadoNodoLocal.ULTIMA_BAJADA).orElse(null));
        e.setUltimoError(outbox.leerEstado(EstadoNodoLocal.ULTIMO_ERROR).orElse(null));
        e.setNodo(estadoNodo.nodo().orElse(null));
        return e;
    }

    /** {@inheritDoc} */
    @Override
    public List<SyncDtos.FilaCola> cola(int limite) {
        exigirNodo();
        return outbox.ultimos(Math.max(1, Math.min(limite, 500)));
    }

    /** {@inheritDoc} */
    @Override
    public SyncDtos.EstadoLocal sincronizarAhora() {
        exigirNodo();
        sincronizador.sincronizar(true);
        return estado();
    }

    /** {@inheritDoc} */
    @Override
    public void descartar(Long id) {
        exigirNodo();
        if (!outbox.descartar(id))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo se descarta un comando que está en error");
    }

    private void exigirNodo() {
        if (!props.esNodoLocal())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Esta instancia no es un servidor local");
    }
}
