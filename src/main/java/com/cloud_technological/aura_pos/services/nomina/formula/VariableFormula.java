package com.cloud_technological.aura_pos.services.nomina.formula;

import java.util.Arrays;
import java.util.Optional;

/**
 * Variables que una fórmula puede usar. Es la lista completa: un nombre que no
 * esté aquí es un error al guardar la fórmula.
 */
public enum VariableFormula {

    SALARIO_BASICO("Salario mensual del contrato"),
    SALARIO_PROPORCIONAL("Salario de los días liquidados"),
    DIAS_LIQUIDADOS("Días de salario del período"),
    SMMLV("Salario mínimo mensual vigente"),
    AUX_TRANSPORTE("Auxilio de transporte mensual vigente"),
    AUXILIO_PERIODO("Auxilio de transporte de los días liquidados"),
    BASE_IBC("Base de seguridad social (sin auxilio, con topes)"),
    BASE_PRESTACIONAL("Base de prima y cesantías (salario + auxilio + lo que hace base)"),
    DEVENGADO_TOTAL("Todo lo devengado del período"),
    NOVEDADES_SALARIALES("Novedades y conceptos que constituyen IBC"),
    NOVEDADES_NO_SALARIALES("Novedades y conceptos que no constituyen IBC"),
    ANTIGUEDAD_ANIOS("Años completos desde el inicio de la vinculación"),
    ANTIGUEDAD_MESES("Meses completos desde el inicio de la vinculación");

    private final String descripcion;

    VariableFormula(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** La variable por su nombre exacto (en mayúsculas). */
    public static Optional<VariableFormula> de(String nombre) {
        return Arrays.stream(values()).filter(v -> v.name().equals(nombre)).findFirst();
    }
}
