package com.cloud_technological.aura_pos.utils;

import java.math.BigDecimal;
import java.util.List;

import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.element.Image;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.IBlockElement;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

/**
 * Gráficas para los PDF, dibujadas con las primitivas de iText.
 *
 * <h2>Por qué no se usa una librería de gráficas</h2>
 *
 * <p>La opción obvia sería JFreeChart, pero renderiza sobre AWT/Java2D y este
 * proyecto ya se quemó con eso: {@code autoSizeColumn} reventaba en producción
 * con {@code UnsatisfiedLinkError: libfreetype.so.6} porque el servidor Linux no
 * tiene las librerías nativas de fuentes (ver {@link ExcelAnchoColumnas}). Una
 * gráfica renderizada por AWT terminaría igual, y falla en tiempo de ejecución
 * —cuando alguien descarga el reporte— no al arrancar.
 *
 * <p>Dibujar con las primitivas de iText usa solo vectores y las fuentes
 * que iText ya carga. Sale más liviano, escala sin pixelarse y no agrega una
 * dependencia que pueda romper el despliegue.
 *
 * <h2>Barras de tabla, no de lienzo</h2>
 *
 * <p>Las barras horizontales se arman con celdas de tabla coloreadas en vez de
 * con canvas: iText posiciona las tablas solo, respeta el salto de página y
 * mantiene la barra alineada con su etiqueta. Con canvas habría que calcular
 * coordenadas absolutas y todo se desalinea al cambiar una sección de sitio.
 */
public final class GraficaPdf {

    private static final DeviceRgb GRIS_PISTA = new DeviceRgb(238, 242, 247);
    private static final DeviceRgb GRIS_TEXTO = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb BLANCO = new DeviceRgb(255, 255, 255);

    private GraficaPdf() {
    }

    /** Una barra del gráfico: su etiqueta, su valor y el texto que se muestra. */
    public record Barra(String etiqueta, BigDecimal valor, String valorTexto, Color color) {
        public Barra(String etiqueta, BigDecimal valor, String valorTexto) {
            this(etiqueta, valor, valorTexto, null);
        }
    }

    /**
     * Barras horizontales proporcionales al mayor valor.
     *
     * <p>Horizontales y no verticales a propósito: las etiquetas de este reporte
     * son nombres largos ("Servicios públicos", "Reverso de edición de compra")
     * y en vertical habría que rotarlas o recortarlas.
     *
     * @param color el color de las barras que no traigan el suyo
     */
    public static IBlockElement barras(List<Barra> datos, Color color) {
        Div contenedor = new Div();
        if (datos == null || datos.isEmpty()) {
            return contenedor;
        }

        BigDecimal mayor = datos.stream()
                .map(b -> b.valor() != null ? b.valor().abs() : BigDecimal.ZERO)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        // Todo en cero: pintar barras de ancho cero se ve como un error de
        // render. Es más honesto decirlo.
        if (mayor.signum() == 0) {
            return contenedor.add(new Paragraph("Sin valores para graficar.")
                    .setFontSize(8).setItalic().setFontColor(GRIS_TEXTO));
        }

        Table t = new Table(UnitValue.createPercentArray(new float[]{32, 48, 20}))
                .useAllAvailableWidth();

        for (Barra b : datos) {
            t.addCell(new Cell()
                    .add(new Paragraph(b.etiqueta()).setFontSize(8))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(3));

            t.addCell(new Cell()
                    .add(barra(b, mayor, color))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(3));

            t.addCell(new Cell()
                    .add(new Paragraph(b.valorTexto()).setFontSize(8).setBold())
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(3)
                    .setTextAlignment(TextAlignment.RIGHT));
        }
        return contenedor.add(t);
    }

    /**
     * Una barra: la pista gris de fondo y encima el relleno proporcional.
     *
     * <p>La pista se dibuja siempre para que se vea contra qué se compara — sin
     * ella, dos barras cortas parecen iguales aunque una sea el doble.
     */
    private static Table barra(Barra b, BigDecimal mayor, Color colorPorDefecto) {
        BigDecimal valor = b.valor() != null ? b.valor().abs() : BigDecimal.ZERO;
        // Mínimo 1% para que un valor pequeño pero no nulo siga siendo visible.
        float pct = Math.max(1f, valor.multiply(BigDecimal.valueOf(100))
                .divide(mayor, 2, java.math.RoundingMode.HALF_UP).floatValue());
        float resto = Math.max(0.01f, 100f - pct);

        Table barra = new Table(UnitValue.createPercentArray(new float[]{pct, resto}))
                .useAllAvailableWidth();
        barra.addCell(new Cell()
                .setHeight(11)
                .setBackgroundColor(b.color() != null ? b.color() : colorPorDefecto)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        barra.addCell(new Cell()
                .setHeight(11)
                .setBackgroundColor(GRIS_PISTA)
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        return barra;
    }

    /**
     * Una barra apilada: la composición de un total en sus partes.
     *
     * <p>Sirve para "de este saldo, tanto está corriente y tanto vencido": una
     * sola línea dice la proporción sin que el lector tenga que dividir.
     */
    public static IBlockElement barraApilada(List<Barra> partes) {
        Div contenedor = new Div();
        if (partes == null || partes.isEmpty()) {
            return contenedor;
        }

        BigDecimal total = partes.stream()
                .map(p -> p.valor() != null ? p.valor().abs() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return contenedor;
        }

        // Las partes en cero se omiten: una franja de ancho cero no se ve pero
        // sí desordena la leyenda.
        List<Barra> visibles = partes.stream()
                .filter(p -> p.valor() != null && p.valor().abs().signum() > 0)
                .toList();

        float[] anchos = new float[visibles.size()];
        for (int i = 0; i < visibles.size(); i++) {
            anchos[i] = visibles.get(i).valor().abs()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, java.math.RoundingMode.HALF_UP).floatValue();
        }

        Table barra = new Table(UnitValue.createPercentArray(anchos)).useAllAvailableWidth();
        for (Barra p : visibles) {
            barra.addCell(new Cell()
                    .setHeight(14)
                    .setBackgroundColor(p.color() != null ? p.color() : GRIS_PISTA)
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER));
        }
        contenedor.add(barra);

        // La leyenda va debajo con el valor: el color solo no dice cuánto.
        Table leyenda = new Table(UnitValue.createPercentArray(anchoIgual(visibles.size())))
                .useAllAvailableWidth().setMarginTop(2);
        for (Barra p : visibles) {
            leyenda.addCell(new Cell()
                    .add(new Paragraph("■ " + p.etiqueta())
                            .setFontSize(7)
                            .setFontColor(p.color() != null ? p.color() : GRIS_TEXTO))
                    .add(new Paragraph(p.valorTexto()).setFontSize(7.5f).setBold())
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(1));
        }
        return contenedor.add(leyenda);
    }

    // ══ Gráficas de lienzo ═══════════════════════════════════════════════
    //
    // Torta y barras verticales sí necesitan dibujo real: no se pueden armar
    // con celdas de tabla. Se pintan sobre un PdfFormXObject y se insertan como
    // Image, que es lo que las mantiene dentro del flujo del documento — iText
    // las posiciona y respeta el salto de página igual que a un párrafo.
    //
    // El lienzo dibuja SOLO geometría. Todo el texto va en la maquetación, con
    // la fuente que el documento ya carga: escribir sobre el canvas obligaría a
    // crear un PdfFont aparte y a pelear con la codificación de los acentos.

    /** La paleta por defecto, cuando las partes no traen color propio. */
    private static final Color[] PALETA = {
        new DeviceRgb(37, 99, 235),   // azul de marca
        new DeviceRgb(13, 148, 136),  // verde azulado
        new DeviceRgb(217, 119, 6),   // ámbar
        new DeviceRgb(139, 92, 246),  // morado
        new DeviceRgb(220, 38, 38),   // rojo
        new DeviceRgb(100, 116, 139), // gris
        new DeviceRgb(5, 150, 105),   // verde
        new DeviceRgb(202, 138, 4),   // mostaza
    };

    /**
     * Torta de anillo con su leyenda al lado.
     *
     * <p>Anillo y no torta maciza: el hueco del centro deja el ojo comparando
     * los arcos, que es lo que se quiere leer, en vez de las áreas — que el ojo
     * humano estima mal.
     *
     * <p>Las partes que no llegan al 2% se agrupan en "Otros": una tajada de
     * medio grado no se ve y sí ensucia la leyenda.
     */
    public static IBlockElement torta(PdfDocument pdf, List<Barra> partes, float diametro) {
        Div contenedor = new Div();
        List<Barra> datos = agruparMenores(partes);
        if (datos.isEmpty()) {
            return contenedor.add(new Paragraph("Sin valores para graficar.")
                    .setFontSize(8).setItalic().setFontColor(GRIS_TEXTO));
        }

        BigDecimal total = datos.stream()
                .map(p -> p.valor().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() == 0) {
            return contenedor;
        }

        float r = diametro / 2f;
        PdfFormXObject lienzo = new PdfFormXObject(new Rectangle(diametro, diametro));
        PdfCanvas canvas = new PdfCanvas(lienzo, pdf);

        // Se arranca arriba y se avanza en sentido horario, como se lee un reloj.
        float anguloActual = 90f;
        for (int i = 0; i < datos.size(); i++) {
            Barra p = datos.get(i);
            float extension = p.valor().abs().multiply(BigDecimal.valueOf(360))
                    .divide(total, 4, java.math.RoundingMode.HALF_UP).floatValue();
            if (extension <= 0) {
                continue;
            }
            canvas.saveState()
                    .setFillColor(color(p, i))
                    .moveTo(r, r)
                    // arc() recibe el rectángulo que circunscribe la elipse.
                    .arc(0, 0, diametro, diametro, anguloActual, -extension)
                    .lineTo(r, r)
                    .fill()
                    .restoreState();
            anguloActual -= extension;
        }

        // El hueco: un círculo del color del papel encima del centro.
        canvas.saveState()
                .setFillColor(BLANCO)
                .circle(r, r, r * 0.55f)
                .fill()
                .restoreState();

        Table fila = new Table(UnitValue.createPercentArray(new float[]{35, 65}))
                .useAllAvailableWidth();
        fila.addCell(new Cell()
                .add(new Image(lienzo))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(2));
        fila.addCell(new Cell()
                .add(leyenda(datos, total))
                .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                .setPadding(2)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE));
        return contenedor.add(fila);
    }

    /** La leyenda con el valor y el porcentaje: el color solo no dice cuánto. */
    private static Table leyenda(List<Barra> datos, BigDecimal total) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{58, 25, 17}))
                .useAllAvailableWidth();
        for (int i = 0; i < datos.size(); i++) {
            Barra p = datos.get(i);
            BigDecimal pct = p.valor().abs().multiply(BigDecimal.valueOf(100))
                    .divide(total, 1, java.math.RoundingMode.HALF_UP);
            t.addCell(new Cell()
                    .add(new Paragraph("■ " + p.etiqueta()).setFontSize(7.5f)
                            .setFontColor(color(p, i)))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(1));
            t.addCell(new Cell()
                    .add(new Paragraph(p.valorTexto()).setFontSize(7.5f))
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(1)
                    .setTextAlignment(TextAlignment.RIGHT));
            t.addCell(new Cell()
                    .add(new Paragraph(pct + "%").setFontSize(7.5f).setBold())
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER).setPadding(1)
                    .setTextAlignment(TextAlignment.RIGHT));
        }
        return t;
    }

    /**
     * Barras verticales sobre una línea base.
     *
     * <p>Verticales cuando las etiquetas son cortas (meses, tipos de
     * movimiento) y lo que se compara es la altura. Con nombres largos hay que
     * usar {@link #barras}, que las pone horizontales.
     *
     * <p>Admite valores negativos: la línea base queda en el cero real y lo
     * negativo cuelga hacia abajo. Un gráfico de entradas y salidas en el que
     * las salidas apunten hacia arriba miente sobre lo que pasó.
     */
    public static IBlockElement barrasVerticales(PdfDocument pdf, List<Barra> datos,
            float alto, Color color) {
        Div contenedor = new Div();
        if (datos == null || datos.isEmpty()) {
            return contenedor;
        }

        BigDecimal maxPos = datos.stream().map(b -> nz(b.valor()))
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO).max(BigDecimal.ZERO);
        BigDecimal minNeg = datos.stream().map(b -> nz(b.valor()))
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO).min(BigDecimal.ZERO);
        BigDecimal rango = maxPos.subtract(minNeg);
        if (rango.signum() == 0) {
            return contenedor.add(new Paragraph("Sin valores para graficar.")
                    .setFontSize(8).setItalic().setFontColor(GRIS_TEXTO));
        }

        float ancho = 26f * datos.size() + 10f;
        PdfFormXObject lienzo = new PdfFormXObject(new Rectangle(ancho, alto));
        PdfCanvas canvas = new PdfCanvas(lienzo, pdf);

        // El cero queda donde le corresponde según la proporción de negativos.
        float cero = minNeg.abs().multiply(BigDecimal.valueOf(alto))
                .divide(rango, 4, java.math.RoundingMode.HALF_UP).floatValue();

        canvas.saveState().setStrokeColor(GRIS_PISTA).setLineWidth(0.7f)
                .moveTo(0, cero).lineTo(ancho, cero).stroke().restoreState();

        for (int i = 0; i < datos.size(); i++) {
            BigDecimal v = nz(datos.get(i).valor());
            float h = v.abs().multiply(BigDecimal.valueOf(alto))
                    .divide(rango, 4, java.math.RoundingMode.HALF_UP).floatValue();
            float x = 5f + i * 26f;
            float y = v.signum() >= 0 ? cero : cero - h;
            canvas.saveState()
                    .setFillColor(color(datos.get(i), i, color))
                    .rectangle(x, y, 18f, Math.max(h, 0.8f))
                    .fill()
                    .restoreState();
        }

        contenedor.add(new Image(lienzo));

        // Las etiquetas y los valores van debajo, en la maquetación.
        Table pie = new Table(UnitValue.createPercentArray(anchoIgual(datos.size())))
                .useAllAvailableWidth().setMarginTop(1);
        for (Barra b : datos) {
            pie.addCell(new Cell()
                    .add(new Paragraph(b.etiqueta()).setFontSize(6.5f).setFontColor(GRIS_TEXTO))
                    .add(new Paragraph(b.valorTexto()).setFontSize(7).setBold())
                    .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
                    .setPadding(1)
                    .setTextAlignment(TextAlignment.CENTER));
        }
        return contenedor.add(pie);
    }

    /**
     * Junta en "Otros" las tajadas por debajo del 2%.
     *
     * <p>Una tajada de medio grado no se distingue y sí ocupa una línea de la
     * leyenda; con ocho de esas la gráfica deja de comunicar.
     */
    private static List<Barra> agruparMenores(List<Barra> partes) {
        if (partes == null || partes.isEmpty()) {
            return List.of();
        }
        List<Barra> vivos = partes.stream()
                .filter(p -> p.valor() != null && p.valor().abs().signum() > 0)
                .toList();
        if (vivos.isEmpty()) {
            return List.of();
        }
        BigDecimal total = vivos.stream().map(p -> p.valor().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Barra> out = new java.util.ArrayList<>();
        BigDecimal otros = BigDecimal.ZERO;
        for (Barra p : vivos) {
            BigDecimal pct = p.valor().abs().multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, java.math.RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.valueOf(2)) < 0 && vivos.size() > 5) {
                otros = otros.add(p.valor().abs());
            } else {
                out.add(p);
            }
        }
        if (otros.signum() > 0) {
            out.add(new Barra("Otros", otros, formatoCorto(otros)));
        }
        return out;
    }

    private static String formatoCorto(BigDecimal v) {
        return String.format("%,.0f", v);
    }

    private static Color color(Barra b, int i) {
        return b.color() != null ? b.color() : PALETA[i % PALETA.length];
    }

    private static Color color(Barra b, int i, Color porDefecto) {
        if (b.color() != null) return b.color();
        return porDefecto != null ? porDefecto : PALETA[i % PALETA.length];
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static float[] anchoIgual(int columnas) {
        float[] a = new float[columnas];
        java.util.Arrays.fill(a, 1f);
        return a;
    }

    /**
     * Comparación de dos valores lado a lado (este período vs. el anterior).
     *
     * <p>Dos barras y la variación entre ellas. Un porcentaje suelto no dice si
     * el negocio creció desde mucho o desde poco.
     */
    public static IBlockElement comparativo(String etiquetaA, BigDecimal valorA, String textoA,
            String etiquetaB, BigDecimal valorB, String textoB, Color color) {
        return barras(List.of(
                new Barra(etiquetaA, valorA, textoA, color),
                new Barra(etiquetaB, valorB, textoB, GRIS_PISTA)), color);
    }
}
