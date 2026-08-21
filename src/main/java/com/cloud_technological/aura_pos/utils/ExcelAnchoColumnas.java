package com.cloud_technological.aura_pos.utils;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Ajusta el ancho de las columnas de una hoja midiendo el <b>texto</b>, sin
 * pasar por AWT.
 *
 * <p>Reemplaza a {@code Sheet.autoSizeColumn}, que para medir el ancho renderiza
 * la fuente y por eso arrastra {@code libfontmanager} → {@code libfreetype}. En
 * un servidor Linux sin esas librerías nativas el reporte revienta con
 * {@code UnsatisfiedLinkError: libfreetype.so.6: cannot open shared object file}
 * — pasó en producción con el reporte de facturación electrónica, y falla en
 * tiempo de ejecución, no al arrancar, así que no se nota hasta que alguien
 * descarga el reporte.
 *
 * <p>Medir por número de caracteres da un ancho aproximado en vez de exacto,
 * pero para una hoja de datos es suficiente y no depende de que el sistema
 * operativo tenga fuentes instaladas. De paso es mucho más rápido: autoSizeColumn
 * recorre y renderiza cada celda.
 */
public final class ExcelAnchoColumnas {

    /** POI mide en 1/256 de carácter. */
    private static final int UNIDAD = 256;
    private static final int MIN_CARACTERES = 10;
    /** Tope para que el CUFE/CUDE (96 caracteres) no estire la hoja entera. */
    private static final int MAX_CARACTERES = 46;
    /** Aire para que el contenido no quede pegado al borde. */
    private static final int HOLGURA = 2;

    private ExcelAnchoColumnas() {
    }

    public static void ajustar(Sheet hoja, int columnas) {
        for (int col = 0; col < columnas; col++) {
            hoja.setColumnWidth(col, anchoDe(hoja, col));
        }
    }

    private static int anchoDe(Sheet hoja, int col) {
        int largoMaximo = MIN_CARACTERES;
        for (Row fila : hoja) {
            Cell celda = fila.getCell(col);
            if (celda == null) {
                continue;
            }
            largoMaximo = Math.max(largoMaximo, textoDe(celda).length());
        }
        return Math.min(largoMaximo + HOLGURA, MAX_CARACTERES) * UNIDAD;
    }

    /**
     * El texto que se ve en la celda. Los números se miden por su valor crudo:
     * no se puede aplicar el formato sin un DataFormatter, y para calcular un
     * ancho la diferencia no importa.
     */
    private static String textoDe(Cell celda) {
        return switch (celda.getCellType()) {
            case STRING -> celda.getStringCellValue();
            case NUMERIC -> String.valueOf((long) celda.getNumericCellValue());
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            case FORMULA -> celda.getCellFormula();
            default -> "";
        };
    }
}
