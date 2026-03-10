package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import com.cloud_technological.aura_pos.dto.auth.RegisterRequestDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;

@Mapper(componentModel = "spring")
public interface AuthMapper {
    // 1. Mapear Empresa
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "nit", source = "dto.nit"),
        @Mapping(target = "razonSocial", source = "dto.razonSocial"),
        @Mapping(target = "activa", constant = "true"), // Por defecto activa
        @Mapping(target = "configuracion", ignore = true), // Se llena luego o null
        @Mapping(target = "logoUrl", ignore = true)
    })
    EmpresaEntity toEmpresaEntity(RegisterRequestDto dto);

    // 2. Mapear Sucursal
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "nombre", source = "dto.nombreSucursal"),
        @Mapping(target = "codigo", constant = "001"), // Primera sucursal
        @Mapping(target = "activa", constant = "true"),
        @Mapping(target = "empresa", ignore = true), // Se asigna en el servicio
        @Mapping(target = "direccion", ignore = true),
        @Mapping(target = "ciudad", ignore = true),
        @Mapping(target = "telefono", ignore = true)
    })
    SucursalEntity toSucursalEntity(RegisterRequestDto dto);

    // 3. Mapear Tercero (Datos Personales)
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "tipoDocumento", constant = "CC"), // O lo recibes en el DTO
        @Mapping(target = "numeroDocumento", source = "dto.numeroDocumento"),
        @Mapping(target = "nombres", source = "dto.nombres"),
        @Mapping(target = "apellidos", source = "dto.apellidos"),
        @Mapping(target = "email", source = "dto.email"),
        @Mapping(target = "esEmpleado", constant = "true"), // Es el admin
        @Mapping(target = "activo", constant = "true"),
        @Mapping(target = "empresa", ignore = true) // Se asigna en el servicio
    })
    TerceroEntity toTerceroEntity(RegisterRequestDto dto);

    // 4. Mapear Usuario (Credenciales)
    // Nota: El password se encripta en el servicio, aquí pasamos null o ignoramos
    @Mappings({
        @Mapping(target = "id", ignore = true),
        @Mapping(target = "createdAt", ignore = true),
        @Mapping(target = "username", source = "dto.email"),
        @Mapping(target = "rol", constant = "SUPER_ADMIN"),
        @Mapping(target = "activo", constant = "true"),
        @Mapping(target = "empresa", ignore = true),
        @Mapping(target = "tercero", ignore = true),
        @Mapping(target = "sucursalesAsignadas", ignore = true),
        @Mapping(target = "password", ignore = true) // ¡Ojo! Lo seteamos manual en Service con encoder
    })
    UsuarioEntity toUsuarioEntity(RegisterRequestDto dto);
}
