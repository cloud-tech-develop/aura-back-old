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
}
