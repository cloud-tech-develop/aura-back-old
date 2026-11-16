package com.cloud_technological.aura_pos.dto.nube;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO de la nube de documentos (docs/PLAN_NUBE_DOCUMENTOS.md §6). Ninguno lleva
 * la clave del objeto, el IV ni una URL: el archivo solo sale por la descarga.
 */
public final class NubeDtos {

    private NubeDtos() {
    }

    /** Carpeta tal como la ve el usuario. {@code creadaPorFirma}: la creó alguien que entró por la firma. */
    public record CarpetaDto(Long id, Long padreId, String nombre, String visibilidad, short nivel,
            boolean creadaPorFirma, LocalDateTime createdAt, LocalDateTime deletedAt) {
    }

    public record ArchivoDto(Long id, Long carpetaId, String nombre, String extension, String mime,
            int tamanoBytes, String sha256, boolean subidoPorFirma, LocalDateTime createdAt,
            LocalDateTime deletedAt) {
    }

    /** Un tramo de la ruta (migas de pan), de la raíz a la carpeta actual. */
    public record TramoRutaDto(Long id, String nombre) {
    }

    /** Lo que hay en una carpeta ({@code carpeta} null = raíz). */
    public record ContenidoDto(CarpetaDto carpeta, List<TramoRutaDto> ruta, List<CarpetaDto> carpetas,
            List<ArchivoDto> archivos) {
    }

    public record CrearCarpetaDto(String nombre, Long padreId, String visibilidad) {
    }

    /** Renombrar y/o mover; {@code moverA} con {@code raiz = true} = a la raíz. Visibilidad solo la firma. */
    public record ActualizarCarpetaDto(String nombre, Long moverA, Boolean raiz, String visibilidad) {
    }

    public record ActualizarArchivoDto(String nombre, Long moverA, Boolean raiz) {
    }

    /**
     * Uso del espacio. {@code aviso}: null, "80" o "95" (porcentaje alcanzado) o
     * "LLENO". {@code esFirma}: quien consulta entró por la firma (puede crear SOLO_FIRMA).
     */
    public record UsoDto(long usadoBytes, long cuotaBytes, int archivos, int maxArchivos, double porcentaje,
            String aviso, boolean terminosAceptados, String terminosVersion, boolean esFirma,
            boolean esAdministrador, List<String> extensionesPermitidas, int maxBytesArchivo,
            SolicitudEspacioDto solicitud) {
    }

    /** Pedido de más espacio (lo hace un administrador; tiene costo). */
    public record SolicitarEspacioDto(Integer cuotaSolicitadaMb) {
    }

    /**
     * Última solicitud de la empresa, como la ve ella. {@code estado}: PENDIENTE,
     * APROBADA o RECHAZADA; {@code observacion}: la respuesta de Aura.
     */
    public record SolicitudEspacioDto(Long id, int cuotaActualMb, int cuotaSolicitadaMb, String estado,
            String observacion, LocalDateTime createdAt, LocalDateTime atendidoAt) {
    }

    /** Solicitud vista desde el panel de plataforma (sin ningún documento, solo el uso). */
    public record SolicitudPlataformaDto(Long id, Integer empresaId, String empresaNombre, String empresaNit,
            String solicitadoPor, int cuotaActualMb, int cuotaSolicitadaMb, long usadoBytes, String estado,
            LocalDateTime createdAt) {
    }

    /** Respuesta de plataforma: al aprobar, {@code cuotaMb} (si falta, la solicitada). */
    public record ResponderSolicitudDto(Integer cuotaMb, String observacion) {
    }

    public record PapeleraDto(List<CarpetaDto> carpetas, List<ArchivoDto> archivos) {
    }

    public record EventoDto(Long id, String accion, Integer usuarioId, String usuario, boolean porFirma,
            Long archivoId, Long carpetaId, String detalle, String ip, LocalDateTime createdAt) {
    }

    /** Archivo listo para entregar por el controlador. */
    public record DescargaDto(String nombre, String mime, byte[] contenido) {
    }
}
