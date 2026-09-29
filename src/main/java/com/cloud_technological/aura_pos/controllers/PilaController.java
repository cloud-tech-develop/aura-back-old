package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.pila.PilaDtos.CotizanteDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.PilaDtos.EncabezadoDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.CargaEntidadDto;
import com.cloud_technological.aura_pos.dto.nomina.pila.ValidacionPilaDtos.ReporteValidacionDto;
import com.cloud_technological.aura_pos.entity.PilaAportanteConfigEntity;
import com.cloud_technological.aura_pos.entity.PilaEncabezadoEntity;
import com.cloud_technological.aura_pos.services.nomina.pila.GeneradorPila;
import com.cloud_technological.aura_pos.services.nomina.pila.PilaAportanteConfigService;
import com.cloud_technological.aura_pos.services.nomina.pila.PilaArchivoExporter;
import com.cloud_technological.aura_pos.services.nomina.pila.PilaConsultaService;
import com.cloud_technological.aura_pos.services.nomina.pila.validacion.PilaCatalogoService;
import com.cloud_technological.aura_pos.services.nomina.pila.validacion.ValidacionPilaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/**
 * PILA — planilla integrada de liquidación de aportes (Fase 6).
 *
 * <p>Genera y consulta los datos estructurados de la planilla. El <b>archivo
 * plano</b> es un export aparte y depende del operador (cada uno tiene su
 * layout), por eso no vive aquí: estos datos son agnósticos del operador.
 */
@RestController
@RequestMapping("/api/pila")
public class PilaController {

    @Autowired
    private GeneradorPila generadorPila;

    @Autowired
    private PilaConsultaService consultaService;

    @Autowired
    private ValidacionPilaService validacionService;

    @Autowired
    private PilaCatalogoService catalogoService;

    @Autowired
    private PilaAportanteConfigService aportanteConfigService;

    @Autowired
    private PilaArchivoExporter archivoExporter;

    @Autowired
    private SecurityUtils securityUtils;

    /** Planillas generadas de la empresa, más recientes primero. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<EncabezadoDto>>> listar() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Planillas consultadas", consultaService.listar(empresaId));
    }

    /**
     * Genera la planilla de un período ({@code YYYY-MM}).
     *
     * <p>Responde <b>409 con la lista de problemas por empleado</b> si a algún
     * contrato le falta afiliación, tipo de cotizante o código de catálogo. Es
     * texto accionable: hay que mostrarlo completo, no un toast.
     */
    @PostMapping("/generar/{periodo}")
    public ResponseEntity<ApiResponse<EncabezadoDto>> generar(@PathVariable String periodo) {
        Integer empresaId = securityUtils.getEmpresaId();
        PilaEncabezadoEntity enc = generadorPila.generar(empresaId, periodo);
        return ok("Planilla generada", consultaService.obtener(empresaId, enc.getPeriodo()));
    }

    /** Encabezado/resumen de la planilla de un período. */
    @GetMapping("/{periodo}")
    public ResponseEntity<ApiResponse<EncabezadoDto>> obtener(@PathVariable String periodo) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Planilla consultada", consultaService.obtener(empresaId, periodo));
    }

    /** Cotizantes (registros tipo 02) de una planilla. */
    @GetMapping("/{encabezadoId}/cotizantes")
    public ResponseEntity<ApiResponse<List<CotizanteDto>>> cotizantes(@PathVariable Long encabezadoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Cotizantes consultados", consultaService.cotizantes(empresaId, encabezadoId));
    }

    /**
     * Reporte de validación tipo UGPP de la planilla de un período (P1).
     *
     * <p>Revisa el modelo generado y devuelve hallazgos clasificados
     * (ERROR / ADVERTENCIA / INFORMACIÓN / NO_EVALUABLE) con el estado de cada
     * cotizante y de la planilla. No modifica datos.
     */
    @GetMapping("/{periodo}/validar")
    public ResponseEntity<ApiResponse<ReporteValidacionDto>> validar(@PathVariable String periodo) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Validación PILA", validacionService.validar(empresaId, periodo));
    }

    /**
     * Carga (upsert) el catálogo oficial de entidades EPS/AFP/ARL/CCF (P2b).
     * Habilita la validación de código y vigencia de entidad. Catálogo GLOBAL.
     */
    @PostMapping("/catalogo/entidades")
    public ResponseEntity<ApiResponse<Integer>> cargarEntidades(@RequestBody List<CargaEntidadDto> filas) {
        return ok("Catálogo de entidades cargado", catalogoService.cargarEntidades(filas));
    }

    /** Cuántas entidades hay cargadas en el catálogo. */
    @GetMapping("/catalogo/entidades/total")
    public ResponseEntity<ApiResponse<Long>> totalEntidades() {
        return ok("Total de entidades", catalogoService.totalEntidades());
    }

    /** Configuración del aportante para PILA de la empresa (P4b). */
    @GetMapping("/aportante-config")
    public ResponseEntity<ApiResponse<PilaAportanteConfigEntity>> obtenerAportanteConfig() {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Configuración del aportante", aportanteConfigService.obtener(empresaId));
    }

    @PutMapping("/aportante-config")
    public ResponseEntity<ApiResponse<PilaAportanteConfigEntity>> guardarAportanteConfig(
            @RequestBody PilaAportanteConfigEntity dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Configuración guardada", aportanteConfigService.guardar(empresaId, dto));
    }

    /**
     * Genera el archivo plano PILA del período (formato estándar Anexo Técnico 2,
     * dirigido por layout — sirve para cualquier operador). Devuelve el contenido.
     */
    @GetMapping("/{periodo}/archivo")
    public ResponseEntity<ApiResponse<String>> archivo(@PathVariable String periodo) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ok("Archivo PILA generado", archivoExporter.generar(empresaId, periodo));
    }

    private <T> ResponseEntity<ApiResponse<T>> ok(String mensaje, T data) {
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), mensaje, false, data), HttpStatus.OK);
    }
}
