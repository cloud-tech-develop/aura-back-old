package com.cloud_technological.aura_pos.services.nube;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ActualizarArchivoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ActualizarCarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ArchivoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.CarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.ContenidoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.CrearCarpetaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.DescargaDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.EventoDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.PapeleraDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.SolicitarEspacioDto;
import com.cloud_technological.aura_pos.dto.nube.NubeDtos.UsoDto;

/**
 * Nube de documentos de la empresa del token (docs/PLAN_NUBE_DOCUMENTOS.md).
 *
 * <p>Todo método recibe el {@link NubeContexto} y solo toca el espacio de
 * {@code ctx.empresaId()}. Lo que no es de esa empresa, o no es visible para quien
 * pide, responde 404 (nunca 403) para no confirmar que existe.
 */
public interface NubeService {

    /** Carpetas y archivos de una carpeta ({@code carpetaId} null = raíz) con su ruta. */
    ContenidoDto contenido(NubeContexto ctx, Long carpetaId);

    /** Crea una carpeta. Las de adentro de una SOLO_FIRMA también lo son. */
    CarpetaDto crearCarpeta(NubeContexto ctx, CrearCarpetaDto dto);

    /** Renombra, mueve o (solo la firma) cambia la visibilidad de una carpeta. */
    CarpetaDto actualizarCarpeta(NubeContexto ctx, Long id, ActualizarCarpetaDto dto);

    /** Manda la carpeta, con todo lo de adentro, a la papelera (30 días). */
    void eliminarCarpeta(NubeContexto ctx, Long id);

    /**
     * Sube uno o varios archivos a una carpeta. Todo o nada: si uno no pasa la
     * validación o la cuota, no se guarda ninguno.
     */
    List<ArchivoDto> subir(NubeContexto ctx, Long carpetaId, List<MultipartFile> archivos);

    /** Descifra y entrega el archivo; {@code vista} = vista previa (queda como VER en la bitácora). */
    DescargaDto descargar(NubeContexto ctx, Long id, boolean vista);

    /** Renombra o mueve un archivo. */
    ArchivoDto actualizarArchivo(NubeContexto ctx, Long id, ActualizarArchivoDto dto);

    /** Manda el archivo a la papelera (30 días). */
    void eliminarArchivo(NubeContexto ctx, Long id);

    PapeleraDto papelera(NubeContexto ctx);

    /** Saca de la papelera una carpeta (con lo que cayó con ella) o un archivo. */
    void restaurar(NubeContexto ctx, String tipo, Long id);

    /** Borra para siempre de la papelera. Solo administradores. */
    void eliminarDefinitivo(NubeContexto ctx, String tipo, Long id);

    /** Espacio usado, cuota, avisos y si ya se aceptaron los términos. */
    UsoDto uso(NubeContexto ctx);

    /** Aceptación de los términos de uso de la nube, por la empresa. */
    UsoDto aceptarTerminos(NubeContexto ctx, String version);

    /**
     * Pide más espacio (solo administradores; tiene costo). Una sola pendiente por
     * empresa; la aprueba o rechaza la plataforma. Devuelve el uso con la solicitud.
     */
    UsoDto solicitarEspacio(NubeContexto ctx, SolicitarEspacioDto dto);

    /** Bitácora de la nube. Solo administradores. */
    List<EventoDto> eventos(NubeContexto ctx, Long archivoId, LocalDate desde, LocalDate hasta);
}
