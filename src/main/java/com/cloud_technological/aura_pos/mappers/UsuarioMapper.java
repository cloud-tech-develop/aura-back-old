package com.cloud_technological.aura_pos.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.cloud_technological.aura_pos.dto.usuarios.CreateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UpdateUsuarioDto;
import com.cloud_technological.aura_pos.dto.usuarios.UsuarioDto;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;

@Mapper(componentModel = "spring", uses = {TerceroMapper.class})
public interface UsuarioMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    @Mapping(target = "tercero", ignore = true)
    @Mapping(target = "empleado", ignore = true)
    @Mapping(target = "tipoEmpleado", ignore = true)
    @Mapping(target = "activo", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "sucursalesAsignadas", ignore = true)
    UsuarioEntity toEntity(CreateUsuarioDto dto);

    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "empresa", ignore = true)
    @Mapping(target = "tercero", ignore = true)
    @Mapping(target = "empleado", ignore = true)
    @Mapping(target = "tipoEmpleado", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "username", source="dto.email")
    @Mapping(target = "sucursalesAsignadas", ignore = true)
    @Mapping(target = "password", ignore = true)
    void updateEntityFromDto(UpdateUsuarioDto dto, @MappingTarget UsuarioEntity entity);

    @Mapping(target = "nombres", source = "tercero.nombres")
    @Mapping(target = "apellidos", source = "tercero.apellidos")
    @Mapping(target = "tipoDocumento", source = "tercero.tipoDocumento")
    @Mapping(target = "numeroDocumento", source = "tercero.numeroDocumento")
    @Mapping(target = "telefono", source = "tercero.telefono")
    @Mapping(target = "email", source = "tercero.email")
    @Mapping(target = "sucursales", ignore = true)
    @Mapping(target = "empleadoId", expression = "java(entity.getEmpleado() != null ? entity.getEmpleado().getId() : null)")
    @Mapping(target = "tipoEmpleadoId", expression = "java(entity.getTipoEmpleado() != null ? entity.getTipoEmpleado().getId() : null)")
    @Mapping(target = "tipoEmpleadoNombre", expression = "java(entity.getTipoEmpleado() != null ? entity.getTipoEmpleado().getNombre() : null)")
    UsuarioDto toDto(UsuarioEntity entity);

    default TerceroEntity mapTerceroFromCreateDto(CreateUsuarioDto dto) {
        if (dto == null) {
            return null;
        }
        return TerceroEntity.builder()
                .nombres(dto.getNombres())
                .apellidos(dto.getApellidos())
                .tipoDocumento(dto.getTipoDocumento())
                .numeroDocumento(dto.getNumeroDocumento())
                .telefono(dto.getTelefono())
                .email(dto.getEmail())
                .esCliente(false)
                .esEmpleado(true)
                .activo(true)
                .build();
    }

    default void updateTerceroFromUpdateDto(UpdateUsuarioDto dto, TerceroEntity tercero) {
        if (dto == null || tercero == null) {
            return;
        }
        if (dto.getNombres() != null) {
            tercero.setNombres(dto.getNombres());
        }
        if (dto.getTelefono() != null) {
            tercero.setTelefono(dto.getTelefono());
        }
        if (dto.getEmail() != null) {
            tercero.setEmail(dto.getEmail());
        }
    }
}
