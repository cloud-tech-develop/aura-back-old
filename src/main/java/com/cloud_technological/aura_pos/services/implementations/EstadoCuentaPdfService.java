package com.cloud_technological.aura_pos.services.implementations;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.dto.terceros.EstadoCuentaClienteDto;
import com.cloud_technological.aura_pos.dto.terceros.MovimientoCuentaDto;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EstadoCuentaPdfService {

    private final IEmpresaService empresaService;

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
    private static final DeviceRgb INDIGO_BG   = new DeviceRgb(237, 233, 254);

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_DIA   = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public EstadoCuentaPdfService(IEmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    public byte[] generar(EstadoCuentaClienteDto estado, Integer empresaId,
                          String fechaDesde, String fechaHasta) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);
            document.setMargins(24, 24, 24, 24);

            addHeader(document, empresaId, estado, fechaDesde, fechaHasta);
            addResumen(document, estado);
            addTablaMovimientos(document, estado.getMovimientos());
            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando PDF estado de cuenta: {}", e.getMessage(), e);
            throw new RuntimeException("Error generando PDF estado de cuenta: " + e.getMessage(), e);
        }
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void addHeader(Document document, Integer empresaId,
                           EstadoCuentaClienteDto estado,
                           String fechaDesde, String fechaHasta) {
        EmpresaDto empresa = empresaService.obtenerEmpresaActual(empresaId, null, null);

        // Banner oscuro
        Table banner = new Table(UnitValue.createPercentArray(new float[]{55, 45})).useAllAvailableWidth();
        banner.addCell(new Cell()
            .add(new Paragraph("ESTADO DE CUENTA").setBold().setFontSize(16).setFontColor(ColorConstants.WHITE))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12));
        banner.addCell(new Cell()
            .add(new Paragraph(buildRangoLabel(fechaDesde, fechaHasta))
                .setFontSize(9).setFontColor(ColorConstants.LIGHT_GRAY))
            .setBackgroundColor(DARK_HEADER).setBorder(Border.NO_BORDER).setPadding(12)
            .setTextAlignment(TextAlignment.RIGHT));
        document.add(banner);

        document.add(new Paragraph("\n").setFontSize(6));

        // Empresa + cliente lado a lado
        Table infoTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
        infoTable.setBorder(Border.NO_BORDER);

        // — Empresa —
        Cell empresaCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10).setBackgroundColor(LIGHT_BG);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()));
                logo.setMaxWidth(70);
                empresaCell.add(logo);
            } catch (Exception e) {
                log.warn("Logo load fail: {}", e.getMessage());
            }
        }
        empresaCell.add(new Paragraph(empresa.getRazonSocial())
            .setBold().setFontSize(12).setFontColor(DARK_HEADER).setMarginTop(4));
        empresaCell.add(new Paragraph("NIT: " + empresa.getNit()
            + (empresa.getDv() != null ? "-" + empresa.getDv() : ""))
            .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-3));
        if (empresa.getTelefono() != null)
            empresaCell.add(new Paragraph("Tel: " + empresa.getTelefono())
                .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-2));
        infoTable.addCell(empresaCell);

        // — Cliente —
        Cell clienteCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10)
            .setBackgroundColor(INDIGO_BG).setVerticalAlignment(VerticalAlignment.MIDDLE);
        clienteCell.add(new Paragraph("CLIENTE").setBold().setFontSize(8).setFontColor(INDIGO));
        clienteCell.add(new Paragraph(estado.getNombreCliente())
            .setBold().setFontSize(13).setFontColor(DARK_HEADER).setMarginTop(2));
        clienteCell.add(new Paragraph(estado.getTipoDocumento() + ": " + estado.getNumeroDocumento())
            .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-2));
        if (estado.getEmail() != null)
            clienteCell.add(new Paragraph(estado.getEmail())
                .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-2));
        if (estado.getTelefono() != null)
            clienteCell.add(new Paragraph("Tel: " + estado.getTelefono())
                .setFontSize(9).setFontColor(TEXT_GRAY).setMarginTop(-2));
        clienteCell.add(new Paragraph("Generado el " + LocalDate.now().format(FMT_DIA))
            .setFontSize(8).setFontColor(TEXT_GRAY).setItalic().setMarginTop(6));
        infoTable.addCell(clienteCell);

        document.add(infoTable);
        document.add(new Paragraph("\n").setFontSize(6));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth()
            .setBorder(new SolidBorder(LIGHT_BG, 1)));
        document.add(new Paragraph("\n").setFontSize(8));
    }

    // ── Resumen ───────────────────────────────────────────────────────────────

    private void addResumen(Document document, EstadoCuentaClienteDto e) {
        Table grid = new Table(UnitValue.createPercentArray(new float[]{25, 25, 25, 25})).useAllAvailableWidth();
        grid.setBorder(Border.NO_BORDER).setMarginBottom(4);

        addCard(grid, "Total ventas",     formatCOP(e.getTotalVentas()),    LIGHT_BG,  TEXT_GRAY);
        addCard(grid, "Deuda en crédito", formatCOP(e.getTotalDeuda()),     AMBER_BG,  AMBER_TEXT);
        addCard(grid, "Total abonado",    formatCOP(e.getTotalAbonado()),   GREEN_BG,  GREEN_TEXT);
        addCard(grid, "Saldo pendiente",  formatCOP(e.getSaldoPendiente()),
                e.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0 ? RED_BG   : GREEN_BG,
                e.getSaldoPendiente().compareTo(BigDecimal.ZERO) > 0 ? RED_TEXT : GREEN_TEXT);

        document.add(grid);
    }

    private void addCard(Table grid, String label, String value, DeviceRgb bg, DeviceRgb textColor) {
        Cell cell = new Cell().setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(10).setMargin(3);
        cell.add(new Paragraph(label).setFontSize(8).setFontColor(TEXT_GRAY).setBold());
        cell.add(new Paragraph(value).setFontSize(14).setFontColor(textColor).setBold().setMarginTop(2));
        grid.addCell(cell);
    }

    // ── Tabla de movimientos ──────────────────────────────────────────────────

    private void addTablaMovimientos(Document document, List<MovimientoCuentaDto> movimientos) {
        document.add(new Paragraph("Movimientos")
            .setBold().setFontSize(11).setFontColor(DARK_HEADER).setMarginTop(14).setMarginBottom(6));

        Table tabla = new Table(UnitValue.createPercentArray(new float[]{10, 8, 12, 14, 22, 12, 12, 10}))
            .useAllAvailableWidth();

        String[] headers = {"Tipo", "Forma", "Fecha", "Referencia", "Descripción", "Cargo", "Abono", "Saldo acum."};
        for (String h : headers) {
            tabla.addCell(new Cell()
                .add(new Paragraph(h))
                .setBold().setFontSize(8)
                .setBackgroundColor(DARK_HEADER)
                .setFontColor(ColorConstants.WHITE)
                .setBorder(Border.NO_BORDER)
                .setPadding(6));
        }

        if (movimientos == null || movimientos.isEmpty()) {
            tabla.addCell(new Cell(1, 8)
                .add(new Paragraph("No hay movimientos en el período seleccionado")
                    .setFontColor(TEXT_GRAY).setItalic())
                .setBorder(Border.NO_BORDER).setPadding(12).setTextAlignment(TextAlignment.CENTER));
            document.add(tabla);
            return;
        }

        int idx = 0;
        for (MovimientoCuentaDto m : movimientos) {
            DeviceRgb rowBg = (idx % 2 == 0) ? null : ZEBRA_GRAY;

            // Tipo
            String tipoLabel = tipoLabel(m.getTipo());
            DeviceRgb tipoColor = tipoColor(m.getTipo());
            tabla.addCell(badgeCell(tipoLabel, tipoColor, rowBg));

            // Forma (Crédito/Contado — solo en VENTA)
            if ("VENTA".equals(m.getTipo())) {
                String formaLabel = m.isEsCredito() ? "Crédito" : "Contado";
                DeviceRgb formaColor = m.isEsCredito() ? AMBER_TEXT : GREEN_TEXT;
                tabla.addCell(badgeCell(formaLabel, formaColor, rowBg));
            } else {
                tabla.addCell(dataCell("—", rowBg, TextAlignment.CENTER, false));
            }

            tabla.addCell(dataCell(m.getFecha() != null ? m.getFecha().format(FMT_DIA) : "—", rowBg, TextAlignment.LEFT, false));
            tabla.addCell(dataCell(m.getReferencia() != null ? m.getReferencia() : "—", rowBg, TextAlignment.LEFT, false));
            tabla.addCell(dataCell(m.getDescripcion() != null ? m.getDescripcion() : "—", rowBg, TextAlignment.LEFT, false));

            // Cargo
            boolean tieneCargo = m.getCargo() != null && m.getCargo().compareTo(BigDecimal.ZERO) > 0;
            Cell cargoCell = new Cell()
                .add(new Paragraph(tieneCargo ? formatCOP(m.getCargo()) : "—")
                    .setFontSize(8)
                    .setFontColor(tieneCargo ? RED_TEXT : TEXT_GRAY))
                .setBorder(Border.NO_BORDER).setPadding(5).setTextAlignment(TextAlignment.RIGHT);
            if (rowBg != null) cargoCell.setBackgroundColor(rowBg);
            tabla.addCell(cargoCell);

            // Abono
            boolean tieneAbono = m.getAbono() != null && m.getAbono().compareTo(BigDecimal.ZERO) > 0;
            Cell abonoCell = new Cell()
                .add(new Paragraph(tieneAbono ? formatCOP(m.getAbono()) : "—")
                    .setFontSize(8)
                    .setFontColor(tieneAbono ? GREEN_TEXT : TEXT_GRAY))
                .setBorder(Border.NO_BORDER).setPadding(5).setTextAlignment(TextAlignment.RIGHT);
            if (rowBg != null) abonoCell.setBackgroundColor(rowBg);
            tabla.addCell(abonoCell);

            // Saldo acumulado
            BigDecimal saldo = m.getSaldoAcumulado() != null ? m.getSaldoAcumulado() : BigDecimal.ZERO;
            DeviceRgb saldoColor = saldo.compareTo(BigDecimal.ZERO) > 0 ? RED_TEXT
                    : saldo.compareTo(BigDecimal.ZERO) < 0 ? GREEN_TEXT : TEXT_GRAY;
            Cell saldoCell = new Cell()
                .add(new Paragraph(formatCOP(saldo)).setFontSize(8).setBold().setFontColor(saldoColor))
                .setBorder(Border.NO_BORDER).setPadding(5).setTextAlignment(TextAlignment.RIGHT);
            if (rowBg != null) saldoCell.setBackgroundColor(rowBg);
            tabla.addCell(saldoCell);

            idx++;
        }

        document.add(tabla);
    }

    // ── Footer ────────────────────────────────────────────────────────────────

    private void addFooter(Document document) {
        document.add(new Paragraph("\n\n"));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth()
            .setBorder(new SolidBorder(ZEBRA_GRAY, 0.5f)));
        document.add(new Paragraph("Este documento es una representación informativa del estado de cuenta del cliente.\nImpulsado por Aura POS — Gestión Inteligente")
            .setTextAlignment(TextAlignment.CENTER).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(8));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cell dataCell(String value, DeviceRgb bg, TextAlignment align, boolean bold) {
        Paragraph p = new Paragraph(value != null ? value : "—").setFontSize(8);
        if (bold) p.setBold();
        Cell cell = new Cell().add(p).setBorder(Border.NO_BORDER).setPadding(5).setTextAlignment(align);
        if (bg != null) cell.setBackgroundColor(bg);
        return cell;
    }

    private Cell badgeCell(String label, DeviceRgb textColor, DeviceRgb bg) {
        Cell cell = new Cell()
            .add(new Paragraph(label).setFontSize(7).setBold().setFontColor(textColor))
            .setBorder(Border.NO_BORDER).setPadding(5).setTextAlignment(TextAlignment.CENTER);
        if (bg != null) cell.setBackgroundColor(bg);
        return cell;
    }

    private String tipoLabel(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "VENTA"        -> "Venta";
            case "ABONO"        -> "Abono";
            case "NOTA_CREDITO" -> "Nota Créd.";
            case "NOTA_DEBITO"  -> "Nota Déb.";
            default             -> tipo != null ? tipo : "—";
        };
    }

    private DeviceRgb tipoColor(String tipo) {
        return switch (tipo != null ? tipo : "") {
            case "VENTA"        -> new DeviceRgb(37, 99, 235);   // blue
            case "ABONO"        -> GREEN_TEXT;
            case "NOTA_CREDITO" -> AMBER_TEXT;
            case "NOTA_DEBITO"  -> RED_TEXT;
            default             -> TEXT_GRAY;
        };
    }

    private String formatCOP(BigDecimal v) {
        if (v == null) return "$ 0";
        return String.format("$ %,.0f", v).replace(",", ".");
    }

    private String buildRangoLabel(String desde, String hasta) {
        if (desde != null && hasta != null) return "Del " + desde + " al " + hasta;
        if (desde != null) return "Desde " + desde;
        if (hasta != null) return "Hasta " + hasta;
        return "Historial completo";
    }
}
