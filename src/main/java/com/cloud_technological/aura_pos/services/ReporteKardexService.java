package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
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

import com.cloud_technological.aura_pos.dto.kardex.KardexDetalleLineaDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteFiltroDto;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteLineaDto;
import com.cloud_technological.aura_pos.repositories.kardex.KardexQueryRepository;
import com.cloud_technological.aura_pos.utils.ExcelEstilos;
import com.cloud_technological.aura_pos.utils.SecurityUtils;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;

import lombok.extern.slf4j.Slf4j;

/**
 * Exporta el movimiento de inventario a Excel: el resumen por producto y el
 * kardex clásico de un producto.
 *
 * <p>Las cantidades salen de {@code saldo_nuevo - saldo_anterior}, nunca de
 * {@code SUM(cantidad)}: el reconteo guarda el valor absoluto y pone el sentido
 * en el nombre del tipo, así que sumar la columna daría mal en cuanto haya un
 * ajuste de inventario en el rango. La consulta ya lo resuelve; esto solo lo
 * escribe.
 */
@Slf4j
@Service
public class ReporteKardexService {

    /** Azul primario de la marca. */
    private static final byte[] BLANCO     = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter F_FECHA      = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Tope de filas por exportación: el Excel es un reporte, no un volcado. */
    private static final int MAX_FILAS = 20_000;

    private static final String[] COLS_RESUMEN = {
        "Producto", "SKU", "Categoría", "Marca", "Sucursal", "Lote",
        "Saldo inicial", "Entradas", "Salidas", "Variación", "Saldo final",
        "Valor entradas", "Valor salidas",
        "Compras", "Ventas", "Devoluciones", "Mermas", "Obsequios",
        "Traslados", "Anulaciones", "Reconteos", "Otros", "N° movs."
    };

    private static final String[] COLS_DETALLE = {
        "Fecha", "Tipo", "Grupo", "Documento", "Sucursal", "Lote",
        "Saldo anterior", "Movimiento", "Saldo nuevo", "Costo unitario", "Valor"
    };

    /** El PDF va apaisado y sin la columna "Grupo": el tipo ya la implica. */
    private static final String[] COLS_DETALLE_PDF = {
        "Fecha", "Tipo", "Documento", "Sucursal", "Lote",
        "Saldo ant.", "Movimiento", "Saldo nuevo", "Costo unit.", "Valor"
    };

    private static final DeviceRgb AZUL_PDF   = new DeviceRgb(37, 99, 235);
    private static final DeviceRgb BLANCO_PDF = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb ZEBRA_PDF  = new DeviceRgb(249, 250, 251);

    private final KardexQueryRepository kardexRepository;
    private final SecurityUtils securityUtils;

    public ReporteKardexService(KardexQueryRepository kardexRepository,
                                SecurityUtils securityUtils) {
        this.kardexRepository = kardexRepository;
        this.securityUtils = securityUtils;
    }

    // ── Resumen por producto ──────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] excelResumen(KardexReporteFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        filtro.setPage(0);
        filtro.setRows(MAX_FILAS);

        List<KardexReporteLineaDto> lineas = kardexRepository.reporte(filtro, empresaId).getContent();
        log.info("[Reporte kardex] Empresa {} — {} productos", empresaId, lineas.size());

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Movimiento");
            int fila = e.encabezado(ws, "MOVIMIENTO DE INVENTARIO", rango(filtro), COLS_RESUMEN);

            BigDecimal totEntradas = BigDecimal.ZERO;
            BigDecimal totSalidas = BigDecimal.ZERO;
            BigDecimal totValorEnt = BigDecimal.ZERO;
            BigDecimal totValorSal = BigDecimal.ZERO;

            for (int i = 0; i < lineas.size(); i++) {
                KardexReporteLineaDto l = lineas.get(i);
                XSSFCellStyle st = e.fila(i);
                // Las cantidades admiten decimales; los valores no.
                XSSFCellStyle cant = e.filaCantidad(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, l.getProductoNombre(), st);
                e.texto(r, c++, l.getProductoSku(), st);
                e.texto(r, c++, l.getCategoriaNombre(), st);
                e.texto(r, c++, l.getMarcaNombre(), st);
                e.texto(r, c++, l.getSucursalNombre(), st);
                e.texto(r, c++, l.getCodigoLote(), st);
                e.numero(r, c++, l.getSaldoInicial(), cant);
                e.numero(r, c++, l.getEntradas(), cant);
                e.numero(r, c++, l.getSalidas(), cant);
                e.numero(r, c++, l.getVariacionNeta(), cant);
                e.numero(r, c++, l.getSaldoFinal(), cant);
                e.numero(r, c++, l.getValorEntradas(), val);
                e.numero(r, c++, l.getValorSalidas(), val);
                e.numero(r, c++, l.getCompras(), cant);
                e.numero(r, c++, l.getVentas(), cant);
                e.numero(r, c++, l.getDevoluciones(), cant);
                e.numero(r, c++, l.getMermas(), cant);
                e.numero(r, c++, l.getObsequios(), cant);
                e.numero(r, c++, l.getTraslados(), cant);
                e.numero(r, c++, l.getAnulaciones(), cant);
                e.numero(r, c++, l.getReconteos(), cant);
                e.numero(r, c++, l.getOtros(), cant);
                e.numero(r, c, l.getCantidadMovimientos() != null
                        ? BigDecimal.valueOf(l.getCantidadMovimientos()) : BigDecimal.ZERO, val);

                totEntradas = totEntradas.add(nz(l.getEntradas()));
                totSalidas = totSalidas.add(nz(l.getSalidas()));
                totValorEnt = totValorEnt.add(nz(l.getValorEntradas()));
                totValorSal = totValorSal.add(nz(l.getValorSalidas()));
            }

            if (lineas.isEmpty()) {
                e.filaVacia(ws, fila, COLS_RESUMEN.length, "No hay movimientos con los filtros seleccionados.");
            } else {
                Row rt = ws.createRow(fila);
                e.texto(rt, 0, "TOTALES", e.total);
                for (int c = 1; c <= 6; c++) e.texto(rt, c, "", e.total);
                e.numero(rt, 7, totEntradas, e.cantidadTotal);
                e.numero(rt, 8, totSalidas, e.cantidadTotal);
                e.texto(rt, 9, "", e.total);
                e.texto(rt, 10, "", e.total);
                e.numero(rt, 11, totValorEnt, e.valorTotal);
                e.numero(rt, 12, totValorSal, e.valorTotal);
                for (int c = 13; c < COLS_RESUMEN.length; c++) e.texto(rt, c, "", e.total);
            }

            e.ajustarAnchos(ws, COLS_RESUMEN.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte kardex] Error generando el Excel del resumen", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel del movimiento de inventario: " + ex.getMessage());
        }
    }

    // ── Kardex clásico de un producto ─────────────────────────

    @Transactional(readOnly = true)
    public byte[] excelDetalle(KardexFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        if (filtro.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El kardex detallado es de un producto: indique cuál.");
        }
        filtro.setPage(0);
        filtro.setRows(MAX_FILAS);

        List<KardexDetalleLineaDto> lineas = kardexRepository.detalle(filtro, empresaId).getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Kardex");
            int fila = e.encabezado(ws, "KARDEX DEL PRODUCTO", rango(filtro), COLS_DETALLE);

            for (int i = 0; i < lineas.size(); i++) {
                KardexDetalleLineaDto l = lineas.get(i);
                XSSFCellStyle st = e.fila(i);
                XSSFCellStyle cant = e.filaCantidad(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, l.getFecha() != null ? l.getFecha().format(F_FECHA_HORA) : "", st);
                e.texto(r, c++, l.getTipoEtiqueta(), st);
                e.texto(r, c++, l.getGrupo(), st);
                e.texto(r, c++, l.getReferenciaOrigen(), st);
                e.texto(r, c++, l.getSucursalNombre(), st);
                e.texto(r, c++, l.getCodigoLote(), st);
                e.numero(r, c++, l.getSaldoAnterior(), cant);
                e.numero(r, c++, l.getMovimiento(), cant);
                e.numero(r, c++, l.getSaldoNuevo(), cant);
                e.numero(r, c++, l.getCostoHistorico(), val);
                e.numero(r, c, l.getValorMovimiento(), val);
            }

            if (lineas.isEmpty()) {
                e.filaVacia(ws, fila, COLS_DETALLE.length, "No hay movimientos con los filtros seleccionados.");
            }

            e.ajustarAnchos(ws, COLS_DETALLE.length);
            wb.write(out);
            return out.toByteArray();

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Reporte kardex] Error generando el Excel del detalle", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el kardex del producto: " + ex.getMessage());
        }
    }

    // ── Kardex de un producto, en PDF ─────────────────────────

    /**
     * Solo el detalle se exporta a PDF, no el resumen.
     *
     * <p>El resumen tiene 23 columnas — saldos, valores y el desglose por
     * familia — y en una hoja carta eso no se lee: sale una rejilla de números
     * de 4 puntos. Ese reporte se trabaja en Excel, que es donde además se
     * filtra y se suma. El kardex de un producto tiene 10 columnas y sí es un
     * documento para imprimir y archivar.
     */
    @Transactional(readOnly = true)
    public byte[] pdfDetalle(KardexFiltroDto filtro) {
        Integer empresaId = securityUtils.getEmpresaId();
        if (filtro.getProductoId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El kardex detallado es de un producto: indique cuál.");
        }
        filtro.setPage(0);
        filtro.setRows(MAX_FILAS);

        List<KardexDetalleLineaDto> lineas = kardexRepository.detalle(filtro, empresaId).getContent();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument pdf = new PdfDocument(new PdfWriter(out));
            Document doc = new Document(pdf, PageSize.A4.rotate());
            doc.setMargins(20, 20, 20, 20);

            Table banner = new Table(UnitValue.createPercentArray(new float[]{60, 40}))
                    .useAllAvailableWidth();
            banner.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph("KARDEX DEL PRODUCTO")
                            .setBold().setFontSize(15).setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(AZUL_PDF).setBorder(Border.NO_BORDER).setPadding(11));
            banner.addCell(new com.itextpdf.layout.element.Cell()
                    .add(new Paragraph(rango(filtro) + "  ·  Generado el "
                            + LocalDate.now().format(F_FECHA))
                            .setFontSize(9).setFontColor(ColorConstants.LIGHT_GRAY))
                    .setBackgroundColor(AZUL_PDF).setBorder(Border.NO_BORDER).setPadding(11)
                    .setTextAlignment(TextAlignment.RIGHT));
            doc.add(banner);
            doc.add(new Paragraph(" ").setFontSize(6));

            Table tabla = new Table(UnitValue.createPercentArray(
                    new float[]{1.0f, 1.2f, 1.4f, 0.9f, 0.7f, 0.8f, 0.8f, 0.8f, 0.9f, 0.9f}))
                    .useAllAvailableWidth();

            for (String h : COLS_DETALLE_PDF) {
                tabla.addCell(new com.itextpdf.layout.element.Cell()
                        .add(new Paragraph(h))
                        .setBold().setFontSize(8)
                        .setBackgroundColor(AZUL_PDF)
                        .setFontColor(ColorConstants.WHITE)
                        .setBorder(Border.NO_BORDER)
                        .setPadding(5));
            }

            for (int i = 0; i < lineas.size(); i++) {
                KardexDetalleLineaDto l = lineas.get(i);
                DeviceRgb fondo = (i % 2 == 0) ? BLANCO_PDF : ZEBRA_PDF;
                celda(tabla, l.getFecha() != null ? l.getFecha().format(F_FECHA_HORA) : "", fondo, false);
                celda(tabla, l.getTipoEtiqueta(), fondo, false);
                celda(tabla, l.getReferenciaOrigen(), fondo, false);
                celda(tabla, l.getSucursalNombre(), fondo, false);
                celda(tabla, l.getCodigoLote(), fondo, false);
                celda(tabla, cantidad(l.getSaldoAnterior()), fondo, true);
                celda(tabla, cantidad(l.getMovimiento()), fondo, true);
                celda(tabla, cantidad(l.getSaldoNuevo()), fondo, true);
                celda(tabla, moneda(l.getCostoHistorico()), fondo, true);
                celda(tabla, moneda(l.getValorMovimiento()), fondo, true);
            }

            if (lineas.isEmpty()) {
                doc.add(new Paragraph("Este producto no tiene movimientos en el rango.")
                        .setFontSize(9).setItalic().setTextAlignment(TextAlignment.CENTER));
            } else {
                doc.add(tabla);
            }

            doc.close();
            return out.toByteArray();

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("[Reporte kardex] Error generando el PDF del detalle", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el PDF del kardex: " + ex.getMessage());
        }
    }

    private void celda(Table tabla, String valor, DeviceRgb fondo, boolean derecha) {
        tabla.addCell(new com.itextpdf.layout.element.Cell()
                .add(new Paragraph(valor != null ? valor : "—").setFontSize(7.5f))
                .setBackgroundColor(fondo)
                .setBorder(Border.NO_BORDER)
                .setPadding(4)
                .setTextAlignment(derecha ? TextAlignment.RIGHT : TextAlignment.LEFT));
    }

    private String cantidad(BigDecimal v) {
        return v != null ? v.stripTrailingZeros().toPlainString() : "—";
    }

    private String moneda(BigDecimal v) {
        return String.format("$ %,.0f", nz(v));
    }

    // ── Helpers propios del kardex ────────────────────────────

    /** El rango es opcional: sin fechas el reporte es toda la historia. */
    private String rango(KardexFiltroDto filtro) {
        if (filtro.getFechaDesde() == null && filtro.getFechaHasta() == null) {
            return "Todos los movimientos registrados";
        }
        String desde = filtro.getFechaDesde() != null
                ? filtro.getFechaDesde().toLocalDate().format(F_FECHA) : "el inicio";
        String hasta = filtro.getFechaHasta() != null
                ? filtro.getFechaHasta().toLocalDate().format(F_FECHA) : "hoy";
        return "Del " + desde + " al " + hasta;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}