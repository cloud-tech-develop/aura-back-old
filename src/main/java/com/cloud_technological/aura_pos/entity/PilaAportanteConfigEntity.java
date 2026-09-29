package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuración del aportante para PILA (P4b), una fila por empresa. Alimenta el
 * encabezado (rep. legal, clasificación de aportante, actividad económica…).
 */
@Getter
@Setter
@Entity
@Table(name = "pila_aportante_config")
public class PilaAportanteConfigEntity {

    @Id
    @Column(name = "empresa_id")
    private Integer empresaId;

    @Column(name = "tipo_aportante", length = 5)
    private String tipoAportante;

    @Column(name = "clase_aportante", length = 5)
    private String claseAportante;

    @Column(name = "naturaleza_aportante", length = 5)
    private String naturalezaAportante;

    @Column(name = "cod_actividad_economica", length = 10)
    private String codActividadEconomica;

    @Column(name = "cod_operador", length = 10)
    private String codOperador;

    @Column(name = "forma_presentacion", length = 5)
    private String formaPresentacion;

    @Column(name = "rep_legal_tipo_documento", length = 5)
    private String repLegalTipoDocumento;

    @Column(name = "rep_legal_documento", length = 30)
    private String repLegalDocumento;

    @Column(name = "rep_legal_apellido1", length = 40)
    private String repLegalApellido1;

    @Column(name = "rep_legal_apellido2", length = 40)
    private String repLegalApellido2;

    @Column(name = "rep_legal_nombre1", length = 40)
    private String repLegalNombre1;

    @Column(name = "rep_legal_nombre2", length = 40)
    private String repLegalNombre2;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
