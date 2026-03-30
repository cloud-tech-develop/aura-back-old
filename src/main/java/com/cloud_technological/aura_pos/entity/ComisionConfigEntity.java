package com.cloud_technological.aura_pos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "comision_config")
@Getter
@Setter
public class ComisionConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id")
    private EmpresaEntity empresa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    // tecnico_id → usuario de la misma empresa
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tecnico_id")
    private UsuarioEntity tecnico;

    private String modalidad = "SERVICIO"; // SERVICIO | VENTA

    private String tipo; // PORCENTAJE | VALOR_FIJO

    @Column(name = "categoria_id")
    private Long categoriaId;

    @Column(name = "porcentaje_tecnico")
    private BigDecimal porcentajeTecnico;

    @Column(name = "porcentaje_negocio")
    private BigDecimal porcentajeNegocio;

    private Boolean activo = true;
}
