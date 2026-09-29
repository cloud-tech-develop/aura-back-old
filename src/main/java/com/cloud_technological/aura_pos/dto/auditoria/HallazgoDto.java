package com.cloud_technological.aura_pos.dto.auditoria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Un hallazgo: dos fuentes que deberían decir lo mismo y no lo dicen.
 *
 * <p>Siempre lleva {@link #monto}. "12 documentos con problemas" no mueve a
 * nadie; "$4.300.000 en cartera cuyo saldo no cuadra con sus abonos" sí. Cuando
 * el hallazgo no tiene plata asociada — un producto sin categoría contable — el
 * monto va en cero y lo que manda es la cantidad.
 */
@Getter
@Setter
public class HallazgoDto {

    /** Identificador estable, para que el front y el PDF puedan reaccionar. */
    private String codigo;
    private String titulo;

    /** Qué significa el desacuerdo, en lenguaje del dueño del negocio. */
    private String descripcion;

    /** Las dos fuentes que se compararon. Es lo que hace defendible la cifra. */
    private String cruce;

    /** Qué hacer con esto. Un hallazgo sin salida es solo una queja. */
    private String recomendacion;

    private Severidad severidad;
    private AreaAuditoria area;

    private Integer cantidad = 0;
    private BigDecimal monto = BigDecimal.ZERO;

    /**
     * La magnitud cuando el monto no cuenta la historia.
     *
     * <p>La primera corrida real encontró un producto con 10 unidades que su
     * kardex no explica, valorizadas en $0 porque el producto no tiene costo
     * cargado. Un hallazgo que dice "$0" se lee como "no pasa nada", cuando lo
     * que pasa es que hay diez unidades sin origen. Aquí va "10 unidades".
     */
    private String magnitud;

    /**
     * Por qué suele pasar esto, en orden de probabilidad.
     *
     * <p>Es lo que separa un dato de un análisis. "Sobrante de $954.076" no le
     * dice a nadie qué hacer; "un sobrante de ese tamaño casi siempre son ventas
     * cobradas que no pasaron por el POS" manda a alguien a mirar el sitio
     * correcto.
     *
     * <p>Las causas están ordenadas por frecuencia real en este sistema, no por
     * gravedad: quien revisa empieza por arriba.
     */
    private List<String> causasProbables = new ArrayList<>();

    /**
     * Viene de antes de los arreglos conocidos, no de la operación del período.
     * Va en su propia sección del reporte.
     */
    private Boolean heredado = Boolean.FALSE;

    private List<HallazgoDetalleDto> detalle = new ArrayList<>();

    /**
     * Evidencia de otra área que ayuda a explicar este hallazgo.
     *
     * <p>Un sobrante de caja se entiende mirando qué pasó con el inventario en
     * ese mismo turno: si salió mercancía como merma o como ajuste de reconteo
     * mientras entraba plata de más, las dos cosas cuentan la misma historia.
     * Por separado, cada una parece un problema distinto.
     */
    private List<HallazgoDetalleDto> contexto = new ArrayList<>();

    /** Qué es el contexto, para titularlo en el reporte. */
    private String contextoTitulo;

    /** Un hallazgo sin filas no se reporta: no hay nada que mirar. */
    public boolean tieneAlgo() {
        return cantidad != null && cantidad > 0;
    }
}
