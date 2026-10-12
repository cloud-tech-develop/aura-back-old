package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Accruals;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Bonificacion;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Cesantia;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Incapacidad;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Licencia;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.MontoDesc;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Otro;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Prima;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Vacaciones;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Aporte;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Deductions;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.HoraExtra;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Monto;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Payment;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.SettlementPeriod;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Transporte;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Worker;
import com.cloud_technological.aura_pos.entity.ConceptoNominaEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaDetalleEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaDetalleJPARepository;

/**
 * Mapea nuestra nómina → payload de nómina electrónica de <b>Factus v2</b>
 * ({@link FactusNominaV2Payload}).
 *
 * <p><b>⚠️ Códigos por confirmar con las tablas oficiales de Factus.</b> Los
 * mapeos de tipo de documento, tipo de contrato, medio de pago, período y tipo
 * de hora extra usan los valores del ejemplo de Factus como defaults; están
 * marcados y hay que contrastarlos contra sus catálogos antes de producción.
 *
 * <p><b>Un documento por empleado y MES</b> (lo que pide la DIAN): con pago
 * quincenal o semanal se juntan todas las nóminas confirmadas del mes (y la prima,
 * vacaciones u otros pagos que caigan en él) y se envían como período mensual.
 */
@Component
public class FactusNominaV2Builder {

    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository municipioRepo;
    private final NominaDetalleJPARepository detalleRepo;

    public FactusNominaV2Builder(
            com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository municipioRepo,
            NominaDetalleJPARepository detalleRepo) {
        this.municipioRepo = municipioRepo;
        this.detalleRepo = detalleRepo;
    }

    /**
     * Documento del MES de un empleado: junta todas sus nóminas confirmadas del mes.
     *
     * @param nominas  nóminas del mes en orden de período (la última da el pago y el salario)
     * @param contrato contrato del empleado
     * @param mes      mes del documento
     */
    public FactusNominaV2Payload construirMes(List<NominaEntity> nominas, ContratoLaboralEntity contrato,
                                              YearMonth mes, String referenceCode, String numberingRangeId) {
        if (nominas == null || nominas.isEmpty())
            throw new IllegalArgumentException("El mes no tiene nóminas confirmadas");
        NominaEntity ultima = nominas.get(nominas.size() - 1);

        FactusNominaV2Payload p = new FactusNominaV2Payload();
        p.setReferenceCode(referenceCode);
        // numbering_range_id es opcional: si va vacío, Factus asigna uno.
        if (numberingRangeId != null && !numberingRangeId.isBlank()) {
            p.setNumberingRangeId(numberingRangeId);
        }
        p.setObservation("Nómina electrónica");

        p.setSettlementPeriod(periodoMensual(mes));
        p.setPayment(pago(ultima));
        p.setWorker(trabajador(ultima, contrato, diasDelMes(nominas), mes));
        // El detalle es lo que de verdad se pagó (salario, novedades, conceptos
        // propios, prestaciones) en todas las nóminas del mes.
        List<NominaDetalleEntity> detalle = new ArrayList<>();
        for (NominaEntity n : nominas)
            if (n.getId() != null) detalle.addAll(detalleRepo.findByNominaId(n.getId()));
        p.setAccruals(devengados(nominas, detalle));
        p.setDeductions(deducciones(nominas, detalle));
        return p;
    }

    /**
     * Período mensual. DIAN tabla 5.5.1: 5 = Mensual. Aunque se pague por quincenas,
     * el documento es uno por mes y va como mensual (decisión de producto: la DIAN
     * recibe un solo documento con todo el mes).
     */
    private SettlementPeriod periodoMensual(YearMonth mes) {
        SettlementPeriod sp = new SettlementPeriod();
        sp.setMonth(String.valueOf(mes.getMonthValue()));
        sp.setYear(String.valueOf(mes.getYear()));
        sp.setPayrollPeriodCode("5");
        return sp;
    }

    /** Días trabajados del mes: los de las nóminas del período (no prima ni otros pagos), máx. 30. */
    private static int diasDelMes(List<NominaEntity> nominas) {
        int dias = 0;
        for (NominaEntity n : nominas)
            if (n.getDiasTrabajados() != null) dias += n.getDiasTrabajados();
        return Math.min(30, dias);
    }

    private Payment pago(NominaEntity nomina) {
        Payment pay = new Payment();
        String banco = nomina.getEmpleado() != null ? nomina.getEmpleado().getBancoResuelto() : null;
        if (banco != null && !banco.isBlank()) {
            pay.setPaymentMethodCode("42");   // ⚠️ 42 = consignación. Confirmar.
            pay.setBankName(banco);
            pay.setAccountType(mapTipoCuenta(nomina.getEmpleado().getTipoCuentaResuelto()));
            pay.setAccountNumber(nomina.getEmpleado().getNumeroCuentaResuelto());
        } else {
            pay.setPaymentMethodCode("10");   // ⚠️ 10 = efectivo. Confirmar.
        }
        LocalDate fin = nomina.getPeriodo().getFechaFin();
        pay.setPaymentDate(fin.format(F_FECHA));
        return pay;
    }

    private Worker trabajador(NominaEntity nomina, ContratoLaboralEntity contrato, int diasMes, YearMonth mes) {
        Worker w = new Worker();
        TerceroEntity t = nomina.getEmpleado() != null ? nomina.getEmpleado().getTercero() : null;
        if (t != null) {
            w.setIdentificationDocumentCode(mapTipoDocumento(t.getTipoDocumento()));
            w.setIdentificationNumber(t.getNumeroDocumento());
            w.setFirstName(t.getNombre1());
            w.setOtherNames(t.getNombre2());
            w.setFirstSurname(t.getApellido1());
            // Factus exige segundo apellido: si no hay, va vacío (no null).
            w.setSecondSurname(t.getApellido2() != null ? t.getApellido2() : "");
            w.setAddress(t.getDireccion());
            w.setMunicipalityCode(codigoDaneMunicipio(t.getMunicipioId()));
        }
        w.setCountryCode("CO");
        w.setHasIntegralSalary(Boolean.TRUE.equals(contrato.getEsSalarioIntegral()));
        w.setHasHighRisk(contrato.getNivelRiesgoArl() != null && contrato.getNivelRiesgoArl() >= 4);
        w.setWorkerTypeCode(contrato.getTipoCotizante() != null ? contrato.getTipoCotizante() : "01");
        w.setWorkerSubtype(contrato.getSubtipoCotizante() != null ? contrato.getSubtipoCotizante() : "00");
        w.setContractType(mapTipoContrato(contrato.getTipoContrato()));
        // El de la nómina: es el salario con que se liquidó (al aprendiz le aplica el mínimo legal).
        w.setSalary(dec(nomina.getSalarioBase() != null ? nomina.getSalarioBase() : contrato.getSalarioBase()));
        w.setEntryDate(contrato.getFechaInicio() != null ? contrato.getFechaInicio().format(F_FECHA) : null);
        w.setDaysWorked(dec(BigDecimal.valueOf(diasMes)));
        // Retiro solo el mes en que ocurre.
        LocalDate ini = mes.atDay(1);
        LocalDate fin = mes.atEndOfMonth();
        if (contrato.getFechaFin() != null && !contrato.getFechaFin().isBefore(ini)
                && !contrato.getFechaFin().isAfter(fin)) {
            w.setRetirementDate(contrato.getFechaFin().format(F_FECHA));
        }
        return w;
    }

    /**
     * Devengados desde el detalle de la nómina (lo que de verdad se pagó: salario,
     * novedades, conceptos propios, prestaciones, indemnización), clasificados por el
     * {@code codigo_dian} del concepto. Las fechas y días salen de la novedad ligada a
     * la línea cuando la hay. La licencia no remunerada no tiene valor (no genera
     * línea) y se toma de las novedades.
     */
    private Accruals devengados(List<NominaEntity> nominas, List<NominaDetalleEntity> detalle) {
        Accruals a = new Accruals();
        BigDecimal sueldo = BigDecimal.ZERO;
        BigDecimal transporte = BigDecimal.ZERO;
        BigDecimal indemnizacion = BigDecimal.ZERO;
        List<HoraExtra> horas = new ArrayList<>();
        List<Monto> comisiones = new ArrayList<>();
        List<Bonificacion> bonos = new ArrayList<>();
        List<Cesantia> cesantias = new ArrayList<>();
        List<Vacaciones> vacaciones = new ArrayList<>();
        List<Incapacidad> incapacidades = new ArrayList<>();
        List<Licencia> licencias = new ArrayList<>();
        List<Otro> otros = new ArrayList<>();
        Prima prima = null;

        for (NominaDetalleEntity l : detalle) {
            ConceptoNominaEntity c = l.getConcepto();
            if (c == null || !ConceptoNominaEntity.Clase.DEVENGADO.equals(c.getClase()) || !positivo(l.getValor()))
                continue;
            NominaNovedadEntity nov = l.getNovedad();
            String dian = c.getCodigoDian() != null ? c.getCodigoDian() : "";
            switch (dian) {
                case "Basico" -> sueldo = sueldo.add(l.getValor());
                case "Transporte" -> transporte = transporte.add(l.getValor());
                case "HED", "HEN", "HRN", "HEDDF", "HRDDF", "HENDF", "HRNDF" -> horas.add(hora(c.getCodigo(), l, nov));
                case "Comisiones" -> comisiones.add(monto(l.getValor()));
                case "Bonificaciones" -> {
                    Bonificacion b = new Bonificacion();
                    b.setAmount(dec(l.getValor()));
                    b.setAccrualTypeCode(salarial(c, nov) ? "1" : "2");   // ⚠️ 1 salarial, 2 no salarial
                    bonos.add(b);
                }
                case "Cesantias" -> {
                    Cesantia ce = new Cesantia();
                    ce.setAmount(dec(l.getValor()));
                    boolean intereses = "INT_CESANTIAS".equals(c.getCodigo());
                    ce.setAccrualTypeCode(intereses ? "2" : "1");          // ⚠️ 1 cesantías, 2 intereses
                    if (intereses) ce.setPercentage("12.00");
                    cesantias.add(ce);
                }
                case "Primas" -> {
                    if (prima == null) {
                        prima = new Prima();
                        prima.setAmount(dec(l.getValor()));
                        prima.setQuantity(entero(l.getCantidad()));
                        prima.setAccrualTypeCode("1");                     // ⚠️ 1 = prima
                    } else {
                        prima.setAmount(dec(new BigDecimal(prima.getAmount()).add(l.getValor())));
                    }
                }
                case "Vacaciones" -> {
                    Vacaciones v = new Vacaciones();
                    v.setAmount(dec(l.getValor()));
                    v.setQuantity(entero(l.getCantidad()));
                    boolean disfrutadas = nov != null && Boolean.TRUE.equals(nov.getAfectaDiasSalario());
                    v.setAccrualTypeCode(disfrutadas ? "1" : "2");         // ⚠️ 1 disfrutadas, 2 compensadas
                    if (nov != null && nov.getFechaInicio() != null) v.setStartDate(nov.getFechaInicio().format(F_FECHA));
                    if (nov != null && nov.getFechaFin() != null) v.setEndDate(nov.getFechaFin().format(F_FECHA));
                    vacaciones.add(v);
                }
                case "Incapacidades" -> {
                    Incapacidad in = new Incapacidad();
                    in.setAmount(dec(l.getValor()));
                    in.setQuantity(nov != null && nov.getDias() != null ? nov.getDias() : entero(l.getCantidad()));
                    in.setAccrualTypeCode(tipoIncapacidad(nov));
                    if (nov != null && nov.getFechaInicio() != null) in.setStartDate(nov.getFechaInicio().format(F_FECHA));
                    if (nov != null && nov.getFechaFin() != null) in.setEndDate(nov.getFechaFin().format(F_FECHA));
                    incapacidades.add(in);
                }
                case "LicenciaMP", "LicenciaR" -> {
                    Licencia li = new Licencia();
                    li.setAmount(dec(l.getValor()));
                    li.setQuantity(nov != null && nov.getDias() != null ? nov.getDias() : entero(l.getCantidad()));
                    li.setAccrualTypeCode("LicenciaMP".equals(dian) ? "1" : "2");   // ⚠️ 1 MP, 2 remunerada
                    if (nov != null && nov.getFechaInicio() != null) li.setStartDate(nov.getFechaInicio().format(F_FECHA));
                    if (nov != null && nov.getFechaFin() != null) li.setEndDate(nov.getFechaFin().format(F_FECHA));
                    licencias.add(li);
                }
                case "Indemnizacion" -> indemnizacion = indemnizacion.add(l.getValor());
                default -> {
                    // OtrosConceptos, retroactivo, conceptos propios sin código DIAN…
                    Otro o = new Otro();
                    o.setAmount(dec(l.getValor()));
                    o.setDescription(c.getNombre());
                    o.setAccrualTypeCode(salarial(c, nov) ? "1" : "2");     // ⚠️ 1 salarial, 2 no salarial
                    otros.add(o);
                }
            }
        }

        // Licencias no remuneradas: no pagan, pero se reportan con sus días.
        for (NominaNovedadEntity n : nominas.stream().flatMap(x -> safe(x.getNovedades()).stream()).toList()) {
            if (!"LICENCIA_NO_REMUNERADA".equals(n.getTipo())) continue;
            Licencia li = new Licencia();
            li.setQuantity(n.getDias() != null ? n.getDias() : entero(n.getCantidad()));
            li.setAccrualTypeCode("3");                                    // ⚠️ 3 no remunerada
            if (n.getFechaInicio() != null) li.setStartDate(n.getFechaInicio().format(F_FECHA));
            if (n.getFechaFin() != null) li.setEndDate(n.getFechaFin().format(F_FECHA));
            licencias.add(li);
        }

        // El sueldo siempre va (aunque sea 0, p. ej. una fila de solo prima).
        a.setSuel(monto(detalle.isEmpty() ? suma(nominas, NominaEntity::getSalarioProporcional) : sueldo));
        if (positivo(transporte)) {
            Transporte tra = new Transporte();
            tra.setAmount(dec(transporte));
            tra.setAccrualTypeCode(1);   // ⚠️ 1 = auxilio legal
            a.setTra(List.of(tra));
        }
        if (!horas.isEmpty()) a.setHora(horas);
        if (!comisiones.isEmpty()) a.setComi(comisiones);
        if (!bonos.isEmpty()) a.setBoni(bonos);
        if (!cesantias.isEmpty()) a.setCesa(cesantias);
        if (prima != null) a.setPrim(prima);
        if (!vacaciones.isEmpty()) a.setVaca(vacaciones);
        if (!incapacidades.isEmpty()) a.setInca(incapacidades);
        if (!licencias.isEmpty()) a.setLice(licencias);
        if (!otros.isEmpty()) a.setOtro(otros);
        if (positivo(indemnizacion)) a.setInde(monto(indemnizacion));
        return a;
    }

    /**
     * Deducciones desde el detalle de la nómina, por el {@code codigo_dian} del
     * concepto: salud, pensión, fondo de solidaridad, retención en la fuente,
     * libranzas/embargos y otras.
     */
    private Deductions deducciones(List<NominaEntity> nominas, List<NominaDetalleEntity> detalle) {
        Deductions d = new Deductions();
        BigDecimal salud = BigDecimal.ZERO;
        BigDecimal pension = BigDecimal.ZERO;
        BigDecimal solidaridad = BigDecimal.ZERO;
        BigDecimal retencion = BigDecimal.ZERO;
        BigDecimal pctSalud = null;
        BigDecimal pctPension = null;
        BigDecimal pctSolidaridad = null;
        List<MontoDesc> libranzas = new ArrayList<>();
        List<Monto> otras = new ArrayList<>();

        for (NominaDetalleEntity l : detalle) {
            ConceptoNominaEntity c = l.getConcepto();
            if (c == null || !ConceptoNominaEntity.Clase.DEDUCCION.equals(c.getClase()) || !positivo(l.getValor()))
                continue;
            switch (c.getCodigoDian() != null ? c.getCodigoDian() : "") {
                case "Salud" -> {
                    salud = salud.add(l.getValor());
                    if (l.getPorcentaje() != null) pctSalud = l.getPorcentaje();
                }
                case "FondoPension" -> {
                    pension = pension.add(l.getValor());
                    if (l.getPorcentaje() != null) pctPension = l.getPorcentaje();
                }
                case "FondoSP" -> {
                    solidaridad = solidaridad.add(l.getValor());
                    if (l.getPorcentaje() != null) pctSolidaridad = l.getPorcentaje();
                }
                case "RetencionFuente" -> retencion = retencion.add(l.getValor());
                case "Libranza" -> {
                    MontoDesc m = new MontoDesc();
                    m.setAmount(dec(l.getValor()));
                    m.setDescription(c.getNombre());
                    libranzas.add(m);
                }
                default -> otras.add(monto(l.getValor()));
            }
        }

        if (detalle.isEmpty()) {
            // Fila sin detalle (flujo viejo): los totales de la nómina.
            salud = suma(nominas, NominaEntity::getDeduccionSalud);
            pension = suma(nominas, NominaEntity::getDeduccionPension);
            BigDecimal otrosTotal = suma(nominas, NominaEntity::getDeduccionOtros);
            if (positivo(otrosTotal)) otras.add(monto(otrosTotal));
        }
        // Salud y pensión son obligatorias en el documento DIAN aunque el trabajador no
        // aporte (aprendiz SENA, pensionado): en ese caso van en cero con 0 %.
        d.setSalu(positivo(salud) ? aporte(salud, pctSalud, "4") : aporte(BigDecimal.ZERO, BigDecimal.ZERO, "0"));
        d.setPens(positivo(pension) ? aporte(pension, pctPension, "4") : aporte(BigDecimal.ZERO, BigDecimal.ZERO, "0"));
        if (positivo(solidaridad)) {
            Aporte fsp = aporte(solidaridad, pctSolidaridad, "1");
            fsp.setDeductionTypeCode("1");   // ⚠️ 1 = fondo de solidaridad pensional
            d.setDedu(List.of(fsp));
        }
        if (positivo(retencion)) d.setRete(monto(retencion));
        if (!libranzas.isEmpty()) d.setLibr(libranzas);
        if (!otras.isEmpty()) d.setOtra(otras);
        return d;
    }

    /** Suma un valor de todas las nóminas del mes (nulos como cero). */
    private static BigDecimal suma(List<NominaEntity> nominas, java.util.function.Function<NominaEntity, BigDecimal> campo) {
        BigDecimal total = BigDecimal.ZERO;
        for (NominaEntity n : nominas) total = total.add(nz(campo.apply(n)));
        return total;
    }

    private HoraExtra hora(String codigo, NominaDetalleEntity l, NominaNovedadEntity nov) {
        HoraExtra h = new HoraExtra();
        BigDecimal cantidad = l.getCantidad() != null ? l.getCantidad() : nov != null ? nov.getCantidad() : null;
        h.setQuantity(cantidad != null ? cantidad.stripTrailingZeros().toPlainString() : "0");
        h.setPercentage(porcentajeHora(codigo));
        h.setAmount(dec(l.getValor()));
        if (nov != null && nov.getFechaInicio() != null) h.setStartDate(nov.getFechaInicio().atStartOfDay().format(F_FECHA_HORA));
        if (nov != null && nov.getFechaFin() != null) h.setEndDate(nov.getFechaFin().atStartOfDay().format(F_FECHA_HORA));
        h.setAccrualTypeCode(mapHora(codigo));   // ⚠️ confirmar tabla
        return h;
    }

    /** ¿El pago es salarial? Lo dice la novedad si la hay; si no, el concepto. */
    private static boolean salarial(ConceptoNominaEntity c, NominaNovedadEntity nov) {
        if (nov != null && nov.getConstituyeIbc() != null) return nov.getConstituyeIbc();
        return Boolean.TRUE.equals(c.getConstituyeIbc());
    }

    /** ⚠️ Factus: 1 común (EPS), 2 profesional, 3 laboral (ARL). */
    private static String tipoIncapacidad(NominaNovedadEntity nov) {
        String st = nov != null && nov.getSubtipo() != null ? nov.getSubtipo().toUpperCase() : "GENERAL";
        if (st.contains("RIESGO") || st.contains("LABORAL") || st.contains("ARL")) return "3";
        if (st.contains("PROFESIONAL")) return "2";
        return "1";
    }

    private static Aporte aporte(BigDecimal valor, BigDecimal porcentaje, String porDefecto) {
        Aporte a = new Aporte();
        a.setAmount(dec(valor));
        a.setPercentage(porcentaje != null ? porcentaje.stripTrailingZeros().toPlainString() : porDefecto);
        return a;
    }

    private static Monto monto(BigDecimal v) {
        Monto m = new Monto();
        m.setAmount(dec(v));
        return m;
    }

    private static Integer entero(BigDecimal v) {
        return v != null ? v.setScale(0, java.math.RoundingMode.HALF_UP).intValue() : 0;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── Mapeos de código (⚠️ contra tablas oficiales de Factus) ─────────────

    /** Resuelve el id interno de municipio al código DANE que exige Factus. */
    private String codigoDaneMunicipio(Long municipioId) {
        if (municipioId == null) return null;
        var m = municipioRepo.findById(municipioId);
        return m != null ? m.getCodigo() : null;
    }

    private String mapTipoDocumento(String tipo) {
        if (tipo == null) return "13";
        return switch (tipo) {
            case "CC" -> "13";
            case "TI" -> "12";
            case "CE" -> "22";
            case "PASAPORTE", "PA" -> "41";
            case "NIT" -> "31";
            case "PEP" -> "47";
            default -> "13";
        };
    }

    private String mapTipoContrato(String tipo) {
        // Factus: 1=Término fijo, 2=Término indefinido, 3=Término o labor,
        // 4=Aprendizaje, 5=Prácticas o Pasantías.
        if (tipo == null) return "2";
        return switch (tipo) {
            case "FIJO" -> "1";
            case "INDEFINIDO" -> "2";
            case "OBRA_LABOR", "OBRA" -> "3";
            case "APRENDIZAJE" -> "4";
            case "PRACTICAS", "PASANTIA", "PASANTIAS" -> "5";
            default -> "2";
        };
    }

    private String mapTipoCuenta(String tipo) {
        // Factus: 1=Nómina, 2=Ahorros, 3=Corriente.
        if (tipo == null) return "2";
        return switch (tipo.toUpperCase()) {
            case "AHORROS", "AHORRO" -> "2";
            case "CORRIENTE" -> "3";
            case "NOMINA" -> "1";
            default -> "2";
        };
    }

    private String mapHora(String tipo) {
        // Factus: 1=extra diurna, 2=extra nocturna, 3=recargo nocturno,
        // 4=extra diurna dom/fest, 5=recargo diurno dom/fest,
        // 6=extra nocturna dom/fest, 7=recargo nocturno dom/fest.
        return switch (tipo) {
            case "HORA_EXTRA_DIURNA", "HORA_EXTRA" -> "1";
            case "HORA_EXTRA_NOCTURNA" -> "2";
            case "RECARGO_NOCTURNO" -> "3";
            case "HORA_EXTRA_DOMINICAL", "HORA_EXTRA_FESTIVO", "HORA_EXTRA_DOMINICAL_FESTIVA" -> "4";
            case "RECARGO_DOMINICAL_FESTIVO" -> "5";
            default -> "1";
        };
    }

    private String porcentajeHora(String tipo) {
        // Recargo legal sobre la hora ordinaria (Código Sustantivo del Trabajo).
        return switch (tipo) {
            case "HORA_EXTRA_DIURNA", "HORA_EXTRA" -> "25.00";
            case "HORA_EXTRA_NOCTURNA" -> "75.00";
            case "RECARGO_NOCTURNO" -> "35.00";
            case "HORA_EXTRA_DOMINICAL", "HORA_EXTRA_FESTIVO", "HORA_EXTRA_DOMINICAL_FESTIVA" -> "100.00";
            case "RECARGO_DOMINICAL_FESTIVO" -> "75.00";
            default -> "25.00";
        };
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private static String dec(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private static boolean positivo(BigDecimal v) { return v != null && v.signum() > 0; }
    private List<NominaNovedadEntity> safe(List<NominaNovedadEntity> l) { return l != null ? l : List.of(); }
}
