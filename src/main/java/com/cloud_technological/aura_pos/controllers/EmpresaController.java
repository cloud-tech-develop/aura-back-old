package com.cloud_technological.aura_pos.controllers;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.dto.empresas.UpdateEmpresaContactoDto;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.cloud_technological.aura_pos.utils.ApiResponse;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.SecurityUtils;

@RestController
@RequestMapping("/api/empresa")
public class EmpresaController {

    @Autowired
    private IEmpresaService empresaService;

    @Autowired
    private SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<EmpresaDto>> obtenerEmpresaActual() {
        Integer empresaId = securityUtils.getEmpresaId();
        Long sucursalId = securityUtils.getSucursalId();
        Long usuarioId = securityUtils.getUsuarioId();
        EmpresaDto result = empresaService.obtenerEmpresaActual(empresaId, sucursalId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Empresa obtenida exitosamente", false, result),
                HttpStatus.OK);
    }

    /**
     * Contacto de la empresa desde "Mi perfil". Cualquiera puede verla; solo el
     * administrador la cambia, y se valida aqui porque {@code @PreAuthorize}
     * todavia no esta activo en este proyecto.
     */
    @PutMapping("/contacto")
    public ResponseEntity<ApiResponse<EmpresaDto>> actualizarContacto(
            @Valid @RequestBody UpdateEmpresaContactoDto dto) {
        exigirAdministrador();
        Integer empresaId = securityUtils.getEmpresaId();
        Long sucursalId = securityUtils.getSucursalId();
        Long usuarioId = securityUtils.getUsuarioId();
        EmpresaDto result = empresaService.actualizarContacto(dto, empresaId, sucursalId, usuarioId);
        return new ResponseEntity<>(
                new ApiResponse<>(HttpStatus.OK.value(), "Datos de la empresa actualizados", false, result),
                HttpStatus.OK);
    }

    private void exigirAdministrador() {
        String rol = securityUtils.getRol();
        boolean admin = rol != null
                && ("ADMIN".equalsIgnoreCase(rol) || "SUPER_ADMIN".equalsIgnoreCase(rol));
        if (!admin)
            throw new GlobalException(HttpStatus.FORBIDDEN,
                    "Solo un administrador puede cambiar los datos de la empresa");
    }
}
