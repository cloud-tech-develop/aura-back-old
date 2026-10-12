package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Qué filas liquidar: las dadas, o (si vienen vacías) todas o solo las pendientes. */
@Getter
@Setter
public class LiquidarDocumentoDto {
    private List<Long> nominaIds = new ArrayList<>();

    /** Con {@code nominaIds} vacío: solo las filas aún no liquidadas (nuevas o con error). */
    private boolean soloPendientes;
}
