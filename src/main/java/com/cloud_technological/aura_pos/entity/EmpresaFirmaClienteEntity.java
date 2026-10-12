package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una firma contable atiende a una empresa cliente (V199, docs/PLAN_FIRMA_CONTABLE.md).
 * Un cliente tiene como máximo un vínculo activo; quitar el acceso lo desactiva y
 * volver a darlo crea uno nuevo, así queda la historia.
 */
@Entity
@Table(name = "empresa_firma_cliente")
@Getter
@Setter
@NoArgsConstructor
public class EmpresaFirmaClienteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firma_empresa_id", nullable = false)
    private Integer firmaEmpresaId;

    @Column(name = "cliente_empresa_id", nullable = false)
    private Integer clienteEmpresaId;

    /** Perfil "Contador externo" del cliente con el que entran los usuarios de la firma. */
    @Column(name = "perfil_id")
    private Long perfilId;

    @Column(nullable = false)
    private Boolean activo = true;

    /** CLIENTE_PAGA (usa Aura y paga su licencia) o GESTIONADO (la creó la firma y la paga ella), V200. */
    @Column(nullable = false, length = 20)
    private String modalidad = MODALIDAD_CLIENTE_PAGA;

    public static final String MODALIDAD_CLIENTE_PAGA = "CLIENTE_PAGA";
    public static final String MODALIDAD_GESTIONADO = "GESTIONADO";

    @Column(name = "creado_por")
    private Integer creadoPor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Usuario del cliente o administrador de plataforma que quitó el acceso. */
    @Column(name = "revocado_por")
    private Integer revocadoPor;

    @Column(name = "revocado_at")
    private LocalDateTime revocadoAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (activo == null) activo = true;
        if (modalidad == null) modalidad = MODALIDAD_CLIENTE_PAGA;
    }
}
