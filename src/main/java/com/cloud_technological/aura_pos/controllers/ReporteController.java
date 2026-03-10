package com.cloud_technological.aura_pos.controllers;

import java.time.LocalDate;

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

import com.cloud_technological.aura_pos.services.ReporteInventarioService;
import com.cloud_technological.aura_pos.services.ReporteVentasService;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {
    private final ReporteVentasService reporteVentasService;
    private final ReporteInventarioService reporteInventarioService;

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
