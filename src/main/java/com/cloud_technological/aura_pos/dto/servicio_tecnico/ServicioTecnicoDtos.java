package com.cloud_technological.aura_pos.dto.servicio_tecnico;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTOs del módulo Servicio técnico (docs/PLAN_SERVICIO_TECNICO.md).
 */
public final class ServicioTecnicoDtos {

    private ServicioTecnicoDtos() {
    }

    // ── Entrada ─────────────────────────────────────────────────────────

    /** Datos del equipo (la card). Los IMEI son opcionales: una tablet WiFi no tiene. */
    @Data
    @NoArgsConstructor
    public static class Equipo {
        @NotBlank(message = "Indique el tipo de equipo")
        @Size(max = 20)
        private String tipo = "CELULAR";

        @NotBlank(message = "La marca es obligatoria")
        @Size(max = 60, message = "La marca admite máximo 60 caracteres")
        private String marca;

        @NotBlank(message = "El modelo es obligatorio")
        @Size(max = 80, message = "El modelo admite máximo 80 caracteres")
        private String modelo;

        @Size(max = 20)
        private String imei;

        @Size(max = 20)
        private String imei2;

        @Size(max = 60)
        private String serial;

        @Size(max = 40)
        private String color;

        private List<String> accesorios = new ArrayList<>();

        private List<String> estadoFisico = new ArrayList<>();

        private Boolean enciende;

        @NotBlank(message = "Describa el problema que reporta el cliente")
        @Size(max = 4000)
        private String problemasReportados;
    }

    /** Crear o editar una orden. */
    @Data
    @NoArgsConstructor
    public static class GuardarOrden {
        @NotNull(message = "Seleccione el cliente")
        private Long clienteId;

        @Size(max = 30)
        private String contactoTelefono;

        @Email(message = "El correo de contacto no es válido")
        @Size(max = 150)
        private String contactoEmail;

        private Long tecnicoId;

        private String prioridad = "NORMAL";

        private LocalDate fechaEstimada;

        @Size(max = 8000)
        private String diagnostico;

        @Size(max = 8000)
        private String trabajoRealizado;

        @Size(max = 4000)
        private String observaciones;

        @Min(value = 0, message = "La garantía no puede ser negativa")
        @Max(value = 730, message = "La garantía máxima es de 730 días")
        private Integer garantiaDias;

        @Valid
        @NotNull(message = "Faltan los datos del equipo")
        private Equipo equipo;
    }

    /** Agregar una línea: repuesto, mano de obra u otro. */
    @Data
    @NoArgsConstructor
    public static class GuardarItem {
        @NotBlank(message = "Indique el tipo de línea")
        private String tipo;

        /** Producto del inventario; null = repuesto libre (comprado afuera). */
        private Long productoId;

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 200, message = "La descripción admite máximo 200 caracteres")
        private String descripcion;

        @NotNull(message = "La cantidad es obligatoria")
        @DecimalMin(value = "0.0001", message = "La cantidad debe ser mayor que cero")
        private BigDecimal cantidad;

        @NotNull(message = "El valor es obligatorio")
        @DecimalMin(value = "0", message = "El valor no puede ser negativo")
        private BigDecimal valorUnitario;
    }

    /** Pasar la orden a otro estado. */
    @Data
    @NoArgsConstructor
    public static class CambiarEstado {
        @NotBlank(message = "Seleccione el estado")
        private String estado;

        @Size(max = 1000)
        private String nota;
    }

    /** Nota libre en el historial. */
    @Data
    @NoArgsConstructor
    public static class Nota {
        @NotBlank(message = "Escriba la nota")
        @Size(max = 1000, message = "La nota admite máximo 1000 caracteres")
        private String nota;
    }

    /** Filtros de la bandeja (van en {@code params} del PageableDto). */
    @Data
    @NoArgsConstructor
    public static class Filtro {
        /** Un estado, o ABIERTAS (todas menos ENTREGADO y CANCELADO). */
        private String estado;
        private Long tecnicoId;
        private boolean soloMias;
        private boolean soloVencidas;
        private LocalDate desde;
        private LocalDate hasta;
    }

    /** Cliente creado desde la recepción, con lo mínimo. */
    @Data
    @NoArgsConstructor
    public static class ClienteRapido {
        @NotBlank(message = "Seleccione el tipo de documento")
        private String tipoDocumento = "CC";

        @NotBlank(message = "El número de documento es obligatorio")
        @Size(max = 20, message = "El documento admite máximo 20 caracteres")
        private String numeroDocumento;

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 80)
        private String nombres;

        @Size(max = 80)
        private String apellidos;

        @NotBlank(message = "El teléfono es obligatorio")
        @Size(max = 30)
        private String telefono;

        @Email(message = "El correo no es válido")
        @Size(max = 150)
        private String email;
    }

    /** Cliente como lo usa la recepción. */
    @Data
    @NoArgsConstructor
    public static class ClienteDto {
        private Long id;
        private String numeroDocumento;
        private String nombreCompleto;
        private String telefono;
        private String email;
    }

    @Data
    @NoArgsConstructor
    public static class GuardarConfig {
        @NotBlank(message = "El prefijo es obligatorio")
        @Size(max = 10, message = "El prefijo admite máximo 10 caracteres")
        private String prefijo;

        @NotNull
        @Min(value = 0, message = "La garantía no puede ser negativa")
        @Max(value = 730, message = "La garantía máxima es de 730 días")
        private Integer garantiaDias;

        @Size(max = 4000)
        private String condiciones;

        /** Servicio con el que se factura la mano de obra (y repuestos fuera del inventario). */
        private Long productoServicioId;
    }

    /** Cómo entra el dinero: igual que un recibo de caja. */
    @Data
    @NoArgsConstructor
    public static class Pago {
        @NotNull(message = "Escriba el valor")
        @DecimalMin(value = "1", message = "El valor debe ser mayor que cero")
        private BigDecimal monto;

        @NotBlank(message = "Elija el medio de pago")
        private String metodoPago;

        /** Cuenta donde entró el dinero (caja general, caja menor o banco). */
        private Long cuentaContableId;

        /** Solo al cobrar el saldo en efectivo: la caja abierta donde entra. */
        private Long turnoCajaId;

        /** Efectivo que ya se movió de la caja otro día. */
        private Boolean cajaOtroDia;

        @Size(max = 120)
        private String referencia;
    }

    @Data
    @NoArgsConstructor
    public static class AnularAbono {
        @NotBlank(message = "Escriba por qué se anula el abono")
        @Size(max = 300)
        private String motivo;
    }

    /** Entregar el equipo, con o sin factura. */
    @Data
    @NoArgsConstructor
    public static class Entregar {
        /** true = factura de venta con las líneas de la orden (los abonos se cruzan). */
        private boolean facturar = true;

        /** FACTURA o CUENTA_COBRO; null = el que le corresponde a la empresa. */
        private String tipoDocumento;

        /** Lo que paga el cliente al recoger; null = el saldo queda en cartera. */
        @Valid
        private Pago cobro;

        /** Obligatoria si se entrega sin factura una orden con valor (garantía, sin cobro…). */
        @Size(max = 1000)
        private String nota;
    }

    // ── Salida ──────────────────────────────────────────────────────────

    /** Fila de la bandeja. */
    @Data
    @NoArgsConstructor
    public static class OrdenFila {
        private Long id;
        private String codigo;
        private Long clienteId;
        private String clienteNombre;
        private String clienteTelefono;
        private String equipoTipo;
        private String equipoMarca;
        private String equipoModelo;
        private String equipoImei;
        private String equipoColor;
        private Long tecnicoId;
        private String tecnicoNombre;
        private String estado;
        private String prioridad;
        private LocalDateTime fechaRecibido;
        private LocalDate fechaEstimada;
        private BigDecimal total;
        private BigDecimal totalAbonado;
        private BigDecimal saldo;
        private Boolean vencida;
        private Integer fotos;
        private Long totalRows;
    }

    /** Equipo como sale en la card. */
    @Data
    @NoArgsConstructor
    public static class EquipoDto {
        private String tipo;
        private String marca;
        private String modelo;
        private String imei;
        private String imei2;
        private String serial;
        private String color;
        private List<String> accesorios = new ArrayList<>();
        private List<String> estadoFisico = new ArrayList<>();
        private Boolean enciende;
        private String problemasReportados;
    }

    @Data
    @NoArgsConstructor
    public static class ItemDto {
        private Long id;
        private String tipo;
        private Long productoId;
        private String productoSku;
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal valorUnitario;
        private BigDecimal total;
    }

    /** Metadatos de una foto; el contenido se pide aparte (nunca una URL pública). */
    @Data
    @NoArgsConstructor
    public static class FotoDto {
        private Long id;
        private String nombre;
        private String mime;
        private Integer tamanoBytes;
        private String momento;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    public static class HistorialDto {
        private Long id;
        private String tipo;
        private String estadoAnterior;
        private String estadoNuevo;
        private String nota;
        private String usuarioNombre;
        private LocalDateTime createdAt;
    }

    /** Orden completa. */
    @Data
    @NoArgsConstructor
    public static class OrdenDetalle {
        private Long id;
        private String codigo;
        private Integer numero;
        private Long clienteId;
        private String clienteNombre;
        private String clienteDocumento;
        private String clienteTelefono;
        private String clienteEmail;
        private String contactoTelefono;
        private String contactoEmail;
        private Long tecnicoId;
        private String tecnicoNombre;
        private String estado;
        private String prioridad;
        private LocalDateTime fechaRecibido;
        private LocalDate fechaEstimada;
        private LocalDateTime fechaEntregado;
        private String diagnostico;
        private String trabajoRealizado;
        private String observaciones;
        private Integer garantiaDias;
        private LocalDate garantiaHasta;
        private BigDecimal totalRepuestos;
        private BigDecimal totalManoObra;
        private BigDecimal total;
        private BigDecimal totalAbonado;
        private BigDecimal saldo;
        private String motivoCancelacion;
        private Boolean vencida;
        private Boolean editable;
        private LocalDateTime createdAt;
        private EquipoDto equipo;
        private List<ItemDto> items = new ArrayList<>();
        private List<FotoDto> fotos = new ArrayList<>();
        private List<HistorialDto> historial = new ArrayList<>();
        /** Estados a los que se puede pasar desde el actual. */
        private List<String> estadosPermitidos = new ArrayList<>();
        private List<AbonoDto> abonos = new ArrayList<>();
        private Long facturaVentaId;
        private String facturaNumero;
        private String facturaEstado;
        private Long ventaId;
        private BigDecimal saldoCartera;
        private String tokenPublico;
        private LocalDateTime aprobadoClienteAt;
        /** Otras órdenes del mismo IMEI (el equipo ya vino antes). */
        private List<OrdenAnterior> anteriores = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class AbonoDto {
        private Long id;
        private Long reciboCajaId;
        private String reciboNumero;
        private BigDecimal monto;
        private String metodoPago;
        private String estado;
        private String usuarioNombre;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    public static class OrdenAnterior {
        private Long id;
        private String codigo;
        private String estado;
        private LocalDateTime fechaRecibido;
        private String problemasReportados;
        private String trabajoRealizado;
    }

    // ── Consulta pública (sin sesión) ───────────────────────────────────

    /** Lo que ve el cliente con el enlace: sin documento, teléfono ni IMEI completo. */
    @Data
    @NoArgsConstructor
    public static class ConsultaPublica {
        private String empresaNombre;
        private String empresaTelefono;
        private String empresaLogoUrl;
        private String codigo;
        private String clienteNombre;
        private String estado;
        private String equipoTipo;
        private String equipoMarca;
        private String equipoModelo;
        private String equipoColor;
        private String imeiFinal;
        private String problemasReportados;
        private String diagnostico;
        private String trabajoRealizado;
        private LocalDateTime fechaRecibido;
        private LocalDate fechaEstimada;
        private LocalDateTime fechaEntregado;
        private BigDecimal total;
        private BigDecimal totalAbonado;
        private BigDecimal saldo;
        private Integer garantiaDias;
        private LocalDateTime aprobadoClienteAt;
        private boolean puedeAprobar;
        private String condiciones;
        private List<LineaPublica> items = new ArrayList<>();
        private List<PasoPublico> pasos = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    public static class LineaPublica {
        private String tipo;
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal total;
    }

    @Data
    @NoArgsConstructor
    public static class PasoPublico {
        private String estado;
        private LocalDateTime fecha;
    }

    /** Respuesta del cliente al presupuesto desde el enlace. */
    @Data
    @NoArgsConstructor
    public static class RespuestaPresupuesto {
        private boolean aprueba;

        @Size(max = 500)
        private String comentario;
    }

    /** Indicadores de la bandeja. */
    @Data
    @NoArgsConstructor
    public static class Indicadores {
        private long abiertas;
        private long recibidas;
        private long enProceso;
        private long esperandoCliente;
        private long listas;
        private long vencidas;
        private long entregadasMes;
    }

    @Data
    @NoArgsConstructor
    public static class ConfigDto {
        private String prefijo;
        private Integer siguienteNumero;
        private Integer garantiaDias;
        private String condiciones;
        private Long productoServicioId;
        private String productoServicioNombre;
    }

    /** Contenido de una foto ya descifrado. */
    public record FotoContenido(String nombre, String mime, byte[] bytes) {
    }
}
