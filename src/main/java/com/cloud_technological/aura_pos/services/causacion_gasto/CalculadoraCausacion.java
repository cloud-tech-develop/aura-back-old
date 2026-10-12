package com.cloud_technological.aura_pos.services.causacion_gasto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Calculo;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.Linea;
import com.cloud_technological.aura_pos.dto.causacion_gasto.CausacionGastoDtos.LineaCrear;
import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Valores de una causación: el único lugar donde se calculan (el formulario los
 * pide con /calcular y el back los recalcula al grabar, nunca los toma del cliente).
 *
 * <ul>
 * <li>IVA = base × tarifa. Si no es descontable se suma al gasto.</li>
 * <li>Retefuente = base × tarifa del concepto según la persona (natural/jurídica),
 * o la tarifa escrita a mano. La base mínima del concepto se compara contra la
 * suma de las bases de ese concepto en el documento: por debajo, no se retiene.</li>
 * <li>ReteIVA = IVA × tarifa (15 % normalmente). ReteICA = base × tarifa.</li>
 * </ul>
 * Todo a 2 decimales, HALF_UP.
 */
public final class CalculadoraCausacion {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private CalculadoraCausacion() {
    }

    public static Calculo calcular(List<LineaCrear> lineas, String tipoPersona,
            Map<Long, TarifaRetencionEntity> tarifas) {
        if (lineas == null || lineas.isEmpty()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Agregue al menos una línea a la causación.");
        }
        boolean natural = !"JURIDICA".equalsIgnoreCase(tipoPersona);
        Calculo c = new Calculo();
        c.setTipoPersona(natural ? "NATURAL" : "JURIDICA");

        // Base acumulada por concepto, para la base mínima.
        Map<Long, BigDecimal> basePorConcepto = new LinkedHashMap<>();
        for (LineaCrear l : lineas) {
            if (l.getTarifaRetencionId() != null && l.getBase() != null) {
                basePorConcepto.merge(l.getTarifaRetencionId(), l.getBase(), BigDecimal::add);
            }
        }
        List<Long> bajoMinimo = new ArrayList<>();
        basePorConcepto.forEach((id, base) -> {
            TarifaRetencionEntity t = tarifas.get(id);
            if (t != null && nz(t.getBaseMinima()).signum() > 0 && base.compareTo(t.getBaseMinima()) < 0) {
                bajoMinimo.add(id);
                c.getAvisos().add("No se retiene \"" + t.getConcepto() + "\": la base del documento ($"
                        + base.setScale(0, RoundingMode.HALF_UP).toPlainString()
                        + ") no llega a la base mínima ($"
                        + t.getBaseMinima().setScale(0, RoundingMode.HALF_UP).toPlainString() + ").");
            }
        });

        int n = 0;
        for (LineaCrear in : lineas) {
            n++;
            if (in.getCuentaId() == null) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + n + ": falta la cuenta.");
            }
            BigDecimal base = nz(in.getBase());
            if (base.signum() <= 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Línea " + n + ": el valor debe ser mayor que cero.");
            }
            BigDecimal tarifaIva = tarifa(in.getTarifaIva(), n, "IVA");
            BigDecimal tarifaReteiva = tarifa(in.getTarifaReteiva(), n, "ReteIVA");
            BigDecimal tarifaReteica = tarifa(in.getTarifaReteica(), n, "ReteICA");

            Linea l = new Linea();
            l.setCuentaId(in.getCuentaId());
            l.setDescripcion(in.getDescripcion());
            l.setCentroCostoId(in.getCentroCostoId());
            l.setBase(redondear(base));
            l.setTarifaIva(tarifaIva);
            l.setValorIva(porcentaje(base, tarifaIva));
            l.setIvaDescontable(in.getIvaDescontable() == null || in.getIvaDescontable());

            BigDecimal tarifaRete = BigDecimal.ZERO;
            if (in.getTarifaRetencionId() != null) {
                TarifaRetencionEntity t = tarifas.get(in.getTarifaRetencionId());
                if (t == null) {
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Línea " + n + ": el concepto de retención no existe o está inactivo.");
                }
                l.setTarifaRetencionId(t.getId());
                l.setConceptoRetencion(t.getConcepto());
                if (!bajoMinimo.contains(t.getId())) {
                    tarifaRete = in.getTarifaRetefuente() != null
                            ? tarifa(in.getTarifaRetefuente(), n, "retención")
                            : nz(natural ? t.getTarifaNatural() : t.getTarifaJuridica());
                }
            } else if (in.getTarifaRetefuente() != null) {
                tarifaRete = tarifa(in.getTarifaRetefuente(), n, "retención");
            }
            l.setTarifaRetefuente(tarifaRete);
            l.setValorRetefuente(porcentaje(base, tarifaRete));
            l.setTarifaReteiva(tarifaReteiva);
            l.setValorReteiva(porcentaje(l.getValorIva(), tarifaReteiva));
            l.setTarifaReteica(tarifaReteica);
            l.setValorReteica(porcentaje(base, tarifaReteica));
            c.getLineas().add(l);

            c.setSubtotal(c.getSubtotal().add(l.getBase()));
            c.setIva(c.getIva().add(l.getValorIva()));
            c.setRetefuente(c.getRetefuente().add(l.getValorRetefuente()));
            c.setReteiva(c.getReteiva().add(l.getValorReteiva()));
            c.setReteica(c.getReteica().add(l.getValorReteica()));
        }
        c.setTotalPagar(c.getSubtotal().add(c.getIva())
                .subtract(c.getRetefuente()).subtract(c.getReteiva()).subtract(c.getReteica()));
        if (c.getTotalPagar().signum() <= 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Las retenciones no pueden ser iguales o mayores que el valor de la factura.");
        }
        return c;
    }

    private static BigDecimal tarifa(BigDecimal v, int linea, String que) {
        BigDecimal t = nz(v);
        if (t.signum() < 0 || t.compareTo(CIEN) > 0) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Línea " + linea + ": la tarifa de " + que + " debe estar entre 0 y 100.");
        }
        return t;
    }

    static BigDecimal porcentaje(BigDecimal base, BigDecimal tarifa) {
        if (tarifa.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return base.multiply(tarifa).divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal redondear(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
