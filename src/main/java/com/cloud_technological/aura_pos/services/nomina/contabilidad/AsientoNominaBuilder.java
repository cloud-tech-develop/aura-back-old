package com.cloud_technological.aura_pos.services.nomina.contabilidad;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.ConceptoContable;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AcreedorImpuestoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.AsientoNominaQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina.AsientoNominaQueryRepository.LineaDetalle;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_conceptos_contables.NominaConceptoContableQueryRepository.Cambio;
import com.cloud_technological.aura_pos.services.ConfiguracionContableService;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina.Regla;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.ReglaContableNomina.TerceroCredito;

/**
 * Arma el asiento de una nómina por concepto y por entidad (rediseño R4c, N1).
 *
 * <p>Sale de {@code nomina_detalle}: cada devengado, aporte y provisión se debita a
 * su cuenta de gasto (con su proyecto, frente y centro de costo); cada deducción,
 * aporte y provisión se acredita al pasivo a nombre de quien corresponde (EPS, AFP,
 * ARL, caja, DIAN o el empleado), y el neto va a salarios por pagar del empleado.
 * Las entidades salen de las afiliaciones vigentes de la vinculación. Lo que no se
 * pueda resolver (una afiliación que falta) va en los avisos; el asiento se arma igual.
 *
 * <p>R4c-2 — la empresa puede cambiar, por concepto, la cuenta del débito, la del
 * crédito y el tercero del crédito ({@code nomina_concepto_contable}); lo que no
 * cambie sale de {@link ReglaContableNomina} y la parametrización contable.
 */
@Component
public class AsientoNominaBuilder {

    /** Líneas del asiento y avisos para el usuario. */
    public record Resultado(List<AsientoDetalleEntity> lineas, List<String> avisos) {
    }

    private final AsientoNominaQueryRepository queryRepository;
    private final AcreedorImpuestoQueryRepository acreedores;
    private final ConfiguracionContableService cuentas;
    private final NominaConceptoContableQueryRepository cambiosEmpresa;

    public AsientoNominaBuilder(AsientoNominaQueryRepository queryRepository,
            AcreedorImpuestoQueryRepository acreedores,
            ConfiguracionContableService cuentas,
            NominaConceptoContableQueryRepository cambiosEmpresa) {
        this.queryRepository = queryRepository;
        this.acreedores = acreedores;
        this.cuentas = cuentas;
        this.cambiosEmpresa = cambiosEmpresa;
    }

    /**
     * Asiento de la nómina.
     *
     * @return vacío si la fila no tiene detalle por concepto (nómina vieja): quien llama
     *         usa el asiento por totales de siempre
     */
    public Optional<Resultado> construir(NominaEntity n, Integer empresaId) {
        List<LineaDetalle> detalle = queryRepository.detalle(n.getId());
        if (detalle.isEmpty()) return Optional.empty();

        LocalDate fecha = fecha(n);
        Map<String, Long> afiliaciones = n.getContrato() != null
                ? queryRepository.afiliaciones(n.getContrato().getId(), fecha)
                : Map.of();
        Long empleado = n.getEmpleado() != null
                ? queryRepository.terceroEmpleado(n.getEmpleado().getId()).orElse(null)
                : null;
        String quien = n.getContrato() != null ? queryRepository.codigoContrato(n.getContrato().getId()) : "";
        Set<String> avisos = new LinkedHashSet<>();
        Map<String, AsientoDetalleEntity> agrupadas = new LinkedHashMap<>();
        Map<String, Cambio> cambios = cambiosEmpresa.cambios(empresaId);

        BigDecimal neto = BigDecimal.ZERO;
        for (LineaDetalle l : detalle) {
            Regla regla = ReglaContableNomina.para(l.codigo(), l.clase());
            Cambio cambio = cambios.get(l.codigo());
            BigDecimal valor = l.valor();
            if (regla.debito() != null) {
                // El gasto va a nombre del empleado y conserva su reparto por proyecto/frente.
                Long cuentaDebito = cambio != null && cambio.cuentaDebitoId() != null
                        ? cambio.cuentaDebitoId() : cuenta(empresaId, regla.debito());
                sumar(agrupadas, cuentaDebito, l.nombre(), valor, BigDecimal.ZERO, empleado,
                        l.proyectoId(), l.frenteId(), l.centroCostoId());
            }
            if (regla.credito() != null) {
                Long cuentaCredito = cambio != null && cambio.cuentaCreditoId() != null
                        ? cambio.cuentaCreditoId() : cuenta(empresaId, regla.credito());
                TerceroCredito tipo = cambio != null && cambio.terceroCredito() != null
                        ? TerceroCredito.valueOf(cambio.terceroCredito()) : regla.tercero();
                Long tercero = tipo == TerceroCredito.FIJO
                        ? cambio.terceroFijoId()
                        : tercero(tipo, afiliaciones, empleado, empresaId, quien, avisos);
                sumar(agrupadas, cuentaCredito, l.nombre(), BigDecimal.ZERO, valor, tercero, null, null, null);
            }
            if ("DEVENGADO".equals(l.clase())) neto = neto.add(valor);
            if ("DEDUCCION".equals(l.clase())) neto = neto.subtract(valor);
        }
        // El neto cierra el asiento. Si los descuentos superan lo devengado, el empleado
        // queda debiendo: va al débito para que el asiento cuadre.
        if (neto.signum() != 0) {
            sumar(agrupadas, cuenta(empresaId, ReglaContableNomina.NETO.credito()), "Salarios netos por pagar",
                    neto.signum() < 0 ? neto.negate() : BigDecimal.ZERO,
                    neto.signum() > 0 ? neto : BigDecimal.ZERO, empleado, null, null, null);
        }
        if (empleado == null) avisos.add(quien + ": el empleado no tiene tercero; sus líneas quedan sin tercero.");
        return Optional.of(new Resultado(new ArrayList<>(agrupadas.values()), new ArrayList<>(avisos)));
    }

    /**
     * Fecha del asiento: la del documento de nómina; si la fila es del flujo viejo,
     * el fin de su período.
     */
    public LocalDate fecha(NominaEntity n) {
        if (n.getDocumentoId() != null) {
            Optional<LocalDate> f = queryRepository.fechaDocumento(n.getDocumentoId());
            if (f.isPresent()) return f.get();
        }
        if (n.getPeriodo() != null && n.getPeriodo().getFechaFin() != null) return n.getPeriodo().getFechaFin();
        return n.getCreatedAt() != null ? n.getCreatedAt().toLocalDate() : LocalDate.now();
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    private Long cuenta(Integer empresaId, ConceptoContable concepto) {
        return cuentas.resolverCuenta(empresaId, concepto).getId();
    }

    /** Tercero del crédito según la regla; si falta la afiliación, null y un aviso. */
    private Long tercero(TerceroCredito tipo, Map<String, Long> afiliaciones, Long empleado, Integer empresaId,
            String quien, Set<String> avisos) {
        return switch (tipo) {
            case EMPLEADO -> empleado;
            case NINGUNO -> null;
            case DIAN -> dian(empresaId, avisos);
            default -> {
                Long entidad = afiliaciones.get(tipo.name());
                if (entidad == null)
                    avisos.add(quien + ": sin afiliación a " + tipo.name() + " vigente; ese aporte queda sin tercero.");
                yield entidad;
            }
        };
    }

    /** La DIAN de la empresa; se crea como tercero si no existe. */
    private Long dian(Integer empresaId, Set<String> avisos) {
        Optional<Long> dian = acreedores.dian(empresaId);
        if (dian.isEmpty()) {
            acreedores.asegurarDian(empresaId);
            dian = acreedores.dian(empresaId);
        }
        if (dian.isEmpty()) avisos.add("La DIAN no existe como tercero; la retención queda sin tercero.");
        return dian.orElse(null);
    }

    /** Suma la línea en su grupo: mismo lado, cuenta, tercero, dimensiones y descripción. */
    private static void sumar(Map<String, AsientoDetalleEntity> agrupadas, Long cuentaId, String descripcion,
            BigDecimal debito, BigDecimal credito, Long terceroId, Long proyectoId, Long frenteId,
            Long centroCostoId) {
        String clave = String.join("|", debito.signum() != 0 ? "D" : "C", String.valueOf(cuentaId),
                String.valueOf(terceroId), String.valueOf(proyectoId), String.valueOf(frenteId),
                String.valueOf(centroCostoId), Objects.toString(descripcion, ""));
        AsientoDetalleEntity g = agrupadas.computeIfAbsent(clave, k -> AsientoDetalleEntity.builder()
                .cuentaId(cuentaId)
                .descripcion(descripcion)
                .debito(BigDecimal.ZERO)
                .credito(BigDecimal.ZERO)
                .terceroId(terceroId)
                .proyectoId(proyectoId)
                .frenteId(frenteId)
                .centroCostoId(centroCostoId)
                .build());
        g.setDebito(g.getDebito().add(debito));
        g.setCredito(g.getCredito().add(credito));
    }
}
