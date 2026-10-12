package com.cloud_technological.aura_pos.dto.nomina.empleado;

import java.time.LocalDate;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** DTOs de las pestañas de hoja de vida del empleado: bancos, familia e historial laboral. */
public final class EmpleadoHojaVidaDtos {

    private EmpleadoHojaVidaDtos() {
    }

    // ── Cuentas bancarias ────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class CuentaBancariaDto {
        private Long id;
        private Long bancoTerceroId;
        private String bancoNombre;
        private String tipoCuenta;
        private String numeroCuenta;
        private Boolean principal;
        private Boolean activa;
    }

    @Getter
    @Setter
    public static class CreateCuentaBancariaDto {
        @NotNull(message = "Elija el banco")
        private Long bancoTerceroId;
        @NotBlank(message = "El tipo de cuenta es obligatorio")
        @Pattern(regexp = "AHORROS|CORRIENTE|DEPOSITO_ELECTRONICO", message = "Tipo de cuenta no válido")
        private String tipoCuenta;
        @NotBlank(message = "El número de cuenta es obligatorio")
        @Size(max = 50, message = "El número admite hasta 50 caracteres")
        private String numeroCuenta;
        /** La cuenta donde se paga la nómina. */
        private Boolean principal = false;
        private Boolean activa = true;
    }

    // ── Familiares ───────────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class FamiliarDto {
        private Long id;
        private String parentesco;
        private String tipoDocumento;
        private String numeroDocumento;
        private String nombre;
        private LocalDate fechaNacimiento;
        private String genero;
        private String telefono;
        private String ocupacion;
        private Boolean convive;
        private Boolean dependeEconomicamente;
        private Boolean beneficiarioCaja;
        private Boolean beneficiarioSalud;
        private Boolean contactoEmergencia;
    }

    @Getter
    @Setter
    public static class CreateFamiliarDto {
        @NotBlank(message = "El parentesco es obligatorio")
        @Pattern(regexp = "CONYUGE|COMPANERO|HIJO|HIJASTRO|PADRE|MADRE|HERMANO|ABUELO|NIETO|OTRO",
                message = "Parentesco no válido")
        private String parentesco;
        @Size(max = 10)
        private String tipoDocumento;
        @Size(max = 30, message = "El documento admite hasta 30 caracteres")
        private String numeroDocumento;
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre admite hasta 150 caracteres")
        private String nombre;
        private LocalDate fechaNacimiento;
        @Pattern(regexp = "M|F|OTRO", message = "Género no válido")
        private String genero;
        @Size(max = 30)
        private String telefono;
        @Size(max = 100)
        private String ocupacion;
        private Boolean convive = false;
        private Boolean dependeEconomicamente = false;
        private Boolean beneficiarioCaja = false;
        private Boolean beneficiarioSalud = false;
        private Boolean contactoEmergencia = false;
    }

    // ── Historial laboral ────────────────────────────────────────────────────

    @Getter
    @Setter
    public static class ExperienciaDto {
        private Long id;
        private String empresa;
        private String cargo;
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        private String motivoRetiro;
        private String jefeInmediato;
        private String telefono;
        private String funciones;
    }

    @Getter
    @Setter
    public static class CreateExperienciaDto {
        @NotBlank(message = "La empresa es obligatoria")
        @Size(max = 150)
        private String empresa;
        @NotBlank(message = "El cargo es obligatorio")
        @Size(max = 150)
        private String cargo;
        @NotNull(message = "La fecha de inicio es obligatoria")
        private LocalDate fechaInicio;
        private LocalDate fechaFin;
        @Size(max = 150)
        private String motivoRetiro;
        @Size(max = 150)
        private String jefeInmediato;
        @Size(max = 30)
        private String telefono;
        @Size(max = 1000, message = "Las funciones admiten hasta 1000 caracteres")
        private String funciones;
    }
}
