package com.cloud_technological.aura_pos.repositories.nomina_cargos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoAdjuntoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoConceptoDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoNovedadDto;
import com.cloud_technological.aura_pos.dto.nomina.cargos.CargoPlantillaDtos.CargoTopeHorasDto;

import lombok.RequiredArgsConstructor;

/**
 * Consultas de la plantilla de nómina del cargo: conceptos, novedades permitidas,
 * topes de horas y adjuntos.
 *
 * <p>Los conceptos se guardan por número; el nombre, la clase y el tipo salen de
 * la versión que ve la empresa (la propia antes que la de ley, la más reciente).
 */
@Repository
@RequiredArgsConstructor
public class NominaCargoPlantillaQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Versión del concepto que ve la empresa, por número. Se usa como LATERAL con {@code x.concepto_numero}. */
    private static final String CONCEPTO = """
            LEFT JOIN LATERAL (
                SELECT cn.nombre, cn.clase, cn.entrada, cn.redondeo_precision, cn.redondeo_modo,
                       t.nombre AS tipo_nombre
                  FROM concepto_nomina cn
                  LEFT JOIN nomina_concepto_tipo t ON t.id = cn.tipo_id
                 WHERE cn.numero = x.concepto_numero
                   AND (cn.empresa_id = :empresaId OR cn.empresa_id IS NULL)
                 ORDER BY cn.empresa_id NULLS LAST, cn.vigente_desde DESC
                 LIMIT 1
            ) cn ON TRUE
            """;

    /**
     * Conceptos fijos del cargo, ordenados por número.
     */
    public List<CargoConceptoDto> conceptos(Long cargoId, Integer empresaId) {
        return jdbc.query("SELECT x.*, cn.* FROM nomina_cargo_concepto x " + CONCEPTO + """
                 WHERE x.cargo_id = :cargoId
                 ORDER BY x.concepto_numero
                """, params(cargoId, empresaId), (rs, i) -> {
            CargoConceptoDto d = new CargoConceptoDto();
            d.setId(rs.getLong("id"));
            d.setConceptoNumero(rs.getInt("concepto_numero"));
            d.setConceptoNombre(rs.getString("nombre"));
            d.setClase(rs.getString("clase"));
            d.setTipoNombre(rs.getString("tipo_nombre"));
            d.setEntrada(rs.getString("entrada"));
            d.setRedondeoPrecision(rs.getString("redondeo_precision"));
            d.setRedondeoModo(rs.getString("redondeo_modo"));
            d.setCantidad(rs.getBigDecimal("cantidad"));
            d.setValor(rs.getBigDecimal("valor"));
            d.setPorcentaje(rs.getBigDecimal("porcentaje"));
            d.setObservacion(rs.getString("observacion"));
            return d;
        });
    }

    /**
     * Novedades permitidas del cargo, ordenadas por número.
     */
    public List<CargoNovedadDto> novedades(Long cargoId, Integer empresaId) {
        return jdbc.query("SELECT x.*, cn.* FROM nomina_cargo_novedad x " + CONCEPTO + """
                 WHERE x.cargo_id = :cargoId
                 ORDER BY x.concepto_numero
                """, params(cargoId, empresaId), (rs, i) -> {
            CargoNovedadDto d = new CargoNovedadDto();
            d.setId(rs.getLong("id"));
            d.setConceptoNumero(rs.getInt("concepto_numero"));
            d.setConceptoNombre(rs.getString("nombre"));
            d.setClase(rs.getString("clase"));
            d.setTipoNombre(rs.getString("tipo_nombre"));
            d.setEntrada(rs.getString("entrada"));
            d.setTopeCantidad(rs.getBigDecimal("tope_cantidad"));
            d.setTopeValor(rs.getBigDecimal("tope_valor"));
            d.setRequiereAutorizacion(rs.getBoolean("requiere_autorizacion"));
            return d;
        });
    }

    /**
     * Los siete tipos de hora (extras 3–6, recargos 7–9) con el tope del cargo, si lo tiene.
     */
    public List<CargoTopeHorasDto> topes(Long cargoId) {
        return jdbc.query("""
                SELECT t.id, t.nombre, h.tope_dia, h.tope_semana, h.tope_mes
                  FROM nomina_concepto_tipo t
                  LEFT JOIN nomina_cargo_tope_horas h ON h.tipo_concepto_id = t.id AND h.cargo_id = :cargoId
                 WHERE t.id BETWEEN 3 AND 9
                 ORDER BY t.id
                """, new MapSqlParameterSource("cargoId", cargoId), (rs, i) -> {
            CargoTopeHorasDto d = new CargoTopeHorasDto();
            d.setTipoConceptoId(rs.getInt("id"));
            d.setTipoNombre(rs.getString("nombre"));
            d.setEsHoraExtra(rs.getInt("id") <= 6);
            d.setTopeDia(rs.getBigDecimal("tope_dia"));
            d.setTopeSemana(rs.getBigDecimal("tope_semana"));
            d.setTopeMes(rs.getBigDecimal("tope_mes"));
            return d;
        });
    }

    /**
     * Adjuntos vigentes del cargo, del más reciente al más viejo.
     */
    public List<CargoAdjuntoDto> adjuntos(Long cargoId) {
        return jdbc.query("""
                SELECT id, nombre_archivo, archivo_url, content_type, tamano_bytes, created_at
                  FROM nomina_cargo_adjunto
                 WHERE cargo_id = :cargoId AND deleted_at IS NULL
                 ORDER BY created_at DESC
                """, new MapSqlParameterSource("cargoId", cargoId), (rs, i) -> {
            CargoAdjuntoDto d = new CargoAdjuntoDto();
            d.setId(rs.getLong("id"));
            d.setNombreArchivo(rs.getString("nombre_archivo"));
            d.setArchivoUrl(rs.getString("archivo_url"));
            d.setContentType(rs.getString("content_type"));
            d.setTamanoBytes(rs.getObject("tamano_bytes", Long.class));
            d.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
            return d;
        });
    }

    /** ¿La empresa ve un concepto activo con ese número (propio o de ley)? */
    public boolean conceptoVisible(Integer empresaId, Integer numero) {
        Long n = jdbc.queryForObject("""
                SELECT COUNT(*) FROM concepto_nomina
                 WHERE numero = :numero AND activo = TRUE
                   AND (empresa_id = :empresaId OR empresa_id IS NULL)
                """, new MapSqlParameterSource("numero", numero).addValue("empresaId", empresaId), Long.class);
        return n != null && n > 0;
    }

    /** Números que ya están en los conceptos fijos del cargo. */
    public List<Integer> numerosConceptos(Long cargoId) {
        return jdbc.queryForList("SELECT concepto_numero FROM nomina_cargo_concepto WHERE cargo_id = :cargoId",
                new MapSqlParameterSource("cargoId", cargoId), Integer.class);
    }

    /** Números que ya están en las novedades permitidas del cargo. */
    public List<Integer> numerosNovedades(Long cargoId) {
        return jdbc.queryForList("SELECT concepto_numero FROM nomina_cargo_novedad WHERE cargo_id = :cargoId",
                new MapSqlParameterSource("cargoId", cargoId), Integer.class);
    }

    /** Tipos de hora con tope ya configurado en el cargo. */
    public List<Integer> tiposConTope(Long cargoId) {
        return jdbc.queryForList("SELECT tipo_concepto_id FROM nomina_cargo_tope_horas WHERE cargo_id = :cargoId",
                new MapSqlParameterSource("cargoId", cargoId), Integer.class);
    }

    /** Ids de los topes guardados del cargo (para reemplazarlos). */
    public List<Long> idsTopes(Long cargoId) {
        return jdbc.queryForList("SELECT id FROM nomina_cargo_tope_horas WHERE cargo_id = :cargoId",
                new MapSqlParameterSource("cargoId", cargoId), Long.class);
    }

    /** Código del nivel del cargo en el catálogo NIVEL_CARGO (DIR, PRO…), si tiene. */
    public Optional<String> codigoNivel(Long cargoId) {
        return jdbc.queryForList("""
                SELECT n.codigo FROM nomina_cargo c
                  JOIN nomina_catalogo n ON n.id = c.nivel_id
                 WHERE c.id = :cargoId
                """, new MapSqlParameterSource("cargoId", cargoId), String.class).stream().findFirst();
    }

    /** Cuántos adjuntos vigentes tiene el cargo. */
    public int contarAdjuntos(Long cargoId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nomina_cargo_adjunto WHERE cargo_id = :cargoId AND deleted_at IS NULL",
                new MapSqlParameterSource("cargoId", cargoId), Integer.class);
        return n != null ? n : 0;
    }

    private static MapSqlParameterSource params(Long cargoId, Integer empresaId) {
        return new MapSqlParameterSource("cargoId", cargoId).addValue("empresaId", empresaId);
    }
}
