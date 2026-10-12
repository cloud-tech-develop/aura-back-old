package com.cloud_technological.aura_pos.dto.nomina.documento;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** Resultado de agregar empleados: cuántos entraron y cuáles se omitieron con su causa. */
@Getter
@Setter
public class ResultadoAgregarEmpleadosDto {
    private int agregados;
    private List<CandidatoNominaDto> omitidos = new ArrayList<>();
}
