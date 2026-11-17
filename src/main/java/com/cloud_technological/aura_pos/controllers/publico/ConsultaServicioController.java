package com.cloud_technological.aura_pos.controllers.publico;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConsultaPublica;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.RespuestaPresupuesto;
import com.cloud_technological.aura_pos.services.servicio_tecnico.ServicioOrdenService;
import com.cloud_technological.aura_pos.utils.ApiResponse;

/**
 * Consulta de una orden de servicio por el cliente, sin iniciar sesión (F4 de
 * docs/PLAN_SERVICIO_TECNICO.md). Va bajo /api/auth a propósito: ahí ya hay límite
 * de intentos por IP y los filtros de sesión/sede la saltan. La clave del enlace es
 * aleatoria (128 bits) y la respuesta no trae documento, teléfono ni IMEI completo.
 */
@RestController
@RequestMapping("/api/auth/consulta-servicio")
public class ConsultaServicioController {

    @Autowired private ServicioOrdenService service;

    @GetMapping("/{token}")
    public ResponseEntity<ApiResponse<ConsultaPublica>> consultar(@PathVariable String token) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "", false, service.consultaPublica(token)));
    }

    @PostMapping("/{token}/respuesta")
    public ResponseEntity<ApiResponse<ConsultaPublica>> responder(@PathVariable String token,
            @Valid @RequestBody RespuestaPresupuesto dto) {
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(),
                dto.isAprueba() ? "Presupuesto aprobado" : "Respuesta enviada", false,
                service.responderPresupuesto(token, dto)));
    }
}
