package com.cloud_technological.aura_pos.dto.permisos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Perfiles de permisos y excepciones por usuario (docs/PLAN_PERMISOS.md). */
public final class PerfilesDtos {

    private PerfilesDtos() {
    }

    /** Fila de la lista de perfiles. */
    @Data
    public static class PerfilFila {
        private Long id;
        private String codigo;
        private String nombre;
        private String descripcion;
        private boolean accesoTotal;
        private boolean esSistema;
        private boolean activo;
        /** Usuarios que lo tienen asignado. */
        private long usuarios;
        /** Descuento máximo sin autorización; null = sin límite (V192). */
        private java.math.BigDecimal descuentoMaxPct;
        /** Rebaja máxima del precio sin autorización; null = sin límite (V192). */
        private java.math.BigDecimal rebajaPrecioMaxPct;
        /** Ve y opera en todas las sedes (V192). */
        private boolean todasSedes = true;
    }

    /** Una acción especial del catálogo (V192): su clave es {@code clave:codigo}. */
    @Data
    public static class AccionEspecialDto {
        private Long id;
        private Long submoduloId;
        private String clave;
        private String codigo;
        private String nombre;
        private String descripcion;
        /** Acción base de la que hereda sin fila explícita; null = solo si se da. */
        private String heredaDe;
    }

    /** Valor explícito de una acción especial; {@code permitido} null = heredar (se borra la fila). */
    @Data
    public static class EspecialValor {
        private Long accionId;
        private Boolean permitido;
    }

    /** Un submódulo del árbol de la empresa, en orden de menú. */
    @Data
    public static class NodoArbol {
        private Long submoduloId;
        private String moduloCodigo;
        private String moduloNombre;
        private String codigo;
        /** modulo.submodulo: la clave que usan el menú y el back. */
        private String clave;
        private String nombre;
        /** Grupo del tercer nivel del que cuelga (RRHH → Gestión → Empleados). */
        private Long padreId;
        /** Es un grupo (Gestión, Asistencia…), no una pantalla. */
        private boolean esGrupo;
    }

    /** Acciones sobre un submódulo. */
    @Data
    public static class Permiso {
        private Long submoduloId;
        private boolean ver;
        private boolean crear;
        private boolean editar;
        private boolean anular;
    }

    /** Excepción de un usuario: null = lo que diga el perfil. */
    @Data
    public static class PermisoExcepcion {
        private Long submoduloId;
        private Boolean ver;
        private Boolean crear;
        private Boolean editar;
        private Boolean anular;
    }

    @Data
    public static class PerfilDetalle {
        private PerfilFila perfil;
        private List<Permiso> permisos = new ArrayList<>();
        /** Lo explícito del perfil; lo que no aparece hereda de su acción base. */
        private List<EspecialValor> especiales = new ArrayList<>();
    }

    @Data
    public static class GuardarPerfil {
        private String nombre;
        private String descripcion;
        private Boolean accesoTotal;
        private Boolean activo;
        private List<Permiso> permisos = new ArrayList<>();
        private List<EspecialValor> especiales = new ArrayList<>();
        private java.math.BigDecimal descuentoMaxPct;
        private java.math.BigDecimal rebajaPrecioMaxPct;
        /** null = sí (como siempre). */
        private Boolean todasSedes;
    }

    /** Lo que hereda un usuario de su perfil y sus excepciones, para la pestaña del usuario. */
    @Data
    public static class PermisosDeUsuario {
        private Integer usuarioId;
        private String username;
        private String rol;
        private Long perfilId;
        private String perfilNombre;
        private boolean perfilAccesoTotal;
        /** Lo que da el perfil (vacío si es de acceso total: da todo). */
        private List<Permiso> delPerfil = new ArrayList<>();
        private List<PermisoExcepcion> excepciones = new ArrayList<>();
        /** Acciones especiales que hoy tiene (perfil + excepciones), {@code clave:codigo}. */
        private List<String> especialesEfectivos = new ArrayList<>();
        /** Lo que le daría su perfil sin excepciones, {@code clave:codigo}. */
        private List<String> especialesDelPerfil = new ArrayList<>();
        private List<EspecialValor> excepcionesEspeciales = new ArrayList<>();
        /** Límites del perfil (para mostrar) y los propios del usuario (null = los del perfil). */
        private java.math.BigDecimal perfilDescuentoMaxPct;
        private java.math.BigDecimal perfilRebajaPrecioMaxPct;
        private boolean perfilTodasSedes = true;
        private java.math.BigDecimal descuentoMaxPct;
        private java.math.BigDecimal rebajaPrecioMaxPct;
    }

    @Data
    public static class GuardarExcepciones {
        private List<PermisoExcepcion> excepciones = new ArrayList<>();
        /** Excepciones de acciones especiales; permitido null = lo del perfil. */
        private List<EspecialValor> especiales = new ArrayList<>();
        /** Límites propios; null = los del perfil. */
        private java.math.BigDecimal descuentoMaxPct;
        private java.math.BigDecimal rebajaPrecioMaxPct;
    }

    /** Una línea del historial de cambios de permisos. */
    @Data
    public static class CambioLog {
        private Long id;
        private LocalDateTime fecha;
        private String usuario;
        private String tipo;
        private String perfil;
        private String usuarioAfectado;
        private String detalle;
    }

    /** Una ruta que el back habría bloqueado (OBSERVAR) o bloqueó (BLOQUEAR). */
    @Data
    public static class BloqueoLog {
        private Long id;
        private LocalDate fecha;
        private String usuario;
        private String rol;
        private String perfil;
        private String modo;
        private String metodo;
        private String ruta;
        private String clave;
        private String accion;
        private int veces;
        private LocalDateTime ultimaVez;
    }

    /** Modo del control en el back: APAGADO, OBSERVAR o BLOQUEAR. */
    @Data
    public static class ModoControl {
        private String modo;
    }
}
