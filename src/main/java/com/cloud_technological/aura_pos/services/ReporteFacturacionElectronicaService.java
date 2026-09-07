package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.entity.FacturaEntity;
import com.cloud_technological.aura_pos.entity.NotaElectronicaEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.facturacion.FacturaJPARepository;
import com.cloud_technological.aura_pos.repositories.ventas.NotaElectronicaJPARepository;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Reportes exportables a Excel de la facturación electrónica: facturas de venta
 * y notas crédito/débito, por rango de fechas.
 *
 * <p>El Excel se arma localmente con Apache POI a partir de nuestra propia base
 * ({@code factura} y {@code nota_electronica}), no se pide al panel de Factus:
 * ese host exige sesión de usuario y no acepta el bearer de la API (ver la nota
 * en {@code FactusNotaService}).
 */
@Slf4j
@Service
public class ReporteFacturacionElectronicaService {

    /** Azul primario de la marca. */
    private static final byte[] AZUL       = {(byte) 0x25, (byte) 0x63, (byte) 0xEB};
    private static final byte[] AZUL_SUAVE = {(byte) 0xDB, (byte) 0xEA, (byte) 0xFE};
    private static final byte[] GRIS_ZEBRA = {(byte) 0xF9, (byte) 0xFA, (byte) 0xFB};
    private static final byte[] BLANCO     = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] GRIS_TEXTO = {(byte) 0x64, (byte) 0x74, (byte) 0x8B};

    private static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter F_FECHA      = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String[] COLS_FACTURAS = {
        "N° Factura", "Fecha emisión", "Cliente", "Identificación", "Forma de pago",
        "Base excluida", "Base 5%", "IVA 5%", "Base 19%", "IVA 19%",
        "Descuento", "IVA total", "Total", "CUFE", "Estado DIAN", "Ambiente"
    };

    private static final String[] COLS_NOTAS = {
        "Tipo", "N° Nota", "Fecha", "Factura ref. (bill_id)", "Reference code",
        "CUDE", "Base gravable", "IVA", "Total", "Estado"
    };

    private final FacturaJPARepository facturaRepository;
    private final NotaElectronicaJPARepository notaRepository;
    private final SecurityUtils securityUtils;

    public ReporteFacturacionElectronicaService(FacturaJPARepository facturaRepository,
                                                NotaElectronicaJPARepository notaRepository,
                                                SecurityUtils securityUtils) {
        this.facturaRepository = facturaRepository;
        this.notaRepository = notaRepository;
        this.securityUtils = securityUtils;
    }

    // ── FACTURAS ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] excelFacturas(LocalDate desde, LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        validarRango(desde, hasta);

        List<FacturaEntity> facturas = facturaRepository.findParaReporte(
                empresaId, desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay());
        log.info("[Reporte FE] Empresa {} — {} facturas entre {} y {}",
                empresaId, facturas.size(), desde, hasta);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Estilos e = new Estilos(wb);
            XSSFSheet ws = wb.createSheet("Facturas");
            int fila = encabezado(ws, e, "REPORTE DE FACTURAS ELECTRÓNICAS", desde, hasta, COLS_FACTURAS);

            BigDecimal totDescuento = BigDecimal.ZERO;
            BigDecimal totIva = BigDecimal.ZERO;
            BigDecimal totTotal = BigDecimal.ZERO;

            for (int i = 0; i < facturas.size(); i++) {
                FacturaEntity f = facturas.get(i);
                XSSFCellStyle st = (i % 2 == 0) ? e.data : e.dataAlt;
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                BigDecimal ivaTotal = nz(f.getIvaValor5()).add(nz(f.getIvaValor19()));
                BigDecimal descuento = nz(f.getDescuento());
                BigDecimal total = nz(f.getValor());

                int c = 0;
                texto(r, c++, numeroFactura(f), st);
                texto(r, c++, f.getFechaHoraEmision() != null
                        ? f.getFechaHoraEmision().format(F_FECHA_HORA) : "", st);
                texto(r, c++, nombreCliente(f.getVenta()), st);
                texto(r, c++, identificacion(f.getVenta()), st);
                texto(r, c++, f.getMetodoPago(), st);
                numero(r, c++, nz(f.getIvaBase0()), val);
                numero(r, c++, nz(f.getIvaBase5()), val);
                numero(r, c++, nz(f.getIvaValor5()), val);
                numero(r, c++, nz(f.getIvaBase19()), val);
                numero(r, c++, nz(f.getIvaValor19()), val);
                numero(r, c++, descuento, val);
                numero(r, c++, ivaTotal, val);
                numero(r, c++, total, val);
                texto(r, c++, f.getCufe(), st);
                texto(r, c++, f.getEstadoDian(), st);
                texto(r, c, f.getTipoAmbiente(), st);

                totDescuento = totDescuento.add(descuento);
                totIva = totIva.add(ivaTotal);
                totTotal = totTotal.add(total);
            }

            if (facturas.isEmpty()) {
                filaVacia(ws, e, fila, COLS_FACTURAS.length);
            } else {
                Row rt = ws.createRow(fila);
                texto(rt, 0, "TOTALES (" + facturas.size() + ")", e.total);
                for (int i = 1; i <= 9; i++) texto(rt, i, "", e.total);
                numero(rt, 10, totDescuento, e.valorTotal);
                numero(rt, 11, totIva, e.valorTotal);
                numero(rt, 12, totTotal, e.valorTotal);
                for (int i = 13; i < COLS_FACTURAS.length; i++) texto(rt, i, "", e.total);
            }

            autoAjustar(ws, COLS_FACTURAS.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte FE] Error generando el Excel de facturas", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de facturas: " + ex.getMessage());
        }
    }

    // ── NOTAS CRÉDITO / DÉBITO ────────────────────────────────

    /**
     * @param tipo {@code CREDITO}, {@code DEBITO} o {@code null} para traer ambas.
     */
    @Transactional(readOnly = true)
    public byte[] excelNotas(LocalDate desde, LocalDate hasta, String tipo) {
        Integer empresaId = securityUtils.getEmpresaId();
        validarRango(desde, hasta);
        String t = normalizarTipo(tipo);

        LocalDateTime ini = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        List<NotaElectronicaEntity> notas = (t == null)
                ? notaRepository
                    .findByEmpresaIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        empresaId, ini, fin)
                : notaRepository
                    .findByEmpresaIdAndTipoAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                        empresaId, t, ini, fin);

        log.info("[Reporte FE] Empresa {} — {} notas ({}) entre {} y {}",
                empresaId, notas.size(), t != null ? t : "CREDITO+DEBITO", desde, hasta);

        String titulo = "REPORTE DE NOTAS " + switch (t == null ? "" : t) {
            case "CREDITO" -> "CRÉDITO";
            case "DEBITO"  -> "DÉBITO";
            default        -> "CRÉDITO Y DÉBITO";
        };

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Estilos e = new Estilos(wb);
            XSSFSheet ws = wb.createSheet("Notas");
            int fila = encabezado(ws, e, titulo, desde, hasta, COLS_NOTAS);

            BigDecimal totBase = BigDecimal.ZERO;
            BigDecimal totIva = BigDecimal.ZERO;
            BigDecimal totTotal = BigDecimal.ZERO;

            for (int i = 0; i < notas.size(); i++) {
                NotaElectronicaEntity n = notas.get(i);
                XSSFCellStyle st = (i % 2 == 0) ? e.data : e.dataAlt;
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                texto(r, c++, "CREDITO".equals(n.getTipo()) ? "CRÉDITO" : "DÉBITO", st);
                texto(r, c++, n.getNumero(), st);
                texto(r, c++, n.getCreatedAt() != null ? n.getCreatedAt().format(F_FECHA_HORA) : "", st);
                texto(r, c++, n.getBillId() != null ? String.valueOf(n.getBillId()) : "", st);
                texto(r, c++, n.getReferenceCode(), st);
                texto(r, c++, n.getCude(), st);
                numero(r, c++, nz(n.getBaseGravable()), val);
                numero(r, c++, nz(n.getIva()), val);
                numero(r, c++, nz(n.getTotal()), val);
                texto(r, c, n.getEstado(), st);

                totBase = totBase.add(nz(n.getBaseGravable()));
                totIva = totIva.add(nz(n.getIva()));
                totTotal = totTotal.add(nz(n.getTotal()));
            }

            if (notas.isEmpty()) {
                filaVacia(ws, e, fila, COLS_NOTAS.length);
            } else {
                Row rt = ws.createRow(fila);
                texto(rt, 0, "TOTALES (" + notas.size() + ")", e.total);
                for (int i = 1; i <= 5; i++) texto(rt, i, "", e.total);
                numero(rt, 6, totBase, e.valorTotal);
                numero(rt, 7, totIva, e.valorTotal);
                numero(rt, 8, totTotal, e.valorTotal);
                texto(rt, 9, "", e.total);
            }

            autoAjustar(ws, COLS_NOTAS.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte FE] Error generando el Excel de notas", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de notas: " + ex.getMessage());
        }
    }

    // ── Armado de la hoja ─────────────────────────────────────

    /** Escribe título, rango y cabecera; devuelve la fila donde empiezan los datos. */
    private int encabezado(XSSFSheet ws, Estilos e, String titulo,
                           LocalDate desde, LocalDate hasta, String[] cols) {
        int lastCol = cols.length - 1;

        Row r0 = ws.createRow(0);
        r0.setHeightInPoints(30);
        Cell t = r0.createCell(0);
        t.setCellValue(titulo + " — AURA POS");
        t.setCellStyle(e.titulo);
        ws.addMergedRegion(new CellRangeAddress(0, 0, 0, lastCol));

        Row r1 = ws.createRow(1);
        r1.setHeightInPoints(16);
        Cell sub = r1.createCell(0);
        sub.setCellValue("Del " + desde.format(F_FECHA) + " al " + hasta.format(F_FECHA)
                + "  ·  Generado el " + LocalDate.now().format(F_FECHA));
        sub.setCellStyle(e.nota);
        ws.addMergedRegion(new CellRangeAddress(1, 1, 0, lastCol));

        ws.createRow(2);

        Row rh = ws.createRow(3);
        rh.setHeightInPoints(22);
        for (int i = 0; i < cols.length; i++) {
            Cell c = rh.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(e.header);
        }
        ws.createFreezePane(0, 4);
        return 4;
    }

    private void filaVacia(XSSFSheet ws, Estilos e, int fila, int cols) {
        Row r = ws.createRow(fila);
        Cell c = r.createCell(0);
        c.setCellValue("No hay documentos en el rango seleccionado.");
        c.setCellStyle(e.nota);
        ws.addMergedRegion(new CellRangeAddress(fila, fila, 0, cols - 1));
    }

    private void autoAjustar(XSSFSheet ws, int cols) {
        // Se mide el texto, no la fuente: autoSizeColumn usa AWT y en el
        // servidor de producción no están las librerías nativas de fuentes,
        // así que el reporte moría con UnsatisfiedLinkError sobre libfreetype.
        com.cloud_technological.aura_pos.utils.ExcelAnchoColumnas.ajustar(ws, cols);
    }

    private void texto(Row r, int col, String valor, XSSFCellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor != null ? valor : "");
        c.setCellStyle(estilo);
    }

    private void numero(Row r, int col, BigDecimal valor, XSSFCellStyle estilo) {
        Cell c = r.createCell(col);
        c.setCellValue(valor != null ? valor.doubleValue() : 0d);
        c.setCellStyle(estilo);
    }

    // ── Datos ─────────────────────────────────────────────────

    private String numeroFactura(FacturaEntity f) {
        String prefijo = f.getPrefijo() != null ? f.getPrefijo() : "";
        return prefijo + (f.getConsecutivo() != null ? f.getConsecutivo() : "");
    }

    /**
     * Razón social si la hay; si no, el nombre desagregado (V97). Los terceros
     * viejos solo tienen los campos {@code nombres}/{@code apellidos}, así que
     * se usan como respaldo aunque estén deprecados.
     */
    @SuppressWarnings("deprecation")
    private String nombreCliente(VentaEntity venta) {
        TerceroEntity cliente = venta != null ? venta.getCliente() : null;
        if (cliente == null) return "Consumidor final";
        if (esTexto(cliente.getRazonSocial())) return cliente.getRazonSocial();

        String nombre = String.join(" ",
                nvl(cliente.getNombre1()), nvl(cliente.getNombre2()),
                nvl(cliente.getApellido1()), nvl(cliente.getApellido2()))
                .replaceAll("\\s+", " ").trim();
        if (esTexto(nombre)) return nombre;

        nombre = (nvl(cliente.getNombres()) + " " + nvl(cliente.getApellidos())).trim();
        return esTexto(nombre) ? nombre : "Consumidor final";
    }

    private String identificacion(VentaEntity venta) {
        TerceroEntity cliente = venta != null ? venta.getCliente() : null;
        if (cliente == null || !esTexto(cliente.getNumeroDocumento())) return "";
        String tipo = esTexto(cliente.getTipoDocumento()) ? cliente.getTipoDocumento() + " " : "";
        return tipo + cliente.getNumeroDocumento();
    }

    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Debe indicar la fecha inicial y la final del reporte.");
        }
        if (hasta.isBefore(desde)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la inicial.");
        }
    }

    private String normalizarTipo(String tipo) {
        if (!esTexto(tipo)) return null;
        String t = tipo.trim().toUpperCase();
        if (t.startsWith("CR")) return "CREDITO";
        if (t.startsWith("DE") || t.startsWith("DÉ")) return "DEBITO";
        if ("AMBAS".equals(t) || "TODAS".equals(t)) return null;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Tipo de nota no válido: " + tipo + ". Use CREDITO, DEBITO o deje vacío para ambas.");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nvl(String v) {
        return v != null ? v : "";
    }

    private static boolean esTexto(String v) {
        return v != null && !v.isBlank();
    }

    // ── Estilos ───────────────────────────────────────────────

    /** Estilos de la hoja; se crean una sola vez por libro (POI los cachea por índice). */
    private static final class Estilos {
        final XSSFCellStyle titulo;
        final XSSFCellStyle nota;
        final XSSFCellStyle header;
        final XSSFCellStyle data;
        final XSSFCellStyle dataAlt;
        final XSSFCellStyle total;
        final XSSFCellStyle valor;
        final XSSFCellStyle valorAlt;
        final XSSFCellStyle valorTotal;

        Estilos(XSSFWorkbook wb) {
            this.titulo = titulo(wb);
            this.nota = nota(wb);
            this.header = header(wb);
            this.data = data(wb, BLANCO);
            this.dataAlt = data(wb, GRIS_ZEBRA);
            this.total = total(wb);

            // Separador de miles: 1.000.000 en vez de 1000000. Se usa el
            // patrón #,##0 y no puntos literales porque Excel dibuja el
            // separador según la región de quien abre el archivo — y escribirlo
            // a mano produciría texto que no se puede sumar.
            short f = wb.createDataFormat().getFormat("#,##0");
            this.valor = conFormato(data(wb, BLANCO), f);
            this.valorAlt = conFormato(data(wb, GRIS_ZEBRA), f);
            this.valorTotal = conFormato(total(wb), f);
        }

        /** Los números van a la derecha: así se comparan las magnitudes. */
        private static XSSFCellStyle conFormato(XSSFCellStyle s, short formato) {
            s.setDataFormat(formato);
            s.setAlignment(HorizontalAlignment.RIGHT);
            return s;
        }

        /** El estilo numérico de la fila i, alternando el fondo. */
        XSSFCellStyle filaValor(int i) {
            return (i % 2 == 0) ? valor : valorAlt;
        }

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
}
