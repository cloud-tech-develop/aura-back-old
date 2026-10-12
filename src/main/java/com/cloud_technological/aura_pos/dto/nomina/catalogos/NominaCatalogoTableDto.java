package com.cloud_technological.aura_pos.dto.nomina.catalogos;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/** Elemento de un catálogo de nómina para tablas, detalle y selects. */
@Getter
@Setter
public class NominaCatalogoTableDto {
    private Long id;
    private String tipo;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer orden;
    private Boolean activo;
    private Map<String, Object> atributos = new LinkedHashMap<>();
    private long totalRows;
}
