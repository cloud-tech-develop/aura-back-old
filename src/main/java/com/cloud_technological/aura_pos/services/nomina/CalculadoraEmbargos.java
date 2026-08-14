package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.EmbargoEntity;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Prelación y límites de embargos (Fase 9).
 *
 * <h2>Lo difícil es la prelación, no la tabla</h2>
 *
 * Límites legales (CST art. 154-156):
 * <ul>
 *   <li><b>Regla general:</b> el salario es inembargable hasta 1 SMMLV. Del
 *       excedente sobre 1 SMMLV, solo se puede embargar la <b>quinta parte</b>
 *       (20%).</li>
 *   <li><b>Excepción:</b> alimentos y cooperativas pueden llegar al <b>50% del
 *       salario total</b>, sin el piso del SMMLV.</li>
 *   <li>Los embargos por <b>alimentos tienen prelación</b> sobre todos.</li>
 * </ul>
 *
 * <p><b>Un embargo de alimentos + uno ordinario NO suman 50% + 20%.</b> El
 * ordinario solo puede tomar del remanente tras el de alimentos, y sin exceder
 * su propio límite.
 *
 * <p>Los embargos se descuentan del <b>neto</b> (después de salud y pensión),
 * no del devengado.
 *
 * <p><b>⚠️ Sin validar con abogado laboral.</b> La concurrencia de embargos es
 * un área con interpretaciones. Antes de usar en producción, verificar.
 */
@Slf4j
@Component
public class CalculadoraEmbargos {

    private static final BigDecimal CIEN = new BigDecimal("100");
    /** Quinta parte del excedente sobre 1 SMMLV. */
    private static final BigDecimal PCT_GENERAL = new BigDecimal("20");
    /** Alimentos y cooperativas. */
    private static final BigDecimal PCT_AMPLIADO = new BigDecimal("50");

    /**
     * Reparte el cupo embargable entre los embargos activos, en orden de prelación.
     *
     * @param neto  el neto a pagar, después de salud y pensión
     * @param smmlv salario mínimo vigente
     * @param embargos activos del contrato
     */
    public List<Descuento> calcular(BigDecimal neto, BigDecimal smmlv, List<EmbargoEntity> embargos) {
        List<Descuento> out = new ArrayList<>();
        if (neto == null || neto.signum() <= 0 || embargos == null || embargos.isEmpty()) return out;

        // Cupo general: (neto − 1 SMMLV) × 20%. Si el neto no supera 1 SMMLV,
        // el cupo es CERO: el salario mínimo es inembargable.
        BigDecimal excedente = neto.subtract(smmlv).max(BigDecimal.ZERO);
        BigDecimal cupoGeneral = excedente.multiply(PCT_GENERAL).divide(CIEN, 2, RoundingMode.HALF_UP);

        // Cupo ampliado: 50% del neto TOTAL, sin piso.
        BigDecimal cupoAmpliado = neto.multiply(PCT_AMPLIADO).divide(CIEN, 2, RoundingMode.HALF_UP);

        // Lo ya tomado, para que el ordinario solo use el remanente.
        BigDecimal tomadoAmpliado = BigDecimal.ZERO;
        BigDecimal tomadoGeneral = BigDecimal.ZERO;

        // Alimentos primero. Luego por prioridad y, a igualdad, el más viejo.
        List<EmbargoEntity> ordenados = embargos.stream()
                .sorted(Comparator
                        .comparing((EmbargoEntity e) -> EmbargoEntity.Tipo.ALIMENTOS.equals(e.getTipo()) ? 0 : 1)
                        .thenComparing(EmbargoEntity::getPrioridad)
                        .thenComparing(EmbargoEntity::getFechaInicio))
                .toList();

        for (EmbargoEntity e : ordenados) {
            BigDecimal solicitado = montoSolicitado(e, neto);
            if (solicitado.signum() <= 0) continue;

            // El saldo manda: no se descuenta más de lo que se debe.
            if (e.getValorTotal() != null) {
                solicitado = solicitado.min(nz(e.getSaldo()));
                if (solicitado.signum() <= 0) continue;
            }

            BigDecimal cupoRestante;
            if (e.tieneCupoAmpliado()) {
                cupoRestante = cupoAmpliado.subtract(tomadoAmpliado).max(BigDecimal.ZERO);
            } else {
                // El ordinario toma del remanente: su cupo, menos lo que ya se
                // llevaron los de cupo ampliado. Nunca 50% + 20%.
                BigDecimal disponible = cupoGeneral.subtract(tomadoGeneral).max(BigDecimal.ZERO);
                BigDecimal remanenteTotal = neto.subtract(tomadoAmpliado).max(BigDecimal.ZERO);
                cupoRestante = disponible.min(remanenteTotal);
            }

            BigDecimal aDescontar = solicitado.min(cupoRestante);
            BigDecimal diferido = solicitado.subtract(aDescontar).max(BigDecimal.ZERO);

            if (aDescontar.signum() <= 0) {
                log.info("Embargo {} (exp. {}) no cabe este mes: se difiere {}",
                        e.getId(), e.getExpediente(), diferido);
                out.add(new Descuento(e, BigDecimal.ZERO, nz(e.getSaldo()), nz(e.getSaldo()), diferido,
                        Traza.vacia().paso("Sin cupo disponible", "difiere " + diferido)));
                continue;
            }

            BigDecimal saldoAntes = nz(e.getSaldo());
            BigDecimal saldoDespues = e.getValorTotal() != null
                    ? saldoAntes.subtract(aDescontar).max(BigDecimal.ZERO)
                    : saldoAntes;

            if (e.tieneCupoAmpliado()) tomadoAmpliado = tomadoAmpliado.add(aDescontar);
            else                        tomadoGeneral = tomadoGeneral.add(aDescontar);

            Traza t = Traza.vacia()
                    .paso("Tipo", e.getTipo())
                    .paso("Expediente", e.getExpediente())
                    .paso("Neto", neto)
                    .paso(e.tieneCupoAmpliado() ? "Cupo (50% del neto)" : "Cupo (20% sobre 1 SMMLV)", cupoRestante)
                    .paso("Solicitado", solicitado)
                    .paso("Descontado", aDescontar);
            if (diferido.signum() > 0) t.paso("Diferido al mes siguiente", diferido);

            out.add(new Descuento(e, aDescontar, saldoAntes, saldoDespues, diferido, t));
        }

        return out;
    }

    /** Monto que el embargo pide este mes: un % del neto, o lo que quede del saldo. */
    private BigDecimal montoSolicitado(EmbargoEntity e, BigDecimal neto) {
        if (e.getPorcentaje() != null) {
            return neto.multiply(e.getPorcentaje()).divide(CIEN, 2, RoundingMode.HALF_UP);
        }
        return nz(e.getSaldo());
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Lo descontado a un embargo en una nómina. */
    @Getter
    public static class Descuento {
        private final EmbargoEntity embargo;
        private final BigDecimal valor;
        private final BigDecimal saldoAntes;
        private final BigDecimal saldoDespues;
        /** Lo que no cupo por el límite legal. Se intenta el mes siguiente. */
        private final BigDecimal valorDiferido;
        private final Traza traza;

        public Descuento(EmbargoEntity embargo, BigDecimal valor, BigDecimal saldoAntes,
                         BigDecimal saldoDespues, BigDecimal valorDiferido, Traza traza) {
            this.embargo = embargo;
            this.valor = valor;
            this.saldoAntes = saldoAntes;
            this.saldoDespues = saldoDespues;
            this.valorDiferido = valorDiferido;
            this.traza = traza;
        }

        /** ¿El embargo quedó saldado con este descuento? */
        public boolean quedaTerminado() {
            return embargo.getValorTotal() != null && saldoDespues.signum() == 0;
        }
    }
}
