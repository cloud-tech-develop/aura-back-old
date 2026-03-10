package com.cloud_technological.aura_pos.dto.categorias;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoriaTableDto {
    private Long id;
    private String nombre;
    private String nombrePadre; // Para mostrar jerarquía
    private BigDecimal impuestoDefecto;
    private Boolean activo;
    
    @JsonIgnore
    private Long totalRows;
}