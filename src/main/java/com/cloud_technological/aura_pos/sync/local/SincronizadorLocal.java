package com.cloud_technological.aura_pos.sync.local;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ciclo de sincronización del nodo local.
 *
 * <ol>
 * <li>Sube la cola en orden. Si un comando falla se detiene ahí: los siguientes
 * pueden depender de él (la venta necesita su turno, la anulación su venta).</li>
 * <li>Cada {@code app.sync.catalogo-cada-ms} baja el catálogo; las existencias
 * solo cuando la cola quedó vacía.</li>
 * <li>En los demás ciclos, con la cola vacía, baja solo los turnos abiertos de
 * la nube (la tienda es la contingencia de la nube).</li>
 * </ol>
 *
 * <p>Sin conexión no pasa nada: la tienda sigue trabajando y el siguiente ciclo
 * vuelve a intentar.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SincronizadorLocal {

    private final SyncProperties props;
    private final ClienteNube nube;
    private final SyncOutboxQueryRepository outbox;
    private final ReplicadorLocal replicador;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    private volatile long ultimaBajadaMs = 0;

    /** Ciclo automático. En la nube no hace nada. */
    @Scheduled(fixedDelayString = "${app.sync.intervalo-ms:15000}", initialDelayString = "${app.sync.retraso-inicial-ms:20000}")
    public void ciclo() {
        if (!props.esNodoLocal() || !props.nubeConfigurada()) return;
        boolean toca = System.currentTimeMillis() - ultimaBajadaMs >= props.getCatalogoCadaMs();
        sincronizar(toca);
    }

    /**
     * Sube la cola y, si se pide, baja catálogo y existencias.
     *
     * @param bajar true para bajar también de la nube
     * @return true si terminó con conexión
     */
    public synchronized boolean sincronizar(boolean bajar) {
        if (!props.esNodoLocal() || !props.nubeConfigurada()) return false;
        try {
            boolean colaVacia = empujar();
            if (bajar) bajar(colaVacia);
            else if (colaVacia) bajarTurnos();
            outbox.guardarEstado(EstadoNodoLocal.EN_LINEA, "true");
            return true;
        } catch (ResourceAccessException e) {
            outbox.guardarEstado(EstadoNodoLocal.EN_LINEA, "false");
            log.debug("[SYNC] Sin conexión con la nube: {}", e.getMessage());
            return false;
        } catch (RuntimeException e) {
            // La nube contestó pero rechazó (token, versión, error interno).
            outbox.guardarEstado(EstadoNodoLocal.EN_LINEA, "true");
            outbox.guardarEstado(EstadoNodoLocal.ULTIMO_ERROR, LocalDateTime.now() + " · " + e.getMessage());
            log.warn("[SYNC] Error sincronizando: {}", e.getMessage());
            return true;
        }
    }

    /**
     * Sube solo la cola, ya. Lo usa la factura electrónica para devolver el CUFE
     * de una vez cuando hay internet.
     *
     * @return true si la cola quedó vacía
     */
    public synchronized boolean empujarAhora() {
        if (!props.esNodoLocal() || !props.nubeConfigurada()) return false;
        try {
            boolean vacia = empujar();
            outbox.guardarEstado(EstadoNodoLocal.EN_LINEA, "true");
            return vacia;
        } catch (ResourceAccessException e) {
            outbox.guardarEstado(EstadoNodoLocal.EN_LINEA, "false");
            return false;
        } catch (RuntimeException e) {
            outbox.guardarEstado(EstadoNodoLocal.ULTIMO_ERROR, LocalDateTime.now() + " · " + e.getMessage());
            return false;
        }
    }

    /**
     * Sube la cola por lotes hasta vaciarla o encontrar un error.
     *
     * @return true si la cola quedó vacía
     */
    private boolean empujar() {
        while (true) {
            List<SyncOutboxQueryRepository.Pendiente> lote = outbox.pendientes(props.getLote());
            if (lote.isEmpty()) return true;

            SyncDtos.PushPeticion peticion = new SyncDtos.PushPeticion();
            for (SyncOutboxQueryRepository.Pendiente p : lote) peticion.getComandos().add(aComando(p));

            SyncDtos.PushRespuesta respuesta = nube.push(peticion);
            Map<String, SyncDtos.Resultado> porUuid = respuesta.getResultados().stream()
                    .collect(Collectors.toMap(SyncDtos.Resultado::getUuid, Function.identity(), (a, b) -> a));

            for (SyncOutboxQueryRepository.Pendiente p : lote) {
                SyncDtos.Resultado r = porUuid.get(p.uuid());
                // Sin resultado: la nube se detuvo antes por un error previo.
                if (r == null) return false;
                if (!r.isOk()) {
                    outbox.marcarError(p.id(), r.getError());
                    outbox.guardarEstado(EstadoNodoLocal.ULTIMO_ERROR,
                            LocalDateTime.now() + " · " + p.tipo() + " " + p.idLocal() + ": " + r.getError());
                    log.warn("[SYNC] {} {} rechazado por la nube: {}", p.tipo(), p.idLocal(), r.getError());
                    return false;
                }
                aplicarResultado(p, r);
                outbox.marcarEnviado(p.id(), texto(r));
            }
            outbox.guardarEstado(EstadoNodoLocal.ULTIMO_ENVIO, LocalDateTime.now().toString());
            if (lote.size() < props.getLote()) return outbox.contarPorEnviar() == 0;
        }
    }

    /** Baja el catálogo y, si la cola está vacía, también las existencias. */
    private void bajar(boolean colaVacia) {
        SyncDtos.PullRespuesta r = nube.pull(colaVacia);
        List<String> fallidas = replicador.aplicarCatalogo(r);
        outbox.guardarEstado(EstadoNodoLocal.ULTIMA_BAJADA, LocalDateTime.now().toString());
        if (!fallidas.isEmpty())
            outbox.guardarEstado(EstadoNodoLocal.ULTIMO_ERROR,
                    LocalDateTime.now() + " · Catálogo sin aplicar en: " + String.join(", ", fallidas));
        if (r.isIncluyeSaldos()) {
            try {
                replicador.aplicarSaldos(r);
                outbox.guardarEstado(EstadoNodoLocal.ULTIMA_BAJADA_SALDOS, LocalDateTime.now().toString());
            } catch (ReplicadorLocal.SaldosDesactualizados e) {
                // Se vendió mientras bajaban: se reintenta en el siguiente ciclo.
                log.info("[SYNC] Existencias no aplicadas: {}", e.getMessage());
            }
        }
        ultimaBajadaMs = System.currentTimeMillis();
    }

    /**
     * Baja solo los turnos de la nube. Corre en cada ciclo con la cola vacía:
     * así un turno que se abrió en la nube hace un momento ya está en la tienda
     * cuando se cae internet, y el cajero sigue en él.
     */
    private void bajarTurnos() {
        try {
            replicador.aplicarTurnos(nube.pullTurnos());
        } catch (ReplicadorLocal.SaldosDesactualizados e) {
            log.debug("[SYNC] Turnos no aplicados: {}", e.getMessage());
        }
    }

    /**
     * Guarda en el nodo lo que la nube devolvió y la tienda necesita ver. Hoy:
     * el CUFE, QR y número de la factura electrónica.
     */
    private void aplicarResultado(SyncOutboxQueryRepository.Pendiente p, SyncDtos.Resultado r) {
        if (!TiposComando.FE_EMITIR.equals(p.tipo()) || r.getDatos() == null || p.idLocal() == null) return;
        JsonNode d = r.getDatos();
        jdbc.update("""
                UPDATE venta
                SET cufe = :cufe, qr_data = :qr, estado_dian = :estado, factus_url = :url, factus_numero = :numero
                WHERE id = :id
                """, new MapSqlParameterSource("id", p.idLocal())
                .addValue("cufe", d.path("cufe").asText(null))
                .addValue("qr", d.path("qr").asText(null))
                .addValue("estado", d.path("estadoDian").asText("EMITIDA"))
                .addValue("url", d.path("pdfUrl").asText(null))
                .addValue("numero", d.path("facturaNumero").asText(null)));
    }

    private SyncDtos.Comando aComando(SyncOutboxQueryRepository.Pendiente p) {
        SyncDtos.Comando c = new SyncDtos.Comando();
        c.setUuid(p.uuid());
        c.setTipo(p.tipo());
        c.setIdLocal(p.idLocal());
        c.setEmpresaId(p.empresaId());
        c.setUsuarioId(p.usuarioId());
        c.setFecha(p.fecha());
        try {
            c.setPayload(objectMapper.readTree(p.payload()));
        } catch (Exception e) {
            throw new IllegalStateException("Comando " + p.id() + " con payload ilegible", e);
        }
        return c;
    }

    private String texto(SyncDtos.Resultado r) {
        try {
            return objectMapper.writeValueAsString(r);
        } catch (Exception e) {
            return null;
        }
    }
}
