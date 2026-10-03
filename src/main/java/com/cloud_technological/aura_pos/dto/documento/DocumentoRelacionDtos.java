package com.cloud_technological.aura_pos.dto.documento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** DTOs de la cadena documental (fase D0). */
public final class DocumentoRelacionDtos {

    private DocumentoRelacionDtos() {
    }

    /** Una relación vista desde un documento, en cualquiera de los dos sentidos. */
    @Data
    public static class Relacionado {
        private Long relacionId;
        /** Tipo del documento del OTRO extremo de la relación. */
        private String tipo;
        /** Id del documento del otro extremo (para armar el enlace en el front). */
        private Long id;
        /** Número visible del otro documento (COT-0001, FE-120…); null si el tipo aún no lo resuelve. */
        private String numero;
        /** Línea del otro extremo, si la relación es por línea. */
        private Long lineaId;
        /** Línea de ESTE documento involucrada en la relación, si aplica. */
        private Long lineaPropiaId;
        private BigDecimal cantidad;
        private BigDecimal valor;
        /** VIGENTE | ANULADA */
        private String estado;
        private LocalDateTime createdAt;
    }

    /** Respuesta del endpoint de relacionados: hacia atrás y hacia adelante. */
    @Data
    public static class Relacionados {
        /** De dónde viene este documento (documentos origen). */
        private List<Relacionado> origenes = new ArrayList<>();
        /** A dónde fue este documento (documentos destino). */
        private List<Relacionado> destinos = new ArrayList<>();
    }
}
