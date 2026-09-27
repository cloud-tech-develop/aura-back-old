package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.Renglon;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.Seccion;
import com.cloud_technological.aura_pos.dto.contabilidad.declaraciones.BorradorDeclaracionDto.Tarifa;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository.Movimiento;
import com.cloud_technological.aura_pos.repositories.contabilidad.DeclaracionesQueryRepository.PorTarifa;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.DeclaracionesService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Borradores de las declaraciones de IVA (300) y retención en la fuente (350),
 * leídos del mayor.
 *
 * <p>Cada valor se agrupa por el tipo de documento que lo originó (ventas,
 * notas, compras, gastos, ajustes), así el contador ve de dónde sale cada peso
 * antes de pasarlo al formulario. Lo que el sistema no puede afirmar se dice
 * en advertencias: asientos en borrador, INC mezclado con IVA, diferencias
 * entre el mayor y los documentos.
 */
@Service
@RequiredArgsConstructor
public class DeclaracionesServiceImpl implements DeclaracionesService {

    private static final BigDecimal TOLERANCIA = new BigDecimal("1");

    private final DeclaracionesQueryRepository repo;
    private final ConfiguracionContableService config;
    private final EmpresaJPARepository empresaRepo;

    // ── IVA (formulario 300) ────────────────────────────────────────────────

    @Override
    public BorradorDeclaracionDto iva(Integer empresaId, LocalDate desde, LocalDate hasta) {
        validar(desde, hasta);
        BorradorDeclaracionDto dto = base("IVA", empresaId, desde, hasta);

        // Qué cuenta es generado y cuál descontable. Además de las de E5, los
        // movimientos viejos en la 2408 misma se clasifican por su documento.
        Set<Long> generado = new HashSet<>();
        Set<Long> descontable = new HashSet<>();
        Set<Long> generadoInc = new HashSet<>();
        for (var ci : repo.cuentasImpuesto(empresaId)) {
            if ("IVA".equalsIgnoreCase(ci.tipo())) {
                if (ci.cuentaGeneradoId() != null) generado.add(ci.cuentaGeneradoId());
                if (ci.cuentaDescontableId() != null) descontable.add(ci.cuentaDescontableId());
            } else if ("INC".equalsIgnoreCase(ci.tipo()) && ci.cuentaGeneradoId() != null) {
                generadoInc.add(ci.cuentaGeneradoId());
            }
        }
        agregarCuentaConcepto(empresaId, ConceptoContable.IVA_GENERADO, generado);
        agregarCuentaConcepto(empresaId, ConceptoContable.IVA_DESCONTABLE, descontable);

        Map<String, BigDecimal> gen = new LinkedHashMap<>();
        Map<String, BigDecimal> desc = new LinkedHashMap<>();
        for (Movimiento m : repo.movimientos(empresaId, desde, hasta, List.of("2408"))) {
            detalle(dto, m);
            boolean esDescontable = descontable.contains(m.cuentaId())
                    || (!generado.contains(m.cuentaId()) && esCompraOGasto(m.tipoOrigen()));
            if (esDescontable) {
                desc.merge(etiquetaDescontable(m.tipoOrigen()), nz(m.debito()).subtract(nz(m.credito())),
                        BigDecimal::add);
            } else {
                gen.merge(etiquetaGenerado(m.tipoOrigen()), nz(m.credito()).subtract(nz(m.debito())),
                        BigDecimal::add);
            }
        }
        Seccion sGen = seccion("IVA generado", false, false, gen);
        Seccion sDesc = seccion("IVA descontable", true, false, desc);

        Map<String, BigDecimal> ret = new LinkedHashMap<>();
        for (Movimiento m : repo.movimientos(empresaId, desde, hasta, List.of("135517"))) {
            detalle(dto, m);
            ret.merge(etiquetaRecaudo(m.tipoOrigen()), nz(m.debito()).subtract(nz(m.credito())),
                    BigDecimal::add);
        }
        Seccion sRet = seccion("Retenciones de IVA que le practicaron", true, false, ret);

        // Ingresos del período: van en la primera parte del formulario.
        Map<String, BigDecimal> ing = new LinkedHashMap<>();
        for (Movimiento m : repo.movimientos(empresaId, desde, hasta, List.of("41", "42"))) {
            String concepto = m.codigo().startsWith("4175")
                    ? "Menos: devoluciones en ventas (4175)"
                    : m.codigo().startsWith("41") ? "Ingresos operacionales (41)"
                            : "Ingresos no operacionales (42)";
            ing.merge(concepto, nz(m.credito()).subtract(nz(m.debito())), BigDecimal::add);
        }
        Seccion sIng = seccion("Ingresos del período", false, true, ing);

        dto.getSecciones().add(sIng);
        dto.getSecciones().add(sGen);
        dto.getSecciones().add(sDesc);
        dto.getSecciones().add(sRet);

        BigDecimal resultado = sGen.getTotal().subtract(sDesc.getTotal()).subtract(sRet.getTotal());
        dto.setResultado(resultado);
        dto.setResultadoEtiqueta(resultado.signum() >= 0 ? "Saldo a pagar" : "Saldo a favor");

        List<PorTarifa> tarifas = repo.ventasPorTarifa(empresaId, desde, hasta);
        BigDecimal ivaDocumentos = BigDecimal.ZERO;
        for (PorTarifa t : tarifas) {
            dto.getBasesPorTarifa().add(new Tarifa("VENTAS", t.tarifa(), nz(t.base()), nz(t.valor()),
                    t.documentos()));
            ivaDocumentos = ivaDocumentos.add(nz(t.valor()));
        }

        // ── Advertencias ──
        if (!generadoInc.isEmpty() && generadoInc.stream().anyMatch(generado::contains)) {
            dto.getAdvertencias().add("El INC y el IVA usan la misma cuenta de impuesto generado: "
                    + "el INC no va en el formulario 300 y aquí queda sumado al IVA. Asígnele al INC "
                    + "su propia cuenta en Contabilidad → Impuestos");
        }
        BigDecimal ivaVentasMayor = gen.getOrDefault(etiquetaGenerado("VENTA"), BigDecimal.ZERO);
        if (ivaDocumentos.subtract(ivaVentasMayor).abs().compareTo(TOLERANCIA) > 0) {
            dto.getAdvertencias().add("El IVA de las ventas del período (" + pesos(ivaDocumentos)
                    + ") no coincide con el contabilizado desde ventas (" + pesos(ivaVentasMayor)
                    + "): puede haber ventas sin asiento o asientos en borrador");
        }
        advertenciasComunes(dto, empresaId, desde, hasta);
        return dto;
    }

    // ── Retención en la fuente (formulario 350) ─────────────────────────────

    @Override
    public BorradorDeclaracionDto retencion(Integer empresaId, LocalDate desde, LocalDate hasta) {
        validar(desde, hasta);
        BorradorDeclaracionDto dto = base("RETENCION", empresaId, desde, hasta);

        Map<String, BigDecimal> renta = new LinkedHashMap<>();
        Map<String, BigDecimal> iva = new LinkedHashMap<>();
        Map<String, BigDecimal> ica = new LinkedHashMap<>();
        BigDecimal rentaCompras = BigDecimal.ZERO;
        for (Movimiento m : repo.movimientos(empresaId, desde, hasta, List.of("2365", "2367", "2368"))) {
            detalle(dto, m);
            BigDecimal neto = nz(m.credito()).subtract(nz(m.debito()));
            String concepto = etiquetaPracticada(m.tipoOrigen());
            if (m.codigo().startsWith("2365")) {
                renta.merge(concepto + cuentaSufijo(m), neto, BigDecimal::add);
                if ("COMPRA".equals(m.tipoOrigen())) rentaCompras = rentaCompras.add(neto);
            } else if (m.codigo().startsWith("2367")) {
                iva.merge(concepto, neto, BigDecimal::add);
            } else {
                ica.merge(concepto, neto, BigDecimal::add);
            }
        }
        Seccion sRenta = seccion("Retención en la fuente a título de renta", false, false, renta);
        Seccion sIva = seccion("Retención a título de IVA", false, false, iva);
        Seccion sIca = seccion("ReteICA practicada (se declara en el municipio, no en el 350)", false,
                true, ica);
        dto.getSecciones().add(sRenta);
        dto.getSecciones().add(sIva);
        dto.getSecciones().add(sIca);

        BigDecimal resultado = sRenta.getTotal().add(sIva.getTotal());
        dto.setResultado(resultado);
        dto.setResultadoEtiqueta("Total retenciones a pagar");

        BigDecimal rentaDocumentosCompras = BigDecimal.ZERO;
        for (PorTarifa t : repo.retencionCompras(empresaId, desde, hasta)) {
            dto.getBasesPorTarifa().add(new Tarifa("COMPRAS", t.tarifa(), nz(t.base()), nz(t.valor()),
                    t.documentos()));
            rentaDocumentosCompras = rentaDocumentosCompras.add(nz(t.valor()));
        }
        for (PorTarifa t : repo.retencionGastos(empresaId, desde, hasta)) {
            dto.getBasesPorTarifa().add(new Tarifa("GASTOS", t.tarifa(), nz(t.base()), nz(t.valor()),
                    t.documentos()));
        }

        if (rentaDocumentosCompras.subtract(rentaCompras).abs().compareTo(TOLERANCIA) > 0) {
            dto.getAdvertencias().add("La retención en la fuente de las compras del período ("
                    + pesos(rentaDocumentosCompras) + ") no coincide con la contabilizada desde "
                    + "compras (" + pesos(rentaCompras) + "): revise compras sin asiento o editadas");
        }
        dto.getAdvertencias().add("Las retenciones se agrupan por cuenta y documento, no por el "
                + "concepto del formulario (honorarios, servicios, compras…): si usa una subcuenta de "
                + "la 2365 por concepto, el detalle por cuenta ya viene separado");
        advertenciasComunes(dto, empresaId, desde, hasta);
        return dto;
    }

    // ── Comunes ─────────────────────────────────────────────────────────────

    private BorradorDeclaracionDto base(String tipo, Integer empresaId, LocalDate desde, LocalDate hasta) {
        BorradorDeclaracionDto dto = new BorradorDeclaracionDto();
        dto.setTipo(tipo);
        dto.setDesde(desde);
        dto.setHasta(hasta);
        empresaRepo.findById(empresaId).ifPresent(e -> {
            dto.setEmpresaNombre(e.getRazonSocial());
            dto.setNit(e.getNit());
        });
        return dto;
    }

    private void advertenciasComunes(BorradorDeclaracionDto dto, Integer empresaId,
            LocalDate desde, LocalDate hasta) {
        int borradores = repo.borradores(empresaId, desde, hasta);
        if (borradores > 0) {
            dto.getAdvertencias().add(0, "Hay " + borradores + " asiento(s) en borrador en el período: "
                    + "no están incluidos. Apruébelos en Revisión de Comprobantes antes de declarar");
        }
        if (hasta.isAfter(LocalDate.now())) {
            dto.getAdvertencias().add("El período no ha terminado: el borrador puede cambiar");
        }
    }

    private void agregarCuentaConcepto(Integer empresaId, ConceptoContable concepto, Set<Long> destino) {
        try {
            destino.add(config.resolverCuenta(empresaId, concepto).getId());
        } catch (Exception ignored) {
            // Sin cuenta configurada: se clasifica por el documento.
        }
    }

    private static Seccion seccion(String titulo, boolean resta, boolean informativa,
            Map<String, BigDecimal> valores) {
        Seccion s = new Seccion(titulo, resta, informativa);
        BigDecimal total = BigDecimal.ZERO;
        for (var e : valores.entrySet()) {
            if (e.getValue().signum() == 0) continue;
            s.getRenglones().add(new Renglon(e.getKey(), e.getValue()));
            total = total.add(e.getValue());
        }
        s.setTotal(total);
        return s;
    }

    private static void detalle(BorradorDeclaracionDto dto, Movimiento m) {
        dto.getCuentas().add(new BorradorDeclaracionDto.CuentaDetalle(m.codigo(), m.nombre(),
                m.tipoOrigen(), nz(m.debito()), nz(m.credito())));
    }

    private static boolean esCompraOGasto(String tipoOrigen) {
        String t = tipoOrigen != null ? tipoOrigen : "";
        return t.contains("COMPRA") || t.contains("GASTO");
    }

    private static String etiquetaGenerado(String tipoOrigen) {
        return switch (tipoOrigen != null ? tipoOrigen : "") {
            case "VENTA" -> "Generado en ventas";
            case "NOTA_DEBITO" -> "Generado en notas débito";
            case "NOTA_CREDITO", "DEVOLUCION", "ANULACION_VENTA", "ANULACION_NOTA_DEBITO" ->
                    "Menos: devoluciones, notas crédito y anulaciones";
            case "OBSEQUIO", "CONSUMO_INTERNO" -> "Generado en retiros de inventario (obsequios, consumo interno)";
            case "MANUAL", "NOTA_DIARIO", "NOTA_CONTABLE" -> "Ajustes en comprobantes y notas contables";
            default -> "Otros movimientos (" + tipoOrigen + ")";
        };
    }

    private static String etiquetaDescontable(String tipoOrigen) {
        String t = tipoOrigen != null ? tipoOrigen : "";
        if (t.startsWith("ANULACION_") || t.contains("NOTA_CREDITO")) return "Menos: anulaciones y notas crédito de compras";
        if (t.contains("COMPRA")) return "Descontable en compras";
        if (t.contains("GASTO")) return "Descontable en gastos";
        if (t.equals("MANUAL") || t.startsWith("NOTA")) return "Ajustes en comprobantes y notas contables";
        return "Otros movimientos (" + t + ")";
    }

    private static String etiquetaRecaudo(String tipoOrigen) {
        return "ABONO_COBRAR".equals(tipoOrigen) ? "Retenidas por clientes al pagar"
                : "Otros movimientos (" + tipoOrigen + ")";
    }

    private static String etiquetaPracticada(String tipoOrigen) {
        String t = tipoOrigen != null ? tipoOrigen : "";
        if (t.startsWith("ANULACION_")) return "Menos: anulaciones";
        return switch (t) {
            case "COMPRA" -> "Practicada en compras";
            case "GASTO" -> "Practicada en gastos";
            case "NOMINA", "NOMINA_PAGO" -> "Practicada en nómina";
            case "ABONO_PAGAR" -> "Practicada en pagos a proveedores";
            case "MANUAL", "NOTA_DIARIO", "NOTA_CONTABLE" -> "Ajustes en comprobantes y notas contables";
            default -> "Otros movimientos (" + t + ")";
        };
    }

    /** Si la 2365 tiene subcuentas por concepto, cada una queda en su renglón. */
    private static String cuentaSufijo(Movimiento m) {
        return m.codigo().length() > 4 ? " · " + m.codigo() + " " + m.nombre() : "";
    }

    private static void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el período (desde y hasta)");
        }
        if (desde.isAfter(hasta)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha inicial es posterior a la final");
        }
    }

    private static String pesos(BigDecimal v) {
        java.text.NumberFormat f = java.text.NumberFormat.getCurrencyInstance(
                java.util.Locale.forLanguageTag("es-CO"));
        f.setMaximumFractionDigits(0);
        return f.format(v);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
