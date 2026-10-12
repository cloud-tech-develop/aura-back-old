package com.cloud_technological.aura_pos.dto.auth;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {
    private String token;
    private String tipoToken; // "Bearer"
    private Integer usuarioId;
    private Long empleadoId; // ID del empleado vinculado (nullable)
    private String username;
    private String nombreCompleto;
    private boolean facturaElectronica;
    private String rol;
    private String logo_url;
    private List<SucursalSimpleDto> sucursales;
    /** Líneas de uso de la empresa (POS, COMERCIAL, CONTABILIDAD, NOMINA); null para plataforma. */
    private List<String> lineas;
    /** Línea cuyo tablero ve al entrar; null para plataforma. */
    private String inicio;

    // ── Firma contable (docs/PLAN_FIRMA_CONTABLE.md, F3) ─────────────────
    /** true = el token es de paso: el front muestra "¿Dónde vas a trabajar?" con {@link #empresas}. */
    private Boolean requiereEmpresa;
    /** Empresas que puede elegir: su firma primero y luego sus clientes. */
    private List<EmpresaOpcionDto> empresas;
    /** Empresa en la que quedó trabajando. */
    private Integer empresaId;
    private String empresaNombre;
    private String empresaNit;
    /** Firma desde la que trabaja en un cliente; null en su propia empresa. */
    private String firmaNombre;
    /** Tiene más de una empresa: el front muestra "Cambiar empresa". */
    private Boolean puedeCambiarEmpresa;
    /** La empresa de la sesión es una firma contable (menú "Clientes de la firma"). */
    private Boolean esFirmaContable;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmpresaOpcionDto {
        private Integer id;
        private String razonSocial;
        private String nombreComercial;
        private String nit;
        private String dv;
        private String logoUrl;
        /** Es la firma del usuario (su propia empresa), no un cliente. */
        private boolean propia;
    }
}
