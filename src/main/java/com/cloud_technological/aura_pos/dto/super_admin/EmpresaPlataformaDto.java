package com.cloud_technological.aura_pos.dto.super_admin;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EmpresaPlataformaDto {
    private Integer id;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private String dv;
    private String logoUrl;
    private Boolean activa;
    private LocalDateTime createdAt;
    // Stats en vivo
    private Integer totalSucursales;
    private Integer totalUsuarios;
    private Long totalVentas;
}
