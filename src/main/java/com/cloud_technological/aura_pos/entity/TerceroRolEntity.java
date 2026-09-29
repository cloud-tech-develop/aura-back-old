package com.cloud_technological.aura_pos.entity;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Rol de un tercero. Reemplaza a los booleanos es_cliente / es_proveedor /
 * es_empleado / es_banco de {@link TerceroEntity}.
 *
 * <p>Motivo: la Fase 5.5 (afiliaciones a seguridad social) necesita cuatro
 * roles más (EPS, AFP, CCF, ARL). Con booleanos serían ocho columnas, cada una
 * replicada en cuatro DTOs y con su propia query. Con tabla, un rol nuevo es
 * una fila.
 *
 * <p><b>Durante la migración hay doble escritura:</b> los booleanos de
 * {@code tercero} siguen siendo la fuente de verdad hasta que todo el código
 * lea de aquí. Ver V98__tercero_rol.sql.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tercero_rol")
@IdClass(TerceroRolEntity.TerceroRolId.class)
public class TerceroRolEntity {

    @Id
    @Column(name = "tercero_id")
    private Long terceroId;

    @Id
    @Column(name = "rol", length = 20)
    private String rol;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** Roles válidos. Debe coincidir con el CHECK de tercero_rol. */
    public static final class Rol {
        public static final String CLIENTE   = "CLIENTE";
        public static final String PROVEEDOR = "PROVEEDOR";
        public static final String EMPLEADO  = "EMPLEADO";
        public static final String BANCO     = "BANCO";
        // Seguridad social (Fase 5.5): son terceros a los que se les paga.
        public static final String EPS = "EPS";
        public static final String AFP = "AFP";
        public static final String CCF = "CCF";
        public static final String ARL = "ARL";
        public static final String CESANTIAS = "CESANTIAS";

        private Rol() {}
    }

    /** Clave compuesta (tercero_id, rol). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerceroRolId implements Serializable {
        private Long terceroId;
        private String rol;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TerceroRolId that)) return false;
            return Objects.equals(terceroId, that.terceroId)
                && Objects.equals(rol, that.rol);
        }

        @Override
        public int hashCode() {
            return Objects.hash(terceroId, rol);
        }
    }
}
