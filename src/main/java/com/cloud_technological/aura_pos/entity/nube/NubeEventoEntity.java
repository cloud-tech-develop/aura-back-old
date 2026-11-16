package com.cloud_technological.aura_pos.entity.nube;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 * Bitácora de la nube (V236): quién hizo qué, desde qué empresa y si entró por la
 * firma. Solo se inserta; la base tiene un trigger que impide editarla o borrarla.
 */
@Entity
@Immutable
@Table(name = "nube_evento")
@Getter
@Setter
@NoArgsConstructor
public class NubeEventoEntity {

    public static final String SUBIR = "SUBIR";
    public static final String DESCARGAR = "DESCARGAR";
    public static final String VER = "VER";
    public static final String RENOMBRAR = "RENOMBRAR";
    public static final String MOVER = "MOVER";
    public static final String ELIMINAR = "ELIMINAR";
    public static final String RESTAURAR = "RESTAURAR";
    public static final String PURGAR = "PURGAR";
    public static final String CREAR_CARPETA = "CREAR_CARPETA";
    public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "empresa_id", nullable = false)
    private Integer empresaId;

    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(name = "firma_empresa_id")
    private Integer firmaEmpresaId;

    @Column(nullable = false, length = 20)
    private String accion;

    @Column(name = "archivo_id")
    private Long archivoId;

    @Column(name = "carpeta_id")
    private Long carpetaId;

    /** JSON como String (gotcha: un Map da 409 al confirmar). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detalle;

    @Column(length = 45)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void alCrear() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
