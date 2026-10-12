package com.cloud_technological.aura_pos.services.nomina.catalogos;

import java.math.BigDecimal;
import java.util.List;

/**
 * Atributo propio de un tipo de catálogo de nómina (p. ej. la prioridad de un tipo
 * de embargo o quién paga un motivo de ausentismo).
 *
 * <p>Describe el dato para que el servicio lo valide y para que el front pinte el
 * campo sin conocer el catálogo: el formulario se arma con esta definición.
 *
 * @param nombre      llave en el JSON de atributos (camelCase)
 * @param etiqueta    texto del campo en pantalla
 * @param tipo        tipo de dato
 * @param obligatorio si el valor es requerido al guardar
 * @param opciones    valores permitidos cuando el tipo es {@link Tipo#OPCION}
 * @param minimo      valor mínimo para números (null = sin mínimo)
 * @param maximo      valor máximo para números (null = sin máximo)
 * @param ayuda       texto corto de ayuda bajo el campo
 */
public record AtributoCatalogo(
        String nombre,
        String etiqueta,
        Tipo tipo,
        boolean obligatorio,
        List<String> opciones,
        BigDecimal minimo,
        BigDecimal maximo,
        String ayuda) {

    /** Tipos de dato que admite un atributo. */
    public enum Tipo {
        TEXTO, ENTERO, DECIMAL, BOOLEANO, OPCION
    }

    /** Atributo sí/no. */
    public static AtributoCatalogo booleano(String nombre, String etiqueta, String ayuda) {
        return new AtributoCatalogo(nombre, etiqueta, Tipo.BOOLEANO, true, List.of(), null, null, ayuda);
    }

    /** Atributo numérico entero con rango. */
    public static AtributoCatalogo entero(String nombre, String etiqueta, int minimo, int maximo, String ayuda) {
        return new AtributoCatalogo(nombre, etiqueta, Tipo.ENTERO, true, List.of(),
                BigDecimal.valueOf(minimo), BigDecimal.valueOf(maximo), ayuda);
    }

    /** Atributo decimal con rango. */
    public static AtributoCatalogo decimal(String nombre, String etiqueta, String minimo, String maximo, String ayuda) {
        return new AtributoCatalogo(nombre, etiqueta, Tipo.DECIMAL, true, List.of(),
                new BigDecimal(minimo), new BigDecimal(maximo), ayuda);
    }

    /** Atributo con valores fijos. */
    public static AtributoCatalogo opcion(String nombre, String etiqueta, boolean obligatorio, List<String> opciones,
            String ayuda) {
        return new AtributoCatalogo(nombre, etiqueta, Tipo.OPCION, obligatorio, opciones, null, null, ayuda);
    }
}
