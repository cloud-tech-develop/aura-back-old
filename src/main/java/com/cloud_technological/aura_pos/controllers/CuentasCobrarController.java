package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cloud_technological.aura_pos.services.implementations.CuentaPdfService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.AbonoCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.services.CuentaCobrarService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/cuentas-cobrar")
public class CuentasCobrarController {

    @Autowired
    private CuentaCobrarService cuentaCobrarService;

    @Autowired
    private CuentaPdfService cuentaPdfService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<CuentaCobrarTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String estado) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<CuentaCobrarTableDto> result;
        
        if (fechaDesde != null || fechaHasta != null || clienteId != null || estado != null) {
            result = cuentaCobrarService.listarConFiltros(pageable, empresaId, fechaDesde, fechaHasta, clienteId, estado);
        } else {
            result = cuentaCobrarService.listar(pageable, empresaId);
        }
        
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CuentaCobrarDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaCobrarDto dto = cuentaCobrarService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuenta por cobrar encontrada", false, dto), HttpStatus.OK);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CuentaCobrarDto>> crear(@Valid @RequestBody CreateCuentaCobrarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        CuentaCobrarDto created = cuentaCobrarService.crear(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Cuenta por cobrar creada", false, created), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CuentaCobrarDto>> actualizar(@PathVariable Long id, @RequestBody CreateCuentaCobrarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaCobrarDto updated = cuentaCobrarService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuenta por cobrar actualizada", false, updated), HttpStatus.OK);
    }

    // Endpoints de Abonos
    @PostMapping("/{id}/abonos")
    public ResponseEntity<ApiResponse<AbonoCobrarDto>> registrarAbono(@PathVariable Long id, @Valid @RequestBody AbonoCobrarDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        AbonoCobrarDto created = cuentaCobrarService.registrarAbono(id, dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Abono registrado", false, created), HttpStatus.CREATED);
    }

    @GetMapping("/{id}/abonos")
    public ResponseEntity<ApiResponse<List<AbonoCobrarDto>>> listarAbonos(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        List<AbonoCobrarDto> abonos = cuentaCobrarService.listarAbonos(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado de abonos", false, abonos), HttpStatus.OK);
    }

    @DeleteMapping("/{cuentaId}/abonos/{abonoId}")
    public ResponseEntity<ApiResponse<Void>> eliminarAbono(@PathVariable Long cuentaId, @PathVariable Long abonoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        cuentaCobrarService.eliminarAbono(cuentaId, abonoId, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Abono eliminado", false, null), HttpStatus.OK);
    }
    
    // Endpoints de Resumen
    @GetMapping("/resumen")
    public ResponseEntity<ApiResponse<CuentaCobrarResumenDto>> obtenerResumen(
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String estado) {
        Integer empresaId = securityUtils.getEmpresaId();
        CuentaCobrarResumenDto resumen = cuentaCobrarService.obtenerResumen(empresaId, fechaDesde, fechaHasta, clienteId, estado);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Resumen de cuentas por cobrar", false, resumen), HttpStatus.OK);
    }
    
    @GetMapping("/vencidas")
    public ResponseEntity<ApiResponse<List<CuentaCobrarTableDto>>> obtenerVencidas() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<CuentaCobrarTableDto> vencidas = cuentaCobrarService.obtenerVencidas(empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Cuentas vencidas", false, vencidas), HttpStatus.OK);
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        byte[] pdf = cuentaPdfService.generarFacturaCuentaCobrar(id, empresaId);
        return respuestaPdf(pdf, "cuenta_cobrar_" + id + ".pdf");
    }

    @GetMapping("/abonos/{abonoId}/pdf")
    public ResponseEntity<byte[]> descargarAbonoPdf(@PathVariable Long abonoId) {
        Integer empresaId = securityUtils.getEmpresaId();
        byte[] pdf = cuentaPdfService.generarReciboCajaCobrar(abonoId, empresaId);
        return respuestaPdf(pdf, "recibo_caja_" + abonoId + ".pdf");
    }

    private ResponseEntity<byte[]> respuestaPdf(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}
