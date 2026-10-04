package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** El plan de cuentas (recurso puc/puc_comerciantes.csv) y las reglas con que se carga. */
class PlanCuentasPucTest {

    private static Map<String, String> puc() throws Exception {
        Map<String, String> m = new LinkedHashMap<>();
        try (var in = PlanCuentasPucTest.class.getClassLoader().getResourceAsStream("puc/puc_comerciantes.csv")) {
            assertNotNull(in, "falta el recurso del PUC");
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) {
                if (l.isBlank() || l.startsWith("#") || l.startsWith("codigo;")) continue;
                int i = l.indexOf(';');
                m.put(l.substring(0, i), l.substring(i + 1));
            }
        }
        return m;
    }

    @Test
    void elRecursoTraeElPucCompletoYCadaCuentaTienePadre() throws Exception {
        Map<String, String> m = puc();
        assertEquals(1117, m.size());
        for (String c : m.keySet()) {
            if (c.length() == 1) continue;
            assertNotNull(PlanCuentasServiceImpl.padreDe(c, m.keySet()), "sin padre: " + c);
        }
        assertEquals("Caja general", m.get("110505"));
        assertEquals("Bancos", m.get("1110"));
        assertEquals("Caja general", m.get("11050501"));
        assertEquals("Ventas", m.get("41350101"));
        assertEquals("Bienes recibidos en arrendamiento financiero", m.get("8305"));
        assertTrue(m.keySet().stream().filter(c -> c.length() == 1).count() == 9);
    }

    /** Cada equivalencia lleva a una cuenta del catálogo que recibe movimiento (sin hijas). */
    @Test
    void lasEquivalenciasApuntanAAuxiliaresDelCatalogo() throws Exception {
        Map<String, String> m = puc();
        java.util.Set<String> conHijas = new java.util.HashSet<>();
        for (String c : m.keySet()) {
            String p = PlanCuentasServiceImpl.padreDe(c, m.keySet());
            if (p != null) conHijas.add(p);
        }
        Map<String, String> eq = new CuentaPorDefectoResolver(null).equivalencias();
        assertTrue(eq.size() > 60, "no se leyó puc/equivalencias.csv");
        for (var e : eq.entrySet()) {
            assertTrue(m.containsKey(e.getValue()), e.getKey() + " → " + e.getValue() + " no está en el catálogo");
            assertTrue(!conHijas.contains(e.getValue()), e.getKey() + " → " + e.getValue() + " es agrupadora");
        }
        for (var c : com.cloud_technological.aura_pos.entity.ConceptoContable.values()) {
            if (c.name().equals("PROVISION_INVENTARIO")) continue; // el catálogo no trae 1499
            assertTrue(eq.containsKey(c.name()), "falta la equivalencia de " + c.name());
        }
        assertEquals("11050501", eq.get("CAJA"));
    }

    @Test
    void nivelYTipoSalenDelCodigo() {
        assertEquals(1, PlanCuentasServiceImpl.nivelDe("1"));
        assertEquals(2, PlanCuentasServiceImpl.nivelDe("11"));
        assertEquals(3, PlanCuentasServiceImpl.nivelDe("1105"));
        assertEquals(4, PlanCuentasServiceImpl.nivelDe("110505"));
        assertEquals(5, PlanCuentasServiceImpl.nivelDe("11050501"));
        assertEquals("ACTIVO", PlanCuentasServiceImpl.tipoDeClase('1'));
        assertEquals("COSTO", PlanCuentasServiceImpl.tipoDeClase('7'));
        assertEquals("ORDEN", PlanCuentasServiceImpl.tipoDeClase('9'));
    }

    @Test
    void lasCuentasCorrectorasVanAlReves() {
        assertEquals("DEBITO", PlanCuentasServiceImpl.naturalezaPuc("110505", "Caja general"));
        assertEquals("CREDITO", PlanCuentasServiceImpl.naturalezaPuc("1592", "Depreciación acumulada"));
        assertEquals("CREDITO", PlanCuentasServiceImpl.naturalezaPuc("1399", "Provisiones"));
        assertEquals("DEBITO", PlanCuentasServiceImpl.naturalezaPuc("417505", "Devoluciones en ventas"));
        assertEquals("CREDITO", PlanCuentasServiceImpl.naturalezaPuc("8305", "Bienes recibidos en arrendamiento"));
        assertEquals("DEBITO", PlanCuentasServiceImpl.naturalezaPuc("9305", "Contratos de administración"));
        assertEquals("CREDITO", PlanCuentasServiceImpl.naturalezaPuc("4135", "Comercio"));
        assertEquals("DEBITO", PlanCuentasServiceImpl.naturalezaPuc("5105", "Gastos de personal"));
    }
}
