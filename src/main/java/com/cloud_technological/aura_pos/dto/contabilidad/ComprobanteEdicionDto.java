package com.cloud_technological.aura_pos.dto.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Un comprobante manual (CE/RC) tal como lo necesita el formulario para
 * editarlo: cabecera, origen de fondos, líneas y lo que cruzó de cartera.
 */
@Getter
@Setter
public class ComprobanteEdicionDto {
    private Long id;
    private String numeroComprobante;
    private String tipoComprobante;
    private LocalDate fecha;
    private String concepto;
    private Long beneficiarioTerceroId;
    private String beneficiarioNombre;
    private String beneficiarioDireccion;
    private String beneficiarioTelefono;
    private String ciudad;
    private LocalDate fechaVencimiento;

    /** CAJA | BANCO | CUENTA | CAJA_OTRO_DIA (deducido de lo que guardó el asiento). */
    private String origenFondos;
    private String metodoPago;
    private Long cuentaBancariaId;
    private Long cuentaContableId;
    private Integer sucursalId;

    /** false con el motivo: período cerrado, turno cerrado, anulado… */
    private boolean editable;
    private String motivoNoEditable;

    private List<AsientoDetalleDto> lineas = new ArrayList<>();
    private List<Aplicacion> aplicaciones = new ArrayList<>();

    @Getter
    @Setter
    public static class Aplicacion {
        /** CXC | CXP */
        private String tipo;
        private Long cuentaId;
        private BigDecimal monto;
        private String numeroCuenta;
        private String nombre;
        private Long terceroId;
        /** Saldo de la cuenta hoy + lo que este comprobante le abonó. */
        private BigDecimal saldoDisponible;
    }
}
