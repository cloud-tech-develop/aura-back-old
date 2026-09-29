package com.cloud_technological.aura_pos.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "auditoria_nomina_asistencia")
public class AuditoriaNominaAsistenciaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "entidad", length = 50, nullable = false)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(name = "accion", length = 50, nullable = false)
    private String accion;

    @Column(name = "usuario_id")
    private Integer usuarioId;

    @Column(name = "fecha_hora")
    private LocalDateTime fechaHora;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "ip", length = 60)
    private String ip;

    @Column(name = "origen", length = 30)
    private String origen;
}
