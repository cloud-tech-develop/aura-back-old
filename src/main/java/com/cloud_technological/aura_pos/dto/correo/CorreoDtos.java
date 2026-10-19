package com.cloud_technological.aura_pos.dto.correo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Envío de documentos por correo (docs/PLAN_CUENTA_COBRO_CORREO.md). */
public final class CorreoDtos {

    private CorreoDtos() {
    }

    /** PDF que genera la pantalla, en base64 (sin el prefijo data:). */
    @Data
    public static class Adjunto {
        @NotBlank(message = "Falta el nombre del archivo")
        @Size(max = 150, message = "El nombre del archivo no puede superar 150 caracteres")
        private String nombre;

        @NotBlank(message = "Falta el archivo adjunto")
        private String base64;
    }

    /** Un correo con un documento. */
    @Data
    public static class Enviar {
        @NotEmpty(message = "Escriba al menos un destinatario")
        @Size(max = 10, message = "Máximo 10 destinatarios")
        private List<String> para = new ArrayList<>();

        @Size(max = 10, message = "Máximo 10 correos en copia")
        private List<String> cc = new ArrayList<>();

        @NotBlank(message = "Escriba el asunto")
        @Size(max = 200, message = "El asunto no puede superar 200 caracteres")
        private String asunto;

        @Size(max = 5000, message = "El mensaje no puede superar 5000 caracteres")
        private String mensaje;

        /** PDF que armó la pantalla; null en el cobro de cartera (lo arma el servidor). */
        @Valid
        private Adjunto adjunto;
    }

    /** Un comprobante de nómina del lote: el correo del empleado lo pone el backend. */
    @Data
    public static class ItemLote {
        @NotNull(message = "Falta la nómina del empleado")
        private Long nominaId;

        @Valid
        @NotNull(message = "Falta el PDF del comprobante")
        private Adjunto adjunto;
    }

    /** Recordatorio de cobro a todos los clientes vencidos: el servidor arma cada estado de cuenta. */
    @Data
    public static class CobroMasivo {
        @NotBlank(message = "Escriba el asunto")
        @Size(max = 200, message = "El asunto no puede superar 200 caracteres")
        private String asunto;

        @Size(max = 5000, message = "El mensaje no puede superar 5000 caracteres")
        private String mensaje;
    }

    /**
     * Comprobantes de nómina en masa. El front los manda en tandas: la primera sin
     * {@code loteId} y las siguientes con el que devolvió la primera.
     */
    @Data
    public static class Lote {
        private String loteId;

        @NotBlank(message = "Escriba el asunto")
        @Size(max = 200, message = "El asunto no puede superar 200 caracteres")
        private String asunto;

        @Size(max = 5000, message = "El mensaje no puede superar 5000 caracteres")
        private String mensaje;

        @Valid
        @NotEmpty(message = "No hay comprobantes para enviar")
        @Size(max = 50, message = "Envíe máximo 50 comprobantes por tanda")
        private List<ItemLote> items = new ArrayList<>();
    }

    /** Lo que propone el sistema para el correo de un documento. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sugerencia {
        private List<String> para;
        private String asunto;
        private String mensaje;
        /** El envío de correos está configurado (variables RESEND_* en el .env). */
        private boolean disponible;
    }

    /** Un correo enviado o en cola. */
    @Data
    public static class Fila {
        private Long id;
        private String tipo;
        private Long documentoId;
        private String destinatario;
        private String asunto;
        private String estado;
        private String error;
        private LocalDateTime createdAt;
        private LocalDateTime enviadoAt;
        private String usuarioNombre;
        /** Solo en lotes de nómina: a quién iba. */
        private String empleadoNombre;
    }

    /** Resultado de encolar una tanda del lote. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoteEncolado {
        private String loteId;
        private int encolados;
        /** Empleados que no tienen correo: no se encolan. */
        private List<String> sinCorreo;
    }

    /** Avance del lote. */
    @Data
    public static class ResumenLote {
        private String loteId;
        private int total;
        private int pendientes;
        private int enviados;
        private int fallidos;
        private List<Fila> filas = new ArrayList<>();
    }
}
