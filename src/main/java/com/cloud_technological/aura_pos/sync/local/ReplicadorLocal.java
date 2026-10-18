package com.cloud_technological.aura_pos.sync.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.sync.SyncIds;
import com.cloud_technological.aura_pos.sync.SyncTablas;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.sync.dto.SyncDtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Aplica en la base del nodo lo que bajó de la nube.
 *
 * <p>Las filas llegan como las produce {@code row_to_json} en la nube y entran
 * con {@code json_populate_recordset}: Postgres convierte cada valor al tipo de
 * su columna (fechas, numéricos, jsonb), así que no hay mapeo en Java que se
 * desactualice cuando una tabla gana columnas.
 *
 * <p>Cada tabla es una foto completa de la empresa: lo que el nodo tenga de la
 * nube (id menor a {@link SyncIds#BASE_LOCAL}) y ya no venga, se borró allá y se
 * borra aquí. Sin eso, un registro borrado y vuelto a crear en la nube (una
 * sucursal quitada y reasignada a un usuario) chocaría por sus índices únicos.
 *
 * <p>Cada tabla va en su propio savepoint: si una falla, se registra y las demás
 * se aplican igual.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplicadorLocal {

    /** Tablas cuyos registros el nodo puede crear sin internet y luego fusionar. */
    private static final Set<String> FUSIONABLES = Set.of(TiposComando.T_TERCERO);

    private final NamedParameterJdbcTemplate jdbc;
    private final SyncOutboxQueryRepository outbox;
    private final EstadoNodoLocal estado;
    private final ObjectMapper objectMapper;

    private final Map<String, Estructura> estructuras = new ConcurrentHashMap<>();

    /** Columnas escribibles y llave primaria de una tabla local. */
    private record Estructura(List<String> columnas, List<String> llave) {
        boolean llaveEsId() {
            return llave.size() == 1 && "id".equals(llave.get(0));
        }
    }

    /** La nube tenía saldos más viejos que el nodo: se descartó la bajada. */
    public static class SaldosDesactualizados extends RuntimeException {
        public SaldosDesactualizados() {
            super("Hay operaciones nuevas en la tienda que la nube todavía no tiene");
        }
    }

    /**
     * Reemplaza el catálogo local con el de la nube, fusiona los registros que el
     * nodo creó y la nube ya tiene, y guarda la identidad del nodo.
     *
     * @param r lo bajado de la nube
     * @return tablas que no se pudieron aplicar (vacío si todo entró)
     */
    @Transactional
    public List<String> aplicarCatalogo(SyncDtos.PullRespuesta r) {
        boolean sinLlaves = desactivarLlaves();
        Integer empresaId = r.getNodo() != null ? r.getNodo().getEmpresaId() : null;
        List<String> fallidas = new ArrayList<>();
        // Primero la fusión: la copia local del cliente choca por documento con la
        // que viene de la nube.
        fusionar(r.getMapas());
        for (SyncTablas.Tabla t : SyncTablas.delGrupo(SyncTablas.Grupo.CATALOGO)) {
            JsonNode filas = r.getTablas().get(t.nombre());
            if (filas != null && !aplicarTabla(t, filas, empresaId, sinLlaves)) fallidas.add(t.nombre());
        }
        try {
            if (r.getNodo() != null)
                outbox.guardarEstado(EstadoNodoLocal.NODO, objectMapper.writeValueAsString(r.getNodo()));
        } catch (Exception e) {
            log.warn("[SYNC] No se pudo guardar la identidad del nodo: {}", e.getMessage());
        }
        r.getPisosConsecutivo().forEach(estado::guardarPisoConsecutivoVenta);
        return fallidas;
    }

    /**
     * Reemplaza las existencias locales con las de la nube.
     *
     * <p>Solo es correcto si la nube ya tiene todas las ventas del nodo. Se revisa
     * la cola al final, con las filas de inventario ya bloqueadas: una venta que
     * se confirmó mientras bajaban los saldos deja la cola con pendientes y la
     * bajada se revierte; una que llega después espera el bloqueo y descuenta
     * sobre el saldo nuevo.
     *
     * @param r lo bajado de la nube (con saldos)
     * @throws SaldosDesactualizados si apareció algo en la cola
     */
    @Transactional
    public void aplicarSaldos(SyncDtos.PullRespuesta r) {
        boolean sinLlaves = desactivarLlaves();
        Integer empresaId = r.getNodo() != null ? r.getNodo().getEmpresaId() : null;
        // La fila de inventario que el nodo creó para un producto ya existe en la
        // nube con otro id: la de la nube la reemplaza.
        jdbc.update("DELETE FROM inventario WHERE id >= :base", new MapSqlParameterSource("base", SyncIds.BASE_LOCAL));
        for (SyncTablas.Tabla t : SyncTablas.delGrupo(SyncTablas.Grupo.SALDO)) {
            JsonNode filas = r.getTablas().get(t.nombre());
            if (filas != null) aplicarTabla(t, filas, empresaId, sinLlaves);
        }
        if (outbox.contarPorEnviar() > 0) throw new SaldosDesactualizados();
    }

    /**
     * Aplica una tabla dentro de un savepoint.
     *
     * @param sinLlaves las llaves foráneas están apagadas (ver {@link #desactivarLlaves()})
     * @return true si entró completa
     */
    private boolean aplicarTabla(SyncTablas.Tabla t, JsonNode filas, Integer empresaId, boolean sinLlaves) {
        Estructura e = estructura(t.nombre());
        if (e == null || !filas.isArray()) {
            log.warn("[SYNC] La tabla {} no existe o no tiene llave primaria en el nodo: se omite", t.nombre());
            return false;
        }
        MapSqlParameterSource vacio = new MapSqlParameterSource();
        jdbc.update("SAVEPOINT sync_tabla", vacio);
        try {
            borrarDeLaNube(t, e, filas, empresaId, sinLlaves);
            upsert(t.nombre(), e, filas);
            jdbc.update("RELEASE SAVEPOINT sync_tabla", vacio);
            return true;
        } catch (DataAccessException ex) {
            jdbc.update("ROLLBACK TO SAVEPOINT sync_tabla", vacio);
            log.warn("[SYNC] No se pudo aplicar {}: {}", t.nombre(), ex.getMostSpecificCause().getMessage());
            return false;
        }
    }

    /**
     * Borra la copia que el nodo tiene de la nube antes de volver a insertarla. Lo
     * creado en el nodo (id del rango local) no se toca.
     *
     * <p>Con las llaves apagadas se borra toda la copia: la tabla queda igual a la
     * foto de la nube aunque un mismo id tenga allá otra llave única (en un nodo
     * nuevo, los módulos que sembraron las migraciones tienen otros ids que los de
     * la nube, y actualizar fila por fila choca con el índice único). Con las
     * llaves activas borrar en cascada arrastraría datos del nodo, así que solo se
     * borra lo que la nube ya no tiene.
     */
    private void borrarDeLaNube(SyncTablas.Tabla t, Estructura e, JsonNode filas, Integer empresaId, boolean sinLlaves) {
        if (!e.llaveEsId()) return;
        if (t.filtro() != null && empresaId == null) return;
        String sql = "DELETE FROM " + q(t.nombre()) + " WHERE id < :base"
                + (t.filtro() == null ? "" : " AND " + t.filtro())
                + (sinLlaves ? ""
                        : " AND id NOT IN (SELECT CAST(x->>'id' AS bigint) FROM json_array_elements(CAST(:filas AS json)) x)");
        jdbc.update(sql, new MapSqlParameterSource("base", SyncIds.BASE_LOCAL)
                .addValue("empresaId", empresaId)
                .addValue("filas", filas.toString()));
    }

    /** Inserta o actualiza las filas de una tabla por su llave primaria. */
    private void upsert(String tabla, Estructura e, JsonNode filas) {
        if (filas.isEmpty()) return;
        String cols = e.columnas().stream().map(ReplicadorLocal::q).collect(Collectors.joining(", "));
        String llave = e.llave().stream().map(ReplicadorLocal::q).collect(Collectors.joining(", "));
        List<String> resto = e.columnas().stream().filter(c -> !e.llave().contains(c)).toList();
        String conflicto = resto.isEmpty()
                ? "DO NOTHING"
                : "DO UPDATE SET " + resto.stream().map(c -> q(c) + " = EXCLUDED." + q(c))
                        .collect(Collectors.joining(", "));
        String sql = "INSERT INTO " + q(tabla) + " (" + cols + ") SELECT " + cols
                + " FROM json_populate_recordset(NULL::" + q(tabla) + ", CAST(:filas AS json))"
                + " ON CONFLICT (" + llave + ") " + conflicto;
        jdbc.update(sql, new MapSqlParameterSource("filas", filas.toString()));
    }

    /**
     * El nodo creó un registro (ej. un cliente) que la nube ya tiene con otro id:
     * lo que apuntaba al id local pasa a apuntar al de la nube y la copia local se
     * borra. La fila de la nube entra después con el resto del catálogo.
     */
    private void fusionar(List<SyncDtos.Mapa> mapas) {
        if (mapas == null) return;
        MapSqlParameterSource vacio = new MapSqlParameterSource();
        for (SyncDtos.Mapa m : mapas) {
            if (!FUSIONABLES.contains(m.getTabla()) || !SyncIds.esLocal(m.getIdLocal())) continue;
            MapSqlParameterSource p = new MapSqlParameterSource("local", m.getIdLocal()).addValue("nube", m.getIdNube());
            boolean hayLocal = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM " + q(m.getTabla()) + " WHERE id = :local)", p, Boolean.class));
            if (!hayLocal) continue;
            jdbc.update("SAVEPOINT sync_fusion", vacio);
            try {
                for (Map<String, Object> fk : referencias(m.getTabla())) {
                    jdbc.update("UPDATE " + q((String) fk.get("tabla")) + " SET " + q((String) fk.get("columna"))
                            + " = :nube WHERE " + q((String) fk.get("columna")) + " = :local", p);
                }
                jdbc.update("DELETE FROM " + q(m.getTabla()) + " WHERE id = :local", p);
                jdbc.update("RELEASE SAVEPOINT sync_fusion", vacio);
                log.info("[SYNC] {} {} del nodo fusionado con {} de la nube", m.getTabla(), m.getIdLocal(), m.getIdNube());
            } catch (DataAccessException ex) {
                jdbc.update("ROLLBACK TO SAVEPOINT sync_fusion", vacio);
                log.warn("[SYNC] No se pudo fusionar {} {}: {}", m.getTabla(), m.getIdLocal(),
                        ex.getMostSpecificCause().getMessage());
            }
        }
    }

    /** Columnas (tabla, columna) con llave foránea hacia la tabla dada. */
    private List<Map<String, Object>> referencias(String tabla) {
        return jdbc.queryForList("""
                SELECT cl.relname AS tabla, a.attname AS columna
                FROM pg_constraint k
                JOIN pg_class cl ON cl.oid = k.conrelid
                JOIN pg_attribute a ON a.attrelid = k.conrelid AND a.attnum = ANY (k.conkey)
                WHERE k.contype = 'f' AND k.confrelid = CAST(:tabla AS regclass)
                """, new MapSqlParameterSource("tabla", "public." + tabla));
    }

    /**
     * Apaga llaves foráneas y triggers mientras dura la transacción, para no
     * depender del orden de las tablas. Requiere superusuario (la base del nodo
     * es nuestra); sin él, se confía en el orden de {@link SyncTablas}.
     *
     * @return true si quedaron apagadas
     */
    private boolean desactivarLlaves() {
        Boolean superusuario = jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname = current_user", new MapSqlParameterSource(), Boolean.class);
        if (!Boolean.TRUE.equals(superusuario)) return false;
        jdbc.update("SET LOCAL session_replication_role = replica", new MapSqlParameterSource());
        return true;
    }

    private Estructura estructura(String tabla) {
        return estructuras.computeIfAbsent(tabla, t -> {
            List<String> columnas = jdbc.queryForList("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = :tabla AND is_generated = 'NEVER'
                    ORDER BY ordinal_position
                    """, new MapSqlParameterSource("tabla", t), String.class);
            if (columnas.isEmpty()) return null;
            List<String> llave = jdbc.queryForList("""
                    SELECT a.attname
                    FROM pg_index i
                    JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                    WHERE i.indrelid = CAST(:tabla AS regclass) AND i.indisprimary
                    """, new MapSqlParameterSource("tabla", "public." + t), String.class);
            if (llave.isEmpty()) return null;
            return new Estructura(columnas, llave);
        });
    }

    /** Identificador entre comillas: los nombres vienen del catálogo de Postgres o de SyncTablas. */
    private static String q(String identificador) {
        return "\"" + identificador.replace("\"", "\"\"") + "\"";
    }
}
