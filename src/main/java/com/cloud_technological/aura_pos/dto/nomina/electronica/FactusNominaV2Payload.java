package com.cloud_technological.aura_pos.dto.nomina.electronica;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * Payload de nómina electrónica de <b>Factus v2</b>
 * (POST {@code /v2/payroll/validate}).
 *
 * <p>Estructura fiel a la documentación de Factus: devengados y deducciones van
 * en <b>objetos/arrays nombrados</b> (no una lista con codigoDian), los nombres
 * son snake_case en inglés y <b>no hay objeto empleador</b> (es la cuenta del
 * token). Los campos nulos no se serializan: los objetos/arrays opcionales, una
 * vez con datos, se vuelven obligatorios — por eso solo se pueblan si aplican.
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactusNominaV2Payload {

    @JsonProperty("reference_code")
    private String referenceCode;

    private String observation;

    @JsonProperty("numbering_range_id")
    private String numberingRangeId;

    @JsonProperty("settlement_period")
    private SettlementPeriod settlementPeriod;

    private Payment payment;
    private Worker worker;
    private Accruals accruals;
    private Deductions deductions;

    // ── Período de liquidación ──────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettlementPeriod {
        private String month;
        private String year;
        @JsonProperty("payroll_period_code")
        private String payrollPeriodCode;
        /** Solo si payroll_period_code = 4 (quincenal). Factus lo exige entero. */
        @JsonProperty("pay_period_half")
        private Integer payPeriodHalf;
    }

    // ── Pago ────────────────────────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Payment {
        @JsonProperty("payment_method_code")
        private String paymentMethodCode;
        @JsonProperty("bank_name")
        private String bankName;
        @JsonProperty("account_type")
        private String accountType;
        @JsonProperty("account_number")
        private String accountNumber;
        @JsonProperty("payment_date")
        private String paymentDate;   // AAAA-MM-DD
    }

    // ── Trabajador ──────────────────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Worker {
        @JsonProperty("identification_document_code")
        private String identificationDocumentCode;
        @JsonProperty("identification_number")
        private String identificationNumber;
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("other_names")
        private String otherNames;
        @JsonProperty("first_surname")
        private String firstSurname;
        @JsonProperty("second_surname")
        private String secondSurname;
        private String address;
        @JsonProperty("country_code")
        private String countryCode = "CO";
        @JsonProperty("municipality_code")
        private String municipalityCode;
        @JsonProperty("has_integral_salary")
        private Boolean hasIntegralSalary = Boolean.FALSE;
        @JsonProperty("has_high_risk")
        private Boolean hasHighRisk = Boolean.FALSE;
        @JsonProperty("worker_type_code")
        private String workerTypeCode;
        @JsonProperty("worker_subtype")
        private String workerSubtype = "00";
        @JsonProperty("contract_type")
        private String contractType;
        @JsonProperty("employee_code")
        private String employeeCode;
        private String salary;
        @JsonProperty("entry_date")
        private String entryDate;       // AAAA-MM-DD
        @JsonProperty("days_worked")
        private String daysWorked;
        @JsonProperty("retirement_date")
        private String retirementDate;  // solo el mes del retiro
    }

    // ── Devengados ──────────────────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Accruals {
        private Monto suel;                 // sueldo básico
        private List<Transporte> tra;       // auxilios de transporte
        private List<HoraExtra> hora;       // horas extra / recargos
        private List<Monto> comi;           // comisiones
        private List<Bonificacion> boni;    // bonificaciones
        private List<Cesantia> cesa;        // cesantías / intereses
        private Prima prim;                 // prima de servicios
        private List<Vacaciones> vaca;      // vacaciones
        private List<Incapacidad> inca;     // incapacidades
        private List<Licencia> lice;        // licencias
        private List<Otro> otro;            // otros conceptos
    }

    // ── Deducciones ─────────────────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Deductions {
        private Aporte salu;   // salud (empleado)
        private Aporte pens;   // pensión (empleado)
        private Aporte dedu;   // fondo de solidaridad pensional (>= 4 SMMLV)
        private Monto rete;    // retención en la fuente
        private List<MontoDesc> libr;  // libranzas
        private List<Monto> otra;      // otras deducciones
    }

    // ── Tipos de línea ──────────────────────────────────────────────────────
    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Monto {
        private String amount;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
        @JsonProperty("deduction_type_code")
        private String deductionTypeCode;
        private String description;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MontoDesc {
        private String amount;
        private String description;
        @JsonProperty("deduction_type_code")
        private String deductionTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Transporte {
        private String amount;
        @JsonProperty("accrual_type_code")
        private Integer accrualTypeCode = 1;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class HoraExtra {
        private String quantity;
        private String percentage;
        private String amount;
        @JsonProperty("start_date")
        private String startDate;   // YYYY-MM-DD HH:MM:SS
        @JsonProperty("end_date")
        private String endDate;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Bonificacion {
        private String amount;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Cesantia {
        private String amount;
        private String percentage;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Prima {
        private Integer quantity;
        private String amount;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Vacaciones {
        private Integer quantity;
        private String amount;
        @JsonProperty("start_date")
        private String startDate;
        @JsonProperty("end_date")
        private String endDate;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Incapacidad {
        @JsonProperty("start_date")
        private String startDate;
        @JsonProperty("end_date")
        private String endDate;
        private Integer quantity;
        private String amount;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Licencia {
        private String amount;
        private Integer quantity;
        @JsonProperty("start_date")
        private String startDate;
        @JsonProperty("end_date")
        private String endDate;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Otro {
        private String amount;
        private String description;
        @JsonProperty("accrual_type_code")
        private String accrualTypeCode;
    }

    @Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Aporte {
        private String percentage;
        private String amount;
        @JsonProperty("deduction_type_code")
        private String deductionTypeCode;
    }
}
