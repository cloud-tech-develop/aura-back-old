package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraAbonoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraDocumentoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraResumenDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraTerceroDto;
import com.cloud_technological.aura_pos.repositories.cartera.ReporteCarteraQueryRepository;
import com.cloud_technological.aura_pos.utils.ExcelEstilos;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Estado de cuenta de cartera: clientes (CxC) y proveedores (CxP).
 *
 * <p>El Excel del detalle intercala los abonos bajo su documento en vez de
 * llevarlos a otra hoja. Es la forma en que un estado de cuenta se lee: primero
 * lo que se debía, debajo lo que se ha ido pagando, y al final lo que queda.
 * Separarlos obliga a cruzar dos hojas con la vista.
 *
 * <p>Cada abono muestra en qué caja entró la plata. Ese dato no existía hasta
 * que la parte B del plan ató los abonos de comprobante a su turno.
 */
@Slf4j
@Service
public class ReporteCarteraService {

    private static final int MAX_FILAS = 20_000;

    private static final String[] COLS_RESUMEN = {
        "Tercero", "Identificación", "Teléfono", "Documentos",
        "Total", "Abonado", "Saldo",
        "Corriente", "1–30", "31–60", "61–90", "+90", "Mora máx. (días)"
    };

    private static final String[] COLS_DETALLE = {
        "Documento", "Factura externa", "Tercero", "Identificación",
        "Emisión", "Vencimiento", "Total", "Abonado", "Saldo",
        "Días mora", "Edad", "Estado"
    };

    private final ReporteCarteraQueryRepository repository;
    private final SecurityUtils securityUtils;

    public ReporteCarteraService(ReporteCarteraQueryRepository repository,
                                 SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    // ── Consultas ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReporteCarteraResumenDto resumen(ReporteCarteraFiltroDto filtro) {
        validar(filtro);
        return repository.resumen(filtro, securityUtils.getEmpresaId());
    }

    @Transactional(readOnly = true)
    public PageImpl<ReporteCarteraDocumentoDto> documentos(ReporteCarteraFiltroDto filtro) {
        validar(filtro);
        return repository.documentos(filtro, securityUtils.getEmpresaId());
    }

    // ── Excel ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] excelResumen(ReporteCarteraFiltroDto filtro) {
        validar(filtro);
        ReporteCarteraResumenDto datos = repository.resumen(filtro, securityUtils.getEmpresaId());

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Cartera");
            int fila = e.encabezado(ws, titulo(filtro) + " POR TERCERO",
                    subtitulo(filtro), COLS_RESUMEN);

            List<ReporteCarteraTerceroDto> terceros = datos.getTerceros();
            for (int i = 0; i < terceros.size(); i++) {
                ReporteCarteraTerceroDto x = terceros.get(i);
                XSSFCellStyle st = e.fila(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, x.getTerceroNombre(), st);
                e.texto(r, c++, x.getTerceroDocumento(), st);
                e.texto(r, c++, x.getTerceroTelefono(), st);
                e.numero(r, c++, bd(x.getDocumentos()), val);
                e.numero(r, c++, x.getTotalDeuda(), val);
                e.numero(r, c++, x.getTotalAbonado(), val);
                e.numero(r, c++, x.getSaldoPendiente(), val);
                e.numero(r, c++, x.getCorriente(), val);
                e.numero(r, c++, x.getMora1a30(), val);
                e.numero(r, c++, x.getMora31a60(), val);
                e.numero(r, c++, x.getMora61a90(), val);
                e.numero(r, c++, x.getMoraMas90(), val);
                e.numero(r, c, bd(x.getDiasMoraMax()), val);
            }

            if (terceros.isEmpty()) {
                e.filaVacia(ws, fila, COLS_RESUMEN.length,
                        "No hay cartera con los filtros seleccionados.");
            } else {
                Row rt = ws.createRow(fila);
                int c = 0;
                e.texto(rt, c++, "TOTALES", e.total);
                e.texto(rt, c++, "", e.total);
                e.texto(rt, c++, "", e.total);
                e.numero(rt, c++, bd(datos.getDocumentos()), e.valorTotal);
                e.numero(rt, c++, datos.getTotalDeuda(), e.valorTotal);
                e.numero(rt, c++, datos.getTotalAbonado(), e.valorTotal);
                e.numero(rt, c++, datos.getSaldoPendiente(), e.valorTotal);
                e.numero(rt, c++, datos.getCorriente(), e.valorTotal);
                e.numero(rt, c++, datos.getMora1a30(), e.valorTotal);
                e.numero(rt, c++, datos.getMora31a60(), e.valorTotal);
                e.numero(rt, c++, datos.getMora61a90(), e.valorTotal);
                e.numero(rt, c++, datos.getMoraMas90(), e.valorTotal);
                e.texto(rt, c, "", e.total);
            }

            e.ajustarAnchos(ws, COLS_RESUMEN.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte cartera] Error generando el Excel del resumen", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de cartera: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] excelDetalle(ReporteCarteraFiltroDto filtro) {
        validar(filtro);
        filtro.setPage(0);
        filtro.setRows(MAX_FILAS);
        filtro.setIncluirAbonos(Boolean.TRUE);

        List<ReporteCarteraDocumentoDto> docs =
                repository.documentos(filtro, securityUtils.getEmpresaId()).getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Estado de cuenta");
            int fila = e.encabezado(ws, "ESTADO DE CUENTA — " + titulo(filtro),
                    subtitulo(filtro), COLS_DETALLE);

            BigDecimal totalSaldo = BigDecimal.ZERO;

            for (int i = 0; i < docs.size(); i++) {
                ReporteCarteraDocumentoDto d = docs.get(i);
                XSSFCellStyle st = e.fila(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, d.getNumeroCuenta(), st);
                e.texto(r, c++, d.getNumeroFacturaExterno(), st);
                e.texto(r, c++, d.getTerceroNombre(), st);
                e.texto(r, c++, d.getTerceroDocumento(), st);
                e.texto(r, c++, fecha(d.getFechaEmision()), st);
                e.texto(r, c++, fecha(d.getFechaVencimiento()), st);
                e.numero(r, c++, d.getTotalDeuda(), val);
                e.numero(r, c++, d.getTotalAbonado(), val);
                e.numero(r, c++, d.getSaldoPendiente(), val);
                e.numero(r, c++, bd(d.getDiasMora()), val);
                e.texto(r, c++, d.getEdad(), st);
                e.texto(r, c, d.getEstado(), st);

                // Los abonos van debajo de su documento, indentados. Así el
                // saldo parcial queda explicado en el sitio donde se lee, sin
                // saltar a otra hoja.
                for (ReporteCarteraAbonoDto a : d.getAbonos()) {
                    Row ra = ws.createRow(fila++);
                    e.texto(ra, 0, "    ↳ abono", st);
                    e.texto(ra, 1, "", st);
                    e.texto(ra, 2, detalleAbono(a), st);
                    e.texto(ra, 3, a.getMetodoPago(), st);
                    e.texto(ra, 4, fecha(a.getFechaPago()), st);
                    e.texto(ra, 5, "", st);
                    e.texto(ra, 6, "", st);
                    e.numero(ra, 7, a.getMonto(), val);
                    for (int c2 = 8; c2 < COLS_DETALLE.length; c2++) {
                        e.texto(ra, c2, "", st);
                    }
                }

                totalSaldo = totalSaldo.add(nz(d.getSaldoPendiente()));
            }

            if (docs.isEmpty()) {
                e.filaVacia(ws, fila, COLS_DETALLE.length,
                        "No hay documentos con los filtros seleccionados.");
            } else {
                Row rt = ws.createRow(fila);
                e.texto(rt, 0, "SALDO TOTAL", e.total);
                for (int c = 1; c <= 7; c++) e.texto(rt, c, "", e.total);
                e.numero(rt, 8, totalSaldo, e.valorTotal);
                for (int c = 9; c < COLS_DETALLE.length; c++) e.texto(rt, c, "", e.total);
            }

            e.ajustarAnchos(ws, COLS_DETALLE.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte cartera] Error generando el estado de cuenta", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el estado de cuenta: " + ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * De dónde salió el abono: la caja donde entró la plata, o la marca de que
     * se movió otro día. Es lo que permite reclamar un abono sin buscar en los
     * arqueos uno por uno.
     */
    private String detalleAbono(ReporteCarteraAbonoDto a) {
        StringBuilder s = new StringBuilder();
        if (a.getCajaNombre() != null) {
            s.append(a.getCajaNombre());
        } else if (Boolean.TRUE.equals(a.getCajaOtroDia())) {
            s.append("Se movió de la caja otro día");
        } else {
            s.append("Sin caja");
        }
        if (a.getUsuarioNombre() != null) {
            s.append(" · ").append(a.getUsuarioNombre());
        }
        if (a.getReferencia() != null && !a.getReferencia().isBlank()) {
            s.append(" · ").append(a.getReferencia().trim());
        }
        return s.toString();
    }

    private String titulo(ReporteCarteraFiltroDto f) {
        return ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(f.getTipo())
                ? "CUENTAS POR PAGAR" : "CUENTAS POR COBRAR";
    }

    private String subtitulo(ReporteCarteraFiltroDto f) {
        StringBuilder s = new StringBuilder();
        if (f.getFechaDesde() != null || f.getFechaHasta() != null) {
            s.append("Emitidos del ")
             .append(f.getFechaDesde() != null
                     ? f.getFechaDesde().format(ExcelEstilos.F_FECHA) : "inicio")
             .append(" al ")
             .append(f.getFechaHasta() != null
                     ? f.getFechaHasta().format(ExcelEstilos.F_FECHA) : "hoy");
        } else {
            s.append("Toda la cartera");
        }
        s.append("  ·  Estado: ")
         .append(f.getEstado() != null ? f.getEstado() : "PENDIENTE");
        if (f.getDiasMoraMin() != null) {
            s.append("  ·  Con más de ").append(f.getDiasMoraMin()).append(" días de mora");
        }
        return s.toString();
    }

    private String fecha(java.time.LocalDateTime v) {
        return v != null ? v.format(ExcelEstilos.F_FECHA) : "";
    }

    /**
     * Un rango invertido no devuelve nada y se lee como "no hay cartera".
     * Se rechaza para que el usuario vea el error y no cierre creyendo que
     * nadie le debe.
     */
    private void validar(ReporteCarteraFiltroDto f) {
        if (f.getTipo() != null
                && !ReporteCarteraFiltroDto.CXC.equalsIgnoreCase(f.getTipo())
                && !ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(f.getTipo())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El tipo de cartera debe ser CXC (clientes) o CXP (proveedores).");
        }
        if (f.getFechaDesde() != null && f.getFechaHasta() != null
                && f.getFechaHasta().isBefore(f.getFechaDesde())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fecha final no puede ser anterior a la inicial.");
        }
    }

    private static BigDecimal bd(Integer v) {
        return v != null ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
