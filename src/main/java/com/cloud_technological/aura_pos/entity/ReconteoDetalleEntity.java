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

@Getter
@Setter
@Entity
@Table(name = "reconteo_detalles")
public class ReconteoDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reconteo_id")
    private ReconteoEntity reconteo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEntity lote;

    @Column(name = "stock_sistema", precision = 15, scale = 4)
    private BigDecimal stockSistema;

    @Column(name = "stock_contado", precision = 15, scale = 4)
    private BigDecimal stockContado;

    @Column(name = "ajuste_aplicado")
    private Boolean ajusteAplicado = false;
}
