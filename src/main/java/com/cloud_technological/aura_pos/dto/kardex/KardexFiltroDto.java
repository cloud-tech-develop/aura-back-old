package com.cloud_technological.aura_pos.dto.kardex;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KardexFiltroDto {
    private Integer page = 0;
    private Integer rows = 20;
    private Long productoId;       // filtro obligatorio recomendado
    private Long sucursalId;       // filtro opcional
    private Long loteId;           // filtro opcional
    private String tipoMovimiento; // filtro opcional
    private LocalDateTime fechaDesde;
    private LocalDateTime fechaHasta;

    /**
     * Nombre, SKU, código de barras o referencia del documento de origen.
     *
     * <p>El front ya lo mandaba desde siempre, pero el DTO no lo tenía: entraba
     * en el JSON y se descartaba en silencio, así que la caja de búsqueda del
     * kardex no filtraba nada.
     */
    private String search;

    private Long categoriaId;
    private Long marcaId;

    /** ENTRADA | SALIDA | MIXTO — atajo para no listar tipo por tipo. */
    private String grupoMovimiento;

    /**
     * Varios tipos a la vez. {@code tipoMovimiento} solo admitía uno, así que
     * no había forma de pedir "todas las anulaciones" ni de combinar merma con
     * obsequio. Si viene, manda sobre {@code tipoMovimiento}.
     */
    private List<String> tiposMovimiento;
}
