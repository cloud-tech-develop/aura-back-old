package com.cloud_technological.aura_pos.sync.local;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lo que el nodo sabe de sí mismo, guardado en {@code sync_estado}: su identidad
 * en la nube, la serie de sus ventas y cuándo sincronizó por última vez.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EstadoNodoLocal {

    public static final String NODO = "nodo";
    public static final String ULTIMO_ENVIO = "ultimo_envio";
    public static final String ULTIMA_BAJADA = "ultima_bajada";
    public static final String ULTIMA_BAJADA_SALDOS = "ultima_bajada_saldos";
    public static final String ULTIMO_ERROR = "ultimo_error";
    public static final String EN_LINEA = "en_linea";
    private static final String PISO_VENTA = "piso.venta.";

    private final SyncProperties props;
    private final SyncOutboxQueryRepository repo;
    private final ObjectMapper objectMapper;

    /**
     * Identidad del nodo tal como la entregó la nube en la última bajada.
     *
     * @return el nodo, vacío si todavía no ha bajado nada
     */
    public Optional<SyncDtos.NodoInfo> nodo() {
        return repo.leerEstado(NODO).map(json -> {
            try {
                return objectMapper.readValue(json, SyncDtos.NodoInfo.class);
            } catch (Exception e) {
                log.warn("[SYNC] Identidad del nodo ilegible en sync_estado: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Prefijo de las ventas hechas en este nodo.
     *
     * <p>Solo en modo LOCAL y si la nube le asignó serie propia; si no, la venta
     * usa el prefijo de la sucursal como siempre.
     *
     * @return el prefijo del nodo, o vacío
     */
    public Optional<String> prefijoVentas() {
        if (!props.esNodoLocal()) return Optional.empty();
        return nodo().map(SyncDtos.NodoInfo::getPrefijoVentas).filter(p -> !p.isBlank());
    }

    /**
     * Último consecutivo que la nube conoce de la serie del nodo en la sucursal.
     *
     * <p>La base del nodo no tiene el historial de ventas de la nube: sin este
     * piso, un nodo recién instalado empezaría otra vez en 1.
     *
     * @param sucursalId sucursal de la venta
     * @return el piso, 0 si no hay
     */
    public long pisoConsecutivoVenta(Integer sucursalId) {
        if (!props.esNodoLocal() || sucursalId == null) return 0;
        return repo.leerEstado(PISO_VENTA + sucursalId).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    public void guardarPisoConsecutivoVenta(String sucursalId, Long piso) {
        if (piso == null) return;
        long actual = repo.leerEstado(PISO_VENTA + sucursalId).map(Long::parseLong).orElse(0L);
        if (piso > actual) repo.guardarEstado(PISO_VENTA + sucursalId, String.valueOf(piso));
    }
}
