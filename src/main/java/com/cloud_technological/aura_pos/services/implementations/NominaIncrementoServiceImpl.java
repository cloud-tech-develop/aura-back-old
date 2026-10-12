package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.comprobante.TipoComprobanteTableDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.AgregarEmpleadosDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.CreateDocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.DocumentoNominaDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.FiltroIncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDetalleDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoDto;
import com.cloud_technological.aura_pos.dto.nomina.incremento.IncrementoRequestDto;
import com.cloud_technological.aura_pos.entity.ContratoSalarioHistorialEntity;
import com.cloud_technological.aura_pos.entity.NominaConfigEntity;
import com.cloud_technological.aura_pos.entity.NominaIncrementoEntity;
import com.cloud_technological.aura_pos.repositories.nomina.NominaConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_incrementos.NominaIncrementoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_incrementos.NominaIncrementoQueryRepository;
import com.cloud_technological.aura_pos.repositories.nomina_incrementos.NominaIncrementoQueryRepository.Detalle;
import com.cloud_technological.aura_pos.repositories.nomina_incrementos.NominaIncrementoQueryRepository.Retroactivo;
import com.cloud_technological.aura_pos.repositories.nomina_incrementos.NominaIncrementoQueryRepository.Vinculacion;
import com.cloud_technological.aura_pos.services.NominaDocumentoEmpleadoService;
import com.cloud_technological.aura_pos.services.NominaDocumentoService;
import com.cloud_technological.aura_pos.services.NominaIncrementoService;
import com.cloud_technological.aura_pos.services.NominaTipoComprobanteService;
import com.cloud_technological.aura_pos.services.nomina.comprobante.ClaseComprobanteNomina;
import com.cloud_technological.aura_pos.services.nomina.documento.TipoListaNomina;
import com.cloud_technological.aura_pos.services.nomina.incremento.CalculoIncremento;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

/** Implementación de {@link NominaIncrementoService}. */
@Service
public class NominaIncrementoServiceImpl implements NominaIncrementoService {

    private static final String PENDIENTE = "PENDIENTE";
    private static final String APLICADO = "APLICADO";

    private final NominaIncrementoJPARepository jpaRepository;
    private final NominaIncrementoQueryRepository queryRepository;
    private final NominaConfigJPARepository configRepository;
    private final ContratoLaboralService contratos;
    private final NominaTipoComprobanteService tiposComprobante;
    private final NominaDocumentoService documentos;
    private final NominaDocumentoEmpleadoService empleadosDocumento;

    public NominaIncrementoServiceImpl(NominaIncrementoJPARepository jpaRepository,
            NominaIncrementoQueryRepository queryRepository,
            NominaConfigJPARepository configRepository,
            ContratoLaboralService contratos,
            NominaTipoComprobanteService tiposComprobante,
            NominaDocumentoService documentos,
            NominaDocumentoEmpleadoService empleadosDocumento) {
        this.jpaRepository = jpaRepository;
        this.queryRepository = queryRepository;
        this.configRepository = configRepository;
        this.contratos = contratos;
        this.tiposComprobante = tiposComprobante;
        this.documentos = documentos;
        this.empleadosDocumento = empleadosDocumento;
    }

    @Override
    public PageImpl<IncrementoDto> listar(PageableDto<FiltroIncrementoDto> pageable, Integer empresaId) {
        return queryRepository.listar(pageable, empresaId);
    }

    @Override
    public IncrementoDto obtener(Long id, Integer empresaId) {
        return queryRepository.obtener(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Incremento no encontrado"));
    }

    @Override
    public List<IncrementoDetalleDto> detalle(Long id, Integer empresaId) {
        obtener(id, empresaId);
        return queryRepository.detalle(id);
    }

    @Override
    @Transactional
    public IncrementoDto crear(IncrementoRequestDto dto, Integer empresaId, Long usuarioId) {
        NominaIncrementoEntity i = new NominaIncrementoEntity();
        i.setEmpresaId(empresaId);
        i.setNumero(queryRepository.siguienteNumero(empresaId));
        i.setEstado(PENDIENTE);
        i.setUsuarioId(usuarioId);
        i.setCreatedAt(LocalDateTime.now());
        copiar(dto, i);
        jpaRepository.saveAndFlush(i);
        int sinCambio = calcular(i, dto.getIds());
        IncrementoDto r = obtener(i.getId(), empresaId);
        r.setSinCambio(sinCambio);
        return r;
    }

    @Override
    @Transactional
    public IncrementoDto actualizar(Long id, IncrementoRequestDto dto, Integer empresaId) {
        NominaIncrementoEntity i = pendiente(id, empresaId);
        copiar(dto, i);
        i.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(i);
        int sinCambio = calcular(i, dto.getIds());
        IncrementoDto r = obtener(id, empresaId);
        r.setSinCambio(sinCambio);
        return r;
    }

    @Override
    @Transactional
    public IncrementoDto quitar(Long id, Long detalleId, Integer empresaId) {
        pendiente(id, empresaId);
        if (queryRepository.quitarDetalle(id, detalleId) == 0)
            throw new GlobalException(HttpStatus.NOT_FOUND, "La vinculación no está en el incremento.");
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public IncrementoDto aplicar(Long id, Integer empresaId, Long usuarioId) {
        NominaIncrementoEntity i = pendiente(id, empresaId);
        List<Detalle> lineas = queryRepository.lineas(id);
        if (lineas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El incremento no tiene vinculaciones.");
        String motivo = "Incremento INC-" + String.format("%05d", i.getNumero())
                + (i.getDescripcion() != null ? " — " + i.getDescripcion() : "");
        for (Detalle d : lineas) {
            BigDecimal actual = contratos.obtener(d.contratoId(), empresaId).getSalarioBase();
            if (actual == null || actual.compareTo(d.salarioAnterior()) != 0)
                throw new GlobalException(HttpStatus.CONFLICT, "El salario de una vinculación cambió desde la vista "
                        + "previa (" + d.salarioAnterior() + " → hoy " + actual + "). Recalcule el incremento.");
            contratos.cambiarSalario(d.contratoId(), empresaId, d.salarioNuevo(), i.getFechaAplicacion(), motivo,
                    usuarioId);
            Long historialId = contratos.salarioVigente(d.contratoId())
                    .map(ContratoSalarioHistorialEntity::getId).orElse(null);
            queryRepository.marcarHistorial(d.id(), historialId);
        }
        i.setEstado(APLICADO);
        i.setAplicadoAt(LocalDateTime.now());
        i.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(i);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public IncrementoDto reversar(Long id, Integer empresaId) {
        NominaIncrementoEntity i = entidad(id, empresaId);
        if (!APLICADO.equals(i.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "Solo se reversa un incremento aplicado.");
        IncrementoDto actual = obtener(id, empresaId);
        if (actual.getDocumentoId() != null && !"ANULADO".equals(actual.getDocumentoEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El incremento tiene el retroactivo "
                    + actual.getDocumentoNumero() + ". Anúlelo primero para poder reversar.");
        for (Detalle d : queryRepository.lineas(id)) {
            if (queryRepository.nominaConfirmadaDespues(d.contratoId(), i.getFechaAplicacion(), i.getAplicadoAt()))
                throw new GlobalException(HttpStatus.CONFLICT, "Ya hay nóminas confirmadas con el salario nuevo "
                        + "después de aplicar el incremento: no se puede reversar.");
            if (d.historialId() != null)
                contratos.revertirSalario(d.contratoId(), empresaId, d.historialId(), d.salarioAnterior());
            queryRepository.marcarHistorial(d.id(), null);
        }
        i.setEstado("REVERSADO");
        i.setReversadoAt(LocalDateTime.now());
        i.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(i);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public IncrementoDto anular(Long id, Integer empresaId) {
        NominaIncrementoEntity i = pendiente(id, empresaId);
        i.setEstado("ANULADO");
        i.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(i);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public DocumentoNominaDto generarRetroactivo(Long id, Integer empresaId, Long usuarioId) {
        NominaIncrementoEntity i = entidad(id, empresaId);
        if (!APLICADO.equals(i.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El retroactivo sale de un incremento aplicado.");
        IncrementoDto actual = obtener(id, empresaId);
        if (actual.getDocumentoId() != null && !"ANULADO".equals(actual.getDocumentoEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El incremento ya tiene el retroactivo "
                    + actual.getDocumentoNumero() + ".");
        List<Retroactivo> afectadas = queryRepository.retroactivos(id);
        if (afectadas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay nóminas confirmadas con el salario anterior desde la fecha de aplicación.");

        TipoComprobanteTableDto tipo = tiposComprobante.activos(empresaId).stream()
                .filter(t -> ClaseComprobanteNomina.RETROACTIVO.name().equals(t.getClase()))
                .findFirst()
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                        "Cree (o active) un tipo de comprobante de clase Retroactivo."));
        LocalDate hasta = afectadas.stream().map(Retroactivo::hasta).max(LocalDate::compareTo).orElseThrow();

        CreateDocumentoNominaDto dto = new CreateDocumentoNominaDto();
        dto.setTipoComprobanteId(tipo.getId());
        dto.setFecha(LocalDate.now());
        dto.setFechaDesde(i.getFechaAplicacion());
        dto.setFechaHasta(hasta);
        dto.setFechaLiquidacion(LocalDate.now());
        dto.setSimulacion(false);
        dto.setDescripcion("Retroactivo del incremento " + actual.getNumeroCompleto()
                + (i.getDescripcion() != null ? " — " + i.getDescripcion() : ""));
        DocumentoNominaDto doc = documentos.crearPrenomina(dto, empresaId, usuarioId);

        AgregarEmpleadosDto agregar = new AgregarEmpleadosDto();
        agregar.setTipoLista(TipoListaNomina.VINCULACION.name());
        agregar.setIds(afectadas.stream().map(Retroactivo::contratoId).toList());
        empleadosDocumento.agregar(doc.getId(), agregar, empresaId);

        i.setDocumentoId(doc.getId());
        i.setUpdatedAt(LocalDateTime.now());
        jpaRepository.saveAndFlush(i);
        return documentos.obtener(doc.getId(), empresaId);
    }

    // ── Apoyo ────────────────────────────────────────────────────────────────

    /** Datos del encabezado desde la petición, validados. */
    private static void copiar(IncrementoRequestDto dto, NominaIncrementoEntity i) {
        TipoListaNomina lista = TipoListaNomina.valueOf(dto.getTipoLista());
        List<Long> ids = dto.getIds() == null ? List.of() : dto.getIds();
        if (lista.exigeIds() && ids.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija a quién aplica el incremento.");
        boolean conValor = !CalculoIncremento.Tipo.SMMLV.name().equals(dto.getTipoIncremento());
        if (conValor && (dto.getValor() == null || dto.getValor().signum() <= 0))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Indique el valor del incremento.");
        i.setTipoLista(lista.name());
        i.setListaIds(lista.exigeIds() ? ids.stream().map(String::valueOf).collect(Collectors.joining(",")) : null);
        i.setTipoIncremento(dto.getTipoIncremento());
        i.setValor(conValor ? dto.getValor() : null);
        i.setRedondeo(dto.getRedondeo());
        i.setFechaAplicacion(dto.getFechaAplicacion());
        i.setDescripcion(dto.getDescripcion() == null || dto.getDescripcion().isBlank()
                ? null : dto.getDescripcion().trim());
    }

    /**
     * Recalcula la vista previa: nuevo salario por vinculación de la lista; las que no
     * cambian no entran.
     *
     * @return cuántas vinculaciones quedaron fuera por no cambiar
     */
    private int calcular(NominaIncrementoEntity i, List<Long> ids) {
        BigDecimal smmlv = configRepository.findByEmpresaId(i.getEmpresaId())
                .map(NominaConfigEntity::getSmmlv).orElse(null);
        CalculoIncremento.Tipo tipo = CalculoIncremento.Tipo.valueOf(i.getTipoIncremento());
        CalculoIncremento.Redondeo redondeo = CalculoIncremento.Redondeo.valueOf(i.getRedondeo());

        List<Vinculacion> entran = new ArrayList<>();
        List<BigDecimal> nuevos = new ArrayList<>();
        int sinCambio = 0;
        for (Vinculacion v : queryRepository.vinculaciones(i.getEmpresaId(),
                TipoListaNomina.valueOf(i.getTipoLista()), ids == null ? List.of() : ids)) {
            BigDecimal nuevo = CalculoIncremento.nuevoSalario(v.salario(), tipo, i.getValor(), smmlv, redondeo);
            if (nuevo.compareTo(v.salario()) == 0 || nuevo.signum() <= 0) {
                sinCambio++;
                continue;
            }
            entran.add(v);
            nuevos.add(nuevo);
        }
        if (entran.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, sinCambio > 0
                    ? "El incremento no le cambia el salario a ninguna de las " + sinCambio + " vinculaciones elegidas."
                    : "No hay vinculaciones activas con salario para lo que eligió.");
        queryRepository.reemplazarDetalle(i.getId(), entran, nuevos);
        return sinCambio;
    }

    private NominaIncrementoEntity entidad(Long id, Integer empresaId) {
        return jpaRepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Incremento no encontrado"));
    }

    private NominaIncrementoEntity pendiente(Long id, Integer empresaId) {
        NominaIncrementoEntity i = entidad(id, empresaId);
        if (!PENDIENTE.equals(i.getEstado()))
            throw new GlobalException(HttpStatus.CONFLICT, "El incremento está " + i.getEstado().toLowerCase()
                    + ": ya no se puede cambiar.");
        return i;
    }
}
