package com.cloud_technological.aura_pos.services.terceros;

import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.terceros.TerceroCorreoDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroTelefonoDto;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroContactoQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.services.implementations.R2StorageService;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Datos personales del tercero que no caben en sus columnas (V211): varios
 * teléfonos y correos, propios y de la persona de contacto, y las imágenes
 * (foto oficial, foto de perfil y firma).
 *
 * <p>{@code tercero.telefono} y {@code tercero.email} siguen guardando el primero
 * propio, porque facturas, PDF y envíos los leen de ahí.
 */
@Service
public class TerceroDatosPersonalesService {

    /** Imágenes que admite el tercero, por el segmento de la URL. */
    public static final Set<String> IMAGENES = Set.of("foto-oficial", "foto-perfil", "firma");

    private final TerceroContactoQueryRepository contactos;
    private final TerceroJPARepository terceros;
    private final R2StorageService storage;

    public TerceroDatosPersonalesService(TerceroContactoQueryRepository contactos,
            TerceroJPARepository terceros, R2StorageService storage) {
        this.contactos = contactos;
        this.terceros = terceros;
        this.storage = storage;
    }

    /**
     * Pone en la entidad el primer teléfono y el primer correo propios, antes de
     * guardarla. Si una lista es null no se toca el valor que tenga.
     */
    public void aplicarPrincipales(TerceroEntity entity, List<TerceroTelefonoDto> telefonos,
            List<TerceroCorreoDto> correos) {
        if (telefonos != null)
            entity.setTelefono(telefonos.stream().filter(t -> !Boolean.TRUE.equals(t.getDeContacto()))
                    .map(t -> t.getNumero().trim()).findFirst().orElse(null));
        if (correos != null)
            entity.setEmail(correos.stream().filter(c -> !Boolean.TRUE.equals(c.getDeContacto()))
                    .map(c -> c.getCorreo().trim().toLowerCase()).findFirst().orElse(null));
    }

    /**
     * Guarda las listas del tercero ya persistido. Si una lista no llega (diálogo
     * rápido del POS, importadores), el teléfono o correo suelto de la entidad
     * queda como el primero de su lista, para que no se desfasen.
     */
    @Transactional
    public void guardarContactos(TerceroEntity tercero, List<TerceroTelefonoDto> telefonos,
            List<TerceroCorreoDto> correos) {
        Long id = tercero.getId();
        if (telefonos != null) contactos.reemplazarTelefonos(id, telefonos);
        else if (tercero.getTelefono() != null && !tercero.getTelefono().isBlank())
            contactos.fijarTelefonoPrincipal(id, tercero.getTelefono());
        if (correos != null) contactos.reemplazarCorreos(id, correos);
        else if (tercero.getEmail() != null && !tercero.getEmail().isBlank())
            contactos.fijarCorreoPrincipal(id, tercero.getEmail());
    }

    /** Completa el DTO de salida con los teléfonos y correos. */
    public void completar(TerceroDto dto) {
        dto.setTelefonos(contactos.telefonos(dto.getId()));
        dto.setCorreos(contactos.correos(dto.getId()));
    }

    /**
     * Sube o reemplaza una imagen del tercero.
     *
     * @param tipo foto-oficial | foto-perfil | firma
     * @return la URL pública de la imagen
     */
    @Transactional
    public String subirImagen(Long terceroId, String tipo, MultipartFile archivo, Integer empresaId) {
        TerceroEntity t = buscar(terceroId, empresaId, tipo);
        if (archivo == null || archivo.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Adjunte la imagen.");
        String url = storage.subirImagen(archivo, "terceros/" + empresaId);
        fijarImagen(t, tipo, url);
        terceros.save(t);
        return url;
    }

    /** Quita una imagen del tercero (el archivo queda en el almacenamiento). */
    @Transactional
    public void quitarImagen(Long terceroId, String tipo, Integer empresaId) {
        TerceroEntity t = buscar(terceroId, empresaId, tipo);
        fijarImagen(t, tipo, null);
        terceros.save(t);
    }

    private TerceroEntity buscar(Long terceroId, Integer empresaId, String tipo) {
        if (!IMAGENES.contains(tipo))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de imagen no válido: " + tipo);
        return terceros.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));
    }

    private static void fijarImagen(TerceroEntity t, String tipo, String url) {
        switch (tipo) {
            case "foto-oficial" -> t.setFotoOficialUrl(url);
            case "foto-perfil" -> t.setFotoPerfilUrl(url);
            default -> t.setFirmaUrl(url);
        }
    }
}
