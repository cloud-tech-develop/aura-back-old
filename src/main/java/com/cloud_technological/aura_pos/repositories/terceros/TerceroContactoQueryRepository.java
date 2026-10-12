package com.cloud_technological.aura_pos.repositories.terceros;

import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cloud_technological.aura_pos.dto.terceros.TerceroCorreoDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTelefonoDto;

import lombok.RequiredArgsConstructor;

/** Teléfonos y correos de un tercero (tablas tercero_telefono y tercero_correo, V211). */
@Repository
@RequiredArgsConstructor
public class TerceroContactoQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Teléfonos del tercero en el orden en que se guardaron (propios y del contacto). */
    public List<TerceroTelefonoDto> telefonos(Long terceroId) {
        return jdbc.query("""
                SELECT de_contacto, tipo, numero, extension FROM tercero_telefono
                 WHERE tercero_id = :id ORDER BY de_contacto, orden, id
                """, new MapSqlParameterSource("id", terceroId), (rs, i) -> {
            TerceroTelefonoDto t = new TerceroTelefonoDto();
            t.setDeContacto(rs.getBoolean("de_contacto"));
            t.setTipo(rs.getString("tipo"));
            t.setNumero(rs.getString("numero"));
            t.setExtension(rs.getString("extension"));
            return t;
        });
    }

    /** Correos del tercero en el orden en que se guardaron (propios y del contacto). */
    public List<TerceroCorreoDto> correos(Long terceroId) {
        return jdbc.query("""
                SELECT de_contacto, correo FROM tercero_correo
                 WHERE tercero_id = :id ORDER BY de_contacto, orden, id
                """, new MapSqlParameterSource("id", terceroId), (rs, i) -> {
            TerceroCorreoDto c = new TerceroCorreoDto();
            c.setDeContacto(rs.getBoolean("de_contacto"));
            c.setCorreo(rs.getString("correo"));
            return c;
        });
    }

    /**
     * Pone el número como primer teléfono propio: actualiza el primero que haya o lo
     * crea. Lo usan las altas y ediciones que solo mandan el teléfono suelto.
     */
    public void fijarTelefonoPrincipal(Long terceroId, String numero) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", terceroId).addValue("numero", numero.trim());
        int n = jdbc.update("""
                UPDATE tercero_telefono SET numero = :numero
                 WHERE id = (SELECT id FROM tercero_telefono
                              WHERE tercero_id = :id AND de_contacto = FALSE ORDER BY orden, id LIMIT 1)
                """, p);
        if (n == 0)
            jdbc.update("""
                    INSERT INTO tercero_telefono (tercero_id, de_contacto, tipo, numero, orden)
                    VALUES (:id, FALSE, 'CELULAR', :numero, 0)
                    """, p);
    }

    /** Pone el correo como primer correo propio: actualiza el primero que haya o lo crea. */
    public void fijarCorreoPrincipal(Long terceroId, String correo) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", terceroId)
                .addValue("correo", correo.trim().toLowerCase());
        int n = jdbc.update("""
                UPDATE tercero_correo SET correo = :correo
                 WHERE id = (SELECT id FROM tercero_correo
                              WHERE tercero_id = :id AND de_contacto = FALSE ORDER BY orden, id LIMIT 1)
                """, p);
        if (n == 0)
            jdbc.update("""
                    INSERT INTO tercero_correo (tercero_id, de_contacto, correo, orden)
                    VALUES (:id, FALSE, :correo, 0)
                    """, p);
    }

    /** Reemplaza todos los teléfonos del tercero por la lista dada. */
    public void reemplazarTelefonos(Long terceroId, List<TerceroTelefonoDto> telefonos) {
        jdbc.update("DELETE FROM tercero_telefono WHERE tercero_id = :id", new MapSqlParameterSource("id", terceroId));
        int orden = 0;
        for (TerceroTelefonoDto t : telefonos) {
            jdbc.update("""
                    INSERT INTO tercero_telefono (tercero_id, de_contacto, tipo, numero, extension, orden)
                    VALUES (:id, :contacto, :tipo, :numero, :extension, :orden)
                    """, new MapSqlParameterSource("id", terceroId)
                    .addValue("contacto", Boolean.TRUE.equals(t.getDeContacto()))
                    .addValue("tipo", t.getTipo() != null ? t.getTipo() : "CELULAR")
                    .addValue("numero", t.getNumero().trim())
                    .addValue("extension", t.getExtension() != null && !t.getExtension().isBlank()
                            ? t.getExtension().trim() : null)
                    .addValue("orden", orden++));
        }
    }

    /** Reemplaza todos los correos del tercero por la lista dada. */
    public void reemplazarCorreos(Long terceroId, List<TerceroCorreoDto> correos) {
        jdbc.update("DELETE FROM tercero_correo WHERE tercero_id = :id", new MapSqlParameterSource("id", terceroId));
        int orden = 0;
        for (TerceroCorreoDto c : correos) {
            jdbc.update("""
                    INSERT INTO tercero_correo (tercero_id, de_contacto, correo, orden)
                    VALUES (:id, :contacto, :correo, :orden)
                    """, new MapSqlParameterSource("id", terceroId)
                    .addValue("contacto", Boolean.TRUE.equals(c.getDeContacto()))
                    .addValue("correo", c.getCorreo().trim().toLowerCase())
                    .addValue("orden", orden++));
        }
    }
}
