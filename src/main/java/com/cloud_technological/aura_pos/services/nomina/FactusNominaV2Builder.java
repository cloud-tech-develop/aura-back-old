package com.cloud_technological.aura_pos.services.nomina;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Accruals;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Aporte;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Deductions;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.HoraExtra;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Monto;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Payment;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.SettlementPeriod;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Transporte;
import com.cloud_technological.aura_pos.dto.nomina.electronica.FactusNominaV2Payload.Worker;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.NominaEntity;
import com.cloud_technological.aura_pos.entity.NominaNovedadEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;

/**
 * Mapea nuestra nómina → payload de nómina electrónica de <b>Factus v2</b>
 * ({@link FactusNominaV2Payload}).
 *
 * <p><b>⚠️ Códigos por confirmar con las tablas oficiales de Factus.</b> Los
 * mapeos de tipo de documento, tipo de contrato, medio de pago, período y tipo
 * de hora extra usan los valores del ejemplo de Factus como defaults; están
 * marcados y hay que contrastarlos contra sus catálogos antes de producción.
 * Cubre el caso mensual estándar: sueldo, transporte, comisiones, horas extra y
 * deducciones de salud/pensión. Cesantías/prima/vacaciones/incapacidades y FSP/
 * retefuente se agregan después.
 */
@Component
public class FactusNominaV2Builder {

    private static final DateTimeFormatter F_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter F_FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository municipioRepo;

    public FactusNominaV2Builder(
            com.cloud_technological.aura_pos.repositories.municipios.MunicipioQueryRepository municipioRepo) {
        this.municipioRepo = municipioRepo;
    }

    public FactusNominaV2Payload construir(NominaEntity nomina, ContratoLaboralEntity contrato,
                                           String referenceCode, String numberingRangeId) {
        FactusNominaV2Payload p = new FactusNominaV2Payload();
        p.setReferenceCode(referenceCode);
        // numbering_range_id es opcional: si va vacío, Factus asigna uno.
        if (numberingRangeId != null && !numberingRangeId.isBlank()) {
            p.setNumberingRangeId(numberingRangeId);
        }
        p.setObservation("Nómina electrónica");

        p.setSettlementPeriod(periodo(nomina));
        p.setPayment(pago(nomina));
        p.setWorker(trabajador(nomina, contrato));
        p.setAccruals(devengados(nomina));
        p.setDeductions(deducciones(nomina));
        return p;
    }

    private SettlementPeriod periodo(NominaEntity nomina) {
        SettlementPeriod sp = new SettlementPeriod();
        LocalDate ini = nomina.getPeriodo().getFechaInicio();
        LocalDate fin = nomina.getPeriodo().getFechaFin();
        sp.setMonth(String.valueOf(fin.getMonthValue()));
        sp.setYear(String.valueOf(fin.getYear()));

        // DIAN tabla 5.5.1 (Periodo de Nómina): 1=Semanal, 2=Decadal,
        // 3=Catorcenal, 4=Quincenal, 5=Mensual. No hay campo de frecuencia en
        // el período, así que se infiere por la cantidad de días.
        long dias = java.time.temporal.ChronoUnit.DAYS.between(ini, fin) + 1;
        String codigo;
        if (dias <= 8)       codigo = "1";   // semanal
        else if (dias <= 11) codigo = "2";   // decadal
        else if (dias <= 14) codigo = "3";   // catorcenal
        else if (dias <= 16) codigo = "4";   // quincenal
        else                 codigo = "5";   // mensual
        sp.setPayrollPeriodCode(codigo);
        if ("4".equals(codigo)) {
            // 1ª quincena si empieza en la primera mitad del mes.
            sp.setPayPeriodHalf(ini.getDayOfMonth() <= 15 ? 1 : 2);
        }
        return sp;
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

    private Worker trabajador(NominaEntity nomina, ContratoLaboralEntity contrato) {
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
        w.setSalary(dec(contrato.getSalarioBase()));
        w.setEntryDate(contrato.getFechaInicio() != null ? contrato.getFechaInicio().format(F_FECHA) : null);
        w.setDaysWorked(dec(BigDecimal.valueOf(nomina.getDiasTrabajados() != null ? nomina.getDiasTrabajados() : 30)));
        // Retiro solo el mes en que ocurre.
        LocalDate ini = nomina.getPeriodo().getFechaInicio();
        LocalDate fin = nomina.getPeriodo().getFechaFin();
        if (contrato.getFechaFin() != null && !contrato.getFechaFin().isBefore(ini)
                && !contrato.getFechaFin().isAfter(fin)) {
            w.setRetirementDate(contrato.getFechaFin().format(F_FECHA));
        }
        return w;
    }

    private Accruals devengados(NominaEntity nomina) {
        Accruals a = new Accruals();

        Monto suel = new Monto();
        suel.setAmount(dec(nomina.getSalarioProporcional()));
        a.setSuel(suel);

        if (positivo(nomina.getAuxilioTransporte())) {
            Transporte tra = new Transporte();
            tra.setAmount(dec(nomina.getAuxilioTransporte()));
            tra.setAccrualTypeCode(1);   // ⚠️ 1 = auxilio legal. Confirmar.
            a.setTra(List.of(tra));
        }

        List<HoraExtra> horas = new ArrayList<>();
        List<Monto> comisiones = new ArrayList<>();
        for (NominaNovedadEntity n : safe(nomina.getNovedades())) {
            String tipo = n.getTipo();
            if (tipo == null) continue;
            if (tipo.startsWith("HORA_EXTRA") || tipo.startsWith("RECARGO")) {
                HoraExtra h = new HoraExtra();
                h.setQuantity(n.getCantidad() != null ? n.getCantidad().stripTrailingZeros().toPlainString() : "0");
                h.setPercentage(porcentajeHora(tipo));
                h.setAmount(dec(n.getValorTotal()));
                if (n.getFechaInicio() != null) h.setStartDate(n.getFechaInicio().atStartOfDay().format(F_FECHA_HORA));
                if (n.getFechaFin() != null) h.setEndDate(n.getFechaFin().atStartOfDay().format(F_FECHA_HORA));
                h.setAccrualTypeCode(mapHora(tipo));   // ⚠️ confirmar tabla
                horas.add(h);
            } else if ("COMISION".equals(tipo)) {
                Monto m = new Monto();
                m.setAmount(dec(n.getValorTotal()));
                comisiones.add(m);
            }
        }
        if (!horas.isEmpty()) a.setHora(horas);
        if (!comisiones.isEmpty()) a.setComi(comisiones);
        return a;
    }

    private Deductions deducciones(NominaEntity nomina) {
        Deductions d = new Deductions();
        if (positivo(nomina.getDeduccionSalud())) {
            Aporte salu = new Aporte();
            salu.setAmount(dec(nomina.getDeduccionSalud()));
            salu.setPercentage("4");
            d.setSalu(salu);
        }
        if (positivo(nomina.getDeduccionPension())) {
            Aporte pens = new Aporte();
            pens.setAmount(dec(nomina.getDeduccionPension()));
            pens.setPercentage("4");
            d.setPens(pens);
        }
        if (positivo(nomina.getDeduccionOtros())) {
            Monto otra = new Monto();
            otra.setAmount(dec(nomina.getDeduccionOtros()));
            d.setOtra(List.of(otra));
        }
        return d;
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
    private String dec(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
    private boolean positivo(BigDecimal v) { return v != null && v.signum() > 0; }
    private List<NominaNovedadEntity> safe(List<NominaNovedadEntity> l) { return l != null ? l : List.of(); }
}
