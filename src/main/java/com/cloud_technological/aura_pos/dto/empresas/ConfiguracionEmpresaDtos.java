package com.cloud_technological.aura_pos.dto.empresas;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** Líneas de uso y arranque de la empresa (docs/PLAN_PERFIL_EMPRESA.md). */
public final class ConfiguracionEmpresaDtos {

    private ConfiguracionEmpresaDtos() {
    }

    /** Lo que la empresa declaró y el estado de su arranque. */
    @Data
    public static class ConfiguracionEmpresaDto {
        private Integer empresaId;
        private int version;
        /** Líneas declaradas, en orden de prioridad; si no declaró, la línea por defecto. */
        private List<String> lineas = new ArrayList<>();
        /** false = la empresa nunca declaró líneas y se le asume la línea por defecto. */
        private boolean declarada;
        /** Tablero preferido tal como se guardó (null = automático). */
        private String inicio;
        /** Tablero que ve al entrar: el preferido o el de la primera línea. */
        private String inicioResuelto;
        private List<EstadoPasoDto> arranque = new ArrayList<>();
        /** Hay algún paso de arranque que no terminó bien. */
        private boolean arranquePendiente;
    }

    @Data
    public static class EstadoPasoDto {
        private String paso;
        private String nombre;
        /** OK, ERROR o PENDIENTE. */
        private String resultado;
        /** Fecha y hora (Bogotá, ISO) del último intento; null si nunca corrió. */
        private String ejecutado;
        private String detalle;
    }

    /** Cambio de líneas desde el panel de plataforma. */
    @Data
    public static class ActualizarConfiguracionDto {
        private List<String> lineas;
        /** Línea cuyo tablero ve al entrar; null = automático. Debe estar en {@code lineas}. */
        private String inicio;
        /**
         * true = activa además los submódulos de la plantilla de las líneas (se suman a
         * los que ya tiene; no se apaga nada). false = se exige que ya estén activos.
         */
        private boolean completarModulos;
    }

    /** Una línea del catálogo, con los submódulos que trae marcados en el árbol. */
    @Data
    public static class LineaUsoDto {
        private String codigo;
        private String nombre;
        private String descripcion;
        /** Ids de submódulos de la plantilla, incluida la base común. */
        private List<Integer> submodulos = new ArrayList<>();
        /** Ids de los submódulos que no se pueden desmarcar si la línea está elegida. */
        private List<Integer> minimos = new ArrayList<>();
    }
}
