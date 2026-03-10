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

import com.cloud_technological.aura_pos.entity.TurnoCajaEntity;
import com.cloud_technological.aura_pos.entity.VentaDetalleEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.venta_detalle.VentaDetalleJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@Service
public class ReporteVentasService {

    private final VentaJPARepository ventaRepository;
    private final VentaDetalleJPARepository ventaDetalleRepository;
    private final SecurityUtils securityUtils;

    public ReporteVentasService(VentaJPARepository ventaRepository, 
                               VentaDetalleJPARepository ventaDetalleRepository,
                               SecurityUtils securityUtils) {
        this.ventaRepository = ventaRepository;
        this.ventaDetalleRepository = ventaDetalleRepository;
        this.securityUtils = securityUtils;
    }

    // ── EXCEL ─────────────────────────────────────────────────
    public byte[] generarExcel(LocalDate desde, LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<VentaEntity> ventas = filtrar(empresaId, desde, hasta);

        if (ventas == null || ventas.isEmpty()) {
            ventas = List.of();
        }

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            XSSFSheet ws = wb.createSheet("Ventas");

            // Estilos
            XSSFCellStyle hdrStyle = crearEstiloHeader(wb);
            XSSFCellStyle dataStyle = crearEstiloData(wb, false);
            XSSFCellStyle dataAlt   = crearEstiloData(wb, true);
            XSSFCellStyle totalStyle = crearEstiloTotal(wb);
            XSSFCellStyle titleStyle = crearEstiloTitulo(wb);

            // Título
            Row r0 = ws.createRow(0); r0.setHeightInPoints(30);
            Cell t = r0.createCell(0);
            t.setCellValue("REPORTE DE VENTAS — AURA POS");
            t.setCellStyle(titleStyle);
            ws.addMergedRegion(new CellRangeAddress(0,0,0,5));

            Row r1 = ws.createRow(1); r1.setHeightInPoints(16);
            Cell sub = r1.createCell(0);
            sub.setCellValue("Generado el " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
            ws.addMergedRegion(new CellRangeAddress(1,1,0,5));

            ws.createRow(2); // separador

            // Header columnas
            String[] cols = {"N° Venta", "Fecha", "Cajero / Caja", "Productos", "Total", "Estado"};
            Row rh = ws.createRow(3); rh.setHeightInPoints(22);
            for (int i = 0; i < cols.length; i++) {
                Cell c = rh.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(hdrStyle);
            }

            // Datos
            BigDecimal totalCompletadas = BigDecimal.ZERO;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            for (int vi = 0; vi < ventas.size(); vi++) {
                VentaEntity v = ventas.get(vi);
                Row rd = ws.createRow(4 + vi);
                rd.setHeightInPoints(28);
                XSSFCellStyle st = vi % 2 == 0 ? dataStyle : dataAlt;

                String numeroVenta = generarNumeroVenta(v);
                setCell(rd, 0, numeroVenta, st);
                setCell(rd, 1, v.getFechaEmision() != null ? v.getFechaEmision().format(fmt) : "", st);
                setCell(rd, 2, obtenerNombreCaja(v), st);
                setCell(rd, 3, contarProductos(v), st);
                setCell(rd, 4, formatCOP(v.getTotalPagar()), st);
                
                String estado = v.getEstadoVenta() != null ? v.getEstadoVenta() : "";
                setCell(rd, 5, estado, st);

                if ("COMPLETADA".equals(estado) && v.getTotalPagar() != null) {
                    totalCompletadas = totalCompletadas.add(v.getTotalPagar());
                }
            }

            // Fila total
            int tr = 4 + ventas.size();
            Row rt = ws.createRow(tr); rt.setHeightInPoints(24);
            Cell lbl = rt.createCell(0);
            lbl.setCellValue("TOTAL VENTAS COMPLETADAS");
            lbl.setCellStyle(totalStyle);
            ws.addMergedRegion(new CellRangeAddress(tr, tr, 0, 3));
            for (int i = 1; i <= 3; i++) {
                rt.createCell(i).setCellStyle(totalStyle);
            }
            setCell(rt, 4, formatCOP(totalCompletadas), totalStyle);
            rt.createCell(5).setCellStyle(totalStyle);

            // Anchos columnas
            ws.setColumnWidth(0, 14 * 256);
            ws.setColumnWidth(1, 20 * 256);
            ws.setColumnWidth(2, 22 * 256);
            ws.setColumnWidth(3, 40 * 256);
            ws.setColumnWidth(4, 16 * 256);
            ws.setColumnWidth(5, 16 * 256);

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
        // TODO: Implementar generación de PDF con iText
        throw new UnsupportedOperationException("PDF generation not implemented yet");
    }

    // ── Helpers ───────────────────────────────────────────────
    private List<VentaEntity> filtrar(Integer empresaId, LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            return ventaRepository.findByEmpresaId(empresaId);
        }
        return ventaRepository.findByEmpresaIdAndFechaEmisionBetween(
                empresaId, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
    }

    private String contarProductos(VentaEntity v) {
        if (v == null || v.getId() == null) return "—";
        try {
            List<VentaDetalleEntity> detalles = ventaDetalleRepository.findByVentaId(v.getId());
            if (detalles == null || detalles.isEmpty()) return "—";
            return detalles.stream()
                    .filter(d -> d != null && d.getProducto() != null)
                    .map(d -> d.getProducto().getNombre() + " x" + d.getCantidad().intValue())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("—");
        } catch (Exception e) {
            return "—";
        }
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

    private void setBorder(XSSFCellStyle s) {
        s.setBorderLeft(BorderStyle.THIN);   s.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderRight(BorderStyle.THIN);  s.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderTop(BorderStyle.THIN);    s.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setBorderBottom(BorderStyle.THIN); s.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
    }
}
