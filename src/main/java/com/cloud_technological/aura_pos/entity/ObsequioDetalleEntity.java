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
@Table(name = "obsequio_detalle")
public class ObsequioDetalleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "obsequio_id")
    private ObsequioEntity obsequio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private ProductoEntity producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_id")
    private LoteEntity lote;

    private BigDecimal cantidad;

    /** Costo congelado al entregar: el asiento no puede moverse si el costo cambia después. */
    @Column(name = "costo_unitario", precision = 15, scale = 2)
    private BigDecimal costoUnitario;

    /** Valor comercial unitario SIN IVA (base del impuesto por retiro). */
    @Column(name = "base_comercial_unitaria", precision = 15, scale = 2)
    private BigDecimal baseComercialUnitaria;

    @Column(name = "iva_valor", precision = 15, scale = 2)
    private BigDecimal ivaValor;
}
