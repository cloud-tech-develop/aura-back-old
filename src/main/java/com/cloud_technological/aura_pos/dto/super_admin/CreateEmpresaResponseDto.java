package com.cloud_technological.aura_pos.dto.super_admin;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateEmpresaResponseDto {
    private EmpresaPlataformaDto empresa;
    private String emailAdmin;
    private String passwordTemporal;
    private String resetLink;
    /** Se le envió al administrador el correo con su acceso (Resend). */
    private Boolean correoEnviado;
    /** Por qué no se envió; null si se envió. */
    private String correoError;

    public CreateEmpresaResponseDto(EmpresaPlataformaDto empresa, String emailAdmin, String passwordTemporal,
            String resetLink) {
        this(empresa, emailAdmin, passwordTemporal, resetLink, null, null);
    }
}
