package com.cloud_technological.aura_pos.dto.nomina.vacaciones;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Resultado de las vacaciones colectivas: cuántas se registraron y a quién no, con la causa. */
@Getter
@Setter
public class ResultadoColectivasDto {
    private int registradas;
    private String lote;
    private List<String> omitidos = new ArrayList<>();
}
