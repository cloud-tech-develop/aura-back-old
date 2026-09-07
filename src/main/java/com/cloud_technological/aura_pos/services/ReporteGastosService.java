package com.cloud_technological.aura_pos.services;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosDetalleDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosLineaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosResumenDto;
import com.cloud_technological.aura_pos.repositories.gastos.ReporteGastosQueryRepository;
import com.cloud_technological.aura_pos.utils.ExcelEstilos;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Reporte de gastos: resumen agrupado y detalle, exportables a Excel.
 *
 * <p>Deducible y no deducible salen en columnas separadas en las dos vistas. No
 * es un adorno: un gasto sin soporte válido baja la utilidad del negocio pero no
 * el impuesto de renta, y un reporte que los suma en una sola cifra obliga al
 * contador a rehacer la separación a mano —que es exactamente el trabajo que el
 * reporte debería ahorrarle.
 */
@Slf4j
@Service
public class ReporteGastosService {

    /** Tope de filas por exportación: el Excel es un reporte, no un volcado. */
    private static final int MAX_FILAS = 20_000;

    private static final String[] COLS_RESUMEN = {
        "Grupo", "N° gastos", "Total", "Deducible", "No deducible",
        "Contado", "Crédito", "Base IVA", "IVA", "Retefuente", "ReteICA", "% del total"
    };

    private static final String[] COLS_DETALLE = {
        "Fecha", "Categoría", "Descripción", "Tercero", "Identificación",
        "Sucursal", "Centro de costo", "Cuenta contable", "Proyecto",
        "Monto", "Deducible", "Forma de pago", "Método", "Documento soporte",
        "Base IVA", "IVA", "Retefuente", "ReteICA", "Observación", "Registró"
    };

    private final ReporteGastosQueryRepository repository;
    private final SecurityUtils securityUtils;

    public ReporteGastosService(ReporteGastosQueryRepository repository,
                                SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    // ── Consultas ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReporteGastosResumenDto resumen(ReporteGastosFiltroDto filtro) {
        validar(filtro);
        return repository.resumen(filtro, securityUtils.getEmpresaId());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.PageImpl<ReporteGastosDetalleDto> detalle(
            ReporteGastosFiltroDto filtro) {
        validar(filtro);
        return repository.detalle(filtro, securityUtils.getEmpresaId());
    }

    // ── Excel ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public byte[] excelResumen(ReporteGastosFiltroDto filtro) {
        validar(filtro);
        Integer empresaId = securityUtils.getEmpresaId();
        ReporteGastosResumenDto datos = repository.resumen(filtro, empresaId);
        log.info("[Reporte gastos] Empresa {} — {} grupos, total {}",
                empresaId, datos.getLineas().size(), datos.getTotal());

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Resumen");
            int fila = e.encabezado(ws, "REPORTE DE GASTOS",
                    subtitulo(filtro), COLS_RESUMEN);

            List<ReporteGastosLineaDto> lineas = datos.getLineas();
            for (int i = 0; i < lineas.size(); i++) {
                ReporteGastosLineaDto l = lineas.get(i);
                XSSFCellStyle st = e.fila(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, l.getGrupo(), st);
                e.numero(r, c++, bd(l.getCantidad()), val);
                e.numero(r, c++, l.getTotal(), val);
                e.numero(r, c++, l.getTotalDeducible(), val);
                e.numero(r, c++, l.getTotalNoDeducible(), val);
                e.numero(r, c++, l.getTotalContado(), val);
                e.numero(r, c++, l.getTotalCredito(), val);
                e.numero(r, c++, l.getBaseIva(), val);
                e.numero(r, c++, l.getValorIva(), val);
                e.numero(r, c++, l.getValorRetefuente(), val);
                e.numero(r, c++, l.getValorReteica(), val);
                e.numero(r, c, l.getParticipacion(), e.filaPorcentaje(i));
            }

            if (lineas.isEmpty()) {
                e.filaVacia(ws, fila, COLS_RESUMEN.length,
                        "No hay gastos con los filtros seleccionados.");
            } else {
                // Los totales vienen de una consulta sobre todo el período, no
                // de sumar las filas de arriba: si el resumen se paginara, el
                // pie seguiría cuadrando con la declaración.
                Row rt = ws.createRow(fila);
                int c = 0;
                e.texto(rt, c++, "TOTALES", e.total);
                e.numero(rt, c++, bd(datos.getCantidad()), e.valorTotal);
                e.numero(rt, c++, datos.getTotal(), e.valorTotal);
                e.numero(rt, c++, datos.getTotalDeducible(), e.valorTotal);
                e.numero(rt, c++, datos.getTotalNoDeducible(), e.valorTotal);
                e.numero(rt, c++, datos.getTotalContado(), e.valorTotal);
                e.numero(rt, c++, datos.getTotalCredito(), e.valorTotal);
                e.numero(rt, c++, datos.getBaseIva(), e.valorTotal);
                e.numero(rt, c++, datos.getValorIva(), e.valorTotal);
                e.numero(rt, c++, datos.getValorRetefuente(), e.valorTotal);
                e.numero(rt, c++, datos.getValorReteica(), e.valorTotal);
                e.texto(rt, c, "100%", e.total);
            }

            e.ajustarAnchos(ws, COLS_RESUMEN.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte gastos] Error generando el Excel del resumen", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de gastos: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public byte[] excelDetalle(ReporteGastosFiltroDto filtro) {
        validar(filtro);
        Integer empresaId = securityUtils.getEmpresaId();
        filtro.setPage(0);
        filtro.setRows(MAX_FILAS);

        List<ReporteGastosDetalleDto> gastos =
                repository.detalle(filtro, empresaId).getContent();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Gastos");
            int fila = e.encabezado(ws, "DETALLE DE GASTOS",
                    subtitulo(filtro), COLS_DETALLE);

            BigDecimal total = BigDecimal.ZERO;
            BigDecimal totalIva = BigDecimal.ZERO;

            for (int i = 0; i < gastos.size(); i++) {
                ReporteGastosDetalleDto g = gastos.get(i);
                XSSFCellStyle st = e.fila(i);
                XSSFCellStyle val = e.filaValor(i);
                Row r = ws.createRow(fila++);

                int c = 0;
                e.texto(r, c++, g.getFecha() != null
                        ? g.getFecha().format(ExcelEstilos.F_FECHA) : "", st);
                e.texto(r, c++, g.getCategoria(), st);
                e.texto(r, c++, g.getDescripcion(), st);
                e.texto(r, c++, g.getTerceroNombre(), st);
                e.texto(r, c++, g.getTerceroDocumento(), st);
                e.texto(r, c++, g.getSucursalNombre(), st);
                e.texto(r, c++, g.getCentroCostoNombre(), st);
                e.texto(r, c++, cuenta(g), st);
                e.texto(r, c++, g.getProyectoNombre(), st);
                e.numero(r, c++, g.getMonto(), val);
                e.texto(r, c++, Boolean.TRUE.equals(g.getDeducible()) ? "Sí" : "No", st);
                e.texto(r, c++, g.getFormaPago(), st);
                e.texto(r, c++, g.getMetodoPago(), st);
                e.texto(r, c++, documento(g), st);
                e.numero(r, c++, g.getBaseIva(), val);
                e.numero(r, c++, g.getValorIva(), val);
                e.numero(r, c++, g.getValorRetefuente(), val);
                e.numero(r, c++, g.getValorReteica(), val);
                e.texto(r, c++, observacion(g), st);
                e.texto(r, c, g.getUsuarioNombre(), st);

                total = total.add(nz(g.getMonto()));
                totalIva = totalIva.add(nz(g.getValorIva()));
            }

            if (gastos.isEmpty()) {
                e.filaVacia(ws, fila, COLS_DETALLE.length,
                        "No hay gastos con los filtros seleccionados.");
            } else {
                Row rt = ws.createRow(fila);
                e.texto(rt, 0, "TOTAL", e.total);
                for (int c = 1; c <= 8; c++) e.texto(rt, c, "", e.total);
                e.numero(rt, 9, total, e.valorTotal);
                for (int c = 10; c <= 14; c++) e.texto(rt, c, "", e.total);
                e.numero(rt, 15, totalIva, e.valorTotal);
                for (int c = 16; c < COLS_DETALLE.length; c++) e.texto(rt, c, "", e.total);
            }

            e.ajustarAnchos(ws, COLS_DETALLE.length);
            wb.write(out);
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("[Reporte gastos] Error generando el Excel del detalle", ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el detalle de gastos: " + ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Lo que el usuario filtró, escrito en la hoja.
     *
     * <p>Un Excel sin el rango ni los filtros impresos es un archivo que nadie
     * puede volver a defender tres meses después: no se sabe si eran todos los
     * gastos o solo los deducibles de una sucursal.
     */
    private String subtitulo(ReporteGastosFiltroDto f) {
        StringBuilder s = new StringBuilder();
        if (f.getFechaDesde() != null || f.getFechaHasta() != null) {
            s.append("Del ")
             .append(f.getFechaDesde() != null
                     ? f.getFechaDesde().format(ExcelEstilos.F_FECHA) : "el inicio")
             .append(" al ")
             .append(f.getFechaHasta() != null
                     ? f.getFechaHasta().format(ExcelEstilos.F_FECHA) : "hoy");
        } else {
            s.append("Todos los gastos registrados");
        }
        if (f.getDeducible() != null) {
            s.append(Boolean.TRUE.equals(f.getDeducible())
                    ? "  ·  Solo deducibles" : "  ·  Solo NO deducibles");
        }
        if (f.getCategoria() != null && !f.getCategoria().isBlank()) {
            s.append("  ·  Categoría: ").append(f.getCategoria().trim());
        }
        if (f.getFormaPago() != null && !f.getFormaPago().isBlank()) {
            s.append("  ·  ").append(f.getFormaPago().trim());
        }
        if (f.getEstado() != null && !"ACTIVO".equalsIgnoreCase(f.getEstado())) {
            s.append("  ·  Estado: ").append(f.getEstado());
        }
        return s.toString();
    }

    private String cuenta(ReporteGastosDetalleDto g) {
        if (g.getCuentaCodigo() == null && g.getCuentaNombre() == null) return "";
        return (g.getCuentaCodigo() != null ? g.getCuentaCodigo() + " — " : "")
                + (g.getCuentaNombre() != null ? g.getCuentaNombre() : "");
    }

    private String documento(ReporteGastosDetalleDto g) {
        if (g.getNumeroDocSoporte() == null || g.getNumeroDocSoporte().isBlank()) return "";
        return (g.getTipoDocSoporte() != null ? g.getTipoDocSoporte() + " " : "")
                + g.getNumeroDocSoporte();
    }

    /**
     * Las marcas que el revisor necesita ver sin abrir el gasto: que la plata
     * salió otro día, o que alguien autorizó un documento retroactivo.
     */
    private String observacion(ReporteGastosDetalleDto g) {
        StringBuilder s = new StringBuilder();
        if (Boolean.TRUE.equals(g.getSalidaCajaOtroDia())) {
            s.append("Salió de la caja otro día");
        }
        if (g.getMotivoRetroactivo() != null && !g.getMotivoRetroactivo().isBlank()) {
            if (s.length() > 0) s.append(" · ");
            s.append("Retroactivo: ").append(g.getMotivoRetroactivo().trim());
        }
        return s.toString();
    }

    /**
     * Un rango invertido no devuelve nada y parece "no hay gastos".
     * Se rechaza para que el usuario vea el error, no un reporte vacío.
     */
    private void validar(ReporteGastosFiltroDto f) {
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
