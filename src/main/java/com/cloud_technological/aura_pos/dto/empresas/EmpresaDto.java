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
    // Rangos de numeración en Factus
    private Integer factusNumberingRangeId;
    private String  factusNominaNumberingRangeId;
    /** El back decide si quien consulta puede editar (caja.usuarios:EDITAR_EMPRESA). */
    private Boolean puedeEditar;
    /** La empresa es una firma contable (menú "Clientes de la firma"). */
    private Boolean esFirmaContable;
    /** NATURAL | JURIDICA. */
    private String tipoPersona;
    /** Persona natural no responsable de IVA: puede emitir cuentas de cobro. */
    private Boolean puedeCuentaCobro;
    private Integer sucursalId;
    private String sucursalNombre;
    private String sucursalDireccion;
    private String sucursalTelefono;
    private String sucursalCiudad;
    private String sucursalPrefijoFacturacion;
}
