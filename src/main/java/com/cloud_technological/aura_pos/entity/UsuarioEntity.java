package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    // Relación con los datos personales (Nombre, Cédula)
    @OneToOne(fetch = FetchType.EAGER, optional = true)
    @JoinColumn(name = "tercero_id", nullable = true)
    private TerceroEntity tercero;

    // Relación con el empleado (nullable - un usuario puede no estar vinculado a un empleado)
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "empleado_id", nullable = true)
    private EmpleadoEntity empleado;

    // Relación con el cargo del empleado (para el rol)
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "tipo_empleado_id", nullable = true)
    private TipoEmpleadoEntity tipoEmpleado;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String password;

    @Column(name = "pin_acceso_rapido")
    private String pinAccesoRapido;

    private String rol; // ADMIN, CAJERO (Según tu SQL es varchar, no tabla foránea por ahora)

    /**
     * Perfil de permisos (V190). El rol sigue siendo el "tipo" de usuario para la
     * lógica de negocio; el perfil decide qué ve y qué puede hacer. Null = el
     * perfil de sistema de su rol.
     */
    @Column(name = "perfil_id")
    private Long perfilId;

    @lombok.Builder.Default
    private Boolean activo = true;

    /** Límites propios (V192); null = los del perfil. Porcentaje 0–100. */
    @Column(name = "descuento_max_pct", precision = 5, scale = 2)
    private java.math.BigDecimal descuentoMaxPct;

    @Column(name = "rebaja_precio_max_pct", precision = 5, scale = 2)
    private java.math.BigDecimal rebajaPrecioMaxPct;

    /**
     * Va en el token (V192): subirla cierra las sesiones abiertas (desactivar,
     * cambio de clave o de sedes, "Cerrar sesiones").
     */
    @lombok.Builder.Default
    @Column(name = "token_version", nullable = false)
    private Integer tokenVersion = 0;

    @lombok.Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    // Lista para saber a qué sucursales puede entrar
    @OneToMany(mappedBy = "usuario", fetch = FetchType.LAZY)
    private List<UsuarioSucursalEntity> sucursalesAsignadas;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
