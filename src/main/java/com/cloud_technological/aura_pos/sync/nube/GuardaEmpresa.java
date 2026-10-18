package com.cloud_technological.aura_pos.sync.nube;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Segunda barrera para que a una tienda nunca le baje información de otra
 * empresa.
 *
 * <p>La primera es el filtro SQL fijo de cada tabla en
 * {@link com.cloud_technological.aura_pos.sync.SyncTablas}. Esta revisa lo que
 * ese filtro devolvió, antes de enviarlo: si una tabla nueva queda mal filtrada,
 * la bajada se corta en vez de entregar datos ajenos.
 */
public final class GuardaEmpresa {

    private GuardaEmpresa() {
    }

    /** Una fila de la bajada pertenece a otra empresa. */
    public static class DatosDeOtraEmpresa extends RuntimeException {
        public DatosDeOtraEmpresa(String tabla, String detalle) {
            super("La tabla " + tabla + " traía datos de otra empresa (" + detalle + "): se canceló la bajada");
        }
    }

    /**
     * Verifica que todas las filas sean de la empresa del nodo.
     *
     * <ul>
     * <li>En {@code empresa}, el id de cada fila debe ser el de la empresa.</li>
     * <li>En las demás, {@code empresa_id} debe ser el de la empresa o vacío
     * (plantillas globales, como los perfiles o el plan de cuentas base).</li>
     * </ul>
     *
     * @param tabla     nombre de la tabla
     * @param filas     arreglo JSON de filas (como lo produce {@code row_to_json})
     * @param empresaId empresa del nodo
     * @throws DatosDeOtraEmpresa si alguna fila es de otra empresa
     */
    public static void verificar(String tabla, JsonNode filas, Integer empresaId) {
        if (filas == null || !filas.isArray()) return;
        boolean esEmpresa = "empresa".equals(tabla);
        for (JsonNode fila : filas) {
            JsonNode valor = fila.get(esEmpresa ? "id" : "empresa_id");
            if (valor == null || valor.isNull()) {
                if (esEmpresa) throw new DatosDeOtraEmpresa(tabla, "fila sin id");
                continue;
            }
            if (empresaId == null || valor.asLong() != empresaId.longValue())
                throw new DatosDeOtraEmpresa(tabla, "empresa " + valor.asText());
        }
    }
}
