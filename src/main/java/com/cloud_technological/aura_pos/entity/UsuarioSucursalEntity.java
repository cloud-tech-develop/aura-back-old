package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario_sucursal")
@Getter @Setter
@Builder
@NoArgsConstructor  // ✅
@AllArgsConstructor // ✅
public class UsuarioSucursalEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private UsuarioEntity usuario;

    @ManyToOne(fetch = FetchType.EAGER) // Eager para traer el nombre de la sucursal rápido al login
    @JoinColumn(name = "sucursal_id")
    private SucursalEntity sucursal;

    @Column(name = "es_default")
    private Boolean esDefault;

    private Boolean activo = true;
}
