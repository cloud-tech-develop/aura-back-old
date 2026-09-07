package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Estilos y armado de hoja compartidos por los reportes en Excel.
 *
 * <p>Esto vivía duplicado en cada servicio de reporte. Con dos copias todavía
 * pasaba; a la tercera el azul de la marca ya se había desincronizado en una de
 * ellas ({@code ReporteInventarioService} sigue usando un índigo heredado). Un
 * reporte que sale de otro color no parece del mismo sistema.
 *
 * <p>Los estilos se crean una sola vez por libro: POI los cachea por índice y
 * crear uno por celda revienta el límite de 64.000 estilos en reportes grandes.
 */
public final class ExcelEstilos {

    /** Azul primario de la marca (#2563EB). */
    public static final byte[] AZUL       = {(byte) 0x25, (byte) 0x63, (byte) 0xEB};
    public static final byte[] AZUL_SUAVE = {(byte) 0xDB, (byte) 0xEA, (byte) 0xFE};
    public static final byte[] GRIS_ZEBRA = {(byte) 0xF9, (byte) 0xFA, (byte) 0xFB};
    public static final byte[] BLANCO     = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    public static final byte[] GRIS_TEXTO = {(byte) 0x64, (byte) 0x74, (byte) 0x8B};

    public static final DateTimeFormatter F_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Formato de miles para valores.
     *
     * <p>Se usa {@code #,##0} y no un literal con puntos a propósito: Excel
     * dibuja el separador según la configuración regional de quien abre el
     * archivo, así que en Colombia sale 1.000.000 y en un equipo en inglés
     * 1,000,000 — en los dos casos legible. Escribir los puntos a mano
     * produciría texto que no se puede sumar.
     */
    private static final String FORMATO_VALOR = "#,##0";

    /**
     * Las cantidades de inventario sí llevan decimales (la columna es
     * {@code numeric(14,4)}), pero solo cuando los tienen: 12 se ve "12" y no
     * "12,0000".
     */
    private static final String FORMATO_CANTIDAD = "#,##0.####";

    private static final String FORMATO_PORCENTAJE = "#,##0.0\"%\"";

    public final XSSFCellStyle titulo;
    public final XSSFCellStyle nota;
    public final XSSFCellStyle header;
    public final XSSFCellStyle data;
    public final XSSFCellStyle dataAlt;
    public final XSSFCellStyle total;

    public final XSSFCellStyle valor;
    public final XSSFCellStyle valorAlt;
    public final XSSFCellStyle valorTotal;

    public final XSSFCellStyle cantidad;
    public final XSSFCellStyle cantidadAlt;
    public final XSSFCellStyle cantidadTotal;

    public final XSSFCellStyle porcentaje;
    public final XSSFCellStyle porcentajeAlt;

    public ExcelEstilos(XSSFWorkbook wb) {
        this.titulo = titulo(wb);
        this.nota = nota(wb);
        this.header = header(wb);
        this.data = data(wb, BLANCO);
        this.dataAlt = data(wb, GRIS_ZEBRA);
        this.total = total(wb);

        short fValor = wb.createDataFormat().getFormat(FORMATO_VALOR);
        short fCantidad = wb.createDataFormat().getFormat(FORMATO_CANTIDAD);
        short fPorcentaje = wb.createDataFormat().getFormat(FORMATO_PORCENTAJE);

        this.valor = numerico(wb, BLANCO, fValor, false);
        this.valorAlt = numerico(wb, GRIS_ZEBRA, fValor, false);
        this.valorTotal = numerico(wb, AZUL_SUAVE, fValor, true);

        this.cantidad = numerico(wb, BLANCO, fCantidad, false);
        this.cantidadAlt = numerico(wb, GRIS_ZEBRA, fCantidad, false);
        this.cantidadTotal = numerico(wb, AZUL_SUAVE, fCantidad, true);

        this.porcentaje = numerico(wb, BLANCO, fPorcentaje, false);
        this.porcentajeAlt = numerico(wb, GRIS_ZEBRA, fPorcentaje, false);
    }

    /** El estilo de texto de la fila i, alternando el fondo. */
    public XSSFCellStyle fila(int i) {
        return (i % 2 == 0) ? data : dataAlt;
    }

    /** Plata y conteos: separador de miles y alineados a la derecha. */
    public XSSFCellStyle filaValor(int i) {
        return (i % 2 == 0) ? valor : valorAlt;
    }

    /** Cantidades de inventario: admiten decimales. */
    public XSSFCellStyle filaCantidad(int i) {
        return (i % 2 == 0) ? cantidad : cantidadAlt;
    }

    public XSSFCellStyle filaPorcentaje(int i) {
        return (i % 2 == 0) ? porcentaje : porcentajeAlt;
    }

    // ── Armado de la hoja ─────────────────────────────────────

    /**
     * Escribe título, subtítulo y cabecera; devuelve la fila donde empiezan los
     * datos.
     */
    public int encabezado(XSSFSheet ws, String titulo, String subtitulo, String[] cols) {
        int lastCol = cols.length - 1;

        Row r0 = ws.createRow(0);
        r0.setHeightInPoints(30);
        Cell t = r0.createCell(0);
        t.setCellValue(titulo + " — AURA POS");
        t.setCellStyle(this.titulo);
        ws.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        Row r1 = ws.createRow(1);
        r1.setHeightInPoints(16);
        Cell sub = r1.createCell(0);
        sub.setCellValue(subtitulo + "  ·  Generado el " + LocalDate.now().format(F_FECHA));
        sub.setCellStyle(this.nota);
        ws.addMergedRegion(new CellRangeAddress(1, 1, 0, lastCol));

        ws.createRow(2);

        Row rh = ws.createRow(3);
        rh.setHeightInPoints(22);
        for (int i = 0; i < cols.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(this.header);
        }
        ws.createFreezePane(0, 4);
        return 4;
    }

    public void filaVacia(XSSFSheet ws, int fila, int cols, String mensaje) {
        Row r = ws.createRow(fila);
        Cell c = r.createCell(0);
        c.setCellValue(mensaje);
        c.setCellStyle(nota);
        ws.addMergedRegion(new CellRangeAddress(fila, fila, 0, cols - 1));
    }

    public void texto(Row r, int col, String valor, XSSFCellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor != null ? valor : "");
        c.setCellStyle(estilo);
    }

    public void numero(Row r, int col, BigDecimal valor, XSSFCellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor != null ? valor.doubleValue() : 0d);
        c.setCellStyle(estilo);
    }

    /**
     * Ajusta los anchos midiendo el texto, sin pasar por AWT.
     *
     * @see ExcelAnchoColumnas por qué no se usa {@code autoSizeColumn}
     */
    public void ajustarAnchos(XSSFSheet ws, int cols) {
        ExcelAnchoColumnas.ajustar(ws, cols);
    }

    // ── Estilos ───────────────────────────────────────────────

    private static XSSFCellStyle header(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(AZUL, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        f.setFontHeightInPoints((short) 10);
        s.setFont(f);
        borde(s);
        return s;
    }

    /**
     * Estilo de celda numérica: formato de miles y alineación a la derecha.
     *
     * <p>Los números van a la derecha porque así se comparan las magnitudes de
     * un vistazo — las unidades quedan una debajo de otra. Centrados, una cifra
     * de siete dígitos y una de tres parecen del mismo tamaño.
     */
    private static XSSFCellStyle numerico(XSSFWorkbook wb, byte[] fondo,
            short formato, boolean negrita) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(fondo, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setDataFormat(formato);
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) (negrita ? 10 : 9));
        f.setBold(negrita);
        if (negrita) {
            f.setColor(new XSSFColor(AZUL, null));
        }
        s.setFont(f);
        borde(s);
        return s;
    }

    private static XSSFCellStyle data(XSSFWorkbook wb, byte[] fondo) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(fondo, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setFontHeightInPoints((short) 9);
        s.setFont(f);
        borde(s);
        return s;
    }

    private static XSSFCellStyle total(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(AZUL_SUAVE, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 10);
        f.setColor(new XSSFColor(AZUL, null));
        s.setFont(f);
        borde(s);
        return s;
    }

    private static XSSFCellStyle titulo(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        f.setColor(new XSSFColor(AZUL, null));
        s.setFont(f);
        return s;
    }

    private static XSSFCellStyle nota(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setItalic(true);
        f.setFontHeightInPoints((short) 9);
        f.setColor(new XSSFColor(GRIS_TEXTO, null));
        s.setFont(f);
        return s;
    }

    private static void borde(XSSFCellStyle s) {
        s.setBorderLeft(BorderStyle.THIN);
        s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderRight(BorderStyle.THIN);
        s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderTop(BorderStyle.THIN);
        s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderBottom(BorderStyle.THIN);
        s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
