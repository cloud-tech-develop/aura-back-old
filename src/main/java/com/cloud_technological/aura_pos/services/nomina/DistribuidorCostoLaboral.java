package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ContratoCentroCostoEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.HorasPorFrenteQueryRepository;
import com.cloud_technological.aura_pos.repositories.asistencia_frente.HorasPorFrenteQueryRepository.HorasFrente;

import lombok.extern.slf4j.Slf4j;

/**
 * Reparte el costo laboral entre proyecto / frente / centro de costo (Fase 4.a).
 *
 * <h2>CAPA OPCIONAL</h2>
 * La nómina funciona completa sin esto. Muchas empresas no manejan proyectos:
 * tienen empleados que son trabajadores normales y punto. Para ellas la cascada
 * cae al paso 3 y el resultado es idéntico a no tener esta fase.
 *
 * <p><b>El paso 3 es el caso mayoritario, no el borde.</b>
 *
 * <h2>Cascada de resolución</h2>
 * <ol>
 *   <li>¿Tiene asistencia por frente en el período? → repartir por <b>horas
 *       reales</b>.</li>
 *   <li>¿El contrato tiene {@code contrato_centro_costo}? → repartir por esos
 *       porcentajes.</li>
 *   <li>Si no → una sola fila, sin dimensiones, 100%.</li>
 * </ol>
 *
 * <p>El paso 1 es mejor que el enfoque del ERP de referencia, que reparte por
 * un porcentaje fijo pactado. Aquí se reparte por lo que de verdad pasó.
 */
@Slf4j
@Component
public class DistribuidorCostoLaboral {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final HorasPorFrenteQueryRepository horasRepo;

    public DistribuidorCostoLaboral(HorasPorFrenteQueryRepository horasRepo) {
        this.horasRepo = horasRepo;
    }

    /**
     * Resuelve cómo repartir el costo de un contrato en un período.
     *
     * @return las porciones. Siempre al menos una; suman exactamente 100.
     */
    public List<Porcion> resolver(ContratoLaboralEntity contrato, Integer empresaId,
                                  LocalDate desde, LocalDate hasta) {
        // ── Paso 1: horas reales ────────────────────────────────────────────
        if (contrato.getEmpleado() != null) {
            List<HorasFrente> horas = horasRepo.horasPorFrente(
                    empresaId, contrato.getEmpleado().getId(), desde, hasta);
            if (!horas.isEmpty()) {
                return porHoras(horas);
            }
        }

        // ── Paso 2: porcentajes del contrato ────────────────────────────────
        List<ContratoCentroCostoEntity> ccs = contrato.getCentrosCosto();
        if (ccs != null && !ccs.isEmpty()) {
            return porCentrosCosto(ccs);
        }

        // ── Paso 3: sin dimensiones. EL CASO MAYORITARIO. ───────────────────
        return List.of(Porcion.sinDimension());
    }

    /**
     * Reparto por horas trabajadas en cada frente.
     *
     * <p>{@code % frente A = horas en A ÷ horas totales del período}
     */
    private List<Porcion> porHoras(List<HorasFrente> horas) {
        BigDecimal total = horas.stream()
                .map(HorasFrente::horas)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.signum() == 0) return List.of(Porcion.sinDimension());

        List<Porcion> out = new ArrayList<>();
        for (HorasFrente h : horas) {
            BigDecimal pct = h.horas().multiply(CIEN).divide(total, 2, RoundingMode.HALF_UP);
            out.add(new Porcion(h.proyectoId(), h.frenteId(), null, pct));
        }
        return ajustarA100(out);
    }

    private List<Porcion> porCentrosCosto(List<ContratoCentroCostoEntity> ccs) {
        List<Porcion> out = new ArrayList<>();
        for (ContratoCentroCostoEntity cc : ccs) {
            out.add(new Porcion(null, null, cc.getCentroCostoId(), cc.getPorcentaje()));
        }
        return ajustarA100(out);
    }

    /**
     * Fuerza que los porcentajes sumen exactamente 100.
     *
     * <p>Repartir entre 3 frentes deja centavos: 33.33 × 3 = 99.99. La
     * diferencia se le suma a la porción mayor — la que menos se distorsiona en
     * términos relativos.
     *
     * <p>Sin esto, la suma de las filas de detalle no cuadraría con el total
     * del concepto, y el desprendible mentiría.
     */
    private List<Porcion> ajustarA100(List<Porcion> porciones) {
        if (porciones.isEmpty()) return List.of(Porcion.sinDimension());

        BigDecimal suma = porciones.stream()
                .map(Porcion::porcentaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal diff = CIEN.subtract(suma);
        if (diff.signum() == 0) return porciones;

        int idxMayor = 0;
        for (int i = 1; i < porciones.size(); i++) {
            if (porciones.get(i).porcentaje().compareTo(porciones.get(idxMayor).porcentaje()) > 0) {
                idxMayor = i;
            }
        }
        Porcion mayor = porciones.get(idxMayor);
        porciones.set(idxMayor, new Porcion(mayor.proyectoId(), mayor.frenteId(),
                mayor.centroCostoId(), mayor.porcentaje().add(diff)));

        return porciones;
    }

    /**
     * Aplica un porcentaje a un valor, garantizando que las porciones sumen el
     * total exacto.
     *
     * <p>El {@code esUltima} corrige el redondeo: a la última porción se le da
     * el remanente en vez de su porcentaje calculado. Si no, repartir 100.000
     * entre 3 daría 33.333 × 3 = 99.999 y faltaría un peso.
     */
    public BigDecimal aplicar(BigDecimal valor, Porcion p, BigDecimal yaRepartido, boolean esUltima) {
        if (esUltima) {
            return valor.subtract(yaRepartido);
        }
        return valor.multiply(p.porcentaje()).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    /**
     * Una porción del costo.
     *
     * @param porcentaje siempre > 0; el conjunto suma exactamente 100
     */
    public record Porcion(Long proyectoId, Long frenteId, Long centroCostoId, BigDecimal porcentaje) {

        /** El caso mayoritario: empresa sin proyectos. */
        static Porcion sinDimension() {
            return new Porcion(null, null, null, CIEN);
        }

        public boolean tieneDimension() {
            return proyectoId != null || frenteId != null || centroCostoId != null;
        }
    }
}
