package com.cloud_technological.aura_pos.dto.nomina.catalogos;

import java.util.List;

import com.cloud_technological.aura_pos.services.nomina.catalogos.AtributoCatalogo;

/**
 * Definición de un tipo de catálogo para el front: con ella arma la pantalla
 * (título, descripción y campos propios).
 *
 * @param ruta        segmento de la URL y del submódulo
 * @param nombre      título de la pantalla
 * @param descripcion subtítulo
 * @param atributos   campos propios del catálogo
 */
public record TipoCatalogoNominaDto(String ruta, String nombre, String descripcion, List<AtributoCatalogo> atributos) {
}
