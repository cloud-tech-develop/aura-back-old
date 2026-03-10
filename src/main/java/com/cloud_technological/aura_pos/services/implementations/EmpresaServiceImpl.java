package com.cloud_technological.aura_pos.services.implementations;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.empresas.EmpresaDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.sucursales.SucursalJPARepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.repositories.users.UsuarioJPARepository;
import com.cloud_technological.aura_pos.services.IEmpresaService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements IEmpresaService {

    private final EmpresaJPARepository empresaRepository;
    private final SucursalJPARepository sucursalRepository;
    private final UsuarioJPARepository usuarioRepository;
    private final TerceroJPARepository terceroRepository;

    @Override
    public EmpresaDto obtenerEmpresaActual(Integer empresaId, Long sucursalId, Long usuarioId) {
        EmpresaEntity empresa = empresaRepository.findById(empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));

        SucursalEntity sucursal = null;
        if (sucursalId != null) {
            sucursal = sucursalRepository.findByIdAndEmpresaId(sucursalId.intValue(), empresaId)
                    .orElse(null);
        }

        String telefono = "";
        String direccion = "";
        String municipio = "";
        String correo = "";

        // 1. Buscar el tercero de la empresa por NIT (excluye proveedores)
        TerceroEntity terceroEmpresa = null;
        if (empresa.getNit() != null) {
            terceroEmpresa = terceroRepository
                    .findEmpresaTerceroByNit(empresaId, empresa.getNit())
                    .orElse(null);
        }
        // 2. Fallback: usar el tercero del SUPER_ADMIN
        if (terceroEmpresa == null) {
            var superAdmin = usuarioRepository.findSuperAdminByEmpresaId(empresaId).orElse(null);
            if (superAdmin != null) terceroEmpresa = superAdmin.getTercero();
        }

        if (terceroEmpresa != null) {
            correo    = terceroEmpresa.getEmail()     != null ? terceroEmpresa.getEmail()     : "";
            telefono  = terceroEmpresa.getTelefono()  != null ? terceroEmpresa.getTelefono()  : "";
            direccion = terceroEmpresa.getDireccion() != null ? terceroEmpresa.getDireccion() : "";
            municipio = terceroEmpresa.getMunicipio() != null ? terceroEmpresa.getMunicipio() : "";
        }

        return EmpresaDto.builder()
                .id(empresa.getId())
                .razonSocial(empresa.getRazonSocial())
                .nombreComercial(empresa.getNombreComercial())
                .nit(empresa.getNit())
                .dv(empresa.getDv())
                .logoUrl(empresa.getLogoUrl())
                .telefono(telefono)
                .correo(correo)
                .direccion(direccion)
                .municipio(municipio)
                .facturaElectronica(empresa.isFacturaElectronica())
                .sucursalId(sucursal != null ? sucursal.getId() : null)
                .sucursalNombre(sucursal != null ? sucursal.getNombre() : null)
                .sucursalDireccion(sucursal != null ? sucursal.getDireccion() : null)
                .sucursalTelefono(sucursal != null ? sucursal.getTelefono() : null)
                .sucursalCiudad(sucursal != null ? sucursal.getCiudad() : null)
                .sucursalPrefijoFacturacion(sucursal != null ? sucursal.getPrefijoFacturacion() : null)
                .build();
    }
}
