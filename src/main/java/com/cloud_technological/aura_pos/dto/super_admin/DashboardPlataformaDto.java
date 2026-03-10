package com.cloud_technological.aura_pos.dto.super_admin;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardPlataformaDto {
    private Long totalEmpresas;
    private Long empresasActivas;
    private Long empresasInactivas;
    private Long nuevasEsteMes;
    private List<EmpresaTableDto> ultimasEmpresas;
}
