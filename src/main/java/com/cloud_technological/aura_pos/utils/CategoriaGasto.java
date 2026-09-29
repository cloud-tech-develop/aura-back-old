package com.cloud_technological.aura_pos.utils;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de categorías de gasto.
 *
 * <p>Lo que se guarda en {@code gasto.categoria} es el código
 * ({@code OTRO_NO_DEDUCIBLE}); la etiqueta vivía solo en una constante del
 * frontend. Mientras el reporte se veía en pantalla eso pasaba, pero un Excel
 * que le llega al contador con "OTRO_NO_DEDUCIBLE" en la columna de categoría
 * es un reporte que hay que traducir a mano antes de usarlo.
 *
 * <p>{@link #deduciblePorDefecto} es una <b>sugerencia</b> para el formulario,
 * no una regla: lo que manda es el {@code deducible} que se guardó en cada
 * gasto. Un arriendo sin soporte válido no es deducible por más que su
 * categoría lo sugiera, y el reporte tiene que mostrar lo que realmente se
 * declaró.
 */
public enum CategoriaGasto {

    ARRIENDO("Arriendo / Alquiler", true),
    SERVICIOS_PUBLICOS("Servicios públicos", true),
    SALARIO_DIARIO("Salario diario", false),
    NOMINA("Nómina / Salarios", false),
    PAPELERIA("Papelería / Insumos", true),
    TRANSPORTE("Transporte / Fletes", true),
    PUBLICIDAD("Publicidad", true),
    MULTA("Multas / Sanciones", false),
    PERSONAL("Gastos personales", false),
    MANTENIMIENTO("Mantenimiento", true),
    OTRO_DEDUCIBLE("Otro deducible", true),
    OTRO_NO_DEDUCIBLE("Otro no deducible", false);

    private final String etiqueta;
    private final boolean deduciblePorDefecto;

    CategoriaGasto(String etiqueta, boolean deduciblePorDefecto) {
        this.etiqueta = etiqueta;
        this.deduciblePorDefecto = deduciblePorDefecto;
    }

    public String codigo() {
        return name();
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean deduciblePorDefecto() {
        return deduciblePorDefecto;
    }

    /**
     * Vacío si el código no está en el catálogo. En la tabla hay gastos de
     * antes de que existiera la lista, con categorías escritas a mano.
     */
    public static Optional<CategoriaGasto> de(String codigo) {
        if (codigo == null) return Optional.empty();
        return Arrays.stream(values())
                .filter(c -> c.name().equalsIgnoreCase(codigo.trim()))
                .findFirst();
    }

    /**
     * La etiqueta legible, o el texto tal cual si no está en el catálogo.
     *
     * <p>Se devuelve el original antes que un "Sin categoría": esconder una
     * categoría vieja hace que su plata desaparezca del reporte agrupado.
     */
    public static String etiquetaDe(String codigo) {
        return de(codigo).map(CategoriaGasto::etiqueta).orElse(codigo);
    }
}
