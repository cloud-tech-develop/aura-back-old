package com.cloud_technological.aura_pos.sync.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Mensajes entre el nodo local y la nube (docs/MODO_HIBRIDO.md). */
public final class SyncDtos {

    private SyncDtos() {
    }

    /** Operación hecha en el nodo que la nube debe reproducir. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Comando {
        /** Identidad del comando: hace el envío idempotente. */
        private String uuid;
        /** VENTA_CREAR, TURNO_ABRIR... ver {@code TiposComando}. */
        private String tipo;
        /** Id del registro principal en el nodo. */
        private Long idLocal;
        private Integer empresaId;
        private Long usuarioId;
        /** Cuándo ocurrió en la tienda. */
        private LocalDateTime fecha;
        private JsonNode payload;
    }

    /** Resultado de reproducir un comando en la nube. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Resultado {
        private String uuid;
        private boolean ok;
        /** Id que recibió en la nube el registro principal. */
        private Long idNube;
        private String error;
        /** Datos que el nodo debe guardar (ej: CUFE de la factura electrónica). */
        private JsonNode datos;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PushPeticion {
        private List<Comando> comandos = new ArrayList<>();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class PushRespuesta {
        private List<Resultado> resultados = new ArrayList<>();
    }

    /** Identidad del nodo según la nube. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class NodoInfo {
        private Long id;
        private Integer empresaId;
        private Integer sucursalId;
        private String codigo;
        private String nombre;
        private String prefijoVentas;
        private Boolean activo;
        private LocalDateTime ultimoContacto;
        private LocalDateTime createdAt;
    }

    /** Equivalencia id del nodo → id de la nube. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Mapa {
        private String tabla;
        private Long idLocal;
        private Long idNube;
    }

    /** Lo que el nodo baja de la nube. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class PullRespuesta {
        private NodoInfo nodo;
        /** Filas por tabla, tal como las da row_to_json en la nube. */
        private Map<String, JsonNode> tablas = new LinkedHashMap<>();
        /** Registros que el nodo creó y que la nube ya tiene con otro id. */
        private List<Mapa> mapas = new ArrayList<>();
        /** Último consecutivo de la serie del nodo, por sucursal. */
        private Map<String, Long> pisosConsecutivo = new LinkedHashMap<>();
        private boolean incluyeSaldos;
        private LocalDateTime generadoEn;
    }

    /** Alta de un nodo en la nube. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class NodoNuevo {
        private String codigo;
        private String nombre;
        private Integer sucursalId;
        private String prefijoVentas;
    }

    /** Respuesta del alta: el token se ve una sola vez. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodoCreado {
        private NodoInfo nodo;
        private String token;
    }

    /** Comando de la cola del nodo, para la pantalla de sincronización. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class FilaCola {
        private Long id;
        private String uuid;
        private String tipo;
        private Long idLocal;
        private String estado;
        private Integer intentos;
        private String ultimoError;
        private LocalDateTime fecha;
        private LocalDateTime enviadoAt;
    }

    /** Estado del nodo para el indicador del front. */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class EstadoLocal {
        private String modo;
        private boolean nubeConfigurada;
        private boolean enLinea;
        private long pendientes;
        private long conError;
        private String ultimoEnvio;
        private String ultimaBajada;
        private String ultimoError;
        private NodoInfo nodo;
    }
}
