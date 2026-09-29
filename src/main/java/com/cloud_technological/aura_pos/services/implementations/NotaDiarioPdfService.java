package com.cloud_technological.aura_pos.services.implementations;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioLineaDto;
import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Comprobante de diario (nota contable) en PDF: el que el contador archiva con
 * sus soportes y firma. Lleva el bloque de firmas elaboró / revisó / aprobó,
 * que es lo primero que pide un revisor fiscal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotaDiarioPdfService {

    private static final DeviceRgb AZUL = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb OSCURO = new DeviceRgb(30, 41, 59);
    private static final DeviceRgb FONDO = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb GRIS = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb BORDE = new DeviceRgb(226, 232, 240);
    private static final DeviceRgb ROJO = new DeviceRgb(185, 28, 28);

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    private final IEmpresaService empresaService;

    public byte[] generar(NotaDiarioDto nota, Integer empresaId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(28, 28, 28, 28);

            encabezado(doc, nota, empresaId);
            datos(doc, nota);
            lineas(doc, nota.getLineas());
            firmas(doc, nota);

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de la nota {}: {}", nota.getId(), e.getMessage(), e);
            throw new RuntimeException("Error generando el PDF de la nota contable: " + e.getMessage(), e);
        }
    }

    private void encabezado(Document doc, NotaDiarioDto nota, Integer empresaId) {
        EmpresaDto empresa = empresaService.obtenerEmpresaActual(empresaId, null, null);

        Table t = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();

        Cell emp = new Cell().setBorder(Border.NO_BORDER).setPadding(0);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()));
                logo.setMaxWidth(80).setMaxHeight(45);
                emp.add(logo);
            } catch (Exception e) {
                log.warn("No se pudo cargar el logo: {}", e.getMessage());
            }
        }
        emp.add(new Paragraph(texto(empresa.getRazonSocial())).setBold().setFontSize(11)
                .setFontColor(OSCURO).setMarginTop(4));
        emp.add(new Paragraph("NIT " + texto(empresa.getNit())
                + (empresa.getDv() != null && !empresa.getDv().isBlank() ? "-" + empresa.getDv() : ""))
                .setFontSize(8).setFontColor(GRIS).setMarginTop(-2));
        if (empresa.getDireccion() != null) {
            emp.add(new Paragraph(empresa.getDireccion()
                    + (empresa.getMunicipio() != null ? " · " + empresa.getMunicipio() : ""))
                    .setFontSize(8).setFontColor(GRIS).setMarginTop(-2));
        }
        t.addCell(emp);

        Cell num = new Cell().setBorder(new SolidBorder(AZUL, 1.2f)).setPadding(10)
                .setTextAlignment(TextAlignment.CENTER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        num.add(new Paragraph("COMPROBANTE DE DIARIO").setBold().setFontSize(10).setFontColor(AZUL));
        num.add(new Paragraph(nota.getNumeroComprobante() != null ? nota.getNumeroComprobante() : "BORRADOR")
                .setBold().setFontSize(16).setFontColor(OSCURO).setMarginTop(2));
        if ("ANULADO".equals(nota.getEstado())) {
            num.add(new Paragraph("ANULADO").setBold().setFontSize(11).setFontColor(ROJO));
        } else if ("BORRADOR".equals(nota.getEstado())) {
            num.add(new Paragraph("Sin contabilizar").setFontSize(8).setFontColor(GRIS));
        }
        t.addCell(num);
        doc.add(t);
        doc.add(new Paragraph("").setMarginBottom(6));
    }

    private void datos(Document doc, NotaDiarioDto nota) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{18, 32, 18, 32})).useAllAvailableWidth()
                .setBackgroundColor(FONDO);
        par(t, "Fecha", fechaLarga(nota.getFecha()));
        par(t, "Período", nota.getPeriodo() != null ? mesAnio(nota.getPeriodo()) : "—");
        par(t, "Clasificación", clasificacion(nota.getClasificacion()));
        par(t, "Estado", estado(nota.getEstado()));
        if (nota.getReversaDeNumero() != null) par(t, "Reversa a", nota.getReversaDeNumero());
        if (nota.getRevertidoPorNumero() != null) par(t, "Reversada por", nota.getRevertidoPorNumero());
        if (nota.getReversaDeNumero() != null ^ nota.getRevertidoPorNumero() != null) {
            par(t, "", "");
        }
        t.addCell(etiqueta("Concepto"));
        t.addCell(new Cell(1, 3).add(new Paragraph(texto(nota.getDescripcion())).setFontSize(9))
                .setBorder(Border.NO_BORDER).setPadding(5));
        if ("ANULADO".equals(nota.getEstado()) && nota.getMotivoAnulacion() != null) {
            t.addCell(etiqueta("Motivo anulación"));
            t.addCell(new Cell(1, 3).add(new Paragraph(nota.getMotivoAnulacion()).setFontSize(9)
                    .setFontColor(ROJO)).setBorder(Border.NO_BORDER).setPadding(5));
        }
        doc.add(t);
        doc.add(new Paragraph("").setMarginBottom(8));
    }

    private void lineas(Document doc, List<NotaDiarioLineaDto> lineas) {
        Table t = new Table(UnitValue.createPercentArray(new float[]{11, 22, 19, 12, 16, 10, 10}))
                .useAllAvailableWidth();
        for (String h : new String[]{"Código", "Cuenta", "Tercero", "C. costo", "Descripción", "Débito", "Crédito"}) {
            t.addHeaderCell(new Cell().add(new Paragraph(h).setBold().setFontSize(8)
                    .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(OSCURO).setBorder(Border.NO_BORDER).setPadding(5)
                    .setTextAlignment(h.equals("Débito") || h.equals("Crédito")
                            ? TextAlignment.RIGHT : TextAlignment.LEFT));
        }
        BigDecimal totalD = BigDecimal.ZERO;
        BigDecimal totalC = BigDecimal.ZERO;
        for (NotaDiarioLineaDto l : lineas) {
            String tercero = l.getTerceroId() == null ? "" : texto(l.getTerceroDocumento())
                    + (l.getTerceroNombre() != null ? " " + l.getTerceroNombre() : "");
            t.addCell(celda(texto(l.getCuentaCodigo()), TextAlignment.LEFT));
            t.addCell(celda(texto(l.getCuentaNombre()), TextAlignment.LEFT));
            t.addCell(celda(tercero, TextAlignment.LEFT));
            t.addCell(celda(texto(l.getCentroCostoNombre()), TextAlignment.LEFT));
            t.addCell(celda(texto(l.getDescripcion()), TextAlignment.LEFT));
            t.addCell(celda(dinero(l.getDebito()), TextAlignment.RIGHT));
            t.addCell(celda(dinero(l.getCredito()), TextAlignment.RIGHT));
            totalD = totalD.add(l.getDebito() != null ? l.getDebito() : BigDecimal.ZERO);
            totalC = totalC.add(l.getCredito() != null ? l.getCredito() : BigDecimal.ZERO);
        }
        t.addCell(new Cell(1, 5).add(new Paragraph("Totales").setBold().setFontSize(8))
                .setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(OSCURO, 1)).setPadding(5));
        t.addCell(total(totalD));
        t.addCell(total(totalC));
        doc.add(t);
    }

    private void firmas(Document doc, NotaDiarioDto nota) {
        doc.add(new Paragraph("").setMarginTop(40));
        Table t = new Table(UnitValue.createPercentArray(new float[]{33, 34, 33})).useAllAvailableWidth();
        t.addCell(firma("Elaboró", nota.getElaboradoPor()));
        t.addCell(firma("Revisó", null));
        t.addCell(firma("Aprobó / contabilizó", nota.getContabilizadoPor()));
        doc.add(t);
        doc.add(new Paragraph("Generado el " + fechaLarga(LocalDate.now().toString()))
                .setFontSize(7).setFontColor(GRIS).setMarginTop(18).setTextAlignment(TextAlignment.RIGHT));
    }

    // ── Piezas ───────────────────────────────────────────────────────────

    private static Cell firma(String rol, String nombre) {
        Cell c = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(10).setPaddingRight(10);
        c.add(new Paragraph(nombre != null ? nombre : " ").setFontSize(9).setTextAlignment(TextAlignment.CENTER)
                .setBorderBottom(new SolidBorder(OSCURO, 0.8f)).setPaddingBottom(2));
        c.add(new Paragraph(rol).setFontSize(8).setFontColor(GRIS).setTextAlignment(TextAlignment.CENTER));
        return c;
    }

    private static void par(Table t, String etiqueta, String valor) {
        t.addCell(etiqueta(etiqueta));
        t.addCell(new Cell().add(new Paragraph(valor).setFontSize(9)).setBorder(Border.NO_BORDER).setPadding(5));
    }

    private static Cell etiqueta(String s) {
        return new Cell().add(new Paragraph(s).setBold().setFontSize(8).setFontColor(GRIS))
                .setBorder(Border.NO_BORDER).setPadding(5);
    }

    private static Cell celda(String s, TextAlignment align) {
        return new Cell().add(new Paragraph(s).setFontSize(8)).setTextAlignment(align)
                .setBorder(Border.NO_BORDER).setBorderBottom(new SolidBorder(BORDE, 0.5f)).setPadding(4);
    }

    private static Cell total(BigDecimal v) {
        return new Cell().add(new Paragraph(dinero(v)).setBold().setFontSize(8))
                .setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER)
                .setBorderTop(new SolidBorder(OSCURO, 1)).setPadding(5);
    }

    private static String dinero(BigDecimal v) {
        if (v == null || v.signum() == 0) return "";
        // 1.234.567,89 — formato colombiano sin depender del locale de la JVM.
        String s = String.format(java.util.Locale.US, "%,.2f", v).replace(",", "#").replace(".", ",").replace("#", ".");
        return "$ " + (s.endsWith(",00") ? s.substring(0, s.length() - 3) : s);
    }

    private static String fechaLarga(String iso) {
        if (iso == null || iso.length() < 10) return "—";
        LocalDate d = LocalDate.parse(iso.substring(0, 10));
        return d.getDayOfMonth() + " de " + MESES[d.getMonthValue() - 1] + " de " + d.getYear();
    }

    /** '2026-09' → 'Septiembre 2026' */
    private static String mesAnio(String yyyyMm) {
        try {
            int mes = Integer.parseInt(yyyyMm.substring(5, 7));
            String m = MESES[mes - 1];
            return Character.toUpperCase(m.charAt(0)) + m.substring(1) + " " + yyyyMm.substring(0, 4);
        } catch (Exception e) {
            return yyyyMm;
        }
    }

    private static String estado(String e) {
        if (e == null) return "—";
        return switch (e) {
            case "BORRADOR" -> "Borrador";
            case "CONTABILIZADO" -> "Contabilizada";
            case "ANULADO" -> "Anulada";
            default -> e;
        };
    }

    private static String clasificacion(String c) {
        if (c == null) return "Sin clasificar";
        return switch (c) {
            case "AJUSTE" -> "Ajuste";
            case "RECLASIFICACION" -> "Reclasificación";
            case "PROVISION" -> "Provisión";
            case "CAUSACION" -> "Causación";
            case "DEPRECIACION" -> "Depreciación";
            case "CORRECCION" -> "Corrección";
            default -> "Otro";
        };
    }

    private static String texto(String s) {
        return s != null ? s : "";
    }
}
