package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
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
public class ReporteInventarioService {
    private final ProductoJPARepository productoRepository;
    private final InventarioJPARepository inventarioRepository;
    private final SecurityUtils securityUtils;
    private final IEmpresaService empresaService;

    // Color palette
    private static final DeviceRgb INDIGO      = new DeviceRgb(91,  33,  182);
    private static final DeviceRgb DARK_HEADER = new DeviceRgb(30,  41,  59);
    private static final DeviceRgb LIGHT_BG    = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb TEXT_GRAY   = new DeviceRgb(100, 116, 139);
    private static final DeviceRgb ZEBRA_GRAY  = new DeviceRgb(249, 250, 251);
    private static final DeviceRgb GREEN_BG    = new DeviceRgb(220, 252, 231);
    private static final DeviceRgb GREEN_TEXT  = new DeviceRgb(21,  128, 61);
    private static final DeviceRgb RED_BG      = new DeviceRgb(254, 226, 226);
    private static final DeviceRgb RED_TEXT    = new DeviceRgb(185, 28,  28);
    private static final DeviceRgb AMBER_BG    = new DeviceRgb(254, 243, 199);
    private static final DeviceRgb AMBER_TEXT  = new DeviceRgb(146, 64,  14);

    public ReporteInventarioService(ProductoJPARepository productoRepository,
                                    InventarioJPARepository inventarioRepository,
                                    SecurityUtils securityUtils,
                                    IEmpresaService empresaService) {
        this.productoRepository = productoRepository;
        this.inventarioRepository = inventarioRepository;
        this.securityUtils = securityUtils;
        this.empresaService = empresaService;
    }

    // ── EXCEL ─────────────────────────────────────────────────
    public byte[] generarExcel() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ProductoEntity> productos = productoRepository.findByEmpresaId(empresaId);

        if (productos == null || productos.isEmpty()) {
            productos = List.of();
        }

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet ws = wb.createSheet("Inventario");

            XSSFCellStyle hdrStyle   = crearEstiloHeader(wb);
            XSSFCellStyle dataStyle  = crearEstiloData(wb, false);
            XSSFCellStyle dataAlt    = crearEstiloData(wb, true);
            XSSFCellStyle sinStock   = crearEstiloSinStock(wb);
            XSSFCellStyle inactivo   = crearEstiloInactivo(wb);
            XSSFCellStyle titleStyle = crearEstiloTitulo(wb);
            XSSFCellStyle subStyle   = crearEstiloSub(wb);

            // Título
            Row r0 = ws.createRow(0); r0.setHeightInPoints(30);
            Cell t = r0.createCell(0);
            t.setCellValue("REPORTE DE INVENTARIO — AURA POS");
            t.setCellStyle(titleStyle);
            ws.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));

            Row r1 = ws.createRow(1); r1.setHeightInPoints(16);
            Cell sub = r1.createCell(0);
            sub.setCellValue("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            sub.setCellStyle(subStyle);
            ws.addMergedRegion(new CellRangeAddress(1, 1, 0, 7));

            ws.createRow(2); // separador

            // Encabezados
            String[] cols = {"SKU / Cód. Barras", "Nombre Producto", "Categoría",
                    "Stock", "Costo", "Precio Venta", "IVA %", "Estado"};
            Row rh = ws.createRow(3); rh.setHeightInPoints(22);
            for (int i = 0; i < cols.length; i++) {
                Cell c = rh.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hdrStyle);
            }

            // Datos
            for (int pi = 0; pi < productos.size(); pi++) {
                ProductoEntity p = productos.get(pi);
                Row rd = ws.createRow(4 + pi);
                rd.setHeightInPoints(26);

                InventarioEntity inv = inventarioRepository.findByProductoId(p.getId())
                        .stream().findFirst().orElse(null);

                BigDecimal stock = inv != null && inv.getStockActual() != null ? inv.getStockActual() : BigDecimal.ZERO;
                boolean activo = Boolean.TRUE.equals(p.getActivo());
                boolean sinStockB = activo && stock.compareTo(BigDecimal.ZERO) == 0;

                XSSFCellStyle st;
                if (!activo)       st = inactivo;
                else if (sinStockB) st = sinStock;
                else               st = pi % 2 == 0 ? dataStyle : dataAlt;

                String sku = p.getSku() != null ? p.getSku()
                        : (p.getCodigoBarras() != null ? p.getCodigoBarras() : "—");

                setCell(rd, 0, sku, st);
                setCell(rd, 1, p.getNombre() != null ? p.getNombre() : "—", st);
                setCell(rd, 2, p.getCategoria() != null ? p.getCategoria().getNombre() : "—", st);
                setCell(rd, 3, String.valueOf(stock.intValue()), st);
                setCell(rd, 4, formatCOP(p.getCosto()), st);
                setCell(rd, 5, formatCOP(p.getPrecio()), st);
                setCell(rd, 6, p.getIvaPorcentaje() != null ? p.getIvaPorcentaje().toPlainString() + "%" : "0%", st);
                setCell(rd, 7, activo ? "Activo" : "Inactivo", st);
            }

            // Anchos
            ws.setColumnWidth(0, 20 * 256);
            ws.setColumnWidth(1, 38 * 256);
            ws.setColumnWidth(2, 18 * 256);
            ws.setColumnWidth(3, 10 * 256);
            ws.setColumnWidth(4, 14 * 256);
            ws.setColumnWidth(5, 14 * 256);
            ws.setColumnWidth(6,  8 * 256);
            ws.setColumnWidth(7, 12 * 256);

            wb.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel de inventario: " + e.getMessage(), e);
        }
    }

    // ── PDF ───────────────────────────────────────────────────
    public byte[] generarPdf() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<ProductoEntity> productos = productoRepository.findByEmpresaId(empresaId);
        if (productos == null) productos = List.of();

        // Un solo query para todo el inventario — evita N+1
        Map<Long, BigDecimal> stockMap = inventarioRepository.findBySucursalEmpresaId(empresaId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        inv -> inv.getProducto().getId(),
                        inv -> inv.getStockActual() != null ? inv.getStockActual() : BigDecimal.ZERO,
                        (a, b) -> a.add(b) // sumar si hay múltiples sucursales
                ));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4.rotate());
            document.setMargins(20, 20, 20, 20);

            // ── Header ──────────────────────────────────────
            addHeader(document, empresaId);

            // ── Resumen (sin queries adicionales) ───────────
            long activos   = productos.stream().filter(p -> Boolean.TRUE.equals(p.getActivo())).count();
            long inactivos = productos.size() - activos;
            long sinStock  = productos.stream().filter(p -> {
                if (!Boolean.TRUE.equals(p.getActivo())) return false;
                BigDecimal s = stockMap.getOrDefault(p.getId(), BigDecimal.ZERO);
                return s.compareTo(BigDecimal.ZERO) == 0;
            }).count();

            addResumen(document, productos.size(), activos, sinStock, inactivos);

            // ── Tabla ────────────────────────────────────────
            document.add(new Paragraph("Detalle de Productos")
                .setBold().setFontSize(11).setFontColor(DARK_HEADER).setMarginTop(14).setMarginBottom(6));

            Table tabla = new Table(UnitValue.createPercentArray(
                    new float[]{0.7f, 1.6f, 0.9f, 0.4f, 0.75f, 0.75f, 0.35f, 0.55f}))
                    .useAllAvailableWidth();

            String[] headers = {"SKU", "Producto", "Categoría", "Stock", "Costo", "P. Venta", "IVA", "Estado"};
            for (String h : headers) {
                tabla.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(h))
                    .setBold().setFontSize(8)
                    .setBackgroundColor(DARK_HEADER)
                    .setFontColor(ColorConstants.WHITE)
                    .setBorder(Border.NO_BORDER)
                    .setPadding(6));
            }

            int idx = 0;
            for (ProductoEntity p : productos) {
                BigDecimal stock = stockMap.getOrDefault(p.getId(), BigDecimal.ZERO);
                boolean activo = Boolean.TRUE.equals(p.getActivo());
                boolean ceroStock = activo && stock.compareTo(BigDecimal.ZERO) == 0;

                com.itextpdf.kernel.colors.Color rowBg = (idx % 2 == 0)
                        ? ColorConstants.WHITE : ZEBRA_GRAY;

                String sku = p.getSku() != null ? p.getSku()
                        : (p.getCodigoBarras() != null ? p.getCodigoBarras() : "—");
                String nombre    = p.getNombre() != null ? p.getNombre() : "—";
                String categoria = p.getCategoria() != null ? p.getCategoria().getNombre() : "—";
                String iva       = p.getIvaPorcentaje() != null ? p.getIvaPorcentaje().toPlainString() + "%" : "0%";

                addCell(tabla, sku,                              rowBg, TextAlignment.LEFT,   false);
                addCell(tabla, nombre,                           rowBg, TextAlignment.LEFT,   false);
                addCell(tabla, categoria,                        rowBg, TextAlignment.LEFT,   false);
                addStockCell(tabla, stock.intValue(), ceroStock, rowBg);
                addCell(tabla, formatCOP(p.getCosto()),          rowBg, TextAlignment.RIGHT,  false);
                addCell(tabla, formatCOP(p.getPrecio()),         rowBg, TextAlignment.RIGHT,  true);
                addCell(tabla, iva,                              rowBg, TextAlignment.CENTER, false);
                addEstadoCell(tabla, activo, ceroStock, rowBg);

                idx++;
            }

            if (productos.isEmpty()) {
                tabla.addCell(new com.itextpdf.layout.element.Cell(1, 8)
                    .add(new Paragraph("No hay productos registrados").setFontColor(TEXT_GRAY).setItalic())
                    .setBorder(Border.NO_BORDER).setPadding(12).setTextAlignment(TextAlignment.CENTER));
            }

            document.add(tabla);

            addFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Error generando PDF de inventario: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF de inventario: " + e.getMessage(), e);
        }
    }

    // ── PDF helpers ───────────────────────────────────────────

    private void addHeader(Document document, Integer empresaId) {
        EmpresaDto empresa = empresaService.obtenerEmpresaActual(empresaId, null, null);

        // Banner superior
        Table banner = new Table(UnitValue.createPercentArray(new float[]{60, 40})).useAllAvailableWidth();
        banner.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("REPORTE DE INVENTARIO").setBold().setFontSize(16).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12));
        banner.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                .setFontSize(9).setFontColor(ColorConstants.LIGHT_GRAY))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12)
            .setTextAlignment(TextAlignment.RIGHT));
        document.add(banner);

        document.add(new Paragraph("\n").setFontSize(6));

        // Info empresa
        Table companyTable = new Table(UnitValue.createPercentArray(new float[]{15, 85})).useAllAvailableWidth();
        companyTable.setBorder(Border.NO_BORDER);

        com.itextpdf.layout.element.Cell logoCell = new com.itextpdf.layout.element.Cell()
            .setBorder(Border.NO_BORDER)
            .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()));
                logo.setMaxWidth(70);
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
        companyTable.addCell(infoCell);
        document.add(companyTable);

        document.add(new Paragraph("\n").setFontSize(3));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth()
            .setBorder(new SolidBorder(LIGHT_BG, 1)));
        document.add(new Paragraph("\n").setFontSize(6));
    }

    private void addResumen(Document document, int total, long activos, long sinStock, long inactivos) {
        Table grid = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
        grid.setBorder(Border.NO_BORDER).setMarginBottom(4);

        addResumenCard(grid, "Total productos",  String.valueOf(total),    LIGHT_BG,  TEXT_GRAY);
        addResumenCard(grid, "Activos",          String.valueOf(activos),  GREEN_BG,  GREEN_TEXT);
        addResumenCard(grid, "Sin stock",        String.valueOf(sinStock), AMBER_BG,  AMBER_TEXT);
        addResumenCard(grid, "Inactivos",        String.valueOf(inactivos),RED_BG,    RED_TEXT);

        document.add(grid);
    }

    private void addResumenCard(Table grid, String label, String value,
                                DeviceRgb bg, DeviceRgb textColor) {
        com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
            .setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(10).setMargin(3);
        cell.add(new Paragraph(label).setFontSize(8).setFontColor(TEXT_GRAY).setBold());
        cell.add(new Paragraph(value).setFontSize(16).setFontColor(textColor).setBold().setMarginTop(2));
        grid.addCell(cell);
    }

    private void addCell(Table tabla, String value,
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

    private void addStockCell(Table tabla, int stock,
                              boolean ceroStock,
                              com.itextpdf.kernel.colors.Color rowBg) {
        DeviceRgb color = ceroStock ? RED_TEXT : GREEN_TEXT;
        tabla.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(String.valueOf(stock)).setFontSize(8).setBold().setFontColor(color))
            .setBackgroundColor(rowBg)
            .setBorder(Border.NO_BORDER)
            .setPadding(5)
            .setTextAlignment(TextAlignment.CENTER));
    }

    private void addEstadoCell(Table tabla, boolean activo, boolean ceroStock,
                               com.itextpdf.kernel.colors.Color rowBg) {
        String label;
        DeviceRgb color;
        if (!activo) {
            label = "Inactivo"; color = TEXT_GRAY;
        } else if (ceroStock) {
            label = "Sin stock"; color = AMBER_TEXT;
        } else {
            label = "Activo"; color = GREEN_TEXT;
        }
        tabla.addCell(new com.itextpdf.layout.element.Cell()
            .add(new Paragraph(label).setFontSize(7).setBold().setFontColor(color))
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

    // ── Helpers comunes ───────────────────────────────────────
    private String formatCOP(BigDecimal v) {
        if (v == null) return "$ 0";
        return String.format("$ %,.0f", v).replace(",", ".");
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
        s.setFont(f); setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloData(XSSFWorkbook wb, boolean alt) {
        XSSFCellStyle s = wb.createCellStyle();
        if (alt) {
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF9,(byte)0xFA,(byte)0xFB}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)9);
        s.setFont(f); setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloSinStock(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFE,(byte)0xE2,(byte)0xE2}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)9);
        f.setColor(new XSSFColor(new byte[]{(byte)0x99,(byte)0x1B,(byte)0x1B}, null));
        s.setFont(f); setBorder(s);
        return s;
    }

    private XSSFCellStyle crearEstiloInactivo(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF3,(byte)0xF4,(byte)0xF6}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)9);
        f.setColor(new XSSFColor(new byte[]{(byte)0x9C,(byte)0xA3,(byte)0xAF}, null));
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

    private XSSFCellStyle crearEstiloSub(XSSFWorkbook wb) {
        XSSFCellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)9);
        f.setColor(new XSSFColor(new byte[]{(byte)0x6B,(byte)0x72,(byte)0x80}, null));
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
