package com.cloud_technological.aura_pos.services.nomina.catalogos;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.JsonMapas;

/**
 * Valida y serializa los atributos propios de un catálogo de nómina según la
 * definición de su {@link TipoCatalogoNomina}. Se guardan como JSON en texto (la
 * columna es TEXT: asignar un mapa a una columna jsonb falla al confirmar).
 */
@Component
public class AtributosCatalogoValidator {

    /**
     * Deja solo las llaves que el tipo declara, convertidas a su tipo de dato.
     *
     * @param tipo      catálogo
     * @param recibidos atributos que llegaron del front (puede ser null)
     * @return mapa limpio, en el orden de la definición
     * @throws GlobalException 400 si falta un obligatorio, el tipo no corresponde,
     *                         el valor sale del rango o no está entre las opciones
     */
    public Map<String, Object> validar(TipoCatalogoNomina tipo, Map<String, Object> recibidos) {
        Map<String, Object> entrada = recibidos != null ? recibidos : Map.of();
        Map<String, Object> limpio = new LinkedHashMap<>();
        for (AtributoCatalogo a : tipo.getAtributos()) {
            Object valor = entrada.get(a.nombre());
            boolean vacio = valor == null || (valor instanceof String s && s.isBlank());
            if (vacio) {
                if (a.obligatorio() && a.tipo() != AtributoCatalogo.Tipo.BOOLEANO)
                    throw new GlobalException(HttpStatus.BAD_REQUEST, "Falta el dato: " + a.etiqueta());
                if (a.tipo() == AtributoCatalogo.Tipo.BOOLEANO) limpio.put(a.nombre(), false);
                continue;
            }
            limpio.put(a.nombre(), convertir(a, valor));
        }
        return limpio;
    }

    /**
     * JSON en texto para guardar en la columna {@code atributos}.
     *
     * @param atributos mapa ya validado
     * @return JSON, o null si no hay atributos
     */
    public String aJson(Map<String, Object> atributos) {
        try {
            return JsonMapas.aJson(atributos);
        } catch (IllegalArgumentException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Los atributos del catálogo no son válidos.");
        }
    }

    private static Object convertir(AtributoCatalogo a, Object valor) {
        String texto = String.valueOf(valor).trim();
        switch (a.tipo()) {
            case BOOLEANO:
                return valor instanceof Boolean b ? b : Boolean.parseBoolean(texto);
            case ENTERO:
            case DECIMAL:
                BigDecimal numero;
                try {
                    numero = new BigDecimal(texto.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST, a.etiqueta() + " debe ser un número.");
                }
                if ((a.minimo() != null && numero.compareTo(a.minimo()) < 0)
                        || (a.maximo() != null && numero.compareTo(a.maximo()) > 0))
                    throw new GlobalException(HttpStatus.BAD_REQUEST, a.etiqueta() + " debe estar entre "
                            + a.minimo().stripTrailingZeros().toPlainString() + " y "
                            + a.maximo().stripTrailingZeros().toPlainString() + ".");
                if (a.tipo() == AtributoCatalogo.Tipo.ENTERO) {
                    try {
                        return numero.intValueExact();
                    } catch (ArithmeticException e) {
                        throw new GlobalException(HttpStatus.BAD_REQUEST, a.etiqueta() + " debe ser un número entero.");
                    }
                }
                return numero;
            case OPCION:
                if (!a.opciones().contains(texto))
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            a.etiqueta() + " debe ser uno de: " + String.join(", ", a.opciones()) + ".");
                return texto;
            default:
                return texto;
        }
    }
}
