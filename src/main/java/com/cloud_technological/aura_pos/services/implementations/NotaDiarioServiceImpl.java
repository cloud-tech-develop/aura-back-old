package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.contabilidad.notas.ImportarLineasResultadoDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioFiltroDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioLineaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioSoporteDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.NotaDiarioTableDto;
import com.cloud_technological.aura_pos.dto.contabilidad.notas.SaveNotaDiarioDto;
import com.cloud_technological.aura_pos.entity.AsientoContableEntity;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.entity.NotaDiarioSoporteEntity;
import com.cloud_technological.aura_pos.entity.PeriodoContableEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.AsientoContableQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioPlantillaJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioQueryRepository.Resuelto;
import com.cloud_technological.aura_pos.repositories.contabilidad.NotaDiarioSoporteJPARepository;
import com.cloud_technological.aura_pos.services.AsientoContableService;
import com.cloud_technological.aura_pos.services.NotaDiarioService;
import com.cloud_technological.aura_pos.utils.AsientoBalanceValidator;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotaDiarioServiceImpl implements NotaDiarioService {

    private static final String TIPO_ORIGEN = "MANUAL";
    private static final String TIPO_COMPROBANTE = "CD";
    private static final int MAX_SOPORTES = 20;
    private static final int MAX_LINEAS_IMPORTAR = 500;

    private final AsientoContableJPARepository repo;
    private final AsientoContableQueryRepository asientoQueryRepo;
    private final NotaDiarioQueryRepository queryRepo;
    private final PeriodoContableResolver periodoResolver;
    private final AsientoContableService asientoService;
    private final NotaDiarioLineasBuilder lineasBuilder;
    private final NotaDiarioSoporteJPARepository soporteRepo;
    private final NotaDiarioPlantillaJPARepository plantillaRepo;
    private final R2StorageService storage;

    @Override
    public PageImpl<NotaDiarioTableDto> listar(PageableDto<NotaDiarioFiltroDto> pageable, Integer empresaId) {
        return queryRepo.listar(pageable, empresaId);
    }

    @Override
    public NotaDiarioDto obtener(Long id, Integer empresaId) {
        NotaDiarioDto dto = queryRepo.obtener(id, empresaId);
        if (dto == null) {
            throw new GlobalException(HttpStatus.NOT_FOUND, "Nota contable no encontrada");
        }
        dto.setLineas(queryRepo.lineas(id));
        dto.setSoportes(queryRepo.soportes(id));
        return dto;
    }

    @Override
    @Transactional
    public NotaDiarioDto crear(Integer empresaId, Integer usuarioId, SaveNotaDiarioDto dto) {
        AsientoContableEntity nota = AsientoContableEntity.builder()
                .empresaId(empresaId)
                .tipoOrigen(TIPO_ORIGEN)
                .tipoComprobante(TIPO_COMPROBANTE)
                .estado("BORRADOR")
                .usuarioId(usuarioId)
                .build();
        if (dto.getPlantillaId() != null) {
            plantillaRepo.findByIdAndEmpresaIdAndDeletedAtIsNull(dto.getPlantillaId(), empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Plantilla no encontrada"));
            nota.setPlantillaId(dto.getPlantillaId());
        }
        aplicar(nota, empresaId, dto);
        nota = repo.saveAndFlush(nota);

        if (Boolean.TRUE.equals(dto.getContabilizar())) {
            contabilizarInterno(nota, usuarioId);
        }
        return obtener(nota.getId(), empresaId);
    }

    @Override
    @Transactional
    public NotaDiarioDto actualizar(Long id, Integer empresaId, Integer usuarioId, SaveNotaDiarioDto dto) {
        AsientoContableEntity nota = cargar(id, empresaId);
        exigirBorrador(nota, "editar");
        aplicar(nota, empresaId, dto);
        nota.setUpdatedAt(LocalDateTime.now());
        nota = repo.saveAndFlush(nota);

        if (Boolean.TRUE.equals(dto.getContabilizar())) {
            contabilizarInterno(nota, usuarioId);
        }
        return obtener(nota.getId(), empresaId);
    }

    @Override
    @Transactional
    public NotaDiarioDto contabilizar(Long id, Integer empresaId, Integer usuarioId) {
        AsientoContableEntity nota = cargar(id, empresaId);
        exigirBorrador(nota, "contabilizar");
        contabilizarInterno(nota, usuarioId);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        AsientoContableEntity nota = cargar(id, empresaId);
        if (!"BORRADOR".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota " + nota.getNumeroComprobante() + " ya está contabilizada: no se elimina, "
                            + "se anula con motivo para que quede la traza.");
        }
        repo.delete(nota);
    }

    @Override
    @Transactional
    public NotaDiarioDto anular(Long id, Integer empresaId, Integer usuarioId, String motivo) {
        AsientoContableEntity nota = cargar(id, empresaId);
        if ("BORRADOR".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Un borrador no se anula: todavía no afecta la contabilidad. Elimínelo.");
        }
        if ("ANULADO".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota " + nota.getNumeroComprobante() + " ya está anulada.");
        }
        // Anular la original y dejar viva su reversión dejaría la reversión
        // restando sola en los libros.
        if (nota.getRevertidoPorId() != null) {
            String rev = repo.findById(nota.getRevertidoPorId())
                    .map(AsientoContableEntity::getNumeroComprobante).orElse("su reversión");
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota " + nota.getNumeroComprobante() + " fue reversada por " + rev
                            + ". Anule primero la reversión.");
        }

        // La anulación común valida el período y revierte lo que el asiento
        // haya tocado; aquí solo se agrega la traza de quién y por qué.
        asientoService.anular(id, empresaId);

        nota.setEstado("ANULADO");
        nota.setAnuladoPor(usuarioId);
        nota.setAnuladoAt(LocalDateTime.now());
        nota.setMotivoAnulacion(motivo.trim());
        repo.saveAndFlush(nota);

        // Anulada la reversión, la original vuelve a poder reversarse.
        if (nota.getReversaDeId() != null) {
            repo.findById(nota.getReversaDeId()).ifPresent(original -> {
                original.setRevertidoPorId(null);
                repo.saveAndFlush(original);
            });
        }
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public NotaDiarioDto reversar(Long id, Integer empresaId, Integer usuarioId, LocalDate fecha, String concepto) {
        AsientoContableEntity original = cargar(id, empresaId);
        if (!"CONTABILIZADO".equals(original.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Solo se reversa una nota contabilizada. Un borrador se edita o se elimina.");
        }
        if (original.getReversaDeId() != null) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota " + original.getNumeroComprobante() + " ya es una reversión: si sobra, anúlela.");
        }
        if (original.getRevertidoPorId() != null) {
            String rev = repo.findById(original.getRevertidoPorId())
                    .map(AsientoContableEntity::getNumeroComprobante).orElse("otra nota");
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota " + original.getNumeroComprobante() + " ya fue reversada por " + rev + ".");
        }
        if (fecha.isBefore(original.getFecha())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La reversión no puede tener fecha anterior a la nota que reversa ("
                            + original.getFecha() + ").");
        }
        AsientoContableEntity reversion = crearReversion(original, fecha, concepto, usuarioId);
        return obtener(reversion.getId(), empresaId);
    }

    @Override
    @Transactional
    public NotaDiarioSoporteDto subirSoporte(Long id, Integer empresaId, Integer usuarioId, MultipartFile archivo) {
        AsientoContableEntity nota = cargar(id, empresaId);
        if ("ANULADO".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT, "La nota está anulada: no admite soportes nuevos.");
        }
        if (soporteRepo.countByAsientoIdAndDeletedAtIsNull(id) >= MAX_SOPORTES) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nota ya tiene " + MAX_SOPORTES + " soportes, que es el máximo.");
        }
        String url = storage.subirSoporte(archivo, "notas-contables/" + empresaId);

        NotaDiarioSoporteEntity s = new NotaDiarioSoporteEntity();
        s.setEmpresaId(empresaId);
        s.setAsientoId(id);
        String nombre = archivo.getOriginalFilename() != null ? archivo.getOriginalFilename() : "soporte";
        s.setNombreArchivo(nombre.length() > 255 ? nombre.substring(nombre.length() - 255) : nombre);
        s.setArchivoUrl(url);
        s.setContentType(archivo.getContentType());
        s.setTamanoBytes(archivo.getSize());
        s.setUsuarioId(usuarioId);
        s = soporteRepo.saveAndFlush(s);
        return buscarSoporte(id, s.getId());
    }

    @Override
    @Transactional
    public void eliminarSoporte(Long id, Long soporteId, Integer empresaId) {
        AsientoContableEntity nota = cargar(id, empresaId);
        if (!"BORRADOR".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "La nota ya está contabilizada: sus soportes son evidencia y no se quitan.");
        }
        NotaDiarioSoporteEntity s = soporteRepo
                .findByIdAndAsientoIdAndEmpresaIdAndDeletedAtIsNull(soporteId, id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Soporte no encontrado"));
        s.setDeletedAt(LocalDateTime.now());
        soporteRepo.save(s);
    }

    @Override
    public ImportarLineasResultadoDto importarLineas(Integer empresaId, String texto) {
        List<NotaDiarioImportador.FilaCruda> filas = NotaDiarioImportador.leer(texto);
        ImportarLineasResultadoDto out = new ImportarLineasResultadoDto();
        if (filas.isEmpty()) {
            out.getErrores().add(new ImportarLineasResultadoDto.ErrorFila(0,
                    "No se encontraron líneas. Copie desde Excel las columnas: cuenta, tercero, "
                            + "centro de costo, descripción, débito, crédito."));
            return out;
        }
        if (filas.size() > MAX_LINEAS_IMPORTAR) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Se pueden importar hasta " + MAX_LINEAS_IMPORTAR + " líneas por nota.");
        }

        // Todo se resuelve en tres consultas, no una por fila.
        Map<String, Resuelto> cuentas = queryRepo.cuentasPorCodigo(empresaId,
                distintos(filas, NotaDiarioImportador.FilaCruda::cuenta));
        Map<String, Resuelto> terceros = queryRepo.tercerosPorDocumento(empresaId,
                distintos(filas, f -> NotaDiarioImportador.documento(f.tercero())));
        Map<String, Resuelto> centros = queryRepo.centrosCostoPorCodigo(empresaId,
                distintos(filas, NotaDiarioImportador.FilaCruda::centroCosto));

        for (NotaDiarioImportador.FilaCruda f : filas) {
            String error = null;
            Resuelto cuenta = cuentas.get(f.cuenta());
            if (f.cuenta().isEmpty()) error = "falta el código de la cuenta";
            else if (cuenta == null) error = "la cuenta " + f.cuenta() + " no existe en el plan";
            else if (!cuenta.usable()) error = "la cuenta " + cuenta.etiqueta() + " " + cuenta.motivo();

            String doc = NotaDiarioImportador.documento(f.tercero());
            Resuelto tercero = doc.isEmpty() ? null : terceros.get(doc);
            if (error == null && !doc.isEmpty() && tercero == null) {
                error = "no hay un tercero con documento " + f.tercero();
            }
            Resuelto cc = f.centroCosto().isEmpty() ? null : centros.get(f.centroCosto());
            if (error == null && !f.centroCosto().isEmpty() && (cc == null || !cc.usable())) {
                error = cc == null ? "no existe el centro de costo " + f.centroCosto()
                        : "el centro de costo " + cc.etiqueta() + " " + cc.motivo();
            }

            BigDecimal debito = BigDecimal.ZERO;
            BigDecimal credito = BigDecimal.ZERO;
            if (error == null) {
                try {
                    debito = NotaDiarioImportador.numero(f.debito());
                    credito = NotaDiarioImportador.numero(f.credito());
                } catch (NumberFormatException e) {
                    error = "el débito o el crédito no es un número";
                }
            }
            if (error == null && (debito.signum() < 0 || credito.signum() < 0)) {
                error = "los valores no pueden ser negativos";
            }
            if (error == null && debito.signum() > 0 && credito.signum() > 0) {
                error = "tiene débito y crédito a la vez";
            }
            if (error == null && debito.signum() == 0 && credito.signum() == 0) {
                error = "no tiene valor en débito ni en crédito";
            }

            if (error != null) {
                out.getErrores().add(new ImportarLineasResultadoDto.ErrorFila(f.fila(), "Fila " + f.fila() + ": " + error));
                continue;
            }
            NotaDiarioLineaDto l = new NotaDiarioLineaDto();
            l.setCuentaId(cuenta.id());
            l.setCuentaCodigo(f.cuenta());
            l.setCuentaNombre(cuenta.etiqueta());
            l.setDescripcion(f.descripcion().isEmpty() ? null : f.descripcion());
            l.setDebito(debito);
            l.setCredito(credito);
            if (tercero != null) {
                l.setTerceroId(tercero.id());
                l.setTerceroDocumento(doc);
                l.setTerceroNombre(tercero.etiqueta());
            }
            if (cc != null) {
                l.setCentroCostoId(cc.id());
                l.setCentroCostoNombre(cc.etiqueta());
            }
            out.getLineas().add(l);
        }
        return out;
    }

    // ── Internos ─────────────────────────────────────────────────────────

    private NotaDiarioSoporteDto buscarSoporte(Long asientoId, Long soporteId) {
        return queryRepo.soportes(asientoId).stream()
                .filter(x -> x.getId().equals(soporteId))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "El soporte se subió pero no se pudo leer. Recargue la nota."));
    }

    private static Set<String> distintos(List<NotaDiarioImportador.FilaCruda> filas,
            Function<NotaDiarioImportador.FilaCruda, String> campo) {
        return filas.stream().map(campo).filter(v -> v != null && !v.isEmpty()).collect(Collectors.toSet());
    }

    private AsientoContableEntity cargar(Long id, Integer empresaId) {
        return repo.findByIdAndEmpresaId(id, empresaId)
                .filter(a -> TIPO_ORIGEN.equals(a.getTipoOrigen())
                        && TIPO_COMPROBANTE.equals(a.getTipoComprobante()))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Nota contable no encontrada"));
    }

    private void exigirBorrador(AsientoContableEntity nota, String accion) {
        if (!"BORRADOR".equals(nota.getEstado())) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Solo se puede " + accion + " una nota en borrador. La nota "
                            + nota.getNumeroComprobante() + " está " + nota.getEstado().toLowerCase() + ".");
        }
    }

    /**
     * Pasa la cabecera y las líneas del DTO al asiento. El borrador ya valida
     * cuentas y dimensiones (así el error aparece al guardar y no días después
     * al aprobar), pero admite estar descuadrado o incompleto: es trabajo en
     * curso. El cuadre se exige al contabilizar.
     */
    private void aplicar(AsientoContableEntity nota, Integer empresaId, SaveNotaDiarioDto dto) {
        // El mes de la fecha tiene que admitir movimientos: preparar un borrador
        // para un mes cerrado solo aplaza el error hasta el momento de aprobarlo.
        PeriodoContableEntity periodo = periodoResolver.resolver(empresaId, dto.getFecha());
        List<AsientoDetalleEntity> lineas = lineasBuilder.construir(empresaId, dto.getLineas());

        nota.setFecha(dto.getFecha());
        nota.setDescripcion(dto.getDescripcion().trim());
        nota.setClasificacion(lineasBuilder.normalizarClasificacion(dto.getClasificacion()));
        nota.setReversionAutomatica(Boolean.TRUE.equals(dto.getReversionAutomatica()));
        nota.setPeriodoContableId(periodo.getId());
        reemplazarLineas(nota, lineas);
    }

    private static void reemplazarLineas(AsientoContableEntity nota, List<AsientoDetalleEntity> lineas) {
        nota.setTotalDebito(lineas.stream().map(AsientoDetalleEntity::getDebito)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        nota.setTotalCredito(lineas.stream().map(AsientoDetalleEntity::getCredito)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        // orphanRemoval borra las líneas anteriores del borrador.
        nota.getDetalles().clear();
        lineas.forEach(d -> {
            d.setAsiento(nota);
            nota.getDetalles().add(d);
        });
    }

    private void contabilizarInterno(AsientoContableEntity nota, Integer usuarioId) {
        List<AsientoDetalleEntity> lineas = nota.getDetalles();
        if (lineas.size() < 2) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La nota necesita al menos dos líneas: una al débito y otra al crédito.");
        }
        for (int i = 0; i < lineas.size(); i++) {
            AsientoDetalleEntity l = lineas.get(i);
            if (l.getDebito().signum() == 0 && l.getCredito().signum() == 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Línea " + (i + 1) + ": no tiene valor. Digite el débito o el crédito, o quítela.");
            }
        }
        AsientoBalanceValidator.validarCuadre(nota.getTotalDebito(), nota.getTotalCredito());

        // Entre el borrador y la aprobación pueden pasar días: el mes pudo
        // cerrarse y una cuenta pudo desactivarse o dejar de ser auxiliar.
        PeriodoContableEntity periodo = periodoResolver.resolver(nota.getEmpresaId(), nota.getFecha());
        lineasBuilder.validarCuentas(nota.getEmpresaId(), lineas);

        queryRepo.bloquearSerie(nota.getEmpresaId(), TIPO_COMPROBANTE);
        nota.setNumeroComprobante(
                asientoQueryRepo.siguienteNumeroComprobante(nota.getEmpresaId(), TIPO_COMPROBANTE));
        nota.setPeriodoContableId(periodo.getId());
        nota.setEstado("CONTABILIZADO");
        nota.setContabilizadoPor(usuarioId);
        nota.setContabilizadoAt(LocalDateTime.now());
        repo.saveAndFlush(nota);

        // Provisión o causación que se deshace sola el mes siguiente: se deja
        // registrada de una vez, en la misma transacción. Si el mes siguiente
        // no admite movimientos, no se contabiliza ninguna de las dos.
        if (Boolean.TRUE.equals(nota.getReversionAutomatica())) {
            LocalDate primeroSiguiente = nota.getFecha().withDayOfMonth(1).plusMonths(1);
            crearReversion(nota, primeroSiguiente, null, usuarioId);
        }
    }

    /**
     * Nota inversa: mismas cuentas y dimensiones con débito y crédito
     * intercambiados, contabilizada en la fecha dada. La original sigue
     * CONTABILIZADA: los informes suman las dos y netean en cero.
     */
    private AsientoContableEntity crearReversion(AsientoContableEntity original, LocalDate fecha,
            String concepto, Integer usuarioId) {
        String descripcion = concepto != null && !concepto.isBlank()
                ? concepto.trim()
                : "Reversión de " + original.getNumeroComprobante() + ": " + original.getDescripcion();
        if (descripcion.length() > 500) descripcion = descripcion.substring(0, 500);

        AsientoContableEntity rev = AsientoContableEntity.builder()
                .empresaId(original.getEmpresaId())
                .tipoOrigen(TIPO_ORIGEN)
                .tipoComprobante(TIPO_COMPROBANTE)
                .estado("BORRADOR")
                .usuarioId(usuarioId)
                .fecha(fecha)
                .descripcion(descripcion)
                .clasificacion(original.getClasificacion())
                .reversaDeId(original.getId())
                .periodoContableId(periodoResolver.resolver(original.getEmpresaId(), fecha).getId())
                .build();

        List<AsientoDetalleEntity> lineas = new ArrayList<>();
        for (AsientoDetalleEntity l : original.getDetalles()) {
            lineas.add(AsientoDetalleEntity.builder()
                    .cuentaId(l.getCuentaId())
                    .descripcion(l.getDescripcion())
                    .debito(l.getCredito())
                    .credito(l.getDebito())
                    .terceroId(l.getTerceroId())
                    .centroCostoId(l.getCentroCostoId())
                    .proyectoId(l.getProyectoId())
                    .frenteId(l.getFrenteId())
                    .build());
        }
        reemplazarLineas(rev, lineas);
        rev = repo.saveAndFlush(rev);
        contabilizarInterno(rev, usuarioId);

        original.setRevertidoPorId(rev.getId());
        repo.saveAndFlush(original);
        return rev;
    }
}
