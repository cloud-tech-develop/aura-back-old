package com.cloud_technological.aura_pos.contabilidad.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.contabilidad.domain.AsientoBuilder;

/**
 * Raíz de agregado del asiento contable. Solo se construye vía
 * {@link AsientoBuilder}, que garantiza las invariantes: un {@code Asiento}
 * en memoria SIEMPRE está cuadrado. Inmutable salvo la transición de estado.
 */
public final class Asiento {

    private final OrigenDocumento origen;
    private final LocalDate fecha;
    private final String descripcion;
    private final String prefijoComprobante;
    private final EstadoAsiento estado;
    private final List<Partida> partidas;
    private final BigDecimal totalDebito;
    private final BigDecimal totalCredito;

    /** Uso interno del builder; los generadores usan {@link #builder}. */
    public Asiento(OrigenDocumento origen, LocalDate fecha, String descripcion,
            String prefijoComprobante, EstadoAsiento estado, List<Partida> partidas,
            BigDecimal totalDebito, BigDecimal totalCredito) {
        this.origen = origen;
        this.fecha = fecha;
        this.descripcion = descripcion;
        this.prefijoComprobante = prefijoComprobante;
        this.estado = estado;
        this.partidas = List.copyOf(partidas);
        this.totalDebito = totalDebito;
        this.totalCredito = totalCredito;
    }

    public static AsientoBuilder builder(OrigenDocumento origen, LocalDate fecha) {
        return new AsientoBuilder(origen, fecha);
    }

    /** Copia con otro estado (BORRADOR ↔ CONTABILIZADO según el modo de la empresa). */
    public Asiento conEstado(EstadoAsiento nuevoEstado) {
        return new Asiento(origen, fecha, descripcion, prefijoComprobante,
                nuevoEstado, partidas, totalDebito, totalCredito);
    }

    public OrigenDocumento origen() {
        return origen;
    }

    public LocalDate fecha() {
        return fecha;
    }

    public String descripcion() {
        return descripcion;
    }

    public String prefijoComprobante() {
        return prefijoComprobante;
    }

    public EstadoAsiento estado() {
        return estado;
    }

    public List<Partida> partidas() {
        return partidas;
    }

    public BigDecimal totalDebito() {
        return totalDebito;
    }

    public BigDecimal totalCredito() {
        return totalCredito;
    }
}
