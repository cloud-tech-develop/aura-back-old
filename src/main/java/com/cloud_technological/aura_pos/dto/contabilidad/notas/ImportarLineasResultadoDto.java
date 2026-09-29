package com.cloud_technological.aura_pos.dto.contabilidad.notas;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Resultado de interpretar lo pegado. No guarda nada: el formulario recibe las
 * líneas resueltas y el contador decide. Las filas con error no vienen en
 * {@code lineas}, vienen explicadas en {@code errores}.
 */
@Getter @Setter
public class ImportarLineasResultadoDto {

    private List<NotaDiarioLineaDto> lineas = new ArrayList<>();
    private List<ErrorFila> errores = new ArrayList<>();

    @Getter @Setter @AllArgsConstructor @NoArgsConstructor
    public static class ErrorFila {
        /** Número de fila tal como la ve el usuario (1 = primera fila pegada). */
        private int fila;
        private String mensaje;
    }
}
