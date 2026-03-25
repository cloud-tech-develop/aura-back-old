package com.cloud_technological.aura_pos.dto.empresas;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmpresaDto {
    private Integer id;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private String dv;
    private String logoUrl;
    private String telefono;
    private String correo;
    private String direccion;
    private String municipio;
    private Boolean facturaElectronica;
    // Resolución de facturación electrónica
    private String  resolucionNumero;
    private String  resolucionPrefijo;
    private Integer resolucionDesde;
    private Integer resolucionHasta;
    private String  resolucionFechaDesde;
    private String  resolucionFechaHasta;
    private Integer sucursalId;
    private String sucursalNombre;
    private String sucursalDireccion;
    private String sucursalTelefono;
    private String sucursalCiudad;
    private String sucursalPrefijoFacturacion;
}
