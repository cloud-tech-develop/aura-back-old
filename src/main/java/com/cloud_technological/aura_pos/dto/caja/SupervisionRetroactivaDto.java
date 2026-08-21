package com.cloud_technological.aura_pos.dto.caja;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Lo que entró a las cajas sin ser del turno, en un período.
 *
 * <p>Los contadores van arriba a propósito: el administrador abre esto para
 * saber si tiene algo que revisar, no para leer una lista. Si los cuatro están
 * en cero, cerró la pantalla y siguió con su día.
 */
@Getter
@Setter
public class SupervisionRetroactivaDto {

    private List<MovimientoRetroactivoDto> movimientos = new ArrayList<>();

    /** Documentos viejos que entraron a la caja con autorización expresa. */
    private Integer cantidadAutorizados = 0;
    private BigDecimal montoAutorizados = BigDecimal.ZERO;

    /** Pagos cuyo documento es de otro día, dentro de la ventana de gracia. */
    private Integer cantidadOtrasFechas = 0;
    private BigDecimal montoOtrasFechas = BigDecimal.ZERO;

    /** Movimientos que cayeron en una caja que nadie eligió: la dedujo el sistema. */
    private Integer cantidadCajaInferida = 0;
    private BigDecimal montoCajaInferida = BigDecimal.ZERO;

    /** Correcciones sobre arqueos ya cerrados. */
    private Integer cantidadAjustes = 0;
    private BigDecimal montoAjustes = BigDecimal.ZERO;

    /**
     * Documentos declarados como "ya salió de la caja otro día". No descuadran
     * ningún arqueo — por eso no pasan por el freno — y este es el único sitio
     * donde quedan visibles.
     */
    private Integer cantidadSalidaOtroDia = 0;
    private BigDecimal montoSalidaOtroDia = BigDecimal.ZERO;
}
