package com.cloud_technological.aura_pos.dto.nomina.catalogos;

import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Crear o editar un elemento de un catálogo de nómina. */
@Getter
@Setter
public class CreateNominaCatalogoDto {

    @NotBlank(message = "El código es obligatorio")
    @Size(max = 20, message = "El código admite hasta 20 caracteres")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
    private String nombre;

    @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
    private String descripcion;

    private Integer orden = 0;

    private Boolean activo = true;

    /** Atributos propios del tipo de catálogo (ver TipoCatalogoNomina). */
    private Map<String, Object> atributos;
}
