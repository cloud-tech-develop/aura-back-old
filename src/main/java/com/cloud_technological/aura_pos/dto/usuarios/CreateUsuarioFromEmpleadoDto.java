package com.cloud_technological.aura_pos.dto.usuarios;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO para crear un usuario a partir de un empleado existente. El cargo del
 * empleado se usa como rol y se busca el ID del tipo de empleado.
 */
@Getter
@Setter
public class CreateUsuarioFromEmpleadoDto {

    @NotNull(message = "El ID del empleado es obligatorio")
    private Long empleadoId;

    @NotBlank(message = "El username es obligatorio")
    @Size(max = 100, message = "El username no puede exceder 100 caracteres")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 100, message = "La contraseña debe tener entre 6 y 100 caracteres")
    private String password;

    @Size(max = 6, message = "El PIN debe tener máximo 6 caracteres")
    private String pinAccesoRapido;

    // El rol se obtiene del cargo del empleado, pero se puede override si es necesario
    private String rol;

    // ID de la sucursal a asignar (requerido para iniciar sesión)
    @NotNull(message = "El ID de la sucursal es obligatorio")
    private Integer sucursalId;
}