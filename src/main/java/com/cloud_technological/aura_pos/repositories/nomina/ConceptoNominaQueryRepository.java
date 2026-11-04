package com.cloud_technological.aura_pos.repositories.nomina;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.BuscarConceptoParams;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.concepto.ConceptoDtos.ConceptoTipoDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

/**
 * Consultas del catálogo de conceptos de nómina: buscador avanzado, detalle,
 * tipos y validaciones del número visible.
 *
 * <p>Todas resuelven la precedencia igual que el motor: por clave interna, la
 * versión de la empresa gana sobre la global.
 */
@Repository
@RequiredArgsConstructor
public class ConceptoNominaQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * Conceptos visibles para la empresa en una fecha, uno por clave interna.
     * La precedencia se resuelve ANTES de filtrar por activo: si la empresa
     * desactivó su versión, el global no reaparece.
     */
    private static final String VISIBLES = """
            WITH vis AS (
                SELECT DISTINCT ON (c.codigo) c.*
                  FROM concepto_nomina c
                 WHERE (c.empresa_id IS NULL OR c.empresa_id = :empresaId)
                   AND c.vigente_desde <= :fecha
                   AND (c.vigente_hasta IS NULL OR c.vigente_hasta >= :fecha)
                 ORDER BY c.codigo, c.empresa_id NULLS LAST, c.vigente_desde DESC
            )
            SELECT v.*, t.nombre AS tipo_nombre, g.nombre AS grupo_nombre, cc.nombre AS centro_costo_nombre,
                   COUNT(*) OVER() AS total_rows
              FROM vis v
              LEFT JOIN nomina_concepto_tipo t ON t.id = v.tipo_id
              LEFT JOIN nomina_catalogo g ON g.id = v.grupo_id
              LEFT JOIN centros_costos cc ON cc.id = v.centro_costo_id
             WHERE 1 = 1
            """;

    /**
     * Buscador avanzado: texto (número, nombre o tipo) y filtros opcionales.
     *
     * @param pageable  página, filas, texto y filtros
     * @param empresaId empresa
     * @return página de conceptos ordenados por número
     */
    public PageImpl<ConceptoDto> buscar(PageableDto<BuscarConceptoParams> pageable, Integer empresaId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? pageable.getRows().intValue() : 10;
        String search = pageable.getSearch() != null ? pageable.getSearch().trim().toLowerCase() : "";
        BuscarConceptoParams f = pageable.getParams() != null ? pageable.getParams() : new BuscarConceptoParams();

        StringBuilder sql = new StringBuilder(VISIBLES);
        MapSqlParameterSource params = new MapSqlParameterSource("empresaId", empresaId)
                .addValue("fecha", f.getFecha() != null ? f.getFecha() : LocalDate.now());

        if (!Boolean.TRUE.equals(f.getIncluirInactivos())) {
            sql.append(" AND v.activo = TRUE ");
        }
        if (!search.isEmpty()) {
            sql.append("""
                     AND (CAST(v.numero AS TEXT) LIKE :search OR LOWER(v.nombre) LIKE :search
                          OR LOWER(COALESCE(t.nombre, '')) LIKE :search)
                    """);
            params.addValue("search", "%" + search + "%");
        }
        if (f.getClase() != null) {
            sql.append(" AND v.clase = :clase ");
            params.addValue("clase", f.getClase());
        }
        if (f.getTipoId() != null) {
            sql.append(" AND v.tipo_id = :tipoId ");
            params.addValue("tipoId", f.getTipoId());
        }
        if (f.getGrupoId() != null) {
            sql.append(" AND v.grupo_id = :grupoId ");
            params.addValue("grupoId", f.getGrupoId());
        }
        if (f.getEntrada() != null) {
            sql.append(" AND v.entrada = :entrada ");
            params.addValue("entrada", f.getEntrada());
        }
        if (f.getFrecuencia() != null) {
            sql.append(" AND v.frecuencia = :frecuencia ");
            params.addValue("frecuencia", f.getFrecuencia());
        }
        sql.append(" ORDER BY v.numero OFFSET :offset LIMIT :limit ");
        params.addValue("offset", page * size).addValue("limit", size);

        List<ConceptoDto> lista = jdbc.query(sql.toString(), params, this::mapear);
        long total = lista.isEmpty() ? 0 : lista.get(0).getTotalRows();
        return new PageImpl<>(lista, PageRequest.of(page, size), total);
    }

    /**
     * Un concepto por id, si la empresa lo puede ver (global o propio).
     *
     * @return el concepto con nombres de tipo y grupo, o vacío
     */
    public Optional<ConceptoDto> obtener(Long id, Integer empresaId) {
        return jdbc.query("""
                SELECT c.*, t.nombre AS tipo_nombre, g.nombre AS grupo_nombre, cc.nombre AS centro_costo_nombre,
                       0 AS total_rows
                  FROM concepto_nomina c
                  LEFT JOIN nomina_concepto_tipo t ON t.id = c.tipo_id
                  LEFT JOIN nomina_catalogo g ON g.id = c.grupo_id
                  LEFT JOIN centros_costos cc ON cc.id = c.centro_costo_id
                 WHERE c.id = :id AND (c.empresa_id IS NULL OR c.empresa_id = :empresaId)
                """, new MapSqlParameterSource("id", id).addValue("empresaId", empresaId), this::mapear)
                .stream().findFirst();
    }

    /**
     * Catálogo de tipos de concepto activos.
     *
     * @return tipos ordenados por id (agrupados por clase en la semilla)
     */
    public List<ConceptoTipoDto> tipos() {
        return jdbc.query("SELECT id, nombre, clase FROM nomina_concepto_tipo WHERE activo = TRUE ORDER BY id",
                (rs, i) -> {
                    ConceptoTipoDto t = new ConceptoTipoDto();
                    t.setId(rs.getInt("id"));
                    t.setNombre(rs.getString("nombre"));
                    t.setClase(rs.getString("clase"));
                    return t;
                });
    }

    /**
     * Clase del tipo de concepto.
     *
     * @return la clase, o vacío si el tipo no existe o está inactivo
     */
    public Optional<String> claseDeTipo(Integer tipoId) {
        return jdbc.queryForList("SELECT clase FROM nomina_concepto_tipo WHERE id = :id AND activo = TRUE",
                new MapSqlParameterSource("id", tipoId), String.class).stream().findFirst();
    }

    /**
     * ¿El número ya lo usa otro concepto visible para la empresa?
     *
     * <p>Las versiones de vigencia y la personalización del mismo concepto
     * comparten número, por eso se excluye la propia clave interna.
     *
     * @param codigoPropio clave interna del concepto que se guarda (null al crear)
     */
    public boolean numeroOcupado(Integer empresaId, Integer numero, String codigoPropio) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM concepto_nomina
                 WHERE numero = :numero
                   AND (empresa_id IS NULL OR empresa_id = :empresaId)
                   AND (CAST(:codigo AS VARCHAR) IS NULL OR codigo <> :codigo)
                """, new MapSqlParameterSource("numero", numero).addValue("empresaId", empresaId)
                .addValue("codigo", codigoPropio), Long.class);
        return n != null && n > 0;
    }

    /**
     * Siguiente número libre para un concepto nuevo de la empresa.
     *
     * @return el mayor número visible + 1
     */
    public int siguienteNumero(Integer empresaId) {
        Integer max = jdbc.queryForObject("""
                SELECT COALESCE(MAX(numero), 0) FROM concepto_nomina
                 WHERE empresa_id IS NULL OR empresa_id = :empresaId
                """, new MapSqlParameterSource("empresaId", empresaId), Integer.class);
        return (max != null ? max : 0) + 1;
    }

    /**
     * ¿Existe ya la clave interna en la empresa o como global?
     */
    public boolean existeCodigo(Integer empresaId, String codigo) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM concepto_nomina
                 WHERE codigo = :codigo AND (empresa_id IS NULL OR empresa_id = :empresaId)
                """, new MapSqlParameterSource("codigo", codigo).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /**
     * Ids de las versiones de una clave interna del mismo dueño (empresa o global),
     * para validar que las vigencias no se solapen.
     *
     * @param empresaId dueño; null = globales
     */
    public List<Long> versiones(Integer empresaId, String codigo) {
        return jdbc.queryForList("""
                SELECT id FROM concepto_nomina
                 WHERE codigo = :codigo AND empresa_id IS NOT DISTINCT FROM CAST(:empresaId AS INT)
                 ORDER BY vigente_desde DESC
                """, new MapSqlParameterSource("codigo", codigo).addValue("empresaId", empresaId), Long.class);
    }

    /**
     * Versión de la empresa con la misma clave interna y la misma vigencia de un
     * global (su personalización), si ya existe.
     */
    public Optional<Long> personalizacion(Integer empresaId, String codigo, LocalDate vigenteDesde) {
        return jdbc.queryForList("""
                SELECT id FROM concepto_nomina
                 WHERE empresa_id = :empresaId AND codigo = :codigo AND vigente_desde = :desde
                """, new MapSqlParameterSource("empresaId", empresaId).addValue("codigo", codigo)
                .addValue("desde", vigenteDesde), Long.class).stream().findFirst();
    }

    /**
     * Todos los conceptos activos que ve la empresa hoy, uno por concepto (la
     * versión de la empresa antes que la de ley). Para "afectado por" y para
     * revisar los ciclos entre fórmulas.
     */
    public List<ConceptoDto> visibles(Integer empresaId) {
        return jdbc.query(VISIBLES + " AND v.activo = TRUE ORDER BY v.numero",
                new MapSqlParameterSource("empresaId", empresaId).addValue("fecha", LocalDate.now()), this::mapear);
    }

    /** ¿El centro de costo existe y es de la empresa? */
    public boolean centroCostoDeEmpresa(Integer empresaId, Long centroCostoId) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM centros_costos WHERE id = :id AND empresa_id = :empresaId",
                new MapSqlParameterSource("id", centroCostoId).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /** SMMLV y auxilio de transporte de la empresa (para los ejemplos del editor de fórmulas). */
    public Map<String, BigDecimal> parametrosNomina(Integer empresaId) {
        Map<String, BigDecimal> m = new HashMap<>();
        jdbc.query("SELECT smmlv, auxilio_transporte FROM nomina_config WHERE empresa_id = :empresaId",
                new MapSqlParameterSource("empresaId", empresaId), rs -> {
                    m.put("SMMLV", rs.getBigDecimal("smmlv"));
                    m.put("AUX_TRANSPORTE", rs.getBigDecimal("auxilio_transporte"));
                });
        return m;
    }

    private ConceptoDto mapear(ResultSet rs, int fila) throws SQLException {
        ConceptoDto d = new ConceptoDto();
        d.setId(rs.getLong("id"));
        d.setEmpresaId(rs.getObject("empresa_id", Integer.class));
        d.setNumero(rs.getObject("numero", Integer.class));
        d.setCodigo(rs.getString("codigo"));
        d.setNombre(rs.getString("nombre"));
        d.setClase(rs.getString("clase"));
        d.setTipoId(rs.getObject("tipo_id", Integer.class));
        d.setTipoNombre(rs.getString("tipo_nombre"));
        d.setEntrada(rs.getString("entrada"));
        d.setRedondeoPrecision(rs.getString("redondeo_precision"));
        d.setRedondeoModo(rs.getString("redondeo_modo"));
        d.setFrecuencia(rs.getString("frecuencia"));
        d.setGrupoId(rs.getObject("grupo_id", Long.class));
        d.setGrupoNombre(rs.getString("grupo_nombre"));
        d.setAplicaArt383(rs.getBoolean("aplica_art383"));
        d.setDeLey(rs.getBoolean("de_ley"));
        d.setDescripcion(rs.getString("descripcion"));
        d.setFormula(rs.getString("formula"));
        d.setHaceBasePrestacional(rs.getBoolean("hace_base_prestacional"));
        d.setCentroCostoId(rs.getObject("centro_costo_id", Long.class));
        d.setCentroCostoNombre(rs.getString("centro_costo_nombre"));
        d.setConstituyeIbc(rs.getBoolean("constituye_ibc"));
        d.setBase(rs.getString("base"));
        d.setPorcentaje(rs.getBigDecimal("porcentaje"));
        d.setValorFijo(rs.getBigDecimal("valor_fijo"));
        d.setVigenteDesde(rs.getObject("vigente_desde", LocalDate.class));
        d.setVigenteHasta(rs.getObject("vigente_hasta", LocalDate.class));
        d.setCodigoDian(rs.getString("codigo_dian"));
        d.setOrden(rs.getInt("orden"));
        d.setActivo(rs.getBoolean("activo"));
        d.setEsGlobal(d.getEmpresaId() == null);
        d.setPersonalizado(d.getEmpresaId() != null && Boolean.TRUE.equals(d.getDeLey()));
        d.setTotalRows(rs.getLong("total_rows"));
        return d;
    }
}
