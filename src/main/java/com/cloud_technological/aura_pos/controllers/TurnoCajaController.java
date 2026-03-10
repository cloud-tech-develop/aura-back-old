package com.cloud_technological.aura_pos.controllers;

import java.util.Arrays;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.caja.AbrirTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CerrarTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.CreateMovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.MovimientoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.ResumenTurnoDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaDto;
import com.cloud_technological.aura_pos.dto.caja.TurnoCajaTableDto;
import com.cloud_technological.aura_pos.services.TurnoCajaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;


@RestController
@RequestMapping("/api/turnos")
public class TurnoCajaController {

    @Autowired
    private TurnoCajaService turnoService;

    @Autowired
    private SecurityUtils securityUtils;

    /** Roles autorizados para cerrar turno. Default: ADMIN,SUPER_ADMIN */
    @Value("${app.caja.roles-cierre:ADMIN,SUPER_ADMIN}")
    private String rolesCierreRaw;

    private List<String> getRolesCierre() {
        return Arrays.stream(rolesCierreRaw.split(","))
                .map(String::trim)
                .toList();
    }

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<TurnoCajaTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<TurnoCajaTableDto> result = turnoService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron registros");
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TurnoCajaDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        TurnoCajaDto result = turnoService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Turno encontrado", false, result), HttpStatus.OK);
    }

    @GetMapping("/activo")
    public ResponseEntity<ApiResponse<TurnoCajaDto>> obtenerTurnoActivo() {
        Long usuarioId = securityUtils.getUsuarioId();
        TurnoCajaDto result = turnoService.obtenerTurnoActivo(usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.OK.value(), "Turno activo", false, result), HttpStatus.OK);
    }

    @PostMapping("/abrir")
    public ResponseEntity<ApiResponse<TurnoCajaDto>> abrir(@Valid @RequestBody AbrirTurnoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        TurnoCajaDto result = turnoService.abrir(dto, empresaId, usuarioId);
        return new ResponseEntity<>(new ApiResponse<>(HttpStatus.CREATED.value(), "Turno abierto exitosamente", false, result), HttpStatus.CREATED);
    }

    @PatchMapping("/{id}/cerrar")
    public ResponseEntity<ApiResponse<ResumenTurnoDto>> cerrar(
            @PathVariable Long id,
            @Valid @RequestBody CerrarTurnoDto dto) {

        String rolActual = securityUtils.getRol();
        if (rolActual == null || !getRolesCierre().contains(rolActual)) {
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "No tienes permiso para cerrar el turno de caja");
        }

        Integer empresaId = securityUtils.getEmpresaId();
        ResumenTurnoDto result = turnoService.cerrar(id, dto, empresaId);
        return new ResponseEntity<>(
            new ApiResponse<>(HttpStatus.OK.value(), "Turno cerrado correctamente", false, result),
            HttpStatus.OK);
    }

    @GetMapping("/{id}/resumen")
    public ResponseEntity<ApiResponse<ResumenTurnoDto>> resumen(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        ResumenTurnoDto result = turnoService.resumen(id, empresaId);
        return new ResponseEntity<>(
            new ApiResponse<>(HttpStatus.OK.value(), "Resumen del turno", false, result),
            HttpStatus.OK);
    }

    @PostMapping("/{id}/movimientos")
    public ResponseEntity<ApiResponse<MovimientoCajaDto>> registrarMovimiento(
            @PathVariable Long id,
            @Valid @RequestBody CreateMovimientoCajaDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        Long usuarioId = securityUtils.getUsuarioId();
        MovimientoCajaDto result = turnoService.registrarMovimiento(id, dto, empresaId, usuarioId);
        return new ResponseEntity<>(
            new ApiResponse<>(HttpStatus.CREATED.value(), "Movimiento registrado", false, result),
            HttpStatus.CREATED);
    }
}
