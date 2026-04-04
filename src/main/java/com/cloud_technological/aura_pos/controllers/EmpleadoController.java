package com.cloud_technological.aura_pos.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.nomina.empleado.CreateEmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoDto;
import com.cloud_technological.aura_pos.dto.nomina.empleado.EmpleadoTableDto;
import com.cloud_technological.aura_pos.services.EmpleadoService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/empleados")
public class EmpleadoController {

    @Autowired
    private EmpleadoService empleadoService;

    @Autowired
    private SecurityUtils securityUtils;

    @PostMapping("/page")
    public ResponseEntity<ApiResponse<PageImpl<EmpleadoTableDto>>> listar(
            @RequestBody PageableDto<Object> pageable) {
        Integer empresaId = securityUtils.getEmpresaId();
        PageImpl<EmpleadoTableDto> result = empleadoService.listar(pageable, empresaId);
        if (result.isEmpty())
            throw new GlobalException(HttpStatus.PARTIAL_CONTENT, "No se encontraron empleados");
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Listado exitoso", false, result),
                HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<EmpleadoDto>> obtenerPorId(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        EmpleadoDto result = empleadoService.obtenerPorId(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Empleado encontrado", false, result),
                HttpStatus.OK);
    }

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<EmpleadoDto>> crear(@RequestBody CreateEmpleadoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        EmpleadoDto result = empleadoService.crear(dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.CREATED.value(), "Empleado creado exitosamente", false, result),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<EmpleadoDto>> actualizar(
            @PathVariable Long id,
            @RequestBody CreateEmpleadoDto dto) {
        Integer empresaId = securityUtils.getEmpresaId();
        EmpleadoDto result = empleadoService.actualizar(id, dto, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Empleado actualizado", false, result),
                HttpStatus.OK);
    }

    @PutMapping("/{id}/retirar")
    public ResponseEntity<ApiResponse<Void>> retirar(@PathVariable Long id) {
        Integer empresaId = securityUtils.getEmpresaId();
        empleadoService.retirar(id, empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Empleado retirado correctamente", false, null),
                HttpStatus.OK);
    }

    @GetMapping("/vendedores")
    public ResponseEntity<ApiResponse<List<EmpleadoDto>>> listarVendedores() {
        Integer empresaId = securityUtils.getEmpresaId();
        List<EmpleadoDto> result = empleadoService.listarVendedores(empresaId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Vendedores obtenidos", false, result),
                HttpStatus.OK);
    }
}
