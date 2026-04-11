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
import com.cloud_technological.aura_pos.services.ReporteInventarioService;
import com.cloud_technological.aura_pos.services.ReporteVentasService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {
    private final ReporteVentasService reporteVentasService;
    private final ReporteInventarioService reporteInventarioService;

    @Autowired
    private ReporteAvanzadoService reporteAvanzadoService;

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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] bytes = reporteVentasService.generarExcel(desde, hasta);
        return respuesta(bytes, "reporte_ventas.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @GetMapping("/ventas/pdf")
    public ResponseEntity<byte[]> ventasPdf(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {

        byte[] bytes = reporteVentasService.generarPdf(desde, hasta);
        return respuesta(bytes, "reporte_ventas.pdf", MediaType.APPLICATION_PDF_VALUE);
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
