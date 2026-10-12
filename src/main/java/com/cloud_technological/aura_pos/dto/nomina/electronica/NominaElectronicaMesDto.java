package com.cloud_technological.aura_pos.dto.nomina.electronica;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Nómina electrónica MENSUAL de un empleado: qué nóminas confirmadas del mes junta,
 * si el mes ya está completo para enviarlo a la DIAN y el estado de su documento.
 * La usan la modal de nómina electrónica y la vista por mes.
 */
@Getter
@Setter
public class NominaElectronicaMesDto {

    private Long contratoId;
    private String empleadoNombre;
    private String empleadoDocumento;
    private Integer agno;
    private Integer mes;
    /** Las nóminas confirmadas cubren todo el mes del empleado: se puede enviar. */
    private boolean completo;
    /** Por qué no está completo (p. ej. falta la 2ª quincena). */
    private String motivo;
    /** Última nómina del mes: con ella se abre la modal y se envía. */
    private Long ultimaNominaId;
    private BigDecimal totalDevengado;
    /** Cuántas nóminas confirmadas tiene el mes. */
    private int cantidadNominas;
    /** Detalle de esas nóminas (solo en la consulta de un empleado, no en la vista por mes). */
    private List<NominaDelMes> nominas;

    /** Documento electrónico del mes (null si no se ha enviado). */
    private Long documentoId;
    /** ACEPTADO | ANULADO | RECHAZADO… */
    private String documentoEstado;
    private String documentoNumero;
    private String cune;

    /** Una nómina confirmada que entra en el documento del mes. */
    @Getter
    @Setter
    public static class NominaDelMes {
        private Long nominaId;
        /** Número del documento de nómina (NOM-00002); null en nóminas viejas sin documento. */
        private String numero;
        private LocalDate periodoInicio;
        private LocalDate periodoFin;
        /** PERIODICO | PRIMA | LIQUIDACION_DEFINITIVA | … */
        private String clase;
        private BigDecimal totalDevengado;
    }
}
