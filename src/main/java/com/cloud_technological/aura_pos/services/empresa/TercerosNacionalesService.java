package com.cloud_technological.aura_pos.services.empresa;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Terceros nacionales que toda empresa nueva recibe: bancos y entidades de
 * seguridad social (EPS, AFP, cesantías, ARL, cajas) con su código PILA y sus roles.
 * Es el mismo contenido de {@code docs/sql/seed_terceros_bancos_seguridad_social.sql},
 * pero para UNA empresa y desde el arranque (paso TERCEROS de {@link PasoArranque}).
 *
 * <p>Idempotente: si la empresa ya tiene el tercero (mismo NIT, con o sin DV) no lo
 * duplica; si le falta el código de seguridad social, se lo completa. Un NIT = un
 * tercero (UNIQUE empresa_id + numero_documento); los NIT que son EPS y caja a la
 * vez quedan con los dos roles y el código de la EPS (ver la limitación del SQL).
 */
@Service
public class TercerosNacionalesService {

    private static final String RECURSO = "seed/terceros_nacionales.csv";

    private final NamedParameterJdbcTemplate jdbc;
    private volatile List<Entidad> catalogo;

    public TercerosNacionalesService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Una fila del catálogo. {@code tipoEss} null = no va al catálogo nacional (bancos, FNA). */
    record Entidad(String nit, String dv, String nombre, String codigo, List<String> roles, String tipoEss) {
    }

    /** Un tercero por NIT, con todos los roles de sus filas y el código de la EPS si es también caja. */
    record PorNit(String nit, String dv, String nombre, String codigo, Set<String> roles, List<Entidad> filas) {
    }

    /** Siembra en la empresa lo que falte. Devuelve cuántos terceros creó. */
    public int sembrar(Integer empresaId) {
        // 1. Catálogo nacional (global): EPS/AFP/CCF/ARL.
        for (Entidad e : catalogo()) {
            if (e.tipoEss() == null) continue;
            jdbc.update("""
                INSERT INTO entidad_seguridad_social (tipo, codigo_oficial, nit, nombre)
                VALUES (:tipo, :codigo, :nit, :nombre)
                ON CONFLICT (tipo, codigo_oficial) DO NOTHING
                """, new MapSqlParameterSource().addValue("tipo", e.tipoEss()).addValue("codigo", e.codigo())
                    .addValue("nit", e.nit() + "-" + e.dv()).addValue("nombre", e.nombre()));
        }

        int creados = 0;
        for (PorNit t : porNit()) {
            MapSqlParameterSource p = new MapSqlParameterSource()
                    .addValue("empresaId", empresaId).addValue("nit", t.nit()).addValue("nitDv", t.nit() + t.dv())
                    .addValue("dv", t.dv()).addValue("nombre", t.nombre()).addValue("codigo", t.codigo())
                    .addValue("banco", t.roles().contains("BANCO"));

            // 2. ¿Ya existe (incluso borrado: el UNIQUE tampoco mira deleted_at)?
            List<Map<String, Object>> existente = jdbc.queryForList("""
                SELECT id, deleted_at IS NOT NULL AS borrado
                FROM tercero
                WHERE empresa_id = :empresaId
                  AND (numero_documento = :nit
                       OR regexp_replace(numero_documento, '\\D', '', 'g') IN (:nit, :nitDv))
                ORDER BY (deleted_at IS NOT NULL), id
                LIMIT 1
                """, p);
            Long id;
            if (existente.isEmpty()) {
                id = jdbc.queryForObject("""
                    INSERT INTO tercero (
                        empresa_id, tipo_documento, numero_documento, dv, razon_social,
                        tipo_persona, pais, codigo_seguridad_social,
                        es_cliente, es_proveedor, es_empleado, es_banco,
                        gran_contribuyente, auto_retenedor,
                        es_autoretenedor_ica, es_autoretenedor_fuente, declarante,
                        activo, created_at)
                    VALUES (:empresaId, 'NIT', :nit, :dv, :nombre,
                        'JURIDICA', 'Colombia', :codigo,
                        FALSE, FALSE, FALSE, :banco,
                        FALSE, FALSE,
                        FALSE, FALSE, FALSE,
                        TRUE, NOW())
                    RETURNING id
                    """, p, Long.class);
                creados++;
            } else {
                if (Boolean.TRUE.equals(existente.get(0).get("borrado"))) continue;
                id = ((Number) existente.get(0).get("id")).longValue();
                // Lo tenía sin código: se le completa (no se pisa uno que ya tenga).
                jdbc.update("""
                    UPDATE tercero
                       SET codigo_seguridad_social = :codigo, updated_at = NOW()
                     WHERE id = :id AND codigo_seguridad_social IS NULL AND CAST(:codigo AS VARCHAR) IS NOT NULL
                    """, p.addValue("id", id));
                if (t.roles().contains("BANCO")) {
                    jdbc.update("""
                        UPDATE tercero SET es_banco = TRUE, updated_at = NOW()
                        WHERE id = :id AND COALESCE(es_banco, FALSE) = FALSE
                        """, p);
                }
            }

            // 3. Roles (todos los del NIT: Compensar queda EPS + CCF).
            for (String rol : t.roles()) {
                jdbc.update("""
                    INSERT INTO tercero_rol (tercero_id, rol) VALUES (:id, :rol)
                    ON CONFLICT DO NOTHING
                    """, new MapSqlParameterSource().addValue("id", id).addValue("rol", rol));
            }

            // 4. Enlace al catálogo nacional por código y rol.
            jdbc.update("""
                UPDATE tercero t
                   SET entidad_seguridad_social_id = ess.id
                  FROM entidad_seguridad_social ess
                 WHERE t.id = :id
                   AND t.entidad_seguridad_social_id IS NULL
                   AND t.codigo_seguridad_social = ess.codigo_oficial
                   AND EXISTS (SELECT 1 FROM tercero_rol tr WHERE tr.tercero_id = t.id AND tr.rol = ess.tipo)
                """, new MapSqlParameterSource("id", id));
        }
        return creados;
    }

    /** Agrupa por NIT: un tercero con todos los roles; código y nombre de la EPS si también es caja. */
    List<PorNit> porNit() {
        Map<String, List<Entidad>> grupos = new LinkedHashMap<>();
        for (Entidad e : catalogo()) grupos.computeIfAbsent(e.nit(), k -> new ArrayList<>()).add(e);
        List<PorNit> out = new ArrayList<>();
        for (List<Entidad> filas : grupos.values()) {
            Entidad base = filas.stream().filter(f -> "EPS".equals(f.tipoEss())).findFirst().orElse(filas.get(0));
            Set<String> roles = new LinkedHashSet<>();
            filas.forEach(f -> roles.addAll(f.roles()));
            String nombre = filas.size() > 1
                    ? base.nombre().replaceAll("\\s+(EPS(-S)?|CAJA DE COMPENSACION)$", "")
                    : base.nombre();
            out.add(new PorNit(base.nit(), base.dv(), nombre, base.codigo(), roles, filas));
        }
        return out;
    }

    List<Entidad> catalogo() {
        List<Entidad> c = catalogo;
        if (c == null) {
            c = leer();
            catalogo = c;
        }
        return c;
    }

    private static List<Entidad> leer() {
        List<Entidad> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ClassPathResource(RECURSO).getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = r.readLine()) != null) {
                if (linea.isBlank() || linea.startsWith("#")) continue;
                String[] c = linea.split(";", -1);
                out.add(new Entidad(c[0].trim(), c[1].trim(), c[2].trim(), vacio(c[3]),
                        List.of(c[4].trim().split("\\|")), vacio(c[5])));
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RECURSO, e);
        }
        return out;
    }

    private static String vacio(String v) {
        String t = v == null ? "" : v.trim();
        return t.isEmpty() ? null : t;
    }
}
