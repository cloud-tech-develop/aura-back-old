package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "locales")
public class LocalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(name = "nombre", length = 150, nullable = false)
    private String nombre;

    @Column(name = "direccion", length = 255, nullable = false)
    private String direccion;

    @Column(name = "ciudad", length = 200)
    private String ciudad;

    @Column(name = "ciudad_id")
    private Integer ciudadId;

    @Column(name = "barrio", length = 200)
    private String barrio;

    @Column(name = "latitud")
    private Double latitud;

    @Column(name = "longitud")
    private Double longitud;

    @Column(name = "imagen_fachada", length = 500)
    private String imagenFachada;

    @Column(name = "horario_json", columnDefinition = "TEXT")
    private String horarioJson; // JSON: {"lunes": {"apertura": "08:00", "cierre": "18:00"}, ...}

    @Column(name = "preferencia_dias_json", columnDefinition = "TEXT")
    private String preferenciaDiasJson; // JSON: {"dias": ["lunes", "miercoles"], "frecuenciaDias": 7}

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "vendedor_actual_id")
    private EmpleadoEntity vendedorActual;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "vendedor_anterior_id")
    private EmpleadoEntity vendedorAnterior;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}