package com.cloud_technological.aura_pos.sync;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Papel de esta instancia en el modo híbrido (docs/MODO_HIBRIDO.md).
 *
 * <ul>
 * <li><b>NUBE</b> (por defecto): el servidor central. Recibe los comandos de los
 * nodos y les entrega el catálogo.</li>
 * <li><b>LOCAL</b>: el servidor de una tienda. Trabaja con su propia base, con o
 * sin internet, encola lo que hace y lo sube a la nube cuando hay conexión.</li>
 * </ul>
 */
@Component
public class SyncProperties {

    public static final String NUBE = "NUBE";
    public static final String LOCAL = "LOCAL";

    @Value("${app.sync.modo:NUBE}")
    private String modo;

    /** URL base de la nube, sin barra final. Ej: https://api.aura.com.co */
    @Value("${app.sync.nube-url:}")
    private String nubeUrl;

    /** Token del nodo, el que la nube mostró al registrarlo. */
    @Value("${app.sync.nodo-token:}")
    private String nodoToken;

    /** Cada cuánto se intenta subir la cola. */
    @Value("${app.sync.intervalo-ms:15000}")
    private long intervaloMs;

    /** Cada cuánto se baja el catálogo y los saldos de inventario. */
    @Value("${app.sync.catalogo-cada-ms:300000}")
    private long catalogoCadaMs;

    /** Comandos por lote al subir. */
    @Value("${app.sync.lote:50}")
    private int lote;

    /**
     * Indica si esta instancia es el servidor de una tienda.
     *
     * @return true en modo LOCAL
     */
    public boolean esNodoLocal() {
        return LOCAL.equalsIgnoreCase(modo == null ? "" : modo.trim());
    }

    /**
     * Indica si esta instancia es la nube.
     *
     * @return true salvo en modo LOCAL
     */
    public boolean esNube() {
        return !esNodoLocal();
    }

    /**
     * Indica si el nodo tiene con qué hablar con la nube.
     *
     * @return true si hay URL y token configurados
     */
    public boolean nubeConfigurada() {
        return nubeUrl != null && !nubeUrl.isBlank() && nodoToken != null && !nodoToken.isBlank();
    }

    public String getNubeUrl() {
        return nubeUrl == null ? "" : nubeUrl.replaceAll("/+$", "");
    }

    public String getNodoToken() {
        return nodoToken;
    }

    public long getIntervaloMs() {
        return intervaloMs;
    }

    public long getCatalogoCadaMs() {
        return catalogoCadaMs;
    }

    public int getLote() {
        return lote;
    }
}
