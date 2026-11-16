package com.cloud_technological.aura_pos.services.nube;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
import com.cloud_technological.aura_pos.dto.permisos.PermisosUsuarioDto;
import com.cloud_technological.aura_pos.entity.nube.NubeArchivoEntity;
import com.cloud_technological.aura_pos.entity.nube.NubeCarpetaEntity;
import com.cloud_technological.aura_pos.entity.nube.NubeEspacioEntity;
import com.cloud_technological.aura_pos.entity.nube.NubeEventoEntity;
import com.cloud_technological.aura_pos.entity.nube.NubeSolicitudEspacioEntity;
import com.cloud_technological.aura_pos.mappers.NubeMapper;
import com.cloud_technological.aura_pos.repositories.nube.NubeArchivoJPARepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeCarpetaJPARepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeEspacioJPARepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeSolicitudEspacioJPARepository;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.ArchivoFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.CarpetaFila;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Espacio;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Nodo;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.ObjetoArchivo;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Uso;
import com.cloud_technological.aura_pos.services.nube.NubeAlmacenamiento.ObjetoGuardado;
import com.cloud_technological.aura_pos.services.nube.ValidadorArchivoNube.ArchivoValido;
import com.cloud_technological.aura_pos.services.permisos.AccionPermiso;
import com.cloud_technological.aura_pos.services.permisos.PermisoUsuarioService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PoliticaRoles;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de la nube de documentos (docs/PLAN_NUBE_DOCUMENTOS.md, F2–F3).
 *
 * <p>Los permisos se revisan AQUÍ en cada método, aunque el interceptor esté en
 * modo OBSERVAR: con documentos de clientes no hay modo de prueba. La plataforma
 * (PLATFORM_ADMIN) no entra a los documentos de las empresas.
 */
@Slf4j
@Service
public class NubeServiceImpl implements NubeService {

    /** Submódulo que protege la nube (menú y perfiles: docs/sql/menu_nube_documentos.sql). */
    public static final String CLAVE = "principal.documentos";

    /** Versión vigente del texto de términos que acepta la empresa. */
    public static final String TERMINOS_VERSION = "1";

    static final int DIAS_PAPELERA = 30;
    static final int MAX_ARCHIVOS_POR_SUBIDA = 10;

    /** Tope de una solicitud de espacio (10 GB); más que eso se habla con Aura. */
    static final int MAX_CUOTA_SOLICITABLE_MB = 10_240;

    private static final long BYTES_MB = 1024L * 1024L;

    private static final String CARPETA = "carpeta";
    private static final String ARCHIVO = "archivo";

    private final NubeQueryRepository query;
    private final NubeCarpetaJPARepository carpetas;
    private final NubeArchivoJPARepository archivos;
    private final NubeEspacioJPARepository espacios;
    private final NubeSolicitudEspacioJPARepository solicitudes;
    private final NubeEspacioService espacioService;
    private final NubeAlmacenamiento almacenamiento;
    private final NubeBitacora bitacora;
    private final LimiteSubidasNube limite;
    private final PermisoUsuarioService permisos;
    private final NubeMapper mapper;

    public NubeServiceImpl(NubeQueryRepository query, NubeCarpetaJPARepository carpetas,
            NubeArchivoJPARepository archivos, NubeEspacioJPARepository espacios, NubeSolicitudEspacioJPARepository solicitudes, NubeEspacioService espacioService,
            NubeAlmacenamiento almacenamiento,
            NubeBitacora bitacora, LimiteSubidasNube limite, PermisoUsuarioService permisos, NubeMapper mapper) {
        this.query = query;
        this.carpetas = carpetas;
        this.archivos = archivos;
        this.espacios = espacios;
        this.solicitudes = solicitudes;
        this.espacioService = espacioService;
        this.almacenamiento = almacenamiento;
        this.bitacora = bitacora;
        this.limite = limite;
        this.permisos = permisos;
        this.mapper = mapper;
    }

    // ── Contenido y carpetas ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ContenidoDto contenido(NubeContexto ctx, Long carpetaId) {
        exigir(ctx, AccionPermiso.VER);
        CarpetaFila actual = carpetaId == null ? null : carpetaVisible(ctx, carpetaId);
        return new ContenidoDto(
                actual == null ? null : mapper.toDto(actual),
                actual == null ? List.of() : mapper.toRuta(query.ruta(carpetaId, ctx.empresaId())),
                mapper.toCarpetas(query.carpetas(ctx.empresaId(), carpetaId, ctx.firmaId())),
                mapper.toArchivos(query.archivos(ctx.empresaId(), carpetaId, ctx.firmaId())));
    }

    @Override
    @Transactional
    public CarpetaDto crearCarpeta(NubeContexto ctx, CrearCarpetaDto dto) {
        exigir(ctx, AccionPermiso.CREAR);
        if (dto == null) throw error(HttpStatus.BAD_REQUEST, "Indica el nombre de la carpeta.");
        String nombre = ValidadorArchivoNube.limpiarNombreCarpeta(dto.nombre());

        CarpetaFila padre = dto.padreId() == null ? null : carpetaVisible(ctx, dto.padreId());
        int nivel = padre == null ? 1 : padre.nivel() + 1;
        if (nivel > NubeCarpetaEntity.NIVEL_MAXIMO)
            throw error(HttpStatus.BAD_REQUEST, "Solo se permiten " + NubeCarpetaEntity.NIVEL_MAXIMO
                    + " niveles de carpetas.");

        String visibilidad = visibilidadPedida(ctx, dto.visibilidad());
        if (padre != null && NubeCarpetaEntity.SOLO_FIRMA.equals(padre.visibilidad()))
            visibilidad = NubeCarpetaEntity.SOLO_FIRMA; // lo de adentro de una privada es privado

        if (query.existeNombreCarpeta(ctx.empresaId(), dto.padreId(), nombre, null))
            throw error(HttpStatus.CONFLICT, "Ya existe una carpeta con ese nombre aquí.");

        NubeCarpetaEntity c = new NubeCarpetaEntity();
        c.setEmpresaId(ctx.empresaId());
        c.setPadreId(dto.padreId());
        c.setNombre(nombre);
        c.setVisibilidad(visibilidad);
        c.setNivel((short) nivel);
        c.setCreadoPor(ctx.usuarioId());
        c.setCreadoPorFirmaId(ctx.firmaId());
        c.setPrivadaFirmaId(esPrivada(visibilidad) ? ctx.firmaId() : null);
        c = carpetas.saveAndFlush(c);

        bitacora.registrar(ctx, NubeEventoEntity.CREAR_CARPETA, null, c.getId(), esPrivada(visibilidad),
                detalle("nombre", nombre, "padreId", dto.padreId()));
        return mapper.toDto(carpetaVisible(ctx, c.getId()));
    }

    @Override
    @Transactional
    public CarpetaDto actualizarCarpeta(NubeContexto ctx, Long id, ActualizarCarpetaDto dto) {
        exigir(ctx, AccionPermiso.EDITAR);
        if (dto == null) throw error(HttpStatus.BAD_REQUEST, "No hay cambios.");
        CarpetaFila fila = carpetaVisible(ctx, id);
        NubeCarpetaEntity c = carpetaEntidad(ctx, id);

        Long padreFinal = c.getPadreId();
        boolean mueve = Boolean.TRUE.equals(dto.raiz()) || dto.moverA() != null;
        CarpetaFila destino = null;
        List<Nodo> subarbol = query.subarbol(id, ctx.empresaId());

        if (mueve) {
            if (!Boolean.TRUE.equals(dto.raiz())) {
                destino = carpetaVisible(ctx, dto.moverA());
                Long destinoId = destino.id();
                if (subarbol.stream().anyMatch(n -> n.id().equals(destinoId)))
                    throw error(HttpStatus.BAD_REQUEST, "No puedes mover una carpeta dentro de sí misma.");
            }
            padreFinal = destino == null ? null : destino.id();
        }

        String nombre = dto.nombre() != null ? ValidadorArchivoNube.limpiarNombreCarpeta(dto.nombre()) : c.getNombre();
        boolean renombra = !nombre.equals(c.getNombre());
        if ((renombra || mueve) && query.existeNombreCarpeta(ctx.empresaId(), padreFinal, nombre, id))
            throw error(HttpStatus.CONFLICT, "Ya existe una carpeta con ese nombre en el destino.");

        List<Long> ids = subarbol.stream().map(Nodo::id).toList();
        if (mueve) {
            int altura = subarbol.stream().mapToInt(Nodo::profundidad).max().orElse(1);
            int nuevoNivel = destino == null ? 1 : destino.nivel() + 1;
            if (nuevoNivel + altura - 1 > NubeCarpetaEntity.NIVEL_MAXIMO)
                throw error(HttpStatus.BAD_REQUEST, "Al moverla quedarían más de " + NubeCarpetaEntity.NIVEL_MAXIMO
                        + " niveles de carpetas.");
            query.desplazarNivel(ctx.empresaId(), ids, nuevoNivel - c.getNivel());
            c.setNivel((short) nuevoNivel);
            c.setPadreId(padreFinal);
            if (destino != null && esPrivada(destino.visibilidad())) {
                query.marcarSoloFirma(ctx.empresaId(), ids, ctx.firmaId());
                c.setVisibilidad(NubeCarpetaEntity.SOLO_FIRMA);
                c.setPrivadaFirmaId(ctx.firmaId());
            }
        }

        if (dto.visibilidad() != null) {
            String pedida = visibilidadPedida(ctx, dto.visibilidad());
            if (!ctx.esFirma())
                throw error(HttpStatus.FORBIDDEN, "Solo la firma contable puede cambiar la privacidad de una carpeta.");
            if (esPrivada(pedida)) {
                query.marcarSoloFirma(ctx.empresaId(), ids, ctx.firmaId());
                c.setVisibilidad(NubeCarpetaEntity.SOLO_FIRMA);
                c.setPrivadaFirmaId(ctx.firmaId());
            } else if (!pedida.equals(c.getVisibilidad())) {
                boolean padrePrivado = c.getPadreId() != null && carpetas.findById(c.getPadreId())
                        .map(p -> esPrivada(p.getVisibilidad())).orElse(false);
                if (padrePrivado)
                    throw error(HttpStatus.BAD_REQUEST,
                            "Está dentro de una carpeta privada de la firma: muévela antes de compartirla.");
                c.setVisibilidad(NubeCarpetaEntity.COMPARTIDA);
                c.setPrivadaFirmaId(null);
            }
        }

        c.setNombre(nombre);
        carpetas.saveAndFlush(c);

        boolean privada = esPrivada(c.getVisibilidad()) || esPrivada(fila.visibilidad());
        if (renombra)
            bitacora.registrar(ctx, NubeEventoEntity.RENOMBRAR, null, id, privada,
                    detalle("antes", fila.nombre(), "despues", nombre));
        if (mueve)
            bitacora.registrar(ctx, NubeEventoEntity.MOVER, null, id, privada,
                    detalle("nombre", nombre, "desde", fila.padreId(), "hacia", padreFinal));
        return mapper.toDto(carpetaVisible(ctx, id));
    }

    @Override
    @Transactional
    public void eliminarCarpeta(NubeContexto ctx, Long id) {
        exigir(ctx, AccionPermiso.ANULAR);
        CarpetaFila fila = carpetaVisible(ctx, id);
        List<Nodo> vivas = query.subarbol(id, ctx.empresaId()).stream().filter(n -> n.deletedAt() == null).toList();
        exigirSinPrivadasOcultas(ctx, vivas);

        LocalDateTime marca = ahora();
        query.enviarAPapelera(ctx.empresaId(), vivas.stream().map(Nodo::id).toList(), marca, ctx.usuarioId(),
                marca.plusDays(DIAS_PAPELERA));
        bitacora.registrar(ctx, NubeEventoEntity.ELIMINAR, null, id, esPrivada(fila.visibilidad()),
                detalle("tipo", CARPETA, "nombre", fila.nombre(), "subcarpetas", vivas.size() - 1));
    }

    // ── Archivos ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<ArchivoDto> subir(NubeContexto ctx, Long carpetaId, List<MultipartFile> entrada) {
        exigir(ctx, AccionPermiso.CREAR);
        if (entrada == null || entrada.isEmpty())
            throw error(HttpStatus.BAD_REQUEST, "Selecciona al menos un archivo.");
        if (entrada.size() > MAX_ARCHIVOS_POR_SUBIDA)
            throw error(HttpStatus.BAD_REQUEST, "Sube máximo " + MAX_ARCHIVOS_POR_SUBIDA + " archivos a la vez.");

        CarpetaFila carpeta = carpetaId == null ? null : carpetaVisible(ctx, carpetaId);
        boolean privada = carpeta != null && esPrivada(carpeta.visibilidad());

        // 1) Validar todo antes de tocar nada.
        List<Pendiente> pendientes = new ArrayList<>();
        Set<String> enEsteLote = new HashSet<>();
        long total = 0;
        for (MultipartFile f : entrada) {
            if (f == null || f.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "Uno de los archivos está vacío.");
            if (f.getSize() > NubeArchivoEntity.MAX_BYTES)
                throw error(HttpStatus.BAD_REQUEST, "\"" + nombreParaMensaje(f) + "\" pesa más de 1 MB.");
            byte[] bytes;
            try {
                bytes = f.getBytes();
            } catch (IOException e) {
                throw error(HttpStatus.BAD_REQUEST, "No se pudo leer \"" + nombreParaMensaje(f) + "\".");
            }
            ArchivoValido v = ValidadorArchivoNube.validar(f.getOriginalFilename(), bytes);
            String nombre = nombreLibreArchivo(ctx.empresaId(), carpetaId, v.nombre(), null, enEsteLote);
            enEsteLote.add(nombre.toLowerCase(Locale.ROOT));
            pendientes.add(new Pendiente(nombre, v, bytes));
            total += bytes.length;
        }

        // 2) Cuota y términos, con el espacio bloqueado (dos subidas a la vez no se pasan juntas).
        Espacio espacio = query.bloquearEspacio(ctx.empresaId());
        if (!TERMINOS_VERSION.equals(espacio.terminosVersion()))
            throw error(HttpStatus.PRECONDITION_REQUIRED,
                    "Antes de subir archivos hay que aceptar los términos de uso de la nube.");
        Uso uso = query.uso(ctx.empresaId());
        if (uso.bytes() + total > espacio.cuotaMb() * BYTES_MB || uso.archivos() + pendientes.size() > espacio.maxArchivos())
            throw error(HttpStatus.INSUFFICIENT_STORAGE,
                    "Tu espacio está lleno. Elimina archivos (y vacía la papelera) o solicita más espacio (tiene costo).");

        limite.reservar(ctx.usuarioId(), pendientes.size());

        // 3) Subir. Si algo falla y se revierte, se borran del bucket los que ya subieron.
        List<String> subidos = new ArrayList<>();
        alRevertir(subidos, ctx.empresaId());
        List<Long> ids = new ArrayList<>();
        for (Pendiente p : pendientes) {
            ObjetoGuardado o = almacenamiento.guardar(ctx.empresaId(), p.bytes());
            subidos.add(o.objectKey());

            NubeArchivoEntity a = new NubeArchivoEntity();
            a.setEmpresaId(ctx.empresaId());
            a.setCarpetaId(carpetaId);
            a.setNombre(p.nombre());
            a.setExtension(p.valido().extension());
            a.setMime(p.valido().mime());
            a.setTamanoBytes(p.bytes().length);
            a.setSha256(sha256(p.bytes()));
            a.setObjectKey(o.objectKey());
            a.setIv(o.iv());
            a.setVersionLlave(o.versionLlave());
            a.setSubidoPor(ctx.usuarioId());
            a.setSubidoPorFirmaId(ctx.firmaId());
            a = archivos.save(a);
            ids.add(a.getId());
            bitacora.registrar(ctx, NubeEventoEntity.SUBIR, a.getId(), carpetaId, privada,
                    detalle("nombre", p.nombre(), "bytes", p.bytes().length, "sha256", a.getSha256()));
        }
        archivos.flush();
        return ids.stream().map(id -> mapper.toDto(archivoVisible(ctx, id))).toList();
    }

    @Override
    @Transactional
    public DescargaDto descargar(NubeContexto ctx, Long id, boolean vista) {
        exigir(ctx, AccionPermiso.VER);
        ArchivoFila fila = archivoVisible(ctx, id);
        NubeArchivoEntity a = archivoEntidad(ctx, id);
        byte[] contenido = almacenamiento.leer(ctx.empresaId(), a.getObjectKey(), a.getIv(), a.getVersionLlave());
        if (!sha256(contenido).equals(a.getSha256())) {
            log.error("SEGURIDAD: la huella del archivo {} de la empresa {} no coincide", id, ctx.empresaId());
            throw error(HttpStatus.CONFLICT, "El archivo está dañado o fue alterado y no se puede abrir.");
        }
        bitacora.registrar(ctx, vista ? NubeEventoEntity.VER : NubeEventoEntity.DESCARGAR, id, fila.carpetaId(),
                carpetaPrivada(fila.carpetaId()), detalle("nombre", fila.nombre()));
        return new DescargaDto(fila.nombre(), fila.mime(), contenido);
    }

    @Override
    @Transactional
    public ArchivoDto actualizarArchivo(NubeContexto ctx, Long id, ActualizarArchivoDto dto) {
        exigir(ctx, AccionPermiso.EDITAR);
        if (dto == null) throw error(HttpStatus.BAD_REQUEST, "No hay cambios.");
        ArchivoFila fila = archivoVisible(ctx, id);
        NubeArchivoEntity a = archivoEntidad(ctx, id);

        boolean mueve = Boolean.TRUE.equals(dto.raiz()) || dto.moverA() != null;
        Long carpetaFinal = a.getCarpetaId();
        if (mueve) carpetaFinal = Boolean.TRUE.equals(dto.raiz()) ? null : carpetaVisible(ctx, dto.moverA()).id();

        String nombre = a.getNombre();
        if (dto.nombre() != null) {
            String limpio = ValidadorArchivoNube.limpiarNombre(dto.nombre());
            // Renombrar no cambia el tipo: la extensión se conserva.
            String base = limpio.contains(".") ? limpio.substring(0, limpio.lastIndexOf('.')) : limpio;
            nombre = ValidadorArchivoNube.limpiarNombre(base + "." + a.getExtension());
        }
        boolean renombra = !nombre.equals(a.getNombre());
        if ((renombra || mueve) && query.existeNombreArchivo(ctx.empresaId(), carpetaFinal, nombre, id))
            throw error(HttpStatus.CONFLICT, "Ya existe un archivo con ese nombre en el destino.");

        boolean privadaAntes = carpetaPrivada(a.getCarpetaId());
        a.setNombre(nombre);
        a.setCarpetaId(carpetaFinal);
        archivos.saveAndFlush(a);

        boolean privada = privadaAntes || carpetaPrivada(carpetaFinal);
        if (renombra)
            bitacora.registrar(ctx, NubeEventoEntity.RENOMBRAR, id, carpetaFinal, privada,
                    detalle("antes", fila.nombre(), "despues", nombre));
        if (mueve)
            bitacora.registrar(ctx, NubeEventoEntity.MOVER, id, carpetaFinal, privada,
                    detalle("nombre", nombre, "desde", fila.carpetaId(), "hacia", carpetaFinal));
        return mapper.toDto(archivoVisible(ctx, id));
    }

    @Override
    @Transactional
    public void eliminarArchivo(NubeContexto ctx, Long id) {
        exigir(ctx, AccionPermiso.ANULAR);
        ArchivoFila fila = archivoVisible(ctx, id);
        NubeArchivoEntity a = archivoEntidad(ctx, id);
        LocalDateTime marca = ahora();
        a.setDeletedAt(marca);
        a.setEliminadoPor(ctx.usuarioId());
        a.setPurgarEn(marca.plusDays(DIAS_PAPELERA));
        archivos.save(a);
        bitacora.registrar(ctx, NubeEventoEntity.ELIMINAR, id, fila.carpetaId(), carpetaPrivada(fila.carpetaId()),
                detalle("tipo", ARCHIVO, "nombre", fila.nombre()));
    }

    // ── Papelera ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PapeleraDto papelera(NubeContexto ctx) {
        exigir(ctx, AccionPermiso.ANULAR);
        return new PapeleraDto(
                mapper.toCarpetas(query.papeleraCarpetas(ctx.empresaId(), ctx.firmaId())),
                mapper.toArchivos(query.papeleraArchivos(ctx.empresaId(), ctx.firmaId())));
    }

    @Override
    @Transactional
    public void restaurar(NubeContexto ctx, String tipo, Long id) {
        exigir(ctx, AccionPermiso.ANULAR);
        if (CARPETA.equals(tipo)) restaurarCarpeta(ctx, id);
        else if (ARCHIVO.equals(tipo)) restaurarArchivo(ctx, id);
        else throw error(HttpStatus.BAD_REQUEST, "Tipo inválido: carpeta o archivo.");
    }

    private void restaurarCarpeta(NubeContexto ctx, Long id) {
        CarpetaFila fila = query.carpetaEnPapelera(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, CARPETA, id));
        NubeCarpetaEntity c = carpetaEntidad(ctx, id);
        LocalDateTime marca = c.getDeletedAt();

        // Si su carpeta madre sigue en la papelera (o ya no existe), vuelve a la raíz.
        Long padre = c.getPadreId();
        if (padre != null && carpetas.findById(padre).map(p -> p.getDeletedAt() != null).orElse(true)) padre = null;

        List<Nodo> subarbol = query.subarbol(id, ctx.empresaId());
        List<Long> ids = subarbol.stream().filter(n -> marca.equals(n.deletedAt())).map(Nodo::id).toList();
        query.restaurarConMarca(ctx.empresaId(), ids, marca);
        if (padre == null && c.getPadreId() != null) {
            // Todo el subárbol sube de nivel, también lo que sigue en la papelera por su cuenta.
            query.desplazarNivel(ctx.empresaId(), subarbol.stream().map(Nodo::id).toList(), 1 - c.getNivel());
            c.setNivel((short) 1);
        }
        c.setPadreId(padre);
        c.setNombre(nombreLibreCarpeta(ctx.empresaId(), padre, c.getNombre(), id));
        c.setDeletedAt(null);
        c.setEliminadoPor(null);
        c.setPurgarEn(null);
        carpetas.saveAndFlush(c);
        bitacora.registrar(ctx, NubeEventoEntity.RESTAURAR, null, id, esPrivada(fila.visibilidad()),
                detalle("tipo", CARPETA, "nombre", c.getNombre()));
    }

    private void restaurarArchivo(NubeContexto ctx, Long id) {
        ArchivoFila fila = query.archivoEnPapelera(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, ARCHIVO, id));
        NubeArchivoEntity a = archivoEntidad(ctx, id);
        Long carpeta = a.getCarpetaId();
        boolean privada = carpetaPrivada(carpeta);
        if (carpeta != null && carpetas.findById(carpeta).map(c -> c.getDeletedAt() != null).orElse(true)) {
            // Un archivo privado de la firma no puede caer a la raíz, donde lo vería el cliente.
            if (privada)
                throw error(HttpStatus.CONFLICT, "Su carpeta está en la papelera: restaura primero la carpeta.");
            carpeta = null;
        }
        a.setCarpetaId(carpeta);
        a.setNombre(nombreLibreArchivo(ctx.empresaId(), carpeta, a.getNombre(), id, Set.of()));
        a.setDeletedAt(null);
        a.setEliminadoPor(null);
        a.setPurgarEn(null);
        archivos.saveAndFlush(a);
        bitacora.registrar(ctx, NubeEventoEntity.RESTAURAR, id, carpeta, privada,
                detalle("tipo", ARCHIVO, "nombre", fila.nombre()));
    }

    @Override
    @Transactional
    public void eliminarDefinitivo(NubeContexto ctx, String tipo, Long id) {
        exigir(ctx, AccionPermiso.ANULAR);
        exigirAdministrador(ctx);
        if (CARPETA.equals(tipo)) {
            CarpetaFila fila = query.carpetaEnPapelera(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, CARPETA, id));
            List<Nodo> subarbol = query.subarbol(id, ctx.empresaId());
            exigirSinPrivadasOcultas(ctx, subarbol);
            List<Long> ids = subarbol.stream().map(Nodo::id).toList();
            List<ObjetoArchivo> objetos = query.objetosEnCarpetas(ctx.empresaId(), ids);
            query.purgarCarpetas(ctx.empresaId(), ids);
            alConfirmarBorrar(objetos.stream().map(ObjetoArchivo::objectKey).toList(), ctx.empresaId());
            bitacora.registrar(ctx, NubeEventoEntity.PURGAR, null, id, esPrivada(fila.visibilidad()),
                    detalle("tipo", CARPETA, "nombre", fila.nombre(), "archivos", objetos.size()));
        } else if (ARCHIVO.equals(tipo)) {
            ArchivoFila fila = query.archivoEnPapelera(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, ARCHIVO, id));
            NubeArchivoEntity a = archivoEntidad(ctx, id);
            boolean privada = carpetaPrivada(a.getCarpetaId());
            archivos.delete(a);
            alConfirmarBorrar(List.of(a.getObjectKey()), ctx.empresaId());
            bitacora.registrar(ctx, NubeEventoEntity.PURGAR, id, fila.carpetaId(), privada,
                    detalle("tipo", ARCHIVO, "nombre", fila.nombre(), "sha256", fila.sha256()));
        } else {
            throw error(HttpStatus.BAD_REQUEST, "Tipo inválido: carpeta o archivo.");
        }
    }

    // ── Espacio, términos y bitácora ─────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UsoDto uso(NubeContexto ctx) {
        exigir(ctx, AccionPermiso.VER);
        return armarUso(ctx, query.espacio(ctx.empresaId()));
    }

    @Override
    @Transactional
    public UsoDto aceptarTerminos(NubeContexto ctx, String version) {
        exigir(ctx, AccionPermiso.CREAR);
        if (!TERMINOS_VERSION.equals(version))
            throw error(HttpStatus.BAD_REQUEST, "Los términos cambiaron: recarga la página y vuelve a leerlos.");
        query.bloquearEspacio(ctx.empresaId());
        NubeEspacioEntity e = espacios.findById(ctx.empresaId()).orElseThrow();
        e.setTerminosVersion(TERMINOS_VERSION);
        e.setTerminosAceptadosPor(ctx.usuarioId());
        e.setTerminosAceptadosAt(ahora());
        espacios.saveAndFlush(e);
        bitacora.registrar(ctx, "ACEPTAR_TERMINOS", null, null, false, detalle("version", TERMINOS_VERSION));
        return armarUso(ctx, query.espacio(ctx.empresaId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventoDto> eventos(NubeContexto ctx, Long archivoId, LocalDate desde, LocalDate hasta) {
        exigir(ctx, AccionPermiso.VER);
        exigirAdministrador(ctx);
        return mapper.toEventos(query.eventos(ctx.empresaId(), ctx.firmaId(), archivoId,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.plusDays(1).atStartOfDay(), 500));
    }

    @Override
    @Transactional
    public UsoDto solicitarEspacio(NubeContexto ctx, SolicitarEspacioDto dto) {
        exigir(ctx, AccionPermiso.VER);
        exigirAdministrador(ctx);
        Espacio espacio = query.bloquearEspacio(ctx.empresaId());
        Integer pedida = dto == null ? null : dto.cuotaSolicitadaMb();
        if (pedida == null || pedida <= espacio.cuotaMb())
            throw error(HttpStatus.BAD_REQUEST, "Indica un espacio mayor al actual (" + espacio.cuotaMb() + " MB).");
        if (pedida > MAX_CUOTA_SOLICITABLE_MB)
            throw error(HttpStatus.BAD_REQUEST, "Para más de " + (MAX_CUOTA_SOLICITABLE_MB / 1024)
                    + " GB comunícate con Aura.");
        if (query.ultimaSolicitud(ctx.empresaId()).filter(s -> NubeSolicitudEspacioEntity.PENDIENTE.equals(s.estado()))
                .isPresent())
            throw error(HttpStatus.CONFLICT, "Ya tienes una solicitud de espacio en revisión.");

        NubeSolicitudEspacioEntity s = new NubeSolicitudEspacioEntity();
        s.setEmpresaId(ctx.empresaId());
        s.setSolicitadoPor(ctx.usuarioId());
        s.setCuotaActualMb(espacio.cuotaMb());
        s.setCuotaSolicitadaMb(pedida);
        solicitudes.saveAndFlush(s);
        bitacora.registrar(ctx, "SOLICITAR_ESPACIO", null, null, false,
                detalle("actualMb", espacio.cuotaMb(), "solicitadaMb", pedida));
        return armarUso(ctx, query.espacio(ctx.empresaId()));
    }

    private UsoDto armarUso(NubeContexto ctx, Espacio espacio) {
        Uso uso = query.uso(ctx.empresaId());
        return new UsoDto(uso.bytes(), NubeCuota.cuotaBytes(espacio), uso.archivos(), espacio.maxArchivos(),
                NubeCuota.porcentaje(uso, espacio), NubeCuota.aviso(uso, espacio),
                TERMINOS_VERSION.equals(espacio.terminosVersion()), TERMINOS_VERSION, ctx.esFirma(),
                esAdministrador(ctx), ValidadorArchivoNube.extensionesPermitidas(), NubeArchivoEntity.MAX_BYTES,
                espacioService.solicitudVigente(ctx.empresaId()));
    }

    // ── Seguridad ────────────────────────────────────────────────────────────

    /** Permiso del submódulo, siempre (no depende del modo del interceptor). */
    private void exigir(NubeContexto ctx, AccionPermiso accion) {
        if (ctx == null || ctx.empresaId() == null || ctx.usuarioId() == null)
            throw error(HttpStatus.UNAUTHORIZED, "Inicia sesión de nuevo.");
        PermisosUsuarioDto p = permisos.efectivos(ctx.usuarioId());
        if (PoliticaRoles.PLATFORM_ADMIN.equalsIgnoreCase(p.getRol()))
            throw error(HttpStatus.FORBIDDEN, "La plataforma no tiene acceso a los documentos de las empresas.");
        if (!permisos.puede(ctx.usuarioId(), CLAVE, accion))
            throw error(HttpStatus.FORBIDDEN, switch (accion) {
                case VER -> "No tienes permiso para ver los documentos.";
                case CREAR -> "No tienes permiso para subir documentos ni crear carpetas.";
                case EDITAR -> "No tienes permiso para renombrar o mover documentos.";
                case ANULAR -> "No tienes permiso para eliminar documentos.";
            });
    }

    private boolean esAdministrador(NubeContexto ctx) {
        String rol = permisos.efectivos(ctx.usuarioId()).getRol();
        return "ADMIN".equalsIgnoreCase(rol) || PoliticaRoles.SUPER_ADMIN.equalsIgnoreCase(rol);
    }

    private void exigirAdministrador(NubeContexto ctx) {
        if (!esAdministrador(ctx))
            throw error(HttpStatus.FORBIDDEN, "Solo un administrador puede hacer esto.");
    }

    /** Nadie borra lo que no ve: carpetas privadas de la firma (o de otra firma) adentro. */
    private void exigirSinPrivadasOcultas(NubeContexto ctx, List<Nodo> nodos) {
        if (nodos.stream().anyMatch(n -> esPrivada(n.visibilidad()) && !n.privadaFirmaId().equals(ctx.firmaId())))
            throw error(HttpStatus.CONFLICT, "Esta carpeta tiene documentos privados de tu firma contable. "
                    + "Pídele que los mueva antes de eliminarla.");
    }

    private CarpetaFila carpetaVisible(NubeContexto ctx, Long id) {
        return query.carpeta(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, CARPETA, id));
    }

    private ArchivoFila archivoVisible(NubeContexto ctx, Long id) {
        return query.archivo(id, ctx.empresaId(), ctx.firmaId()).orElseThrow(() -> noEncontrado(ctx, ARCHIVO, id));
    }

    /** Entidad JPA, verificando otra vez la empresa (defensa en profundidad). */
    private NubeCarpetaEntity carpetaEntidad(NubeContexto ctx, Long id) {
        return carpetas.findById(id).filter(c -> ctx.empresaId().equals(c.getEmpresaId()))
                .orElseThrow(() -> noEncontrado(ctx, CARPETA, id));
    }

    private NubeArchivoEntity archivoEntidad(NubeContexto ctx, Long id) {
        return archivos.findById(id).filter(a -> ctx.empresaId().equals(a.getEmpresaId()))
                .orElseThrow(() -> noEncontrado(ctx, ARCHIVO, id));
    }

    /** 404 y queda el intento en la bitácora (en su propia transacción). */
    private GlobalException noEncontrado(NubeContexto ctx, String tipo, Long id) {
        bitacora.registrarDenegado(ctx, tipo, id);
        return error(HttpStatus.NOT_FOUND, CARPETA.equals(tipo) ? "Carpeta no encontrada" : "Archivo no encontrado");
    }

    private String visibilidadPedida(NubeContexto ctx, String pedida) {
        String v = pedida == null || pedida.isBlank() ? NubeCarpetaEntity.COMPARTIDA : pedida.trim().toUpperCase(Locale.ROOT);
        if (!v.equals(NubeCarpetaEntity.COMPARTIDA) && !v.equals(NubeCarpetaEntity.SOLO_FIRMA))
            throw error(HttpStatus.BAD_REQUEST, "Visibilidad inválida.");
        if (esPrivada(v) && !ctx.esFirma())
            throw error(HttpStatus.FORBIDDEN, "Solo la firma contable puede crear carpetas privadas.");
        return v;
    }

    private boolean carpetaPrivada(Long carpetaId) {
        return carpetaId != null && carpetas.findById(carpetaId).map(c -> esPrivada(c.getVisibilidad())).orElse(false);
    }

    private static boolean esPrivada(String visibilidad) {
        return NubeCarpetaEntity.SOLO_FIRMA.equals(visibilidad);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** "factura.pdf" ocupado → "factura (2).pdf", etc. */
    private String nombreLibreArchivo(Integer empresaId, Long carpetaId, String nombre, Long exceptoId, Set<String> lote) {
        int punto = nombre.lastIndexOf('.');
        String base = punto > 0 ? nombre.substring(0, punto) : nombre;
        String ext = punto > 0 ? nombre.substring(punto) : "";
        String candidato = nombre;
        for (int i = 2; i < 1000; i++) {
            if (!lote.contains(candidato.toLowerCase(Locale.ROOT))
                    && !query.existeNombreArchivo(empresaId, carpetaId, candidato, exceptoId)) return candidato;
            String sufijo = " (" + i + ")";
            String b = base.length() + sufijo.length() + ext.length() > ValidadorArchivoNube.MAX_LARGO_NOMBRE
                    ? base.substring(0, ValidadorArchivoNube.MAX_LARGO_NOMBRE - sufijo.length() - ext.length()) : base;
            candidato = b + sufijo + ext;
        }
        throw error(HttpStatus.CONFLICT, "Hay demasiados archivos con ese nombre en la carpeta.");
    }

    private String nombreLibreCarpeta(Integer empresaId, Long padreId, String nombre, Long exceptoId) {
        String candidato = nombre;
        for (int i = 2; i < 1000; i++) {
            if (!query.existeNombreCarpeta(empresaId, padreId, candidato, exceptoId)) return candidato;
            String sufijo = " (" + i + ")";
            String b = nombre.length() + sufijo.length() > ValidadorArchivoNube.MAX_LARGO_NOMBRE
                    ? nombre.substring(0, ValidadorArchivoNube.MAX_LARGO_NOMBRE - sufijo.length()) : nombre;
            candidato = b + sufijo;
        }
        throw error(HttpStatus.CONFLICT, "Hay demasiadas carpetas con ese nombre.");
    }

    /** Si la transacción se revierte, borra del bucket lo que alcanzó a subir. */
    private void alRevertir(List<String> claves, Integer empresaId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado != STATUS_COMMITTED) borrarObjetos(claves, empresaId);
            }
        });
    }

    /** Borra del bucket solo cuando la base ya confirmó (si no, quedarían filas sin objeto). */
    private void alConfirmarBorrar(List<String> claves, Integer empresaId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                borrarObjetos(claves, empresaId);
            }
        });
    }

    private void borrarObjetos(List<String> claves, Integer empresaId) {
        for (String k : List.copyOf(claves)) {
            try {
                almacenamiento.eliminar(empresaId, k);
            } catch (RuntimeException e) {
                // Queda huérfano pero cifrado; se anota para limpiarlo a mano.
                log.error("Nube: no se pudo borrar del bucket el objeto {}: {}", k, e.getMessage());
            }
        }
    }

    private static String sha256(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** La base guarda microsegundos: así la marca de la papelera se compara igual. */
    private static LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String nombreParaMensaje(MultipartFile f) {
        String n = f.getOriginalFilename();
        if (n == null) return "archivo";
        n = n.replaceAll("[\\p{Cntrl}\\p{Cf}]", "");
        return n.length() > 60 ? n.substring(0, 60) + "…" : n;
    }

    private static Map<String, Object> detalle(Object... pares) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pares.length; i += 2) m.put(String.valueOf(pares[i]), pares[i + 1]);
        return m;
    }

    private static GlobalException error(HttpStatus status, String mensaje) {
        return new GlobalException(status, mensaje);
    }

    private record Pendiente(String nombre, ArchivoValido valido, byte[] bytes) {
    }
}
