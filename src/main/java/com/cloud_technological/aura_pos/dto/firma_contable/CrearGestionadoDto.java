package com.cloud_technological.aura_pos.dto.firma_contable;

import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Cliente gestionado que crea una firma contable (docs/PLAN_FIRMA_CONTABLE.md §9).
 * No lleva usuario administrador: lo administra la firma. Opcionalmente, un usuario
 * de consulta para que el cliente vea el estado de su empresa.
 */
@Getter
@Setter
public class CrearGestionadoDto {

    /** NATURAL | JURIDICA (null = JURIDICA). */
    @Pattern(regexp = "^$|NATURAL|JURIDICA", message = "El tipo de persona debe ser NATURAL o JURIDICA")
    private String tipoPersona;

    /** NIT o, para persona natural, CC (null = NIT). */
    @Pattern(regexp = "^$|NIT|CC|CE|PA|PPT", message = "Tipo de documento no válido")
    private String tipoDocumento;

    /** Obligatoria para la jurídica; la natural la arma con nombres y apellidos. */
    @Size(max = 200, message = "La razón social no puede superar 200 caracteres")
    private String razonSocial;

    @Size(max = 120, message = "Los nombres no pueden superar 120 caracteres")
    private String nombres;

    @Size(max = 120, message = "Los apellidos no pueden superar 120 caracteres")
    private String apellidos;

    @Size(max = 200, message = "El nombre comercial no puede superar 200 caracteres")
    private String nombreComercial;

    /** NIT o número de cédula; no se cambia después. */
    @NotBlank(message = "El NIT o número de documento es obligatorio")
    @Size(max = 20, message = "El NIT no puede superar 20 caracteres")
    private String nit;

    /** Vacío = se calcula con el NIT. */
    @Size(max = 2, message = "El dígito de verificación es de un dígito")
    private String dv;

    @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
    private String telefono;

    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 150, message = "El correo no puede superar 150 caracteres")
    private String correo;

    @Size(max = 200, message = "La dirección no puede superar 200 caracteres")
    private String direccion;

    private String municipio;
    private Integer municipioId;

    /** CONTABILIDAD (siempre), FACTURACION, COMPRAS, NOMINA. */
    private List<String> paquetes;

    /** Usuario para que el cliente vea el estado de su empresa (solo consulta); null = ninguno. */
    @Valid
    private UsuarioConsulta usuarioConsulta;

    @Getter
    @Setter
    public static class UsuarioConsulta {
        @NotBlank(message = "Los nombres del usuario de consulta son obligatorios")
        private String nombres;

        private String apellidos;

        private String tipoDocumento;

        @NotBlank(message = "El documento del usuario de consulta es obligatorio")
        private String documento;

        @NotBlank(message = "El correo del usuario de consulta es obligatorio")
        @Email(message = "El correo del usuario de consulta no tiene un formato válido")
        private String email;

        @NotBlank(message = "La contraseña del usuario de consulta es obligatoria")
        @Size(min = 6, message = "La contraseña debe tener mínimo 6 caracteres")
        private String password;
    }
}
