package com.cloud_technological.aura_pos.contabilidad.application.generador;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Mapa tipoOrigen → generador. Spring lo puebla solo con todos los
 * {@link GeneradorAsiento} del contexto: agregar un origen nuevo es crear
 * una clase, no tocar este registry ni el caso de uso.
 */
@Component
public class GeneradorRegistry {

    private final Map<String, GeneradorAsiento> porTipo;

    public GeneradorRegistry(List<GeneradorAsiento> generadores) {
        this.porTipo = generadores.stream().collect(Collectors.toUnmodifiableMap(
                GeneradorAsiento::tipoOrigen, g -> g));
    }

    public boolean soporta(String tipoOrigen) {
        return porTipo.containsKey(tipoOrigen);
    }

    public GeneradorAsiento para(String tipoOrigen) {
        GeneradorAsiento generador = porTipo.get(tipoOrigen);
        if (generador == null) {
            throw new IllegalArgumentException(
                    "Ningún generador de asientos soporta el tipo de origen '" + tipoOrigen + "'");
        }
        return generador;
    }
}
