package com.cloud_technological.aura_pos.services.implementations;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.entity.AbonoPagarEntity;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.CuentaPagarEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.AbonoPagarJPARepository;
import com.cloud_technological.aura_pos.repositories.cuentas_pagar.CuentaPagarJPARepository;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.cloud_technological.aura_pos.utils.GlobalException;
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
public class CuentaPdfService {

    private final CuentaCobrarJPARepository cuentaCobrarRepository;
    private final CuentaPagarJPARepository cuentaPagarRepository;
    private final AbonoCobrarJPARepository abonoCobrarRepository;
    private final AbonoPagarJPARepository abonoPagarRepository;
    private final IEmpresaService empresaService;

    // Modern Color Palette
    private static final DeviceRgb ACCENT_BLUE = new DeviceRgb(25, 118, 210);
    private static final DeviceRgb DARK_HEADER = new DeviceRgb(30, 41, 59); // Slate-800
    private static final DeviceRgb LIGHT_BG = new DeviceRgb(248, 250, 252); // Slate-50
    private static final DeviceRgb TEXT_GRAY = new DeviceRgb(100, 116, 139); // Slate-500
    private static final DeviceRgb ZEBRA_GRAY = new DeviceRgb(249, 250, 251);

    public CuentaPdfService(CuentaCobrarJPARepository cuentaCobrarRepository,
                           CuentaPagarJPARepository cuentaPagarRepository,
                           AbonoCobrarJPARepository abonoCobrarRepository,
                           AbonoPagarJPARepository abonoPagarRepository,
                           IEmpresaService empresaService) {
        this.cuentaCobrarRepository = cuentaCobrarRepository;
        this.cuentaPagarRepository = cuentaPagarRepository;
        this.abonoCobrarRepository = abonoCobrarRepository;
        this.abonoPagarRepository = abonoPagarRepository;
        this.empresaService = empresaService;
    }

    public byte[] generarReciboCajaCobrar(Long abonoId, Integer empresaId) {
        AbonoCobrarEntity abono = abonoCobrarRepository.findById(abonoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Abono no encontrado"));

        if (!abono.getCuentaCobrar().getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este abono");
        }

        return generarPdfRecibo("RECIBO DE CAJA", "INGRESO DE DINERO",
                                getNombreTercero(abono.getCuentaCobrar().getTercero()),
                                abono.getCuentaCobrar().getTercero().getNumeroDocumento(),
                                abono.getMonto(),
                                abono.getFechaPago(),
                                abono.getMetodoPago(),
                                abono.getReferencia(),
                                abono.getCuentaCobrar().getNumeroCuenta(),
                                empresaId);
    }

    public byte[] generarReciboCajaPagar(Long abonoId, Integer empresaId) {
        AbonoPagarEntity abono = abonoPagarRepository.findById(abonoId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Abono no encontrado"));

        if (!abono.getCuentaPagar().getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a este abono");
        }

        return generarPdfRecibo("COMPROBANTE DE EGRESO", "SALIDA DE DINERO",
                                getNombreTercero(abono.getCuentaPagar().getTercero()),
                                abono.getCuentaPagar().getTercero().getNumeroDocumento(),
                                abono.getMonto(),
                                abono.getFechaPago(),
                                abono.getMetodoPago(),
                                abono.getReferencia(),
                                abono.getCuentaPagar().getNumeroCuenta(),
                                empresaId);
    }

    public byte[] generarFacturaCuentaCobrar(Long cuentaId, Integer empresaId) {
        CuentaCobrarEntity cuenta = cuentaCobrarRepository.findById(cuentaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        if (!cuenta.getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a esta cuenta");
        }

        return generarPdfCuenta("ESTADO DE CUENTA", "POR COBRAR",
                                getNombreTercero(cuenta.getTercero()),
                                cuenta.getTercero().getNumeroDocumento(),
                                cuenta.getNumeroCuenta(),
                                cuenta.getFechaEmision(),
                                cuenta.getFechaVencimiento(),
                                cuenta.getTotalDeuda(),
                                cuenta.getTotalAbonado(),
                                cuenta.getSaldoPendiente(),
                                cuenta.getEstado(),
                                cuenta.getAbonos(),
                                empresaId);
    }

    public byte[] generarFacturaCuentaPagar(Long cuentaId, Integer empresaId) {
        CuentaPagarEntity cuenta = cuentaPagarRepository.findById(cuentaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        if (!cuenta.getEmpresa().getId().equals(empresaId)) {
            throw new GlobalException(HttpStatus.FORBIDDEN, "No tiene permisos para acceder a esta cuenta");
        }

        return generarPdfCuenta("ESTADO DE CUENTA", "POR PAGAR",
                                getNombreTercero(cuenta.getTercero()),
                                cuenta.getTercero().getNumeroDocumento(),
                                cuenta.getNumeroCuenta(),
                                cuenta.getFechaEmision(),
                                cuenta.getFechaVencimiento(),
                                cuenta.getTotalDeuda(),
                                cuenta.getTotalAbonado(),
                                cuenta.getSaldoPendiente(),
                                cuenta.getEstado(),
                                cuenta.getAbonos(),
                                empresaId);
    }

    private void addModernHeader(Document document, Integer empresaId, String title, String subtitle, String number) {
        EmpresaDto empresa = empresaService.obtenerEmpresaActual(empresaId, null, null);

        // Top banner
        Table topTable = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
        Cell bannerCell = new Cell().setBackgroundColor(DARK_HEADER).setPadding(10).setBorder(Border.NO_BORDER);
        
        Table innerTop = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth().setBorder(Border.NO_BORDER);
        innerTop.addCell(new Cell().add(new Paragraph(title).setBold().setFontSize(16).setFontColor(ColorConstants.WHITE)).setBorder(Border.NO_BORDER));
        if (number != null) {
            innerTop.addCell(new Cell().add(new Paragraph("N° " + number).setBold().setFontSize(16).setFontColor(ColorConstants.WHITE)).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER));
        } else {
            innerTop.addCell(new Cell().add(new Paragraph(subtitle).setFontSize(12).setFontColor(ColorConstants.LIGHT_GRAY)).setTextAlignment(TextAlignment.RIGHT).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.BOTTOM));
        }
        bannerCell.add(innerTop);
        topTable.addCell(bannerCell);
        document.add(topTable);

        document.add(new Paragraph("\n").setFontSize(10));

        // Company Logo and Info Section
        Table companyTable = new Table(UnitValue.createPercentArray(new float[]{25, 75})).useAllAvailableWidth();
        companyTable.setBorder(Border.NO_BORDER);

        // Logo
        Cell logoCell = new Cell().setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE);
        if (empresa.getLogoUrl() != null && !empresa.getLogoUrl().isBlank()) {
            try {
                Image logo = new Image(ImageDataFactory.create(empresa.getLogoUrl()));
                logo.setMaxWidth(100);
                logoCell.add(logo);
            } catch (Exception e) {
                log.warn("Logo load fail: {}", e.getMessage());
            }
        }
        companyTable.addCell(logoCell);

        // Company Details
        Cell infoCell = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(15).setVerticalAlignment(VerticalAlignment.MIDDLE);
        infoCell.add(new Paragraph(empresa.getRazonSocial()).setBold().setFontSize(14).setFontColor(DARK_HEADER));
        infoCell.add(new Paragraph("NIT: " + empresa.getNit() + (empresa.getDv() != null ? "-" + empresa.getDv() : ""))
                .setFontSize(10).setFontColor(TEXT_GRAY).setMarginTop(-5));
        
        String contact = (empresa.getDireccion() != null ? empresa.getDireccion() : "") + 
                         (empresa.getMunicipio() != null ? " - " + empresa.getMunicipio() : "");
        if (!contact.isBlank()) infoCell.add(new Paragraph(contact).setFontSize(10).setFontColor(TEXT_GRAY).setMarginTop(-2));
        if (empresa.getTelefono() != null) infoCell.add(new Paragraph("Tel: " + empresa.getTelefono()).setFontSize(10).setFontColor(TEXT_GRAY).setMarginTop(-2));
        
        companyTable.addCell(infoCell);
        document.add(companyTable);
        
        document.add(new Paragraph("\n").setFontSize(5));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth().setBorder(new SolidBorder(LIGHT_BG, 1)));
        document.add(new Paragraph("\n").setFontSize(10));
    }

    private byte[] generarPdfRecibo(String titulo, String subtitulo, String tercero, String documento, BigDecimal monto, 
                                   LocalDateTime fecha, String metodo, String referencia, String nroCuenta, Integer empresaId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addModernHeader(document, empresaId, titulo, subtitulo, null);

            // Receipt "Card"
            Table card = new Table(UnitValue.createPercentArray(new float[]{100})).useAllAvailableWidth();
            Cell cardCell = new Cell().setBackgroundColor(LIGHT_BG).setPadding(15).setBorder(new SolidBorder(ZEBRA_GRAY, 1));
            
            Table contentTable = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth().setBorder(Border.NO_BORDER);
            
            addCardRow(contentTable, "Fecha de Emisión:", fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            addCardRow(contentTable, "Tercero:", tercero + " (" + documento + ")");
            addCardRow(contentTable, "Método de Pago:", metodo != null ? metodo.toUpperCase() : "N/A");
            addCardRow(contentTable, "Referencia:", referencia != null ? referencia : "N/A");
            addCardRow(contentTable, "Relacionado a:", "Cuenta N° " + nroCuenta);
            
            cardCell.add(contentTable);
            card.addCell(cardCell);
            document.add(card);

            // Amount Highlight
            document.add(new Paragraph("\n").setFontSize(10));
            Table amountTable = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            amountTable.addCell(new Cell().add(new Paragraph("TOTAL RECIBIDO").setBold().setFontSize(12)).setBorder(Border.NO_BORDER).setVerticalAlignment(VerticalAlignment.MIDDLE));
            amountTable.addCell(new Cell().add(new Paragraph(formatCOP(monto)).setBold().setFontSize(22).setFontColor(ACCENT_BLUE)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            document.add(amountTable);

            // Signatures
            document.add(new Paragraph("\n\n\n\n"));
            Table signTable = new Table(UnitValue.createPercentArray(new float[]{40, 20, 40})).useAllAvailableWidth();
            signTable.addCell(new Cell().add(new Paragraph("__________________________\nElaborado por")).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER).setFontSize(10).setFontColor(TEXT_GRAY));
            signTable.addCell(new Cell().setBorder(Border.NO_BORDER));
            signTable.addCell(new Cell().add(new Paragraph("__________________________\nFirma Recibido/Autorizado")).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.CENTER).setFontSize(10).setFontColor(TEXT_GRAY));
            document.add(signTable);

            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error Receipt PDF: {}", e.getMessage());
            throw new RuntimeException("Error Receipt PDF", e);
        }
    }

    private byte[] generarPdfCuenta(String titulo, String subtitulo, String tercero, String documento, String nroCuenta,
                                   LocalDateTime emision, LocalDateTime vencimiento, BigDecimal total,
                                   BigDecimal abonado, BigDecimal saldo, String estado, List<?> abonos, Integer empresaId) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf);

            addModernHeader(document, empresaId, titulo, subtitulo, nroCuenta);

            // Info Section (Two Columns)
            Table infoGrid = new Table(UnitValue.createPercentArray(new float[]{50, 50})).useAllAvailableWidth();
            infoGrid.setBorder(Border.NO_BORDER).setMarginBottom(15);
            
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10).setBackgroundColor(LIGHT_BG);
            leftCell.add(new Paragraph("INFORMACIÓN DEL TERCERO").setBold().setFontSize(9).setFontColor(ACCENT_BLUE));
            leftCell.add(new Paragraph(tercero).setBold().setFontSize(11).setFontColor(DARK_HEADER).setMarginTop(2));
            leftCell.add(new Paragraph("Documento: " + documento).setFontSize(10).setFontColor(TEXT_GRAY));
            infoGrid.addCell(leftCell);

            Cell rightCell = new Cell().setBorder(Border.NO_BORDER).setPadding(10).setBackgroundColor(LIGHT_BG);
            rightCell.add(new Paragraph("FECHAS CLAVE").setBold().setFontSize(9).setFontColor(ACCENT_BLUE));
            rightCell.add(new Paragraph("Emisión: " + emision.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))).setFontSize(10).setFontColor(DARK_HEADER).setMarginTop(2));
            rightCell.add(new Paragraph("Vencimiento: " + vencimiento.format(DateTimeFormatter.ofPattern("dd MMMM yyyy"))).setFontSize(10).setFontColor(DARK_HEADER));
            infoGrid.addCell(rightCell);
            
            document.add(infoGrid);

            // Summary Table
            Table summaryTable = new Table(UnitValue.createPercentArray(new float[]{70, 30})).useAllAvailableWidth();
            summaryTable.addCell(new Cell().add(new Paragraph("TOTAL DE LA DEUDA")).setBold().setBackgroundColor(ZEBRA_GRAY).setBorder(Border.NO_BORDER).setPadding(8));
            summaryTable.addCell(new Cell().add(new Paragraph(formatCOP(total))).setTextAlignment(TextAlignment.RIGHT).setBold().setBackgroundColor(ZEBRA_GRAY).setBorder(Border.NO_BORDER).setPadding(8));
            
            summaryTable.addCell(new Cell().add(new Paragraph("TOTAL ABONADO")).setPadding(8).setBorder(Border.NO_BORDER));
            summaryTable.addCell(new Cell().add(new Paragraph(formatCOP(abonado))).setTextAlignment(TextAlignment.RIGHT).setPadding(8).setBorder(Border.NO_BORDER));
            
            Cell balanceLabel = new Cell().add(new Paragraph("SALDO PENDIENTE")).setBold().setFontSize(12).setBackgroundColor(ACCENT_BLUE).setFontColor(ColorConstants.WHITE).setPadding(10).setBorder(Border.NO_BORDER);
            Cell balanceValue = new Cell().add(new Paragraph(formatCOP(saldo))).setTextAlignment(TextAlignment.RIGHT).setBold().setFontSize(14).setBackgroundColor(ACCENT_BLUE).setFontColor(ColorConstants.WHITE).setPadding(10).setBorder(Border.NO_BORDER);
            summaryTable.addCell(balanceLabel);
            summaryTable.addCell(balanceValue);
            
            document.add(summaryTable);
            document.add(new Paragraph("Estado Actual: " + estado.toUpperCase()).setFontSize(8).setItalic().setFontColor(TEXT_GRAY).setMarginTop(5));

            // Payments Detail
            if (abonos != null && !abonos.isEmpty()) {
                document.add(new Paragraph("\nHISTORIAL DE PAGOS").setBold().setFontSize(11).setFontColor(DARK_HEADER).setMarginTop(20));
                Table paymentsTable = new Table(UnitValue.createPercentArray(new float[]{15, 35, 20, 30})).useAllAvailableWidth();
                paymentsTable.setBorder(Border.NO_BORDER);

                // Header
                paymentsTable.addCell(new Cell().add(new Paragraph("Fecha")).setBold().setBackgroundColor(DARK_HEADER).setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(6));
                paymentsTable.addCell(new Cell().add(new Paragraph("Método / Referencia")).setBold().setBackgroundColor(DARK_HEADER).setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(6));
                paymentsTable.addCell(new Cell().add(new Paragraph("Entidad")).setBold().setBackgroundColor(DARK_HEADER).setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(6));
                paymentsTable.addCell(new Cell().add(new Paragraph("Monto")).setBold().setBackgroundColor(DARK_HEADER).setFontColor(ColorConstants.WHITE).setBorder(Border.NO_BORDER).setPadding(6).setTextAlignment(TextAlignment.RIGHT));

                int count = 0;
                for (Object rawAbono : abonos) {
                    com.itextpdf.kernel.colors.Color rowBg = (count % 2 == 0) ? com.itextpdf.kernel.colors.ColorConstants.WHITE : ZEBRA_GRAY;
                    if (rawAbono instanceof AbonoCobrarEntity abono) {
                        addPaymentRow(paymentsTable, abono.getFechaPago().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), 
                                     abono.getMetodoPago() + (abono.getReferencia() != null ? " (" + abono.getReferencia() + ")" : ""), 
                                     "N/A", formatCOP(abono.getMonto()), rowBg);
                    } else if (rawAbono instanceof AbonoPagarEntity abono) {
                        addPaymentRow(paymentsTable, abono.getFechaPago().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), 
                                     abono.getMetodoPago() + (abono.getReferencia() != null ? " (" + abono.getReferencia() + ")" : ""), 
                                     abono.getBanco() != null ? abono.getBanco() : "Punto de Pago", formatCOP(abono.getMonto()), rowBg);
                    }
                    count++;
                }
                document.add(paymentsTable);
            }

            addFooter(document);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error Account PDF: {}", e.getMessage());
            throw new RuntimeException("Error Account PDF", e);
        }
    }

    private void addCardRow(Table table, String label, String value) {
        table.addCell(new Cell().add(new Paragraph(label).setBold().setFontSize(10).setFontColor(TEXT_GRAY)).setBorder(Border.NO_BORDER).setPaddingBottom(5));
        table.addCell(new Cell().add(new Paragraph(value).setFontSize(10).setFontColor(DARK_HEADER)).setBorder(Border.NO_BORDER).setPaddingBottom(5));
    }

    private void addPaymentRow(Table table, String date, String ref, String entity, String amount, com.itextpdf.kernel.colors.Color bg) {
        table.addCell(new Cell().add(new Paragraph(date)).setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(6).setFontSize(9));
        table.addCell(new Cell().add(new Paragraph(ref)).setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(6).setFontSize(9));
        table.addCell(new Cell().add(new Paragraph(entity)).setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(6).setFontSize(9));
        table.addCell(new Cell().add(new Paragraph(amount)).setBackgroundColor(bg).setBorder(Border.NO_BORDER).setPadding(6).setFontSize(9).setBold().setTextAlignment(TextAlignment.RIGHT));
    }
    
    private void addFooter(Document document) {
        document.add(new Paragraph("\n\n"));
        document.add(new Table(UnitValue.createPercentArray(1)).useAllAvailableWidth().setBorder(new SolidBorder(ZEBRA_GRAY, 0.5f)));
        Paragraph footer = new Paragraph("Este documento es una representación informativa de su estado de cuenta / recibo.\nImpulsado por Aura POS - Gestión Inteligente")
                .setTextAlignment(TextAlignment.CENTER).setFontSize(8).setFontColor(TEXT_GRAY).setMarginTop(10);
        document.add(footer);
    }

    private String formatCOP(BigDecimal v) {
        if (v == null) return "$ 0";
        return String.format("$ %,.0f", v).replace(",", ".");
    }

    private String getNombreTercero(com.cloud_technological.aura_pos.entity.TerceroEntity t) {
        if (t == null) return "N/A";
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        String n = (t.getNombres() != null ? t.getNombres() : "") + " " + (t.getApellidos() != null ? t.getApellidos() : "");
        return n.trim().isEmpty() ? "N/A" : n.trim();
    }
}
