package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

@Service
public class ReporteInventarioService {
    private final ProductoJPARepository productoRepository;
    private final InventarioJPARepository inventarioRepository;
    private final SecurityUtils securityUtils;

    public ReporteInventarioService(ProductoJPARepository productoRepository, 
                                   InventarioJPARepository inventarioRepository,
                                   SecurityUtils securityUtils) {
        this.productoRepository = productoRepository;
        this.inventarioRepository = inventarioRepository;
        this.securityUtils = securityUtils;
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

                // Obtener inventario del producto (TODO: considerar sucursal si aplica)
                InventarioEntity inv = inventarioRepository.findByProductoId(p.getId())
                        .stream().findFirst().orElse(null);

                BigDecimal stock = inv != null && inv.getStockActual() != null ? inv.getStockActual() : BigDecimal.ZERO;
                boolean activo = Boolean.TRUE.equals(p.getActivo());
                boolean sinStockB = activo && stock.compareTo(BigDecimal.ZERO) == 0;

                XSSFCellStyle st;
                if (!activo)     st = inactivo;
                else if (sinStockB) st = sinStock;
                else             st = pi % 2 == 0 ? dataStyle : dataAlt;

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

        if (productos == null || productos.isEmpty()) {
            productos = List.of();
        }

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(out);
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf, com.itextpdf.kernel.geom.PageSize.A4.rotate());
            doc.setMargins(20, 20, 20, 20);

            DeviceRgb headerBg = new DeviceRgb(91, 33, 182);
            DeviceRgb white = new DeviceRgb(255, 255, 255);
            DeviceRgb lightGray = new DeviceRgb(249, 250, 251);
            DeviceRgb redText = new DeviceRgb(153, 27, 27);
            DeviceRgb grayText = new DeviceRgb(107, 114, 128);

            Paragraph title = new Paragraph("REPORTE DE INVENTARIO")
                    .setFontSize(18)
                    .setBold()
                    .setFontColor(headerBg)
                    .setTextAlignment(TextAlignment.CENTER);
            doc.add(title);

            Paragraph subtitle = new Paragraph("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    .setFontSize(10)
                    .setFontColor(grayText)
                    .setTextAlignment(TextAlignment.CENTER);
            doc.add(subtitle);

            doc.add(new Paragraph("\n"));

            Table table = new Table(UnitValue.createPercentArray(new float[]{0.6f, 1.4f, 0.9f, 0.35f, 0.65f, 0.65f, 0.3f, 0.45f}))
                    .useAllAvailableWidth();

            String[] headers = {"SKU", "Producto", "Categoria", "Stock", "Costo", "Venta", "IVA", "Estado"};
            for (String h : headers) {
                com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(h).setBold().setFontColor(white))
                        .setBackgroundColor(headerBg)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setPadding(5f);
                table.addHeaderCell(cell);
            }

            for (ProductoEntity p : productos) {
                InventarioEntity inv = inventarioRepository.findByProductoId(p.getId())
                        .stream().findFirst().orElse(null);

                BigDecimal stock = inv != null && inv.getStockActual() != null ? inv.getStockActual() : BigDecimal.ZERO;
                boolean activo = Boolean.TRUE.equals(p.getActivo());
                boolean sinStockB = activo && stock.compareTo(BigDecimal.ZERO) == 0;

                DeviceRgb bgColor = lightGray;
                DeviceRgb textColor = new DeviceRgb(0, 0, 0);
                if (!activo) {
                    textColor = grayText;
                } else if (sinStockB) {
                    textColor = redText;
                }

                String sku = p.getSku() != null ? p.getSku()
                        : (p.getCodigoBarras() != null ? p.getCodigoBarras() : "—");
                String nombre = p.getNombre() != null ? p.getNombre() : "—";
                String categoria = p.getCategoria() != null ? p.getCategoria().getNombre() : "—";
                String iva = p.getIvaPorcentaje() != null ? p.getIvaPorcentaje().toPlainString() + "%" : "0%";
                String estado = activo ? "Activo" : "Inactivo";

                table.addCell(createPdfCell(sku, textColor, bgColor));
                table.addCell(createPdfCell(nombre, textColor, bgColor));
                table.addCell(createPdfCell(categoria, textColor, bgColor));
                table.addCell(createPdfCell(String.valueOf(stock.intValue()), textColor, bgColor));
                table.addCell(createPdfCell(formatCOP(p.getCosto()), textColor, bgColor));
                table.addCell(createPdfCell(formatCOP(p.getPrecio()), textColor, bgColor));
                table.addCell(createPdfCell(iva, textColor, bgColor));
                table.addCell(createPdfCell(estado, textColor, bgColor));
            }

            doc.add(table);

            Paragraph total = new Paragraph("Total de productos: " + productos.size())
                    .setFontSize(10)
                    .setMarginTop(10)
                    .setFontColor(grayText);
            doc.add(total);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de inventario: " + e.getMessage(), e);
        }
    }

    private com.itextpdf.layout.element.Cell createPdfCell(String text, DeviceRgb textColor, DeviceRgb bgColor) {
        return new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(8))
                .setFontColor(textColor)
                .setBackgroundColor(bgColor)
                .setTextAlignment(TextAlignment.CENTER)
                .setPadding(3f);
    }

    // ── Helpers ───────────────────────────────────────────────
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
    private BigDecimal parseBD(Object val) {
    if (val == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(val.toString().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
