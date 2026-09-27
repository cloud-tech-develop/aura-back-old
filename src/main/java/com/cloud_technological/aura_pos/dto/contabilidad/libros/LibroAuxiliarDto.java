package com.cloud_technological.aura_pos.dto.contabilidad.libros;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Libro auxiliar por tercero: por cada cuenta, cada tercero con su saldo
 * anterior, sus movimientos del rango y su saldo final.
 *
 * <p>Los saldos van según la naturaleza de la cuenta (débito − crédito en las
 * de naturaleza débito, al revés en las crédito), que es como los lee un
 * contador: el proveedor que se le debe sale positivo, no negativo.
 */
@Data
public class LibroAuxiliarDto {

    /** Encabezado del libro impreso. */
    private String empresaNombre;
    private String nit;
    private LocalDate desde;
    private LocalDate hasta;
    private List<Cuenta> cuentas = new ArrayList<>();
    private BigDecimal totalDebito = BigDecimal.ZERO;
    private BigDecimal totalCredito = BigDecimal.ZERO;

    @Data
    public static class Cuenta {
        private Long cuentaId;
        private String codigo;
        private String nombre;
        /** DEBITO | CREDITO: define el signo de los saldos. */
        private String naturaleza;
        private BigDecimal saldoAnterior = BigDecimal.ZERO;
        private BigDecimal debito = BigDecimal.ZERO;
        private BigDecimal credito = BigDecimal.ZERO;
        private BigDecimal saldoFinal = BigDecimal.ZERO;
        private List<Tercero> terceros = new ArrayList<>();
    }

    @Data
    public static class Tercero {
        /** null: movimientos sin tercero (caja, bancos, cierres…). */
        private Long terceroId;
        private String documento;
        private String nombre;
        private BigDecimal saldoAnterior = BigDecimal.ZERO;
        private BigDecimal debito = BigDecimal.ZERO;
        private BigDecimal credito = BigDecimal.ZERO;
        private BigDecimal saldoFinal = BigDecimal.ZERO;
        private List<Linea> lineas = new ArrayList<>();
    }

    @Data
    public static class Linea {
        private LocalDate fecha;
        private String numeroComprobante;
        private String tipoOrigen;
        private String descripcion;
        private BigDecimal debito;
        private BigDecimal credito;
        /** Saldo corrido del tercero en esa cuenta después de la línea. */
        private BigDecimal saldo;
    }
}
