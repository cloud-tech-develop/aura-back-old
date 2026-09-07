package com.cloud_technological.aura_pos.dto.reportes;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

/**
 * Filtros del estado de cuenta de cartera.
 *
 * <p>El mismo filtro sirve para clientes y proveedores: la pregunta es la misma
 * — quién debe qué, desde cuándo — y tener dos reportes casi iguales garantiza
 * que uno de los dos se quede atrás.
 */
@Getter
@Setter
public class ReporteCarteraFiltroDto {

    public static final String CXC = "CXC";
    public static final String CXP = "CXP";

    /** CXC (clientes) | CXP (proveedores). */
    private String tipo = CXC;

    private Integer page = 0;
    private Integer rows = 50;

    /** Rango sobre la fecha de emisión del documento. */
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;

    private Long terceroId;

    /**
     * PENDIENTE (saldo > 0) | VENCIDA | PAGADA | TODAS.
     *
     * <p>PENDIENTE por defecto: un estado de cuenta que arranca mostrando lo ya
     * pagado entierra lo que falta cobrar, que es para lo que se abre.
     */
    private String estado = "PENDIENTE";

    /** Solo documentos con al menos estos días de mora. */
    private Integer diasMoraMin;

    /** Número de documento, factura externa o nombre/NIT del tercero. */
    private String search;

    /**
     * Trae el detalle de los abonos de cada documento.
     *
     * <p>Es lo que convierte el listado en un estado de cuenta: sin los abonos,
     * un saldo parcial no se puede explicar. Se pide aparte porque multiplica
     * las filas y el resumen por tercero no lo necesita.
     */
    private Boolean incluirAbonos = Boolean.FALSE;
}
