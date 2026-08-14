package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoDeduccionRentaEntity;
import com.cloud_technological.aura_pos.entity.EmpleadoDeduccionRentaEntity.Tipo;
import com.cloud_technological.aura_pos.entity.RetefuenteRangoEntity;
import com.cloud_technological.aura_pos.repositories.nomina.RetefuenteJPARepositories.EmpleadoDeduccionRentaRepo;
import com.cloud_technological.aura_pos.repositories.nomina.RetefuenteJPARepositories.RetefuentePorcentajeFijoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.RetefuenteJPARepositories.RetefuenteRangoRepo;
import com.cloud_technological.aura_pos.repositories.nomina.RetefuenteJPARepositories.UvtValorRepo;

import lombok.extern.slf4j.Slf4j;

/**
 * Retención en la fuente sobre salarios (Fase 4.5).
 *
 * <h2>Lo difícil es la depuración, no la tabla de rangos</h2>
 *
 * El orden legal no se puede alterar:
 * <ol>
 *   <li>Ingreso bruto del mes</li>
 *   <li>− Ingresos no constitutivos de renta: aportes obligatorios a salud,
 *       pensión y fondo de solidaridad</li>
 *   <li>− Deducciones, cada una con su tope:
 *       <ul>
 *         <li>dependientes: 10% del ingreso bruto, máx <b>32 UVT/mes</b></li>
 *         <li>intereses de vivienda: máx <b>100 UVT/mes</b></li>
 *         <li>medicina prepagada: máx <b>16 UVT/mes</b></li>
 *       </ul></li>
 *   <li>− Rentas exentas (AFC, AFP voluntario)</li>
 *   <li><b>Tope global:</b> (deducciones + rentas exentas) ≤ 40% del paso 2,
 *       y ≤ 1.340 UVT anuales (≈111,67 UVT/mes)</li>
 *   <li>− <b>25% exento</b> del subtotal, con tope de <b>790 UVT anuales</b>
 *       (≈65,83 UVT/mes)</li>
 *   <li>= Base gravable → a UVT → buscar rango → aplicar fórmula</li>
 *   <li>Redondear a mil más cercano</li>
 * </ol>
 *
 * <p><b>⚠️ Los valores semilla de V108 (UVT y tabla del art. 383) requieren
 * validación con un contador. Retefuente mal calculada es un problema con la
 * DIAN, no un bug cosmético.</b>
 */
@Slf4j
@Component
public class CalculadoraRetefuente {

    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MIL  = new BigDecimal("1000");

    // Topes en UVT mensuales. Son de ley: no se parametrizan por empresa.
    private static final BigDecimal TOPE_DEPENDIENTES_UVT       = new BigDecimal("32");
    private static final BigDecimal PCT_DEPENDIENTES            = new BigDecimal("10");
    private static final BigDecimal TOPE_INTERESES_VIVIENDA_UVT = new BigDecimal("100");
    private static final BigDecimal TOPE_MEDICINA_UVT           = new BigDecimal("16");
    /** 790 UVT anuales ÷ 12. */
    private static final BigDecimal TOPE_25_EXENTO_UVT_MES      = new BigDecimal("65.8333");
    /** 1.340 UVT anuales ÷ 12. */
    private static final BigDecimal TOPE_GLOBAL_UVT_MES         = new BigDecimal("111.6667");
    private static final BigDecimal PCT_EXENTO                  = new BigDecimal("25");
    private static final BigDecimal PCT_TOPE_GLOBAL             = new BigDecimal("40");

    private final UvtValorRepo uvtRepo;
    private final RetefuenteRangoRepo rangoRepo;
    private final RetefuentePorcentajeFijoRepo fijoRepo;
    private final EmpleadoDeduccionRentaRepo deduccionRepo;

    public CalculadoraRetefuente(UvtValorRepo uvtRepo,
                                 RetefuenteRangoRepo rangoRepo,
                                 RetefuentePorcentajeFijoRepo fijoRepo,
                                 EmpleadoDeduccionRentaRepo deduccionRepo) {
        this.uvtRepo = uvtRepo;
        this.rangoRepo = rangoRepo;
        this.fijoRepo = fijoRepo;
        this.deduccionRepo = deduccionRepo;
    }

    /**
     * Retención del mes.
     *
     * @param aportesObligatorios salud + pensión + fondo de solidaridad del empleado
     * @return retención en pesos, redondeada al mil. Cero si no alcanza el
     *         primer rango gravable.
     */
    public Resultado calcular(ContratoLaboralEntity contrato,
                              BigDecimal ingresoBruto,
                              BigDecimal aportesObligatorios,
                              LocalDate fecha) {

        BigDecimal uvt = valorUvt(fecha.getYear());
        if (uvt == null) {
            log.warn("No hay UVT para {}. No se calcula retefuente.", fecha.getYear());
            return Resultado.cero("Sin UVT configurado para " + fecha.getYear());
        }

        return "2".equals(contrato.getProcedimientoRetefuente())
                ? calcularProcedimiento2(contrato, ingresoBruto, aportesObligatorios, uvt, fecha)
                : calcularProcedimiento1(contrato, ingresoBruto, aportesObligatorios, uvt, fecha);
    }

    // ── Procedimiento 1: se depura y busca rango cada mes ───────────────────

    private Resultado calcularProcedimiento1(ContratoLaboralEntity contrato,
                                             BigDecimal ingresoBruto,
                                             BigDecimal aportesObligatorios,
                                             BigDecimal uvt,
                                             LocalDate fecha) {
        Depuracion dep = depurar(contrato, ingresoBruto, aportesObligatorios, uvt, fecha);

        BigDecimal baseUvt = dep.baseGravable().divide(uvt, 4, RoundingMode.HALF_UP);

        RetefuenteRangoEntity rango = rangoRepo.findRango(fecha.getYear(), baseUvt).orElse(null);
        if (rango == null) {
            return Resultado.cero("Base " + baseUvt.setScale(2, RoundingMode.HALF_UP)
                    + " UVT: sin rango aplicable");
        }

        BigDecimal retencionUvt = rango.calcularUvt(baseUvt);
        BigDecimal retencion = redondearAlMil(retencionUvt.multiply(uvt));

        Traza t = dep.traza()
                .paso("Base gravable en UVT", baseUvt.setScale(2, RoundingMode.HALF_UP))
                .paso("Rango (desde UVT)", rango.getUvtDesde())
                .paso("Tarifa", rango.getTarifa() + " %")
                .paso("Retención en UVT", retencionUvt.setScale(2, RoundingMode.HALF_UP))
                .paso("Retención (redondeada al mil)", retencion);

        return new Resultado(retencion, "1", t);
    }

    // ── Procedimiento 2: porcentaje fijo semestral ──────────────────────────

    private Resultado calcularProcedimiento2(ContratoLaboralEntity contrato,
                                             BigDecimal ingresoBruto,
                                             BigDecimal aportesObligatorios,
                                             BigDecimal uvt,
                                             LocalDate fecha) {
        String semestre = semestreDe(fecha);

        BigDecimal pct = fijoRepo.findByContratoIdAndSemestre(contrato.getId(), semestre)
                .map(f -> f.getPorcentaje())
                .orElse(null);

        if (pct == null) {
            // Sin porcentaje fijo calculado, el proc. 2 no puede aplicarse.
            // Devolver cero silenciosamente sería peor: se dejaría de retener.
            log.warn("Contrato {} usa procedimiento 2 pero no tiene porcentaje fijo "
                    + "para el semestre {}. No se retiene.", contrato.getId(), semestre);
            return Resultado.cero("Procedimiento 2 sin porcentaje fijo calculado para " + semestre);
        }

        // El % fijo se aplica sobre el ingreso gravable, no sobre el bruto.
        Depuracion dep = depurar(contrato, ingresoBruto, aportesObligatorios, uvt, fecha);
        BigDecimal retencion = redondearAlMil(
                dep.baseGravable().multiply(pct).divide(CIEN, 2, RoundingMode.HALF_UP));

        Traza t = dep.traza()
                .paso("Porcentaje fijo semestre " + semestre, pct + " %")
                .paso("Retención (redondeada al mil)", retencion);

        return new Resultado(retencion, "2", t);
    }

    // ── Depuración de la base ───────────────────────────────────────────────

    private Depuracion depurar(ContratoLaboralEntity contrato,
                               BigDecimal ingresoBruto,
                               BigDecimal aportesObligatorios,
                               BigDecimal uvt,
                               LocalDate fecha) {
        Traza t = Traza.vacia().paso("Ingreso bruto", ingresoBruto);

        // Paso 2: menos ingresos no constitutivos de renta.
        BigDecimal subtotal2 = ingresoBruto.subtract(nz(aportesObligatorios)).max(BigDecimal.ZERO);
        t.paso("− Aportes obligatorios (salud, pensión, FSP)", aportesObligatorios);

        List<EmpleadoDeduccionRentaEntity> vigentes =
                contrato.getId() != null ? deduccionRepo.findVigentes(contrato.getId(), fecha) : List.of();

        // Paso 3: deducciones, cada una con su tope.
        BigDecimal deducciones = BigDecimal.ZERO;
        for (EmpleadoDeduccionRentaEntity d : vigentes) {
            if (d.esRentaExenta()) continue;
            BigDecimal aplicada = aplicarTope(d, ingresoBruto, uvt);
            if (aplicada.signum() > 0) {
                deducciones = deducciones.add(aplicada);
                t.paso("− " + d.getTipo(), aplicada);
            }
        }

        // Paso 4: rentas exentas.
        BigDecimal rentasExentas = vigentes.stream()
                .filter(EmpleadoDeduccionRentaEntity::esRentaExenta)
                .map(EmpleadoDeduccionRentaEntity::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rentasExentas.signum() > 0) {
            t.paso("− Rentas exentas (AFC / AFP voluntario)", rentasExentas);
        }

        // Paso 5: tope global — 40% del paso 2, y máx 1.340 UVT anuales.
        BigDecimal sumaBeneficios = deducciones.add(rentasExentas);
        BigDecimal tope40 = subtotal2.multiply(PCT_TOPE_GLOBAL).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal topeUvt = TOPE_GLOBAL_UVT_MES.multiply(uvt).setScale(2, RoundingMode.HALF_UP);
        BigDecimal topeGlobal = tope40.min(topeUvt);

        if (sumaBeneficios.compareTo(topeGlobal) > 0) {
            t.paso("Tope global aplicado (40% ó 1.340 UVT/año)", topeGlobal);
            sumaBeneficios = topeGlobal;
        }

        BigDecimal subtotal5 = subtotal2.subtract(sumaBeneficios).max(BigDecimal.ZERO);

        // Paso 6: 25% exento, con tope de 790 UVT anuales.
        BigDecimal exento25 = subtotal5.multiply(PCT_EXENTO).divide(CIEN, 2, RoundingMode.HALF_UP);
        BigDecimal tope25 = TOPE_25_EXENTO_UVT_MES.multiply(uvt).setScale(2, RoundingMode.HALF_UP);
        if (exento25.compareTo(tope25) > 0) {
            t.paso("25% exento (topado a 790 UVT/año)", tope25);
            exento25 = tope25;
        } else {
            t.paso("− 25% exento", exento25);
        }

        BigDecimal baseGravable = subtotal5.subtract(exento25).max(BigDecimal.ZERO);
        t.paso("= Base gravable", baseGravable);

        return new Depuracion(baseGravable, t);
    }

    /** Aplica el tope legal de cada tipo de deducción. */
    private BigDecimal aplicarTope(EmpleadoDeduccionRentaEntity d, BigDecimal ingresoBruto, BigDecimal uvt) {
        BigDecimal valor = nz(d.getValor());
        return switch (d.getTipo()) {
            case Tipo.DEPENDIENTES -> {
                // 10% del ingreso, máx 32 UVT. El valor capturado no manda:
                // la deducción por dependientes es un cálculo, no un monto.
                BigDecimal diez = ingresoBruto.multiply(PCT_DEPENDIENTES).divide(CIEN, 2, RoundingMode.HALF_UP);
                yield diez.min(TOPE_DEPENDIENTES_UVT.multiply(uvt)).setScale(2, RoundingMode.HALF_UP);
            }
            case Tipo.INTERESES_VIVIENDA ->
                    valor.min(TOPE_INTERESES_VIVIENDA_UVT.multiply(uvt)).setScale(2, RoundingMode.HALF_UP);
            case Tipo.MEDICINA_PREPAGADA ->
                    valor.min(TOPE_MEDICINA_UVT.multiply(uvt)).setScale(2, RoundingMode.HALF_UP);
            default -> valor;
        };
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    private BigDecimal valorUvt(int agno) {
        var exacto = uvtRepo.findByAgno(agno);
        if (exacto.isPresent()) return exacto.get().getValor();
        // Fallback: usar el UVT más reciente en vez de dejar de retener en
        // silencio. Es aproximado; hay que cargar el UVT del año apenas la DIAN
        // lo publique (diciembre), pero es mejor que no retener.
        var ultimo = uvtRepo.findTopByOrderByAgnoDesc().orElse(null);
        if (ultimo != null) {
            log.warn("No hay UVT para {}. Se usa el de {} ({}) como aproximación. "
                    + "Carga el UVT de {} apenas lo publique la DIAN.",
                    agno, ultimo.getAgno(), ultimo.getValor(), agno);
            return ultimo.getValor();
        }
        return null;
    }

    /** '2026-1' para ene-jun, '2026-2' para jul-dic. */
    public static String semestreDe(LocalDate fecha) {
        return fecha.getYear() + "-" + (fecha.getMonthValue() <= 6 ? "1" : "2");
    }

    /** La DIAN exige la retención redondeada al mil más cercano. */
    private static BigDecimal redondearAlMil(BigDecimal v) {
        if (v == null || v.signum() <= 0) return BigDecimal.ZERO;
        return v.divide(MIL, 0, RoundingMode.HALF_UP).multiply(MIL).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── Tipos de retorno ────────────────────────────────────────────────────

    private record Depuracion(BigDecimal baseGravable, Traza traza) {}

    /** Retención calculada, con el procedimiento usado y la traza. */
    public record Resultado(BigDecimal valor, String procedimiento, Traza traza) {
        static Resultado cero(String motivo) {
            return new Resultado(BigDecimal.ZERO, null, Traza.vacia().paso("Sin retención", motivo));
        }
        public boolean hayRetencion() {
            return valor != null && valor.signum() > 0;
        }
    }
}
