package com.cloud_technological.aura_pos.dto.permisos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * Permisos efectivos de un usuario: lo que la empresa tiene activo, recortado por
 * su perfil y ajustado por sus excepciones.
 *
 * <p>{@code permisos}: {@code "modulo.submodulo" → ["VER","CREAR","EDITAR","ANULAR"]}.
 * Un submódulo que no aparece no se ve.
 */
@Data
public class PermisosUsuarioDto {
    private Integer usuarioId;
    /** Tipo de usuario (ADMIN, CAJERO…): sigue mandando en la lógica de negocio. */
    private String rol;
    private Long perfilId;
    private String perfilNombre;
    /** El perfil da todo lo que la empresa tenga activo. */
    private boolean accesoTotal;
    private Map<String, List<String>> permisos = new LinkedHashMap<>();
    /** Acciones especiales que tiene: {@code "modulo.submodulo:CODIGO"} (V192). */
    private List<String> especiales = new java.util.ArrayList<>();
    /** Descuento máximo sin autorización; null = sin límite. */
    private java.math.BigDecimal descuentoMaxPct;
    /** Rebaja máxima del precio sin autorización; null = sin límite. */
    private java.math.BigDecimal rebajaPrecioMaxPct;
    /** Ve y opera en todas las sedes de la empresa. */
    private boolean todasLasSedes = true;
    /** Sedes asignadas (las únicas que puede usar si no tiene todas). */
    private List<Long> sucursales = new java.util.ArrayList<>();
}
