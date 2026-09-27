package com.cloud_technological.aura_pos.dto.carrito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Carritos del POS que se vaciaron sin vender. */
public final class CarritoAbandonadoDtos {

    private CarritoAbandonadoDtos() {
    }

    /** Lo que manda el POS al vaciar un carrito con productos. */
    @Data
    public static class Registrar {
        private Integer sucursalId;
        private Long turnoCajaId;
        private Long clienteId;
        /** VACIADO | PESTANA_CERRADA */
        private String motivo;
        /** Cuándo se agregó el primer producto (hora del navegador). */
        private LocalDateTime iniciadoAt;
        /** Cuándo se vació (hora del navegador, la misma fuente que iniciadoAt). */
        private LocalDateTime vaciadoAt;
        private List<Item> items = new ArrayList<>();
    }

    @Data
    public static class Item {
        private Long productoId;
        private Long presentacionId;
        private String nombre;
        private BigDecimal cantidad;
        private BigDecimal precio;
        private BigDecimal subtotal;
        private LocalDateTime agregadoAt;
    }

    // ── Reporte ─────────────────────────────────────────────────────────────

    @Data
    public static class Reporte {
        private LocalDate desde;
        private LocalDate hasta;
        private int minutosMinimos;
        private int carritos;
        private BigDecimal valor = BigDecimal.ZERO;
        private int productos;
        /** Minutos promedio que duró armado un carrito antes de vaciarse. */
        private BigDecimal minutosPromedio = BigDecimal.ZERO;
        /** Carritos vaciados en menos del mínimo: no cuentan como abandono. */
        private int descartadosPorTiempo;
        private List<ProductoAbandonado> topProductos = new ArrayList<>();
        private List<PorCajero> porCajero = new ArrayList<>();
        private List<Carrito> lista = new ArrayList<>();
    }

    @Data
    public static class ProductoAbandonado {
        private Long productoId;
        private String nombre;
        private BigDecimal cantidad;
        private BigDecimal valor;
        private int carritos;
    }

    @Data
    public static class PorCajero {
        private Integer usuarioId;
        private String usuario;
        private int carritos;
        private BigDecimal valor;
    }

    @Data
    public static class Carrito {
        private Long id;
        private LocalDateTime iniciadoAt;
        private LocalDateTime vaciadoAt;
        private BigDecimal minutos;
        private String motivo;
        private String usuario;
        private String cliente;
        private String sucursal;
        private int items;
        private BigDecimal total;
        private List<Item> detalle = new ArrayList<>();
    }
}
