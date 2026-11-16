package com.cloud_technological.aura_pos.services.implementations;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadCreditoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFilaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto.Agrupacion;
import com.cloud_technological.aura_pos.dto.reportes.ReporteUtilidadFiltroDto.Forma;
import com.cloud_technological.aura_pos.repositories.reportes.ReporteUtilidadQueryRepository;
import com.cloud_technological.aura_pos.services.ReporteUtilidadService;
import com.cloud_technological.aura_pos.utils.ExcelEstilos;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Reporte de utilidad en ventas.
 *
 * <p>La utilidad es venta neta sin IVA menos el costo promedio que tenía cada
 * producto cuando se vendió. Las ventas a crédito cuentan: la utilidad nace al
 * vender, igual que en el estado de resultados.
 */
@Slf4j
@Service
public class ReporteUtilidadServiceImpl implements ReporteUtilidadService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final ReporteUtilidadQueryRepository repository;
    private final SecurityUtils securityUtils;

    public ReporteUtilidadServiceImpl(ReporteUtilidadQueryRepository repository, SecurityUtils securityUtils) {
        this.repository = repository;
        this.securityUtils = securityUtils;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReporteUtilidadDto consultar(ReporteUtilidadFiltroDto filtro) {
        normalizar(filtro);
        Integer empresaId = securityUtils.getEmpresaId();

        ReporteUtilidadDto dto = repository.totales(filtro, empresaId);
        dto.setMargenVenta(pct(dto.getUtilidad(), dto.getVentaNeta()));
        dto.setMargenCosto(pct(dto.getUtilidad(), dto.getCosto()));
        dto.setSaldoPorCobrar(repository.saldoPorCobrar(filtro, empresaId));

        List<ReporteUtilidadFilaDto> filas = repository.filas(filtro, empresaId);
        for (ReporteUtilidadFilaDto f : filas) {
            f.setMargenVenta(pct(f.getUtilidad(), f.getVentaNeta()));
            f.setMargenCosto(pct(f.getUtilidad(), f.getCosto()));
        }
        dto.setFilas(filas);
        return dto;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public byte[] excel(ReporteUtilidadFiltroDto filtro) {
        ReporteUtilidadDto datos = consultar(filtro);
        String[] cols = {
            titulo(filtro.getAgrupar()), "Detalle", "Ventas", "Cantidad", "Venta neta (sin IVA)",
            "Costo", "Utilidad", "Margen s/ venta", "Margen s/ costo", "Líneas sin costo"
        };

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ExcelEstilos e = new ExcelEstilos(wb);
            XSSFSheet ws = wb.createSheet("Utilidad");
            int fila = e.encabezado(ws, "UTILIDAD EN VENTAS", subtitulo(filtro), cols);

            List<ReporteUtilidadFilaDto> filas = datos.getFilas();
            for (int i = 0; i < filas.size(); i++) {
                ReporteUtilidadFilaDto f = filas.get(i);
                Row r = ws.createRow(fila++);
                int c = 0;
                e.texto(r, c++, f.getNombre(), e.fila(i));
                e.texto(r, c++, f.getDetalle(), e.fila(i));
                e.numero(r, c++, bd(f.getVentas()), e.filaValor(i));
                e.numero(r, c++, f.getCantidad(), e.filaCantidad(i));
                e.numero(r, c++, f.getVentaNeta(), e.filaValor(i));
                e.numero(r, c++, f.getCosto(), e.filaValor(i));
                e.numero(r, c++, f.getUtilidad(), e.filaValor(i));
                e.numero(r, c++, f.getMargenVenta(), e.filaPorcentaje(i));
                e.numero(r, c++, f.getMargenCosto(), e.filaPorcentaje(i));
                e.numero(r, c, bd(f.getLineasSinCosto()), e.filaValor(i));
            }

            if (filas.isEmpty()) {
                e.filaVacia(ws, fila, cols.length, "No hay ventas en el período seleccionado.");
            } else {
                Row rt = ws.createRow(fila);
                int c = 0;
                e.texto(rt, c++, "TOTALES", e.total);
                e.texto(rt, c++, "", e.total);
                e.numero(rt, c++, bd(datos.getVentas()), e.valorTotal);
                e.texto(rt, c++, "", e.total);
                e.numero(rt, c++, datos.getVentaNeta(), e.valorTotal);
                e.numero(rt, c++, datos.getCosto(), e.valorTotal);
                e.numero(rt, c++, datos.getUtilidad(), e.valorTotal);
                e.numero(rt, c++, datos.getMargenVenta(), e.valorTotal);
                e.numero(rt, c++, datos.getMargenCosto(), e.valorTotal);
                e.numero(rt, c, bd(datos.getLineasSinCosto()), e.valorTotal);
            }

            e.ajustarAnchos(ws, cols.length);
            wb.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            log.error("[Reporte utilidad] Error generando el Excel", ex);
            throw new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "No se pudo generar el Excel de utilidad: " + ex.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<ReporteUtilidadCreditoDto> ventasCredito(ReporteUtilidadFiltroDto filtro) {
        normalizar(filtro);
        return repository.ventasCredito(filtro, securityUtils.getEmpresaId());
    }

    // ── Apoyo ─────────────────────────────────────────────────

    /** Mes en curso por defecto; valida el rango. */
    private static void normalizar(ReporteUtilidadFiltroDto f) {
        if (f.getHasta() == null) f.setHasta(LocalDate.now());
        if (f.getDesde() == null) f.setDesde(f.getHasta().withDayOfMonth(1));
        if (f.getDesde().isAfter(f.getHasta()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha inicial no puede ser posterior a la final");
        if (f.getAgrupar() == null) f.setAgrupar(Agrupacion.PRODUCTO);
        if (f.getForma() == null) f.setForma(Forma.TODAS);
    }

    /** parte / base × 100 con un decimal; cero si no hay base. */
    private static BigDecimal pct(BigDecimal parte, BigDecimal base) {
        if (parte == null || base == null || base.signum() == 0) return BigDecimal.ZERO;
        return parte.multiply(CIEN).divide(base, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(Long v) {
        return v != null ? BigDecimal.valueOf(v) : BigDecimal.ZERO;
    }

    private static String titulo(Agrupacion a) {
        return switch (a) {
            case CATEGORIA -> "Categoría";
            case VENDEDOR -> "Vendedor";
            case CLIENTE -> "Cliente";
            case MES -> "Mes";
            case PRODUCTO -> "Producto";
        };
    }

    private static String subtitulo(ReporteUtilidadFiltroDto f) {
        String forma = switch (f.getForma()) {
            case CONTADO -> "Solo contado";
            case CREDITO -> "Solo crédito";
            case TODAS -> "Contado y crédito";
        };
        return "Del " + f.getDesde().format(ExcelEstilos.F_FECHA) + " al " + f.getHasta().format(ExcelEstilos.F_FECHA)
                + "  ·  " + forma + "  ·  Venta sin IVA − costo promedio al vender";
    }
}
