package com.cloud_technological.aura_pos.services.permisos;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * Alcance por sede en el SQL de los listados (fase P9 de docs/PLAN_PERMISOS.md).
 * El filtro de parámetros y de cuerpo cubre lo que llega con {@code sucursalId};
 * esto cubre los listados que no lo reciben y mostraban todas las sedes.
 *
 * <pre>sql.append(alcance.filtro("v.sucursal_id", params));</pre>
 *
 * <p>Solo recorta en modo BLOQUEAR y a usuarios cuyo perfil no tiene "todas las
 * sedes", igual que el resto del control.
 */
@Component
public class AlcanceSede {

    private final PermisoUsuarioService permisos;
    private final SecurityUtils securityUtils;
    private final String modo;

    public AlcanceSede(PermisoUsuarioService permisos, SecurityUtils securityUtils,
            @Value("${app.permisos.modo:OBSERVAR}") String modo) {
        this.permisos = permisos;
        this.securityUtils = securityUtils;
        this.modo = modo == null ? "OBSERVAR" : modo.trim().toUpperCase();
    }

    /** Sedes que puede ver el usuario de la sesión; null = todas. */
    public List<Long> sedes() {
        if (!"BLOQUEAR".equals(modo)) return null;
        Long usuario = securityUtils.getUsuarioId();
        if (usuario == null || PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(securityUtils.getRol())) return null;
        PermisosUsuarioDto p = permisos.efectivos(usuario.intValue());
        if (p.isTodasLasSedes()) return null;
        // Sin sedes asignadas no ve ninguna (-1 no existe).
        return p.getSucursales().isEmpty() ? List.of(-1L) : p.getSucursales();
    }

    /** {@code " AND columna IN (:alcanceSedes)"} o vacío si ve todas. */
    public String filtro(String columna, MapSqlParameterSource params) {
        List<Long> s = sedes();
        if (s == null) return "";
        params.addValue("alcanceSedes", s);
        return " AND " + columna + " IN (:alcanceSedes)";
    }

    private static final Pattern MARCA = Pattern.compile("/\\*SEDE:([\\w.]+)\\*/");

    /**
     * Para SQL escrito de una pieza (text blocks, UNION, reportes): cada marca
     * {@code /*SEDE:v.sucursal_id*}{@code /} puesta justo después de un WHERE se
     * cambia por el filtro de esa columna. Sin recorte, la marca se quita; una
     * marca que nadie aplique es solo un comentario de SQL.
     *
     * <pre>jdbc.query(alcanceSede.aplicar(sql, params), params, mapper);</pre>
     */
    public String aplicar(String sql, MapSqlParameterSource params) {
        if (sql == null || !sql.contains("/*SEDE:")) return sql;
        List<Long> s = sedes();
        Matcher m = MARCA.matcher(sql);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String reemplazo = s == null ? "" : " AND " + m.group(1) + " IN (:alcanceSedes)";
            m.appendReplacement(out, Matcher.quoteReplacement(reemplazo));
        }
        m.appendTail(out);
        if (s != null) params.addValue("alcanceSedes", s);
        return out.toString();
    }
}
