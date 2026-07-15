package com.cloud_technological.aura_pos.contabilidad.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.cloud_technological.aura_pos.contabilidad.domain.model.Asiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.EstadoAsiento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.OrigenDocumento;
import com.cloud_technological.aura_pos.contabilidad.domain.model.Partida;

/**
 * API fluida para armar asientos. Los montos nulos o en cero se ignoran
 * (los generadores agregan líneas condicionales sin ifs repetidos); los
 * negativos son error. {@link #build()} valida cuadre: es imposible obtener
 * un {@link Asiento} descuadrado.
 */
public final class AsientoBuilder {

    private final OrigenDocumento origen;
    private final LocalDate fecha;
    private final List<Partida> partidas = new ArrayList<>();
    private String descripcion;
    private String prefijoComprobante;

    public AsientoBuilder(OrigenDocumento origen, LocalDate fecha) {
        if (origen == null) {
            throw new IllegalArgumentException("El asiento requiere su documento origen");
        }
        if (fecha == null) {
            throw new IllegalArgumentException("El asiento requiere fecha");
        }
        this.origen = origen;
        this.fecha = fecha;
    }

    public AsientoBuilder descripcion(String descripcion) {
        this.descripcion = descripcion;
        return this;
    }

    /** Prefijo del consecutivo de comprobante (VT, CO, RC…). */
    public AsientoBuilder prefijo(String prefijoComprobante) {
        this.prefijoComprobante = prefijoComprobante;
        return this;
    }

    public AsientoBuilder debito(Long cuentaId, String descripcion, BigDecimal monto) {
        return debito(cuentaId, descripcion, monto, null, null);
    }

    public AsientoBuilder debito(Long cuentaId, String descripcion, BigDecimal monto, Long terceroId) {
        return debito(cuentaId, descripcion, monto, terceroId, null);
    }

    public AsientoBuilder debito(Long cuentaId, String descripcion, BigDecimal monto,
            Long terceroId, Long centroCostoId) {
        if (omitir(monto)) {
            return this;
        }
        partidas.add(new Partida(cuentaId, descripcion, monto, null, terceroId, centroCostoId));
        return this;
    }

    public AsientoBuilder credito(Long cuentaId, String descripcion, BigDecimal monto) {
        return credito(cuentaId, descripcion, monto, null, null);
    }

    public AsientoBuilder credito(Long cuentaId, String descripcion, BigDecimal monto, Long terceroId) {
        return credito(cuentaId, descripcion, monto, terceroId, null);
    }

    public AsientoBuilder credito(Long cuentaId, String descripcion, BigDecimal monto,
            Long terceroId, Long centroCostoId) {
        if (omitir(monto)) {
            return this;
        }
        partidas.add(new Partida(cuentaId, descripcion, null, monto, terceroId, centroCostoId));
        return this;
    }

    public boolean tienePartidas() {
        return !partidas.isEmpty();
    }

    /** Valida las invariantes y entrega el asiento cuadrado. */
    public Asiento build() {
        BigDecimal[] totales = ReglasAsiento.validar(partidas);
        return new Asiento(origen, fecha, descripcion, prefijoComprobante,
                EstadoAsiento.CONTABILIZADO, partidas, totales[0], totales[1]);
    }

    /** Cero o nulo se omite; negativo revienta en {@link Partida}. */
    private static boolean omitir(BigDecimal monto) {
        return monto == null || monto.signum() == 0;
    }
}
