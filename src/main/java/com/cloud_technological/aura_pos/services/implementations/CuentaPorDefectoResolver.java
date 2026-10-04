package com.cloud_technological.aura_pos.services.implementations;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;

/**
 * Qué cuenta usa la configuración por defecto (conceptos contables, formas de
 * pago, categorías, impuestos) cuando nadie la ha elegido.
 *
 * <p>Con el catálogo propio (auxiliares de 8 dígitos) la cuenta de siempre
 * puede no existir (4135) o ser solo agrupadora (110505 con su 11050501). El
 * recurso {@code puc/equivalencias.csv} dice a qué auxiliar va cada concepto o
 * código. Se usa la equivalencia solo si esa cuenta existe, está activa y
 * recibe movimiento; si no, el código por defecto de siempre.
 */
@Component
public class CuentaPorDefectoResolver {

    private static final String RECURSO = "puc/equivalencias.csv";

    private final PlanCuentaJPARepository planRepo;
    private final Map<String, String> equivalencias;

    public CuentaPorDefectoResolver(PlanCuentaJPARepository planRepo) {
        this.planRepo = planRepo;
        this.equivalencias = leer();
    }

    /**
     * @param clave         nombre del concepto (CAJA, INGRESOS_VENTAS…) o null
     * @param codigoDefault el código de siempre (110505, 4135…)
     */
    public Optional<PlanCuentaEntity> resolver(Integer empresaId, String clave, String codigoDefault) {
        for (String k : new String[] { clave, codigoDefault }) {
            String destino = k != null ? equivalencias.get(k) : null;
            if (destino == null) continue;
            Optional<PlanCuentaEntity> c = planRepo.findByEmpresaIdAndCodigo(empresaId, destino)
                    .filter(x -> Boolean.TRUE.equals(x.getActiva()) && Boolean.TRUE.equals(x.getAuxiliar()));
            if (c.isPresent()) return c;
        }
        if (codigoDefault == null) return Optional.empty();
        return planRepo.findByEmpresaIdAndCodigo(empresaId, codigoDefault)
                .filter(x -> Boolean.TRUE.equals(x.getActiva()));
    }

    public Long idCuenta(Integer empresaId, String codigoDefault) {
        return resolver(empresaId, null, codigoDefault).map(PlanCuentaEntity::getId).orElse(null);
    }

    Map<String, String> equivalencias() {
        return equivalencias;
    }

    private static Map<String, String> leer() {
        Map<String, String> m = new LinkedHashMap<>();
        try (var in = CuentaPorDefectoResolver.class.getClassLoader().getResourceAsStream(RECURSO)) {
            if (in == null) return Collections.emptyMap();
            var r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String l;
            while ((l = r.readLine()) != null) {
                l = l.trim();
                if (l.isEmpty() || l.startsWith("#") || l.startsWith("clave;")) continue;
                int i = l.indexOf(';');
                if (i > 0) m.put(l.substring(0, i).trim(), l.substring(i + 1).trim());
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + RECURSO, e);
        }
        return Collections.unmodifiableMap(m);
    }
}
