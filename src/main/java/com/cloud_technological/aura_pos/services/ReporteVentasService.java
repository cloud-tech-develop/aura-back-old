package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
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
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ReporteVentasService {

    private final VentaJPARepository ventaRepository;
    private final VentaDetalleJPARepository ventaDetalleRepository;
    private final SecurityUtils securityUtils;
    private final IEmpresaService empresaService;

    // iText color palette (matching CuentaPdfService style)
    private static final DeviceRgb INDIGO       = new DeviceRgb(91,  33,  182);
    private static final DeviceRgb DARK_HEADER  = new DeviceRgb(30,  41,  59);
    private static final DeviceRgb LIGHT_BG     = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb TEXT_GRAY    = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb ZEBRA_GRAY   = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb GREEN_BG     = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb GREEN_TEXT   = new DeviceRgb(21,  128, 61);
    private static final DeviceRgb RED_BG       = new DeviceRgb(254, 226, 226);
    private static final DeviceRgb RED_TEXT     = new DeviceRgb(185, 28,  28);
    private static final DeviceRgb AMBER_BG     = new DeviceRgb(254, 243, 199);
    private static final DeviceRgb AMBER_TEXT   = new DeviceRgb(146, 64,  14);

    public ReporteVentasService(VentaJPARepository ventaRepository,
                                VentaDetalleJPARepository ventaDetalleRepository,
                                SecurityUtils securityUtils,
                                IEmpresaService empresaService) {
        this.ventaRepository = ventaRepository;
        this.ventaDetalleRepository = ventaDetalleRepository;
        this.securityUtils = securityUtils;
        this.empresaService = empresaService;
    }

    private record LineaReporte(VentaEntity venta, VentaDetalleEntity detalle) {}

    // ── EXCEL ─────────────────────────────────────────────────
    public byte[] generarExcel(LocalDate desde, LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<VentaEntity> ventas = filtrar(empresaId, desde, hasta);

        if (ventas == null || ventas.isEmpty()) {
            ventas = List.of();
        }

        List<LineaReporte> lineas = aplanar(ventas);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet ws = wb.createSheet("Ventas");

            // Estilos
            XSSFCellStyle hdrStyle = crearEstiloHeader(wb);
            XSSFCellStyle dataStyle = crearEstiloData(wb, false);
            XSSFCellStyle dataAlt   = crearEstiloData(wb, true);
            XSSFCellStyle totalStyle = crearEstiloTotal(wb);
            XSSFCellStyle titleStyle = crearEstiloTitulo(wb);
            XSSFCellStyle noteStyle = crearEstiloNota(wb);

            // Título
            Row r0 = ws.createRow(0); r0.setHeightInPoints(30);
            Cell t = r0.createCell(0);
            t.setCellValue("REPORTE DE VENTAS — AURA POS");
            t.setCellStyle(titleStyle);
            ws.addMergedRegion(new CellRangeAddress(0, 0, 0, 10));

            Row r1 = ws.createRow(1); r1.setHeightInPoints(16);
            Cell sub = r1.createCell(0);
            sub.setCellValue("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            ws.addMergedRegion(new CellRangeAddress(1, 1, 0, 10));

            ws.createRow(2); // separador

            // Header columnas
            String[] cols = {
                "N° Venta", "Fecha", "Cajero / Caja", "Producto",
                "Cant.", "Precio Unit.", "IVA %", "IVA $", "Subtotal", "Total", "Estado"
            };
            Row rh = ws.createRow(3); rh.setHeightInPoints(22);
            for (int i = 0; i < cols.length; i++) {
                Cell c = rh.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hdrStyle);
            }

            // Datos
            BigDecimal totalSubtotalCompletadas = BigDecimal.ZERO;
            BigDecimal totalIvaCompletadas = BigDecimal.ZERO;
            BigDecimal totalConIvaCompletadas = BigDecimal.ZERO;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (int li = 0; li < lineas.size(); li++) {
                LineaReporte ln = lineas.get(li);
                VentaEntity v = ln.venta();
                VentaDetalleEntity d = ln.detalle();
                Row rd = ws.createRow(4 + li);
                rd.setHeightInPoints(22);
                XSSFCellStyle st = li % 2 == 0 ? dataStyle : dataAlt;

                String estado = v.getEstadoVenta() != null ? v.getEstadoVenta() : "";

                setCell(rd, 0, generarNumeroVenta(v), st);
                setCell(rd, 1, v.getFechaEmision() != null ? v.getFechaEmision().format(fmt) : "", st);
                setCell(rd, 2, obtenerNombreCaja(v), st);

                if (d == null) {
                    setCell(rd, 3, "—", st);
                    setCell(rd, 4, "—", st);
                    setCell(rd, 5, "—", st);
                    setCell(rd, 6, "—", st);
                    setCell(rd, 7, "—", st);
                    setCell(rd, 8, "—", st);
                    setCell(rd, 9, formatCOP(v.getTotalPagar()), st);
                } else {
                    BigDecimal subtotal = subtotalSinIva(d);
                    BigDecimal total = totalConIva(d);

                    setCell(rd, 3, nombreProducto(d), st);
                    setCell(rd, 4, formatCantidad(d.getCantidad()), st);
                    setCell(rd, 5, formatCOP(d.getPrecioUnitario()), st);
                    setCell(rd, 6, formatIvaPorcentaje(d), st);
                    setCell(rd, 7, formatCOP(d.getImpuestoValor()), st);
                    setCell(rd, 8, formatCOP(subtotal), st);
                    setCell(rd, 9, formatCOP(total), st);

                    if (esVenta(estado)) {
                        totalSubtotalCompletadas = totalSubtotalCompletadas.add(subtotal);
                        totalConIvaCompletadas = totalConIvaCompletadas.add(total);
                        if (d.getImpuestoValor() != null) {
                            totalIvaCompletadas = totalIvaCompletadas.add(d.getImpuestoValor());
                        }
                    }
                }

                setCell(rd, 10, estado, st);
            }

            // Fila total
            int tr = 4 + lineas.size();
            Row rt = ws.createRow(tr); rt.setHeightInPoints(24);
            Cell lbl = rt.createCell(0);
            lbl.setCellValue("TOTALES VENDIDAS (incluye crédito)");
            lbl.setCellStyle(totalStyle);
            ws.addMergedRegion(new CellRangeAddress(tr, tr, 0, 6));
            for (int i = 1; i <= 6; i++) {
                rt.createCell(i).setCellStyle(totalStyle);
            }
            setCell(rt, 7, formatCOP(totalIvaCompletadas), totalStyle);
            setCell(rt, 8, formatCOP(totalSubtotalCompletadas), totalStyle);
            setCell(rt, 9, formatCOP(totalConIvaCompletadas), totalStyle);
            rt.createCell(10).setCellStyle(totalStyle);

            // Nota explicativa del asterisco
            int notaRow = tr + 2;
            Row rn = ws.createRow(notaRow);
            Cell noteCell = rn.createCell(0);
            noteCell.setCellValue("(*) IVA incluido en el precio de venta del producto.");
            noteCell.setCellStyle(noteStyle);
            ws.addMergedRegion(new CellRangeAddress(notaRow, notaRow, 0, 10));

            // Anchos columnas
            ws.setColumnWidth(0, 14 * 256);
            ws.setColumnWidth(1, 20 * 256);
            ws.setColumnWidth(2, 22 * 256);
            ws.setColumnWidth(3, 28 * 256);
            ws.setColumnWidth(4, 8 * 256);
            ws.setColumnWidth(5, 14 * 256);
            ws.setColumnWidth(6, 10 * 256);
            ws.setColumnWidth(7, 14 * 256);
            ws.setColumnWidth(8, 14 * 256);
            ws.setColumnWidth(9, 14 * 256);
            ws.setColumnWidth(10, 12 * 256);

            wb.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de ventas: " + e.getMessage(), e);
        }
    }

    // ── PDF ───────────────────────────────────────────────────
    public byte[] generarPdf(LocalDate desde, LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<VentaEntity> ventas = filtrar(empresaId, desde, hasta);
        if (ventas == null) ventas = List.of();

        List<LineaReporte> lineas = aplanar(ventas);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            pdf.setDefaultPageSize(PageSize.A4.rotate());
            Document document = new Document(pdf);

            // ── Header ──────────────────────────────────────
            addHeader(document, empresaId, desde, hasta);

            // ── Resumen ─────────────────────────────────────
            long completadas = ventas.stream().filter(v -> "COMPLETADA".equals(v.getEstadoVenta())).count();
            long credito     = ventas.stream().filter(v -> "PAGO_PARCIAL".equals(v.getEstadoVenta())).count();
            long anuladas    = ventas.stream().filter(v -> "ANULADA".equals(v.getEstadoVenta())).count();

            BigDecimal totalSubtotal = BigDecimal.ZERO;
            BigDecimal totalConIvaAcum = BigDecimal.ZERO;
            BigDecimal totalIva = BigDecimal.ZERO;
            for (LineaReporte ln : lineas) {
                if (!esVenta(ln.venta().getEstadoVenta())) continue;
                VentaDetalleEntity d = ln.detalle();
                if (d == null) continue;
                totalSubtotal = totalSubtotal.add(subtotalSinIva(d));
                totalConIvaAcum = totalConIvaAcum.add(totalConIva(d));
                if (d.getImpuestoValor() != null) totalIva = totalIva.add(d.getImpuestoValor());
            }

            addResumen(document, ventas.size(), completadas, credito, anuladas, totalIva, totalConIvaAcum);

            // ── Tabla de ventas ─────────────────────────────
            document.add(new Paragraph("Detalle de Ventas")
                .setBold().setFontSize(11).setFontColor(DARK_HEADER).setMarginTop(18).setMarginBottom(6));

            Table tabla = new Table(UnitValue.createPercentArray(
                    new float[]{7, 10, 10, 17, 5, 10, 6, 9, 10, 10, 6}))
                .useAllAvailableWidth();

            String[] headers = {
                "N° Venta", "Fecha", "Cajero / Caja", "Producto",
                "Cant.", "Precio Unit.", "IVA %", "IVA $", "Subtotal", "Total", "Estado"
            };
            for (String h : headers) {
                tabla.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h))
                    .setBold().setFontSize(8)
                    .setBackgroundColor(DARK_HEADER)
                    .setFontColor(ColorConstants.WHITE)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(6));
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            int idx = 0;
            for (LineaReporte ln : lineas) {
                VentaEntity v = ln.venta();
                VentaDetalleEntity d = ln.detalle();
                com.itextpdf.kernel.colors.Color rowBg = (idx % 2 == 0)
                    ? ColorConstants.WHITE : ZEBRA_GRAY;

                String estado = v.getEstadoVenta() != null ? v.getEstadoVenta() : "—";

                addTableCell(tabla, generarNumeroVenta(v), rowBg, TextAlignment.LEFT, false);
                addTableCell(tabla, v.getFechaEmision() != null ? v.getFechaEmision().format(fmt) : "—", rowBg, TextAlignment.LEFT, false);
                addTableCell(tabla, obtenerNombreCaja(v), rowBg, TextAlignment.LEFT, false);

                if (d == null) {
                    addTableCell(tabla, "—", rowBg, TextAlignment.LEFT, false);
                    addTableCell(tabla, "—", rowBg, TextAlignment.CENTER, false);
                    addTableCell(tabla, "—", rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, "—", rowBg, TextAlignment.CENTER, false);
                    addTableCell(tabla, "—", rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, "—", rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, formatCOP(v.getTotalPagar()), rowBg, TextAlignment.RIGHT, true);
                } else {
                    addTableCell(tabla, nombreProducto(d), rowBg, TextAlignment.LEFT, false);
                    addTableCell(tabla, formatCantidad(d.getCantidad()), rowBg, TextAlignment.CENTER, false);
                    addTableCell(tabla, formatCOP(d.getPrecioUnitario()), rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, formatIvaPorcentaje(d), rowBg, TextAlignment.CENTER, false);
                    addTableCell(tabla, formatCOP(d.getImpuestoValor()), rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, formatCOP(subtotalSinIva(d)), rowBg, TextAlignment.RIGHT, false);
                    addTableCell(tabla, formatCOP(totalConIva(d)), rowBg, TextAlignment.RIGHT, true);
                }

                addEstadoCell(tabla, estado, rowBg);

                idx++;
            }

            if (lineas.isEmpty()) {
                tabla.addCell(new com.itextpdf.layout.element.Cell(1, 11)
                    .add(new Paragraph("No hay ventas en el período seleccionado").setFontColor(TEXT_GRAY).setItalic())
                    .setBorder(Border.NO_BORDER).setPadding(12).setTextAlignment(TextAlignment.CENTER));
            }

            document.add(tabla);

            // ── Fila total ───────────────────────────────────
            document.add(new Paragraph("\n").setFontSize(4));
            Table totalRow = new Table(UnitValue.createPercentArray(new float[]{40, 20, 20, 20})).useAllAvailableWidth();

            totalRow.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("TOTALES VENDIDAS (incluye crédito)").setBold().setFontSize(10))
                .setBackgroundColor(new DeviceRgb(237, 233, 254))
                .setFontColor(INDIGO)
                .setBorder(Border.NO_BORDER).setPadding(9));

            totalRow.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("Total IVA").setFontSize(8).setFontColor(TEXT_GRAY))
                .add(new Paragraph(formatCOP(totalIva)).setBold().setFontSize(10).setFontColor(INDIGO))
                .setBackgroundColor(new DeviceRgb(237, 233, 254))
                .setBorder(Border.NO_BORDER).setPadding(9).setTextAlignment(TextAlignment.RIGHT));

            totalRow.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("Subtotal").setFontSize(8).setFontColor(TEXT_GRAY))
                .add(new Paragraph(formatCOP(totalSubtotal)).setBold().setFontSize(10).setFontColor(INDIGO))
                .setBackgroundColor(new DeviceRgb(237, 233, 254))
                .setBorder(Border.NO_BORDER).setPadding(9).setTextAlignment(TextAlignment.RIGHT));

            totalRow.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph("Total").setFontSize(8).setFontColor(TEXT_GRAY))
                .add(new Paragraph(formatCOP(totalConIvaAcum)).setBold().setFontSize(10).setFontColor(INDIGO))
                .setBackgroundColor(new DeviceRgb(237, 233, 254))
                .setBorder(Border.NO_BORDER).setPadding(9).setTextAlignment(TextAlignment.RIGHT));

            document.add(totalRow);

            // Nota explicativa
            document.add(new Paragraph("(*) IVA incluido en el precio de venta del producto.")
                .setFontSize(8).setFontColor(TEXT_GRAY).setItalic().setMarginTop(8));

            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF de ventas: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF de ventas: " + e.getMessage(), e);
        }
    }

    // ── PDF helpers ───────────────────────────────────────────

    private void addHeader(Document document, Integer empresaId, LocalDate desde, LocalDate hasta) {
        EmpresaDto empresa = empresaService.obtenerEmpresaActual(empresaId, null, null);

        // Banner superior
        Table banner = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        com.itextpdf.layout.element.Cell titleCell = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("REPORTE DE VENTAS").setBold().setFontSize(16).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12);
        com.itextpdf.layout.element.Cell dateCell = new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(buildRangeLabel(desde, hasta)).setFontSize(9).setFontColor(ColorConstants.LIGHT_GRAY))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12)
            .setTextAlignment(TextAlignment.RIGHT);
        banner.addCell(titleCell);
        banner.addCell(dateCell);
        document.add(banner);

        document.add(new Paragraph("\n").setFontSize(8));

        // Info de empresa
        Table companyTable = new Table(UnitValue.createPercentArray(new float[]{20, 80})).useAllAvailableWidth();
        companyTable.setBorder(Border.NO_BORDER);

        com.itextpdf.layout.element.Cell logoCell = new com.itextpdf.layout.element.Cell()
            .setBorder(Border.NO_BORDER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()));
                logo.setMaxWidth(80);
                logoCell.add(logo);
            } catch (Exception e) {
                log.warn("Logo load fail: {}", e.getMessage());
            }
        }
        companyTable.addCell(logoCell);

        com.itextpdf.layout.element.Cell infoCell = new com.itextpdf.layout.element.Cell()
            .setBorder(Border.NO_BORDER).setPaddingLeft(12)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        infoCell.add(new Paragraph(empresa.getRazonSocial()).setBold().setFontSize(13).setFontColor(DARK_HEADER));
        infoCell.add(new Paragraph("NIT: " + empresa.getNit() + (empresa.getDv() != null ? "-" + empresa.getDv() : ""))
            .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-4));
        String generado = "Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        infoCell.add(new Paragraph(generado).setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-2));
        companyTable.addCell(infoCell);
        document.add(companyTable);

        document.add(new Paragraph("\n").setFontSize(4));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth()
            .setBorder(new SolidBorder(LIGHT_BG, 1)));
        document.add(new Paragraph("\n").setFontSize(8));
    }

    private void addResumen(Document document, int total, long completadas, long credito, long anuladas,
                            BigDecimal totalIva, BigDecimal monto) {
        Table grid = new Table(UnitValue.createPercentArray(new float[]{16, 16, 16, 16, 18, 18})).useAllAvailableWidth();
        grid.setBorder(Border.NO_BORDER).setMarginBottom(4);

        addResumenCard(grid, "Total ventas", String.valueOf(total), LIGHT_BG, TEXT_GRAY);
        addResumenCard(grid, "Completadas", String.valueOf(completadas), GREEN_BG, GREEN_TEXT);
        addResumenCard(grid, "Crédito", String.valueOf(credito), new DeviceRgb(219, 234, 254), new DeviceRgb(30, 64, 175));
        addResumenCard(grid, "Anuladas", String.valueOf(anuladas), RED_BG, RED_TEXT);
        addResumenCard(grid, "Total IVA", formatCOP(totalIva), AMBER_BG, AMBER_TEXT);
        addResumenCard(grid, "Monto total", formatCOP(monto), new DeviceRgb(237, 233, 254), INDIGO);

        document.add(grid);
    }

    private void addResumenCard(Table grid, String label, String value,
                                DeviceRgb bg, DeviceRgb textColor) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
            .setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(10).setMargin(3);
        cell.add(new Paragraph(label).setFontSize(8).setFontColor(TEXT_GRAY).setBold());
        cell.add(new Paragraph(value).setFontSize(14).setFontColor(textColor).setBold().setMarginTop(2));
        grid.addCell(cell);
    }

    private void addTableCell(Table tabla, String value,
                              com.itextpdf.kernel.colors.Color bg,
                              TextAlignment align, boolean bold) {
        Paragraph p = new Paragraph(value != null ? value : "—").setFontSize(8);
        if (bold) p.setBold();
        tabla.addCell(new com.itextpdf.layout.element.Cell()
            .add(p)
            .setBackgroundColor(bg)
            .setBorder(Border.NO_BORDER)
            .setPadding(5)
            .setTextAlignment(align));
    }

    private void addEstadoCell(Table tabla, String estado,
                               com.itextpdf.kernel.colors.Color rowBg) {
        DeviceRgb color;
        if ("COMPLETADA".equals(estado)) color = GREEN_TEXT;
        else if ("ANULADA".equals(estado)) color = RED_TEXT;
        else color = TEXT_GRAY;

        tabla.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(estado).setFontSize(7).setBold().setFontColor(color))
            .setBackgroundColor(rowBg)
            .setBorder(Border.NO_BORDER)
            .setPadding(5)
            .setTextAlignment(TextAlignment.CENTER));
    }

    private void addFooter(Document document) {
        document.add(new Paragraph("\n\n"));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth()
            .setBorder(new SolidBorder(ZEBRA_GRAY, 0.5f)));
        document.add(new Paragraph("Este reporte es de uso interno. Impulsado por Aura POS — Gestión Inteligente")
            .setTextAlignment(TextAlignment.CENTER).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(8));
    }

    private String buildRangeLabel(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        if (desde != null && hasta != null)
            return "Del " + desde.format(f) + " al " + hasta.format(f);
        if (desde != null) return "Desde " + desde.format(f);
        if (hasta != null) return "Hasta " + hasta.format(f);
        return "Todos los períodos";
    }

    // ── Helpers comunes ───────────────────────────────────────
    private List<VentaEntity> filtrar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return ventaRepository.findByEmpresaId(empresaId);
        }
        return ventaRepository.findByEmpresaIdAndFechaEmisionBetween(
                empresaId, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    private List<LineaReporte> aplanar(List<VentaEntity> ventas) {
        List<LineaReporte> out = new ArrayList<>();
        if (ventas == null) return out;
        for (VentaEntity v : ventas) {
            if (v == null || v.getId() == null) continue;
            List<VentaDetalleEntity> dets = ventaDetalleRepository.findByVentaId(v.getId());
            if (dets == null || dets.isEmpty()) {
                out.add(new LineaReporte(v, null));
            } else {
                for (VentaDetalleEntity d : dets) {
                    out.add(new LineaReporte(v, d));
                }
            }
        }
        return out;
    }

    private boolean esVenta(String estado) {
        return "COMPLETADA".equals(estado) || "PAGO_PARCIAL".equals(estado);
    }

    private BigDecimal subtotalSinIva(VentaDetalleEntity d) {
        if (d == null) return BigDecimal.ZERO;
        BigDecimal precio = d.getPrecioUnitario() != null ? d.getPrecioUnitario() : BigDecimal.ZERO;
        BigDecimal cant = d.getCantidad() != null ? d.getCantidad() : BigDecimal.ZERO;
        BigDecimal descuento = d.getMontoDescuento() != null ? d.getMontoDescuento() : BigDecimal.ZERO;
        return precio.multiply(cant).subtract(descuento);
    }

    private BigDecimal totalConIva(VentaDetalleEntity d) {
        if (d == null) return BigDecimal.ZERO;
        BigDecimal iva = d.getImpuestoValor() != null ? d.getImpuestoValor() : BigDecimal.ZERO;
        return subtotalSinIva(d).add(iva);
    }

    private String nombreProducto(VentaDetalleEntity d) {
        if (d == null || d.getProducto() == null) return "—";
        String nombre = d.getProducto().getNombre();
        return nombre != null ? nombre : "—";
    }

    private String formatCantidad(BigDecimal v) {
        if (v == null) return "0";
        BigDecimal stripped = v.stripTrailingZeros();
        if (stripped.scale() < 0) stripped = stripped.setScale(0);
        return stripped.toPlainString();
    }

    private String formatIvaPorcentaje(VentaDetalleEntity d) {
        ProductoEntity p = d != null ? d.getProducto() : null;
        BigDecimal pct = p != null ? p.getIvaPorcentaje() : null;
        boolean incluido = p != null && Boolean.TRUE.equals(p.getIvaIncluido());
        String base;
        if (pct == null) {
            base = "0 %";
        } else {
            BigDecimal stripped = pct.stripTrailingZeros();
            if (stripped.scale() < 0) stripped = stripped.setScale(0);
            base = stripped.toPlainString().replace(".", ",") + " %";
        }
        return incluido ? base + " *" : base;
    }

    private String formatCOP(BigDecimal v) {
        if (v == null) return "$ 0";
        return String.format("$ %,.0f", v).replace(",", ".");
    }

    private String generarNumeroVenta(VentaEntity v) {
        if (v == null) return "#" + (v != null ? v.getId() : "");
        String prefijo = v.getPrefijo() != null ? v.getPrefijo() : "POS";
        Long consecutivo = v.getConsecutivo() != null ? v.getConsecutivo() : 0L;
        return prefijo + "-" + consecutivo;
    }

    private String obtenerNombreCaja(VentaEntity v) {
        if (v == null || v.getTurnoCaja() == null) return "—";
        TurnoCajaEntity turnoCaja = v.getTurnoCaja();
        if (turnoCaja.getCaja() != null) {
            return turnoCaja.getCaja().getNombre() != null ? turnoCaja.getCaja().getNombre() : "—";
        }
        return "—";
    }

    private void setCell(Row row, int col, String val, CellStyle style) {
        if (row == null) return;
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        c.setCellStyle(style);
    }

    private XSSFCellStyle crearEstiloHeader(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x5B,(byte)0x21,(byte)0xB6}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setBold(true); f.setColor(IndexedColors.WHITE.getIndex()); f.setFontHeightInPoints((short)10);
        s.setFont(f);
        setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloData(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF9,(byte)0xFA,(byte)0xFB}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } else {
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFF,(byte)0xFF,(byte)0xFF}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)9);
        s.setFont(f); setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloTotal(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xED,(byte)0xE9,(byte)0xFE}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short)10);
        f.setColor(new XSSFColor(new byte[]{(byte)0x5B,(byte)0x21,(byte)0xB6}, null));
        s.setFont(f); setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloTitulo(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short)14);
        f.setColor(new XSSFColor(new byte[]{(byte)0x5B,(byte)0x21,(byte)0xB6}, null));
        s.setFont(f);
        return s;
    }

    private XSSFCellStyle crearEstiloNota(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.LEFT);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont();
        f.setItalic(true); f.setFontHeightInPoints((short)9);
        f.setColor(new XSSFColor(new byte[]{(byte)0x64,(byte)0x74,(byte)0x8B}, null));
        s.setFont(f);
        return s;
    }

    private void setBorder(XSSFCellStyle s) {
        s.setBorderLeft(BorderStyle.THIN);   s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderRight(BorderStyle.THIN);  s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderTop(BorderStyle.THIN);    s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderBottom(BorderStyle.THIN); s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
