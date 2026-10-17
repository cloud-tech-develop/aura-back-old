package com.cloud_technological.aura_pos.sync.local;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.sync.SyncContexto;
import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncOutboxServiceImpl implements SyncOutboxService {

    private final SyncProperties props;
    private final SyncOutboxQueryRepository outbox;
    private final ObjectMapper objectMapper;

    /** {@inheritDoc} */
    @Override
    public boolean activo() {
        return props.esNodoLocal() && !SyncContexto.enReplay();
    }

    /** {@inheritDoc} */
    @Override
    public void registrar(String tipo, Long idLocal, Integer empresaId, Long usuarioId, Object payload) {
        if (!activo()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            // Sin el comando la nube nunca se enteraría de la operación: mejor no
            // guardarla que dejarla solo en la tienda.
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo preparar la operación para sincronizar: " + e.getOriginalMessage());
        }
        outbox.insertar(UUID.randomUUID().toString(), tipo, idLocal, empresaId, usuarioId, LocalDateTime.now(), json);
    }
}
