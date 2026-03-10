package com.cloud_technological.aura_pos.dto.super_admin;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpresaTableDto {
    private Integer id;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private Boolean activa;
    private LocalDateTime createdAt;
    private Integer totalSucursales;
    private Integer totalUsuarios;
    private Long totalRows;
}
