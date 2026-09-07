package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.reportes.ReporteMargenesProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteMovimientosCajaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteResumenAvanzadoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteRotacionInventarioDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteTopProductoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasCategoriaDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteVentasVendedorDto;
import com.cloud_technological.aura_pos.services.ReporteAvanzadoService;
import com.cloud_technological.aura_pos.services.ReporteFacturacionElectronicaService;
import com.cloud_technological.aura_pos.services.ReporteInventarioService;
import com.cloud_technological.aura_pos.services.ReporteVentasService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.dto.kardex.KardexFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaFiltroDto;
import com.cloud_technological.aura_pos.dto.auditoria.AuditoriaResultadoDto;
import com.cloud_technological.aura_pos.services.AuditoriaService;
import com.cloud_technological.aura_pos.services.ReporteGerencialService;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraDocumentoDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteCarteraResumenDto;
import com.cloud_technological.aura_pos.services.ReporteCarteraService;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosDetalleDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosFiltroDto;
import com.cloud_technological.aura_pos.dto.reportes.ReporteGastosResumenDto;
import com.cloud_technological.aura_pos.services.ReporteGastosService;
import com.cloud_technological.aura_pos.dto.kardex.KardexReporteFiltroDto;
import com.cloud_technological.aura_pos.services.ReporteKardexService;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {
    private final ReporteVentasService reporteVentasService;
    private final ReporteInventarioService reporteInventarioService;

    @Autowired
    private ReporteAvanzadoService reporteAvanzadoService;

    @Autowired
    private ReporteFacturacionElectronicaService reporteFacturacionElectronicaService;

    @Autowired
    private ReporteKardexService reporteKardexService;

    @Autowired
    private ReporteGastosService reporteGastosService;

    @Autowired
    private ReporteCarteraService reporteCarteraService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    private ReporteGerencialService reporteGerencialService;

    @Autowired
    private SecurityUtils securityUtils;

    public ReporteController(ReporteVentasService reporteVentasService,
                            ReporteInventarioService reporteInventarioService) {
        this.reporteVentasService = reporteVentasService;
        this.reporteInventarioService = reporteInventarioService;
    }

    // ── VENTAS ──────────────────────────────────────────────

    @GetMapping("/ventas/excel")
    public ResponseEntity<byte[]> ventasExcel(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "detallado") String tipo) {

        boolean detallado = !"simple".equalsIgnoreCase(tipo);
        byte[] bytes = reporteVentasService.generarExcel(desde, hasta, detallado);
        return respuesta(bytes, "reporte_ventas_" + (detallado ? "detallado" : "simple") + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/ventas/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "detallado") String tipo) {

        boolean detallado = !"simple".equalsIgnoreCase(tipo);
        byte[] bytes = reporteVentasService.generarPdf(desde, hasta, detallado);
        return respuesta(bytes, "reporte_ventas_" + (detallado ? "detallado" : "simple") + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ── FACTURACIÓN ELECTRÓNICA ─────────────────────────────

    /**
     * Facturas electrónicas del rango, en Excel. Equivale al export de Factus
     * ({@code /reports/bills/export-to-excel}) pero armado con nuestros datos.
     */
    @GetMapping("/facturas-electronicas/excel")
    public ResponseEntity<byte[]> facturasElectronicasExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] bytes = reporteFacturacionElectronicaService.excelFacturas(desde, hasta);
        return respuesta(bytes, "facturas_" + desde + "_" + hasta + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Notas crédito y/o débito del rango, en Excel.
     *
     * @param tipo {@code CREDITO}, {@code DEBITO} o vacío para traer ambas.
     */
    @GetMapping("/notas-electronicas/excel")
    public ResponseEntity<byte[]> notasElectronicasExcel(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tipo) {

        byte[] bytes = reporteFacturacionElectronicaService.excelNotas(desde, hasta, tipo);
        String sufijo = tipo != null && !tipo.isBlank() ? tipo.toLowerCase() : "credito_debito";
        return respuesta(bytes, "notas_" + sufijo + "_" + desde + "_" + hasta + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // ── KARDEX ──────────────────────────────────────────────
    //
    // Cuelgan de /api/reportes y no de /api/kardex para no partir en dos la
    // convención: todo lo exportable vive aquí. Van por POST porque los filtros
    // del kardex (lista de tipos, categoría, marca, agrupación) no caben cómodos
    // en una query string.

    /** Movimiento de inventario agrupado por producto, en Excel. */
    @PostMapping("/kardex/excel")
    public ResponseEntity<byte[]> kardexExcel(@RequestBody KardexReporteFiltroDto filtro) {
        byte[] bytes = reporteKardexService.excelResumen(filtro);
        return respuesta(bytes, "movimiento_inventario_" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /** Kardex clásico de un producto, con saldo corrido, en Excel. */
    @PostMapping("/kardex/detalle/excel")
    public ResponseEntity<byte[]> kardexDetalleExcel(@RequestBody KardexFiltroDto filtro) {
        byte[] bytes = reporteKardexService.excelDetalle(filtro);
        return respuesta(bytes, "kardex_producto_" + filtro.getProductoId() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Kardex de un producto en PDF, para imprimir y archivar.
     *
     * <p>El resumen no tiene PDF a propósito: sus 23 columnas no se leen en una
     * hoja. Ese se trabaja en Excel, que es donde además se filtra y se suma.
     */
    @PostMapping("/kardex/detalle/pdf")
    public ResponseEntity<byte[]> kardexDetallePdf(@RequestBody KardexFiltroDto filtro) {
        byte[] bytes = reporteKardexService.pdfDetalle(filtro);
        return respuesta(bytes, "kardex_producto_" + filtro.getProductoId() + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ── GASTOS ──────────────────────────────────────────────
    //
    // Van por POST porque los filtros (fechas, tercero, centro de costo,
    // cuenta, deducible, agrupación…) no caben cómodos en una query string.

    /** Gastos agrupados por categoría, tercero, centro de costo, cuenta o mes. */
    @PostMapping("/gastos/resumen")
    public ResponseEntity<ApiResponse<ReporteGastosResumenDto>> gastosResumen(
            @RequestBody ReporteGastosFiltroDto filtro) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Reporte generado",
                false, reporteGastosService.resumen(filtro)));
    }

    /** Los gastos uno a uno, con su soporte, impuestos y retenciones. */
    @PostMapping("/gastos/detalle")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.PageImpl<ReporteGastosDetalleDto>>>
            gastosDetalle(@RequestBody ReporteGastosFiltroDto filtro) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Detalle consultado",
                false, reporteGastosService.detalle(filtro)));
    }

    @PostMapping("/gastos/excel")
    public ResponseEntity<byte[]> gastosExcel(@RequestBody ReporteGastosFiltroDto filtro) {
        byte[] bytes = reporteGastosService.excelResumen(filtro);
        return respuesta(bytes, "gastos_resumen_" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/gastos/detalle/excel")
    public ResponseEntity<byte[]> gastosDetalleExcel(@RequestBody ReporteGastosFiltroDto filtro) {
        byte[] bytes = reporteGastosService.excelDetalle(filtro);
        return respuesta(bytes, "gastos_detalle_" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    // ── AUDITORÍA ───────────────────────────────────────────

    /**
     * Los hallazgos del reporte gerencial (fase G1).
     *
     * <p>Devuelve JSON, no PDF, a propósito: los cruces se validan contra datos
     * reales antes de invertir en la maquetación. Un hallazgo falso destruye la
     * confianza en todo el documento, así que primero se miran.
     *
     * <p>{@code incluirDetalle} trae las filas concretas de cada hallazgo; va
     * apagado por defecto porque el semáforo solo necesita los totales.
     */
    @PostMapping("/auditoria")
    public ResponseEntity<ApiResponse<AuditoriaResultadoDto>> auditoria(
            @RequestBody AuditoriaFiltroDto filtro) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Auditoría ejecutada",
                false, auditoriaService.auditar(filtro)));
    }

    /**
     * El reporte gerencial completo en PDF.
     *
     * <p>Los mismos filtros de la auditoría: el semáforo del PDF y el JSON de
     * `/auditoria` salen de la misma corrida, así que lo que se ve en pantalla
     * es lo que sale impreso.
     */
    @PostMapping("/gerencial/pdf")
    public ResponseEntity<byte[]> gerencialPdf(@RequestBody AuditoriaFiltroDto filtro) {
        byte[] bytes = reporteGerencialService.generarPdf(filtro);
        return respuesta(bytes, "reporte_gerencial_" + LocalDate.now() + ".pdf",
                MediaType.APPLICATION_PDF_VALUE);
    }

    // ── CARTERA (CxC y CxP) ─────────────────────────────────
    //
    // Un solo juego de endpoints para las dos caras: la pregunta es la misma
    // — quién debe qué, desde cuándo — y `tipo` decide de cuál se trata.
    // Duplicarlos garantizaría que uno de los dos se quede atrás.

    /** Cartera agrupada por tercero, con el saldo repartido por edades. */
    @PostMapping("/cartera/resumen")
    public ResponseEntity<ApiResponse<ReporteCarteraResumenDto>> carteraResumen(
            @RequestBody ReporteCarteraFiltroDto filtro) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Reporte generado",
                false, reporteCarteraService.resumen(filtro)));
    }

    /**
     * Los documentos con su saldo y, si se piden, sus abonos: el estado de
     * cuenta que se le manda al cliente o se revisa con el proveedor.
     */
    @PostMapping("/cartera/documentos")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.PageImpl<ReporteCarteraDocumentoDto>>>
            carteraDocumentos(@RequestBody ReporteCarteraFiltroDto filtro) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "Documentos consultados",
                false, reporteCarteraService.documentos(filtro)));
    }

    @PostMapping("/cartera/excel")
    public ResponseEntity<byte[]> carteraExcel(@RequestBody ReporteCarteraFiltroDto filtro) {
        byte[] bytes = reporteCarteraService.excelResumen(filtro);
        return respuesta(bytes, "cartera_" + tipoArchivo(filtro) + "_" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @PostMapping("/cartera/detalle/excel")
    public ResponseEntity<byte[]> carteraDetalleExcel(@RequestBody ReporteCarteraFiltroDto filtro) {
        byte[] bytes = reporteCarteraService.excelDetalle(filtro);
        return respuesta(bytes,
                "estado_cuenta_" + tipoArchivo(filtro) + "_" + LocalDate.now() + ".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    private String tipoArchivo(ReporteCarteraFiltroDto filtro) {
        return ReporteCarteraFiltroDto.CXP.equalsIgnoreCase(filtro.getTipo())
                ? "por_pagar" : "por_cobrar";
    }

    // ── INVENTARIO ──────────────────────────────────────────
    @GetMapping("/inventario/excel")
    public ResponseEntity<byte[]> inventarioExcel() {
        byte[] bytes = reporteInventarioService.generarExcel();
        return respuesta(bytes, "reporte_inventario.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/inventario/pdf")
    public ResponseEntity<byte[]> inventarioPdf() {
        byte[] bytes = reporteInventarioService.generarPdf();
        return respuesta(bytes, "reporte_inventario.pdf", MediaType.APPLICATION_PDF_VALUE);
    }

    // ── REPORTES AVANZADOS ───────────────────────────────────

    @GetMapping("/avanzado/resumen")
    public ResponseEntity<ApiResponse<ReporteResumenAvanzadoDto>> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.resumenAvanzado(empresaId, d, h)));
    }

    @GetMapping("/avanzado/ventas-categoria")
    public ResponseEntity<ApiResponse<List<ReporteVentasCategoriaDto>>> ventasPorCategoria(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.ventasPorCategoria(empresaId, d, h)));
    }

    @GetMapping("/avanzado/top-productos")
    public ResponseEntity<ApiResponse<List<ReporteTopProductoDto>>> topProductos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "20") int limite) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.topProductos(empresaId, d, h, limite)));
    }

    @GetMapping("/avanzado/ventas-vendedor")
    public ResponseEntity<ApiResponse<List<ReporteVentasVendedorDto>>> ventasPorVendedor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.ventasPorVendedor(empresaId, d, h)));
    }

    @GetMapping("/avanzado/margenes")
    public ResponseEntity<ApiResponse<List<ReporteMargenesProductoDto>>> margenes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.margenesPorProducto(empresaId, d, h)));
    }

    @GetMapping("/avanzado/rotacion-inventario")
    public ResponseEntity<ApiResponse<List<ReporteRotacionInventarioDto>>> rotacionInventario() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.rotacionInventario(empresaId)));
    }

    @GetMapping("/avanzado/movimientos-caja")
    public ResponseEntity<ApiResponse<ReporteMovimientosCajaDto>> movimientosCaja(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        Integer empresaId = securityUtils.getEmpresaId();
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                reporteAvanzadoService.resumenMovimientosCaja(empresaId, d, h)));
    }

    // ── Helper ───────────────────────────────────────────────
    private ResponseEntity<byte[]> respuesta(byte[] bytes, String filename, String mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(
            ContentDisposition.attachment().filename(filename).build()
        );
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
