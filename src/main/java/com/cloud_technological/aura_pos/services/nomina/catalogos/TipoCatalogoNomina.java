package com.cloud_technological.aura_pos.services.nomina.catalogos;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Catálogos simples de nómina que comparten la tabla {@code nomina_catalogo}
 * (docs/PLAN_NOMINA_REDISENO.md §9b).
 *
 * <p>Cada uno es su propio módulo en pantalla (ruta, submódulo y permiso), pero
 * todos usan el mismo servicio. Para agregar un catálogo basta una constante aquí
 * con sus atributos propios; el front arma el formulario con esa definición.
 */
public enum TipoCatalogoNomina {

    GRUPO_NOMINA("grupos-nomina", "Grupos de nómina", "Empleados que se liquidan juntos", List.of()),

    NIVEL_CARGO("niveles-cargo", "Niveles de cargo", "Jerarquía de los cargos", List.of()),

    TIPO_CARGO("tipos-cargo", "Tipos de cargo", "Planta, temporal, por proyecto", List.of()),

    TIPO_EMBARGO("tipos-embargo", "Tipos de embargo", "Clases de embargo con su prioridad y límite legal", List.of(
            AtributoCatalogo.entero("prioridad", "Prioridad", 1, 99,
                    "1 se descuenta primero (los de alimentos van antes)"),
            AtributoCatalogo.decimal("limitePorcentaje", "Límite (%)", "0", "100",
                    "Alimentos hasta 50 % del salario; los demás, 20 % del excedente del mínimo"),
            AtributoCatalogo.booleano("depositoJudicial", "¿Va a depósito judicial?",
                    "Sí: se consigna en la cuenta del juzgado; no: se paga directo al acreedor"))),

    MOTIVO_RETIRO("motivos-retiro", "Motivos de retiro", "Causas de terminación de la vinculación", List.of(
            AtributoCatalogo.booleano("indemniza", "¿Genera indemnización?",
                    "Despido sin justa causa: la liquidación definitiva calcula la indemnización"))),

    MOTIVO_AUSENTISMO("motivos-ausentismo", "Motivos de ausentismo", "Incapacidades, licencias y permisos", List.of(
            AtributoCatalogo.opcion("pagador", "¿Quién paga?", true, List.of("EMPRESA", "EPS", "ARL", "NINGUNO"),
                    "EPS o ARL: la parte de la entidad queda por cobrar"),
            AtributoCatalogo.booleano("remunerado", "¿Es remunerado?",
                    "No remunerado: descuenta días de salario y baja el IBC"),
            AtributoCatalogo.opcion("codigoPila", "Novedad PILA", false,
                    List.of("IGE", "IRL", "LMA", "SLN", "VAC-LR"), "Cómo se reporta en la planilla"))),

    RIESGO_ARL("riesgos-arl", "Riesgos ARL", "Clases de riesgo y su tarifa", List.of(
            AtributoCatalogo.decimal("porcentaje", "Tarifa (%)", "0", "10", "Clase I 0,522 % … clase V 6,96 %"))),

    CALENDARIO_LABORAL("calendarios-laborales", "Calendarios laborales",
            "Días que se trabajan; los festivos salen del calendario laboral de la empresa", List.of(
            AtributoCatalogo.booleano("lunes", "¿Lunes?", null),
            AtributoCatalogo.booleano("martes", "¿Martes?", null),
            AtributoCatalogo.booleano("miercoles", "¿Miércoles?", null),
            AtributoCatalogo.booleano("jueves", "¿Jueves?", null),
            AtributoCatalogo.booleano("viernes", "¿Viernes?", null),
            AtributoCatalogo.booleano("sabado", "¿Sábado?", null),
            AtributoCatalogo.booleano("domingo", "¿Domingo?", null),
            AtributoCatalogo.booleano("trabajaFestivos", "¿Trabaja festivos?",
                    "Sí: los festivos cuentan como días hábiles (con su recargo)")));

    private final String ruta;
    private final String nombre;
    private final String descripcion;
    private final List<AtributoCatalogo> atributos;

    TipoCatalogoNomina(String ruta, String nombre, String descripcion, List<AtributoCatalogo> atributos) {
        this.ruta = ruta;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.atributos = atributos;
    }

    /** Segmento de la URL y código del submódulo (recursos-humanos.&lt;ruta&gt;). */
    public String getRuta() {
        return ruta;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<AtributoCatalogo> getAtributos() {
        return atributos;
    }

    /**
     * Busca el tipo por el segmento de la URL.
     *
     * @param ruta segmento, p. ej. {@code tipos-embargo}
     * @return el tipo, o vacío si no existe
     */
    public static Optional<TipoCatalogoNomina> deRuta(String ruta) {
        return Arrays.stream(values()).filter(t -> t.ruta.equals(ruta)).findFirst();
    }
}
