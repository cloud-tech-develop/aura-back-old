package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario.Familia;
import com.cloud_technological.aura_pos.utils.TipoMovimientoInventario.Grupo;

/**
 * El catálogo de tipos de movimiento tiene que seguir siendo <b>el</b> catálogo.
 *
 * <p>El bug que motivó el enum fue una deriva silenciosa: el front mantenía su
 * propia lista con 9 tipos mientras el backend escribía 17, así que merma,
 * obsequio, devolución y reconteo se veían en la tabla pero no se podían
 * filtrar. Nadie lo notó porque nada fallaba: simplemente faltaban opciones.
 *
 * <p>Estos tests vigilan las dos formas en que eso puede repetirse: que alguien
 * escriba un tipo a mano en un servicio sin agregarlo al enum, y que un tipo del
 * enum se quede sin familia y caiga en la columna "otros" del reporte.
 */
class KardexCatalogoTest {

    private static final Path SERVICIOS =
            Path.of("src/main/java/com/cloud_technological/aura_pos/services/implementations");

    /** El tipo tal como llega a {@code registrarMovimiento(...)}. */
    private static final Pattern LITERAL_EN_KARDEX = Pattern.compile(
            "registrarMovimiento\\([^;]*?\"([A-Z][A-Z_]{3,})\"", Pattern.DOTALL);

    @Test
    @DisplayName("Ningún servicio escribe un tipo de movimiento que no esté en el catálogo")
    void ningunServicioInventaTipos() throws IOException {
        Set<String> conocidos = Arrays.stream(TipoMovimientoInventario.values())
                .map(TipoMovimientoInventario::codigo)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> intrusos = new ArrayList<>();
        try (Stream<Path> archivos = Files.list(SERVICIOS)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(archivo, StandardCharsets.UTF_8);
                Matcher m = LITERAL_EN_KARDEX.matcher(fuente);
                while (m.find()) {
                    if (!conocidos.contains(m.group(1))) {
                        intrusos.add(archivo.getFileName() + " → " + m.group(1));
                    }
                }
            }
        }

        assertTrue(intrusos.isEmpty(),
                "Estos tipos se escriben en el kardex pero no están en TipoMovimientoInventario, "
                        + "así que no aparecerán en el filtro ni en el desglose del reporte: " + intrusos);
    }

    @Test
    @DisplayName("Cada tipo tiene grupo y familia: sin familia caería en la columna \"otros\"")
    void todosLosTiposEstanClasificados() {
        for (TipoMovimientoInventario tipo : TipoMovimientoInventario.values()) {
            assertTrue(tipo.grupo() != null, tipo + " no tiene grupo");
            assertTrue(tipo.familia() != null, tipo + " no tiene familia");
            assertFalse(tipo.etiqueta().isBlank(), tipo + " no tiene etiqueta legible");
        }
    }

    @Test
    @DisplayName("Los tres grupos y las ocho familias cubren el catálogo completo")
    void losGruposCubrenTodo() {
        int porGrupo = Arrays.stream(Grupo.values())
                .mapToInt(g -> TipoMovimientoInventario.codigosDe(g).size())
                .sum();
        assertEquals(TipoMovimientoInventario.values().length, porGrupo,
                "algún tipo quedó fuera de los grupos");

        int porFamilia = Arrays.stream(Familia.values())
                .mapToInt(f -> TipoMovimientoInventario.codigosDe(f).size())
                .sum();
        assertEquals(TipoMovimientoInventario.values().length, porFamilia,
                "algún tipo quedó fuera de las familias y caería en \"otros\"");
    }

    @Test
    @DisplayName("Un tipo desconocido se muestra con su código crudo, no se esconde")
    void tipoDesconocidoNoSePierde() {
        // En la tabla puede haber tipos de versiones anteriores. El reporte los
        // muestra tal cual antes que ocultar la fila: una fila escondida es un
        // descuadre que nadie va a poder explicar.
        assertEquals("TIPO_VIEJO", TipoMovimientoInventario.etiquetaDe("TIPO_VIEJO"));
        assertTrue(TipoMovimientoInventario.de("TIPO_VIEJO").isEmpty());
    }

    @Test
    @DisplayName("El reconteo es el que rompe el signo de cantidad, y está declarado")
    void elReconteoSigueSiendoLaExcepcion() {
        // ReconteoServiceImpl guarda cantidad.abs() y pone el sentido en el
        // nombre del tipo; el resto de orígenes guarda la salida negada. Por eso
        // el reporte usa saldo_nuevo - saldo_anterior y nunca SUM(cantidad).
        // Si esto cambia, el comentario del repositorio deja de ser cierto.
        assertEquals(Grupo.ENTRADA,
                TipoMovimientoInventario.RECONTEO_AJUSTE_POSITIVO.grupo());
        assertEquals(Grupo.SALIDA,
                TipoMovimientoInventario.RECONTEO_AJUSTE_NEGATIVO.grupo());
    }
}
