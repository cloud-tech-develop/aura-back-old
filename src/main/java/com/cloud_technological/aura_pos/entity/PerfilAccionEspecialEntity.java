package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Acción especial dada o quitada explícitamente a un perfil (V192). Sin fila, la
 * acción hereda de su acción base (ver {@code permiso_accion_especial.hereda_de}).
 */
@Entity
@Table(name = "perfil_accion_especial")
@Getter
@Setter
@NoArgsConstructor
public class PerfilAccionEspecialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "accion_id", nullable = false)
    private Long accionId;

    @Column(nullable = false)
    private Boolean permitido;
}
