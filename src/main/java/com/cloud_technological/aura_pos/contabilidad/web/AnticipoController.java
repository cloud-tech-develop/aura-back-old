package com.cloud_technological.aura_pos.contabilidad.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.cloud_technological.aura_pos.contabilidad.infrastructure.devengo.AnticipoService;
import com.cloud_technological.aura_pos.entity.AnticipoCruceEntity;
import com.cloud_technological.aura_pos.entity.AnticipoEntity;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

/** Anticipos de clientes/proveedores y su cruce contra facturas (E6). */
@RestController
@RequestMapping("/api/contabilidad/anticipos")
public class AnticipoController {

    @Autowired
    private AnticipoService service;

    @Autowired
    private SecurityUtils securityUtils;

    /** Anticipos; con ?terceroId= devuelve solo los ACTIVOS de ese tercero. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AnticipoEntity>>> listar(
            @RequestParam(required = false) Long terceroId) {
        Integer empresaId = securityUtils.getEmpresaId();
        return ResponseEntity.ok(new ApiResponse<>(200, "OK", false,
                service.listar(empresaId, terceroId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnticipoEntity>> crear(@RequestBody AnticipoEntity dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        AnticipoEntity created = service.crear(empresaId, usuarioId, dto);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Anticipo registrado", false, created),
                HttpStatus.CREATED);
    }

    /** Cruza el anticipo contra una factura: {cuentaCobrarId|cuentaPagarId, monto}. */
    @PostMapping("/{id}/cruzar")
    public ResponseEntity<ApiResponse<AnticipoCruceEntity>> cruzar(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        Long cuentaCobrarId = body.get("cuentaCobrarId") != null
                ? Long.valueOf(body.get("cuentaCobrarId")) : null;
        Long cuentaPagarId = body.get("cuentaPagarId") != null
                ? Long.valueOf(body.get("cuentaPagarId")) : null;
        BigDecimal monto = body.get("monto") != null
                ? new BigDecimal(body.get("monto")) : null;
        AnticipoCruceEntity cruce = service.cruzar(empresaId, usuarioId, id,
                cuentaCobrarId, cuentaPagarId, monto);
        return ResponseEntity.ok(new ApiResponse<>(200, "Anticipo cruzado", false, cruce));
    }
}
