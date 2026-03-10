package com.cloud_technological.aura_pos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "municipios")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MunicipioEntity {
    @Id
    private Integer id;

    @Column(length = 10)
    private String codigo;

    @Column(length = 100)
    private String nombre;

    @Column(length = 100)
    private String departamento;
}
