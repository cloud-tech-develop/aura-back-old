package com.cloud_technological.aura_pos.dto.usuarios;

import java.util.List;

import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateUsuarioDto {
    @Size(max = 100)
    private String username;

    @Size(max = 100)
    private String password; // null = no cambia

    @Size(max = 6)
    private String pinAccesoRapido;

    private String rol;
    /** Perfil de permisos; null = se conserva (o pasa al del rol nuevo si tenía el del rol anterior). */
    private Long perfilId;

    /** Cambiar la persona del usuario (otro tercero de la empresa); null = no cambia. */
    private Long terceroId;

    private String nombres;
    private String telefono;
    private String email;

    private Boolean activo;

    // Si viene null = no tocar sucursales; si viene lista = reemplazar
    private List<CreateUsuarioDto.SucursalAsignacion> sucursales;
}
