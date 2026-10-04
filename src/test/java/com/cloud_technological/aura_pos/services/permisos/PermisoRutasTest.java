package com.cloud_technological.aura_pos.services.permisos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cloud_technological.aura_pos.config.RequerirPermiso;

class PermisoRutasTest {

    @Test
    void accionSegunMetodoYRuta() {
        assertEquals(AccionPermiso.VER, PermisoRutas.accionDe("GET", "/api/compras/5"));
        assertEquals(AccionPermiso.VER, PermisoRutas.accionDe("POST", "/api/compras/page"));
        assertEquals(AccionPermiso.CREAR, PermisoRutas.accionDe("POST", "/api/compras"));
        assertEquals(AccionPermiso.EDITAR, PermisoRutas.accionDe("PUT", "/api/compras/5"));
        assertEquals(AccionPermiso.ANULAR, PermisoRutas.accionDe("DELETE", "/api/compras/5"));
        assertEquals(AccionPermiso.ANULAR, PermisoRutas.accionDe("PATCH", "/api/contabilidad/asientos/5/anular"));
        assertEquals(AccionPermiso.ANULAR, PermisoRutas.accionDe("POST", "/api/ventas/9/anular"));
        assertEquals(AccionPermiso.EDITAR, PermisoRutas.accionDe("POST", "/api/cartera/solicitudes/3/aprobar"));
        assertEquals(AccionPermiso.VER, PermisoRutas.accionDe("POST", "/api/cartera/reglas/simular"));
        assertEquals(AccionPermiso.VER, PermisoRutas.accionDe("POST", "/api/reportes/gastos/resumen"));
        assertEquals(AccionPermiso.VER, PermisoRutas.accionDe("POST", "/api/reportes/gerencial/pdf"));
    }

    @Test
    void prefijoMasLargoPorSegmentos() {
        assertEquals(List.of("contabilidad.balance-general"), PermisoRutas.clavesDe("/api/contabilidad/balance"));
        assertEquals(List.of("contabilidad.balance-de-prueba"), PermisoRutas.clavesDe("/api/contabilidad/balance-detallado"));
        assertEquals(List.of("contabilidad.asientos-contables"), PermisoRutas.clavesDe("/api/contabilidad/asientos/7/anular"));
        assertEquals(List.of("contabilidad.*"), PermisoRutas.clavesDe("/api/contabilidad/dashboard"));
        assertEquals(List.of("catalogo.presentaciones"), PermisoRutas.clavesDe("/api/productos/presentaciones/3"));
        assertNull(PermisoRutas.clavesDe("/api/compras-x"));
    }

    @Test
    void elPosVendeSinLaPantallaDeVentas() {
        assertTrue(PermisoRutas.clavesDe("/api/ventas").contains("principal.punto-de-venta"));
        assertTrue(PermisoRutas.clavesDe("/api/cotizaciones").contains("principal.punto-de-venta"));
        assertTrue(PermisoRutas.clavesDe("/api/turnos/abrir").contains("principal.punto-de-venta"));
    }

    @Test
    void lecturaLibreSoloLee() {
        assertTrue(PermisoRutas.esLecturaLibre("/api/productos/search", AccionPermiso.VER));
        assertFalse(PermisoRutas.esLecturaLibre("/api/productos", AccionPermiso.CREAR));
        assertFalse(PermisoRutas.esLecturaLibre("/api/compras", AccionPermiso.VER));
        assertTrue(PermisoRutas.esLibre("/api/permisos/mios"));
        assertFalse(PermisoRutas.esLibre("/api/permisos-x"));
    }

    /**
     * Toda ruta del API debe estar en PermisoRutas (o ser libre, o tener
     * {@code @RequerirPermiso}). Una ruta sin submódulo no se bloquea nunca: si
     * este test falla, agregue el prefijo nuevo a PermisoRutas.CLAVES.
     */
    @Test
    void todasLasRutasDelApiTienenSubmodulo() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        TreeSet<String> sinSubmodulo = new TreeSet<>();
        int rutas = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.cloud_technological.aura_pos.controllers")) {
            Class<?> c = Class.forName(bd.getBeanClassName());
            boolean claseConAnotacion = c.isAnnotationPresent(RequerirPermiso.class);
            List<String> bases = paths(AnnotatedElementUtils.findMergedAnnotation(c, RequestMapping.class));
            if (bases.isEmpty()) bases = List.of("");
            for (Method m : c.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                if (rm == null) continue;
                boolean conAnotacion = claseConAnotacion || m.isAnnotationPresent(RequerirPermiso.class);
                List<String> subs = paths(rm);
                if (subs.isEmpty()) subs = List.of("");
                for (String base : bases) {
                    for (String sub : subs) {
                        String ruta = (base + (sub.isEmpty() || sub.startsWith("/") ? sub : "/" + sub)).replaceAll("/+$", "");
                        if (!ruta.startsWith("/api/")) continue;
                        rutas++;
                        if (conAnotacion || PermisoRutas.esLibre(ruta) || PermisoRutas.clavesDe(ruta) != null) continue;
                        sinSubmodulo.add(c.getSimpleName() + " " + ruta);
                    }
                }
            }
        }
        assertTrue(rutas > 500, "Se esperaban más de 500 rutas, se encontraron " + rutas);
        assertTrue(sinSubmodulo.isEmpty(), "Rutas sin submódulo en PermisoRutas:\n" + String.join("\n", sinSubmodulo));
    }

    private static List<String> paths(RequestMapping rm) {
        List<String> l = new ArrayList<>();
        if (rm == null) return l;
        for (String p : rm.path()) l.add(p);
        for (String p : rm.value()) if (!l.contains(p)) l.add(p);
        return l;
    }
}
