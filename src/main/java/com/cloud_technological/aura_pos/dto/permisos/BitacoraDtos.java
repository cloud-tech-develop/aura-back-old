package com.cloud_technological.aura_pos.dto.permisos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Bitácora de auditoría y autorizaciones (V192, fases P7 y P8 de docs/PLAN_PERMISOS.md). */
public final class BitacoraDtos {

    private BitacoraDtos() {
    }

    @Data
    public static class Filtro {
        private LocalDate desde;
        private LocalDate hasta;
        private Integer usuarioId;
        /** Módulo (ventas) o submódulo (ventas.ventas). */
        private String clave;
        private String accion;
        private String entidad;
        private String entidadId;
        /** Busca en la descripción. */
        private String texto;
        private Integer page = 0;
        private Integer rows = 50;
    }

    @Data
    public static class Evento {
        private Long id;
        private LocalDateTime fecha;
        private Integer usuarioId;
        private String usuario;
        private String autorizadoPor;
        private String clave;
        private String accion;
        private String entidad;
        private String entidadId;
        private String descripcion;
        private String antes;
        private String despues;
        private String metodo;
        private String ruta;
        private String ip;
        private String origen;
    }

    @Data
    public static class Pagina {
        private List<Evento> items = new ArrayList<>();
        private long total;
    }

    /**
     * El cajero usa el código que le dictó el supervisor. Nunca viaja una clave:
     * el código lo generó el supervisor en su sesión, vence en 2 minutos y sirve
     * una sola vez.
     */
    @Data
    public static class UsarCodigo {
        private String codigo;
        /** Descuento que necesita (el mayor de la venta), en %. */
        private BigDecimal descuentoPct;
        /** Rebaja de precio que necesita (la mayor de la venta), en %. */
        private BigDecimal rebajaPct;
        private String motivo;
    }

    /** El cajero pide aprobación remota al supervisor. */
    @Data
    public static class SolicitarAutorizacion {
        private BigDecimal descuentoPct;
        private BigDecimal rebajaPct;
        /** Qué pasa el límite (el detalle del exceso). */
        private List<String> detalle = new ArrayList<>();
        private String motivo;
    }

    /** Código que genera el supervisor para dictarlo. */
    @Data
    public static class CodigoGenerado {
        private String codigo;
        private LocalDateTime expiraEn;
        /** Hasta cuánto cubre: los límites del supervisor (null = sin límite). */
        private BigDecimal descuentoMaxPct;
        private BigDecimal rebajaPrecioMaxPct;
    }

    /** Estado de una solicitud remota, para que el POS espere la respuesta. */
    @Data
    public static class EstadoSolicitud {
        private Long id;
        /** SOLICITADA | VIGENTE (aprobada) | RECHAZADA | VENCIDA */
        private String estado;
        private String autorizador;
        private LocalDateTime expiraEn;
    }

    /** Una solicitud pendiente, en la bandeja del supervisor. */
    @Data
    public static class SolicitudPendiente {
        private Long id;
        private String solicitante;
        private BigDecimal descuentoPct;
        private BigDecimal rebajaPct;
        private String detalle;
        private String motivo;
        private LocalDateTime fecha;
        private LocalDateTime expiraEn;
    }

    @Data
    public static class AutorizacionDada {
        private Long autorizacionId;
        private String autorizador;
        private LocalDateTime expiraEn;
    }

    /** Lo que la venta necesita autorizar, calculado igual que el back lo va a revisar. */
    @Data
    public static class Exceso {
        private BigDecimal descuentoPct = BigDecimal.ZERO;
        private BigDecimal rebajaPct = BigDecimal.ZERO;
        private BigDecimal descuentoMaxPct;
        private BigDecimal rebajaPrecioMaxPct;
        /** Texto para el usuario: qué línea pasa el límite y por cuánto. */
        private List<String> detalle = new ArrayList<>();

        @com.fasterxml.jackson.annotation.JsonProperty("requiereAutorizacion")
        public boolean requiereAutorizacion() {
            return (descuentoMaxPct != null && descuentoPct.compareTo(descuentoMaxPct) > 0)
                    || (rebajaPrecioMaxPct != null && rebajaPct.compareTo(rebajaPrecioMaxPct) > 0);
        }
    }
}
