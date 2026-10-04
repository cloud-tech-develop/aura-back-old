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

/** Lo que un perfil puede hacer en un submódulo (V190). */
@Entity
@Table(name = "perfil_permiso")
@Getter
@Setter
@NoArgsConstructor
public class PerfilPermisoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "submodulo_id", nullable = false)
    private Long submoduloId;

    @Column(nullable = false)
    private Boolean ver = false;

    @Column(nullable = false)
    private Boolean crear = false;

    @Column(nullable = false)
    private Boolean editar = false;

    @Column(nullable = false)
    private Boolean anular = false;
}
