package com.cloud_technological.aura_pos.services.servicio_tecnico;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.AnularAbono;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.CambiarEstado;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ClienteRapido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConfigDto;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.ConsultaPublica;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Entregar;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Equipo;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.FotoContenido;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarConfig;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarItem;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.GuardarOrden;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Indicadores;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Nota;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenDetalle;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.OrdenFila;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.Pago;
import com.cloud_technological.aura_pos.dto.servicio_tecnico.ServicioTecnicoDtos.RespuestaPresupuesto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.CreateReciboCajaDto;
import com.cloud_technological.aura_pos.dto.cartera.recibo.ReciboCajaDto;
import com.cloud_technological.aura_pos.dto.factura_venta.FacturaVentaDtos;
import com.cloud_technological.aura_pos.dto.terceros.CreateTerceroDto;
import com.cloud_technological.aura_pos.dto.terceros.TerceroDto;
import com.cloud_technological.aura_pos.entity.CuentaCobrarEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenAbonoEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenEquipoEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenFotoEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenHistorialEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioOrdenItemEntity;
import com.cloud_technological.aura_pos.entity.servicio_tecnico.ServicioTecnicoConfigEntity;
import com.cloud_technological.aura_pos.mappers.ServicioTecnicoMapper;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.CuentaCobrarJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenAbonoJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenEquipoJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenFotoJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenHistorialJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenItemJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioOrdenJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioTecnicoConfigJPARepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioTecnicoQueryRepository;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioTecnicoQueryRepository.AnticipoAbono;
import com.cloud_technological.aura_pos.repositories.servicio_tecnico.ServicioTecnicoQueryRepository.Consecutivo;
import com.cloud_technological.aura_pos.services.ITerceroService;
import com.cloud_technological.aura_pos.services.ReciboCajaService;
import com.cloud_technological.aura_pos.services.implementations.FacturaVentaService;
import com.cloud_technological.aura_pos.services.nube.NubeAlmacenamiento;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación de {@link ServicioOrdenService}. Las reglas puras (estados, IMEI,
 * listas) viven en {@link ReglasOrdenServicio}.
 */
@Slf4j
@Service
public class ServicioOrdenServiceImpl implements ServicioOrdenService {

    /** Fecha de Colombia: la JVM de producción corre en UTC. */
    private static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final int MAX_FOTOS = 12;

    @Autowired private ServicioOrdenJPARepository ordenRepo;
    @Autowired private ServicioOrdenEquipoJPARepository equipoRepo;
    @Autowired private ServicioOrdenItemJPARepository itemRepo;
    @Autowired private ServicioOrdenFotoJPARepository fotoRepo;
    @Autowired private ServicioOrdenHistorialJPARepository historialRepo;
    @Autowired private ServicioTecnicoConfigJPARepository configRepo;
    @Autowired private ServicioTecnicoQueryRepository query;
    @Autowired private ServicioTecnicoMapper mapper;
    @Autowired private NubeAlmacenamiento almacenamiento;
    @Autowired private ITerceroService terceros;
    @Autowired private ServicioOrdenAbonoJPARepository abonoRepo;
    @Autowired private ReciboCajaService recibos;
    @Autowired private FacturaVentaService facturas;
    @Autowired private CuentaCobrarJPARepository cxcRepo;

    // ── Consultas ───────────────────────────────────────────────────────

    @Override
    public PageImpl<OrdenFila> listar(PageableDto<Map<String, Object>> pageable, Integer empresaId, Long usuarioId) {
        int page = pageable.getPage() != null ? pageable.getPage().intValue() : 0;
        int size = pageable.getRows() != null ? Math.min(pageable.getRows().intValue(), 100) : 10;
        return query.listar(page, size, pageable.getSearch(), pageable.getParams(), empresaId, usuarioId, hoy());
    }

    @Override
    public Indicadores indicadores(Integer empresaId) {
        return query.indicadores(empresaId, hoy());
    }

    @Override
    public OrdenDetalle obtener(Long id, Integer empresaId) {
        OrdenDetalle d = query.detalle(id, empresaId);
        if (d == null) throw noEncontrada();
        Long equipoId = query.equipoId(id, empresaId);
        if (equipoId != null) equipoRepo.findById(equipoId).map(mapper::toDto).ifPresent(d::setEquipo);
        d.setItems(query.items(id, empresaId));
        d.setFotos(query.fotos(id, empresaId));
        d.setHistorial(query.historial(id, empresaId));
        d.setAbonos(query.abonos(id, empresaId));
        if (d.getEquipo() != null)
            d.setAnteriores(query.anteriores(id, d.getEquipo().getImei(), d.getEquipo().getImei2(), empresaId));
        d.setEstadosPermitidos(ReglasOrdenServicio.permitidos(d.getEstado()));
        d.setEditable(!ReglasOrdenServicio.esFinal(d.getEstado()));
        d.setVencida(ReglasOrdenServicio.vencida(d.getEstado(), d.getFechaEstimada(), hoy()));
        return d;
    }

    // ── Recepción y edición ─────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle crear(GuardarOrden dto, Integer empresaId, Long sucursalId, Long usuarioId) {
        validarCabecera(dto, empresaId);
        Consecutivo c = query.tomarConsecutivo(empresaId);

        ServicioOrdenEntity o = new ServicioOrdenEntity();
        mapper.copiar(dto, o);
        o.setEmpresaId(empresaId);
        o.setSucursalId(sucursalId);
        o.setNumero(c.numero());
        o.setCodigo(c.prefijo() + "-" + String.format("%04d", c.numero()));
        o.setEstado(ReglasOrdenServicio.RECIBIDO);
        o.setPrioridad(prioridad(dto.getPrioridad()));
        o.setGarantiaDias(dto.getGarantiaDias() != null ? dto.getGarantiaDias() : c.garantiaDias());
        o.setFechaRecibido(ahora());
        o.setCreatedBy(usuarioId);
        o.setTokenPublico(nuevoToken());
        limpiarContacto(o);
        o = ordenRepo.saveAndFlush(o);

        ServicioOrdenEquipoEntity e = new ServicioOrdenEquipoEntity();
        e.setEmpresaId(empresaId);
        e.setOrdenId(o.getId());
        copiarEquipo(dto.getEquipo(), e);
        equipoRepo.saveAndFlush(e);

        historial(o, "ESTADO", null, ReglasOrdenServicio.RECIBIDO, "Equipo recibido", usuarioId);
        return obtener(o.getId(), empresaId);
    }

    @Override
    @Transactional
    public OrdenDetalle actualizar(Long id, GuardarOrden dto, Integer empresaId, Long usuarioId) {
        ServicioOrdenEntity o = abierta(id, empresaId);
        validarCabecera(dto, empresaId);
        mapper.copiar(dto, o);
        o.setPrioridad(prioridad(dto.getPrioridad()));
        if (dto.getGarantiaDias() != null) o.setGarantiaDias(dto.getGarantiaDias());
        limpiarContacto(o);
        ordenRepo.saveAndFlush(o);

        Long equipoId = query.equipoId(id, empresaId);
        ServicioOrdenEquipoEntity e = equipoId != null ? equipoRepo.findById(equipoId).orElseThrow()
                : new ServicioOrdenEquipoEntity();
        e.setEmpresaId(empresaId);
        e.setOrdenId(id);
        copiarEquipo(dto.getEquipo(), e);
        equipoRepo.saveAndFlush(e);
        return obtener(id, empresaId);
    }

    // ── Estados e historial ─────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle cambiarEstado(Long id, CambiarEstado dto, Integer empresaId, Long usuarioId) {
        ServicioOrdenEntity o = propia(id, empresaId);
        String nuevo = dto.getEstado() == null ? null : dto.getEstado().trim().toUpperCase();
        String nota = dto.getNota() == null ? null : dto.getNota().trim();
        String motivo = ReglasOrdenServicio.motivoTransicionInvalida(o.getEstado(), nuevo, nota);
        if (motivo != null) throw new GlobalException(HttpStatus.BAD_REQUEST, motivo);
        if (ReglasOrdenServicio.ENTREGADO.equals(nuevo))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Use «Entregar» para entregar el equipo (con o sin factura)");
        if (ReglasOrdenServicio.CANCELADO.equals(nuevo) && query.totalAbonado(id, empresaId).signum() > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La orden tiene abonos: anúlelos (o devuélvalos) antes de cancelarla");

        String anterior = o.getEstado();
        o.setEstado(nuevo);
        if (ReglasOrdenServicio.ENTREGADO.equals(nuevo)) o.setFechaEntregado(ahora());
        if (ReglasOrdenServicio.CANCELADO.equals(nuevo)) o.setMotivoCancelacion(recortar(nota, 300));
        ordenRepo.saveAndFlush(o);
        historial(o, "ESTADO", anterior, nuevo, nota == null || nota.isEmpty() ? null : nota, usuarioId);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public OrdenDetalle agregarNota(Long id, Nota dto, Integer empresaId, Long usuarioId) {
        ServicioOrdenEntity o = propia(id, empresaId);
        historial(o, "NOTA", null, null, dto.getNota().trim(), usuarioId);
        return obtener(id, empresaId);
    }

    // ── Repuestos y mano de obra ────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle agregarItem(Long id, GuardarItem dto, Integer empresaId) {
        ServicioOrdenEntity o = abierta(id, empresaId);
        String tipo = dto.getTipo().trim().toUpperCase();
        if (!ReglasOrdenServicio.TIPOS_ITEM.contains(tipo))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de línea no válido");
        if (dto.getProductoId() != null && !query.productoDeEmpresa(dto.getProductoId(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El producto no existe en el inventario");

        ServicioOrdenItemEntity i = new ServicioOrdenItemEntity();
        i.setEmpresaId(empresaId);
        i.setOrdenId(id);
        i.setTipo(tipo);
        i.setProductoId(dto.getProductoId());
        i.setDescripcion(dto.getDescripcion().trim());
        i.setCantidad(dto.getCantidad());
        i.setValorUnitario(dto.getValorUnitario().setScale(2, RoundingMode.HALF_UP));
        i.setTotal(dto.getCantidad().multiply(dto.getValorUnitario()).setScale(2, RoundingMode.HALF_UP));
        itemRepo.save(i);
        recalcular(o);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public OrdenDetalle eliminarItem(Long id, Long itemId, Integer empresaId) {
        ServicioOrdenEntity o = abierta(id, empresaId);
        ServicioOrdenItemEntity i = itemRepo.findById(itemId)
                .filter(x -> x.getOrdenId().equals(id) && x.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Línea no encontrada"));
        itemRepo.delete(i);
        itemRepo.flush();
        recalcular(o);
        return obtener(id, empresaId);
    }

    // ── Fotos ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle subirFoto(Long id, MultipartFile archivo, String momento, Integer empresaId, Long usuarioId) {
        ServicioOrdenEntity o = propia(id, empresaId);
        if (ReglasOrdenServicio.CANCELADO.equals(o.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La orden está cancelada");
        if (query.contarFotos(id, empresaId) >= MAX_FOTOS)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Máximo " + MAX_FOTOS + " fotos por orden");
        String m = momento == null || momento.isBlank() ? "RECIBIDO" : momento.trim().toUpperCase();
        if (!ReglasOrdenServicio.MOMENTOS_FOTO.contains(m))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Momento de la foto no válido");
        if (archivo == null || archivo.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Seleccione una foto");
        if (archivo.getSize() > ServicioOrdenFotoEntity.MAX_BYTES)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La foto pesa más de 3 MB");

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "No se pudo leer la foto");
        }
        // El tipo se toma del contenido, no del nombre ni de lo que diga el navegador.
        String mime = ReglasOrdenServicio.mimeImagen(bytes);
        if (mime == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Solo se aceptan fotos JPG, PNG o WEBP");

        NubeAlmacenamiento.ObjetoGuardado g = almacenamiento.guardar(empresaId, bytes);
        try {
            ServicioOrdenFotoEntity f = new ServicioOrdenFotoEntity();
            f.setEmpresaId(empresaId);
            f.setOrdenId(id);
            f.setNombre(nombreFoto(archivo.getOriginalFilename(), mime));
            f.setMime(mime);
            f.setTamanoBytes(bytes.length);
            f.setObjectKey(g.objectKey());
            f.setIv(g.iv());
            f.setVersionLlave(g.versionLlave());
            f.setMomento(m);
            f.setCreatedBy(usuarioId);
            fotoRepo.saveAndFlush(f);
        } catch (RuntimeException e) {
            borrarObjeto(empresaId, g.objectKey());
            throw e;
        }
        return obtener(id, empresaId);
    }

    @Override
    public FotoContenido foto(Long id, Long fotoId, Integer empresaId) {
        ServicioOrdenFotoEntity f = fotoDe(id, fotoId, empresaId);
        byte[] bytes = almacenamiento.leer(empresaId, f.getObjectKey(), f.getIv(), f.getVersionLlave());
        return new FotoContenido(f.getNombre(), f.getMime(), bytes);
    }

    @Override
    @Transactional
    public OrdenDetalle eliminarFoto(Long id, Long fotoId, Integer empresaId, Long usuarioId) {
        abierta(id, empresaId);
        ServicioOrdenFotoEntity f = fotoDe(id, fotoId, empresaId);
        fotoRepo.delete(f);
        fotoRepo.flush();
        borrarObjeto(empresaId, f.getObjectKey());
        return obtener(id, empresaId);
    }

    // ── Abonos ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle registrarAbono(Long id, Pago dto, Integer empresaId, Integer sucursalId, Long usuarioId) {
        ServicioOrdenEntity o = abierta(id, empresaId);
        BigDecimal monto = dto.getMonto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal abonado = query.totalAbonado(id, empresaId);
        if (o.getTotal().signum() > 0 && abonado.add(monto).compareTo(o.getTotal()) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Con este abono se pasa del valor del servicio ($"
                    + o.getTotal().toPlainString() + "). Abonado hasta ahora: $" + abonado.toPlainString());
        boolean efectivo = "EFECTIVO".equalsIgnoreCase(dto.getMetodoPago().trim());
        boolean otroDia = Boolean.TRUE.equals(dto.getCajaOtroDia());
        // El anticipo no entra al arqueo del turno: en efectivo va a una cuenta (caja general,
        // caja menor) o se declara de otro día. Es la misma regla del recibo de caja.
        if (efectivo && !otroDia && dto.getCuentaContableId() == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Un abono en efectivo no entra al cuadre de la caja abierta: elija la cuenta donde queda (caja general o caja menor)");

        CreateReciboCajaDto r = new CreateReciboCajaDto();
        r.setTerceroId(o.getClienteId());
        r.setValorRecibido(monto);
        r.setMetodoPago(dto.getMetodoPago().trim().toUpperCase());
        r.setReferencia(vacioANull(dto.getReferencia()));
        r.setCuentaContableId(otroDia ? null : dto.getCuentaContableId());
        r.setCajaOtroDia(otroDia);
        r.setSucursalId(o.getSucursalId() != null ? o.getSucursalId().intValue() : sucursalId);
        r.setObservaciones("Abono a la orden de servicio " + o.getCodigo());
        r.setAplicaciones(List.of());
        ReciboCajaDto recibo = recibos.crear(r, empresaId, usuarioId);

        ServicioOrdenAbonoEntity a = new ServicioOrdenAbonoEntity();
        a.setEmpresaId(empresaId);
        a.setOrdenId(id);
        a.setReciboCajaId(recibo.getId());
        a.setAnticipoId(recibo.getAnticipoId());
        a.setMonto(monto);
        a.setMetodoPago(r.getMetodoPago());
        a.setUsuarioId(usuarioId);
        abonoRepo.save(a);
        recalcularAbonos(o);
        historial(o, "NOTA", null, null, "Abono de $" + monto.toPlainString() + " (" + recibo.getNumero() + ")", usuarioId);
        return obtener(id, empresaId);
    }

    @Override
    @Transactional
    public OrdenDetalle anularAbono(Long id, Long abonoId, AnularAbono dto, Integer empresaId, Long usuarioId) {
        ServicioOrdenEntity o = abierta(id, empresaId);
        ServicioOrdenAbonoEntity a = abonoRepo.findById(abonoId)
                .filter(x -> x.getOrdenId().equals(id) && x.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Abono no encontrado"));
        if (!"ACTIVO".equals(a.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El abono ya está anulado");
        // Reversa el recibo y su anticipo (falla si el anticipo ya se cruzó con una factura).
        recibos.anular(a.getReciboCajaId(), dto.getMotivo().trim(), empresaId, usuarioId);
        a.setEstado("ANULADO");
        abonoRepo.save(a);
        recalcularAbonos(o);
        historial(o, "NOTA", null, null, "Abono de $" + a.getMonto().toPlainString() + " anulado: " + dto.getMotivo().trim(),
                usuarioId);
        return obtener(id, empresaId);
    }

    private void recalcularAbonos(ServicioOrdenEntity o) {
        abonoRepo.flush();
        o.setTotalAbonado(query.totalAbonado(o.getId(), o.getEmpresaId()));
        ordenRepo.saveAndFlush(o);
    }

    // ── Entrega ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrdenDetalle entregar(Long id, Entregar dto, Integer empresaId, Integer sucursalId, Long usuarioId) {
        ServicioOrdenEntity o = propia(id, empresaId);
        String motivo = ReglasOrdenServicio.motivoTransicionInvalida(o.getEstado(), ReglasOrdenServicio.ENTREGADO, null);
        if (motivo != null) throw new GlobalException(HttpStatus.BAD_REQUEST, motivo);
        String nota = vacioANull(dto.getNota());
        BigDecimal abonado = query.totalAbonado(id, empresaId);
        StringBuilder resumen = new StringBuilder();

        if (dto.isFacturar()) {
            if (o.getTotal().signum() <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La orden no tiene valor: agregue repuestos o mano de obra, o entréguela sin factura");
            FacturaVentaDtos.Detalle factura = facturar(o, dto, empresaId, sucursalId, usuarioId);
            o.setFacturaVentaId(factura.getId());
            o.setVentaId(factura.getVentaId());
            resumen.append("Factura ").append(factura.getNumero() != null ? factura.getNumero() : "#" + factura.getId());
            if (abonado.signum() > 0) resumen.append(" · abonos cruzados $").append(abonado.toPlainString());

            if (dto.getCobro() != null && dto.getCobro().getMonto() != null && dto.getCobro().getMonto().signum() > 0) {
                ReciboCajaDto recibo = cobrarSaldo(o, factura, dto.getCobro(), empresaId, sucursalId, usuarioId);
                resumen.append(" · cobrado $").append(recibo.getValorRecibido().toPlainString())
                        .append(" (").append(recibo.getNumero()).append(")");
            }
        } else {
            if (abonado.signum() > 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La orden tiene abonos: entréguela con factura (se cruzan) o anule los abonos primero");
            if (o.getTotal().signum() > 0 && nota == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Escriba por qué se entrega sin factura (garantía, sin cobro…)");
            resumen.append("Entregado sin factura");
        }
        if (nota != null) resumen.append(" · ").append(nota);

        String anterior = o.getEstado();
        o.setEstado(ReglasOrdenServicio.ENTREGADO);
        o.setFechaEntregado(ahora());
        ordenRepo.saveAndFlush(o);
        historial(o, "ESTADO", anterior, ReglasOrdenServicio.ENTREGADO, resumen.toString(), usuarioId);
        return obtener(id, empresaId);
    }

    /**
     * Arma, guarda y emite la factura con las líneas de la orden. Los valores de la
     * orden son precio final (IVA incluido): la base se saca del IVA de cada producto.
     * Se emite a crédito para poder cruzar los abonos; el saldo se cobra con un recibo.
     */
    private FacturaVentaDtos.Detalle facturar(ServicioOrdenEntity o, Entregar dto, Integer empresaId,
            Integer sucursalId, Long usuarioId) {
        List<ServicioTecnicoDtos.ItemDto> items = query.items(o.getId(), empresaId);
        if (items.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La orden no tiene líneas para facturar");
        query.asegurarConfig(empresaId);
        Long servicio = configRepo.findById(empresaId).map(ServicioTecnicoConfigEntity::getProductoServicioId).orElse(null);

        FacturaVentaDtos.Guardar g = new FacturaVentaDtos.Guardar();
        g.setTipo(vacioANull(dto.getTipoDocumento()));
        g.setSucursalId(o.getSucursalId() != null ? o.getSucursalId().intValue() : sucursalId);
        g.setClienteId(o.getClienteId());
        g.setFormaPago("CREDITO");
        g.setFechaVencimiento(hoy());
        ServicioOrdenEquipoEntity e = equipoRepo.findById(query.equipoId(o.getId(), empresaId)).orElse(null);
        g.setNotas("Orden de servicio " + o.getCodigo()
                + (e != null ? " · " + e.getMarca() + " " + e.getModelo() + (e.getImei() != null ? " IMEI " + e.getImei() : "") : ""));
        boolean cuentaCobro = "CUENTA_COBRO".equalsIgnoreCase(String.valueOf(dto.getTipoDocumento()));

        List<FacturaVentaDtos.Linea> lineas = new ArrayList<>();
        for (ServicioTecnicoDtos.ItemDto i : items) {
            Long producto = i.getProductoId() != null ? i.getProductoId() : servicio;
            if (producto == null)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Falta el servicio con el que se factura la mano de obra: elíjalo en Servicio técnico › Configuración");
            BigDecimal pct = cuentaCobro ? BigDecimal.ZERO : query.ivaProducto(producto, empresaId);
            FacturaVentaDtos.Linea l = new FacturaVentaDtos.Linea();
            l.setProductoId(producto);
            l.setDescripcion(i.getDescripcion());
            l.setCantidad(i.getCantidad());
            l.setPrecioUnitario(ReglasOrdenServicio.base(i.getValorUnitario(), pct));
            l.setImpuestoPorcentaje(pct);
            lineas.add(l);
        }
        g.setLineas(lineas);

        FacturaVentaDtos.Detalle borrador = facturas.crear(g, empresaId, usuarioId);
        // Abonos: se cruzan contra la factura hasta su total.
        FacturaVentaDtos.Emitir emitir = new FacturaVentaDtos.Emitir();
        BigDecimal pendiente = borrador.getTotal();
        for (AnticipoAbono a : query.anticiposConSaldo(o.getId(), empresaId)) {
            if (pendiente.signum() <= 0) break;
            BigDecimal monto = a.saldo().min(pendiente);
            FacturaVentaDtos.AnticipoAplicar ap = new FacturaVentaDtos.AnticipoAplicar();
            ap.setAnticipoId(a.anticipoId());
            ap.setMonto(monto);
            emitir.getAnticipos().add(ap);
            pendiente = pendiente.subtract(monto);
        }
        return facturas.emitir(borrador.getId(), empresaId, usuarioId, emitir);
    }

    /** Recibo de caja por lo que paga el cliente al recoger, aplicado a la factura. */
    private ReciboCajaDto cobrarSaldo(ServicioOrdenEntity o, FacturaVentaDtos.Detalle factura, Pago pago,
            Integer empresaId, Integer sucursalId, Long usuarioId) {
        CuentaCobrarEntity cxc = cxcRepo.findByVentaIdAndEmpresaId(factura.getVentaId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.CONFLICT, "La factura no quedó en cartera"));
        BigDecimal monto = pago.getMonto().setScale(2, RoundingMode.HALF_UP);
        // Al discriminar el IVA línea por línea la factura puede quedar unos centavos por
        // debajo del valor de la orden: esa diferencia de redondeo no es un sobrante.
        if (monto.compareTo(cxc.getSaldoPendiente()) > 0
                && monto.subtract(cxc.getSaldoPendiente()).compareTo(BigDecimal.ONE) <= 0)
            monto = cxc.getSaldoPendiente();
        if (monto.compareTo(cxc.getSaldoPendiente()) > 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El cobro ($" + monto.toPlainString()
                    + ") supera el saldo de la factura ($" + cxc.getSaldoPendiente().toPlainString()
                    + "): registre el valor exacto y entregue el cambio");
        CreateReciboCajaDto r = new CreateReciboCajaDto();
        r.setTerceroId(o.getClienteId());
        r.setValorRecibido(monto);
        r.setMetodoPago(pago.getMetodoPago().trim().toUpperCase());
        r.setReferencia(vacioANull(pago.getReferencia()));
        r.setCuentaContableId(pago.getCuentaContableId());
        r.setTurnoCajaId(pago.getTurnoCajaId());
        r.setCajaOtroDia(Boolean.TRUE.equals(pago.getCajaOtroDia()));
        r.setSucursalId(o.getSucursalId() != null ? o.getSucursalId().intValue() : sucursalId);
        r.setObservaciones("Entrega de la orden de servicio " + o.getCodigo());
        CreateReciboCajaDto.Aplicacion ap = new CreateReciboCajaDto.Aplicacion();
        ap.setCuentaCobrarId(cxc.getId());
        ap.setMonto(monto);
        r.setAplicaciones(List.of(ap));
        return recibos.crear(r, empresaId, usuarioId);
    }

    // ── Consulta pública ────────────────────────────────────────────────

    @Override
    public ConsultaPublica consultaPublica(String token) {
        String t = tokenValido(token);
        ConsultaPublica c = query.consultaPublica(t);
        if (c == null) throw new GlobalException(HttpStatus.NOT_FOUND, "No encontramos la orden");
        c.setItems(query.lineasPublicas(t));
        c.setPasos(query.pasosPublicos(t));
        return c;
    }

    @Override
    @Transactional
    public ConsultaPublica responderPresupuesto(String token, RespuestaPresupuesto dto) {
        String t = tokenValido(token);
        Long id = query.ordenPorToken(t);
        if (id == null) throw new GlobalException(HttpStatus.NOT_FOUND, "No encontramos la orden");
        ServicioOrdenEntity o = ordenRepo.findById(id).orElseThrow(this::noEncontrada);
        if (!ReglasOrdenServicio.ESPERANDO_APROBACION.equals(o.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Esta orden ya no está esperando su aprobación");
        String comentario = dto.getComentario() == null || dto.getComentario().isBlank() ? null
                : recortar(dto.getComentario().trim(), 500);
        String nuevo = dto.isAprueba() ? ReglasOrdenServicio.EN_REPARACION : ReglasOrdenServicio.NO_REPARADO;
        if (dto.isAprueba()) o.setAprobadoClienteAt(ahora());
        o.setEstado(nuevo);
        ordenRepo.saveAndFlush(o);
        historial(o, "ESTADO", ReglasOrdenServicio.ESPERANDO_APROBACION, nuevo,
                (dto.isAprueba() ? "El cliente aprobó el presupuesto desde el enlace" : "El cliente no aprobó el presupuesto")
                        + (comentario != null ? ": " + comentario : ""),
                null);
        return consultaPublica(t);
    }

    /** La clave pública es de 32 caracteres hexadecimales: cualquier otra cosa ni se busca. */
    private static String tokenValido(String token) {
        if (token == null || !token.matches("[0-9a-f]{32}"))
            throw new GlobalException(HttpStatus.NOT_FOUND, "No encontramos la orden");
        return token;
    }

    private static String nuevoToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ── Cliente rápido ──────────────────────────────────────────────────

    @Override
    @Transactional
    public ClienteDto crearCliente(ClienteRapido dto, Integer empresaId) {
        String documento = dto.getNumeroDocumento().replaceAll("[\\s.\\-]", "");
        if (!documento.matches("[0-9A-Za-z]{3,20}"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El número de documento no es válido");
        String[] nombres = dto.getNombres().trim().split("\\s+", 2);
        String[] apellidos = dto.getApellidos() == null || dto.getApellidos().isBlank() ? new String[0]
                : dto.getApellidos().trim().split("\\s+", 2);

        CreateTerceroDto t = new CreateTerceroDto();
        t.setTipoDocumento(dto.getTipoDocumento().trim().toUpperCase());
        t.setNumeroDocumento(documento);
        t.setNombres(dto.getNombres().trim());
        t.setApellidos(vacioANull(dto.getApellidos()));
        t.setNombre1(nombres[0]);
        if (nombres.length > 1) t.setNombre2(nombres[1]);
        if (apellidos.length > 0) t.setApellido1(apellidos[0]);
        if (apellidos.length > 1) t.setApellido2(apellidos[1]);
        t.setTelefono(dto.getTelefono().trim());
        t.setEmail(vacioANull(dto.getEmail()));
        t.setEsCliente(true);
        // Reusa la creación normal: valida el documento repetido y sincroniza los roles.
        TerceroDto creado = terceros.crear(t, empresaId);

        ClienteDto c = new ClienteDto();
        c.setId(creado.getId());
        c.setNumeroDocumento(documento);
        c.setNombreCompleto((t.getNombres() + " " + (t.getApellidos() == null ? "" : t.getApellidos())).trim());
        c.setTelefono(t.getTelefono());
        c.setEmail(t.getEmail());
        return c;
    }

    // ── Configuración ───────────────────────────────────────────────────

    @Override
    @Transactional
    public ConfigDto config(Integer empresaId) {
        query.asegurarConfig(empresaId);
        return conNombre(mapper.toDto(configRepo.findById(empresaId).orElseThrow()), empresaId);
    }

    @Override
    @Transactional
    public ConfigDto guardarConfig(GuardarConfig dto, Integer empresaId) {
        query.asegurarConfig(empresaId);
        ServicioTecnicoConfigEntity c = configRepo.findById(empresaId).orElseThrow();
        String prefijo = dto.getPrefijo().trim().toUpperCase();
        if (!prefijo.matches("[A-Z0-9]{1,10}"))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El prefijo solo admite letras y números, sin espacios");
        c.setPrefijo(prefijo);
        c.setGarantiaDias(dto.getGarantiaDias());
        c.setCondiciones(dto.getCondiciones() == null || dto.getCondiciones().isBlank() ? null : dto.getCondiciones().trim());
        if (dto.getProductoServicioId() != null && !query.productoDeEmpresa(dto.getProductoServicioId(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El servicio elegido no existe en el catálogo");
        c.setProductoServicioId(dto.getProductoServicioId());
        return conNombre(mapper.toDto(configRepo.save(c)), empresaId);
    }

    private ConfigDto conNombre(ConfigDto c, Integer empresaId) {
        c.setProductoServicioNombre(query.nombreProducto(c.getProductoServicioId(), empresaId));
        return c;
    }

    // ── Apoyo ───────────────────────────────────────────────────────────

    /** Orden de la empresa del token; 404 si no existe o es de otra. */
    private ServicioOrdenEntity propia(Long id, Integer empresaId) {
        return ordenRepo.findById(id)
                .filter(o -> o.getEmpresaId().equals(empresaId))
                .orElseThrow(this::noEncontrada);
    }

    /** Orden propia que todavía se puede editar. */
    private ServicioOrdenEntity abierta(Long id, Integer empresaId) {
        ServicioOrdenEntity o = propia(id, empresaId);
        if (ReglasOrdenServicio.esFinal(o.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La orden está "
                    + (ReglasOrdenServicio.ENTREGADO.equals(o.getEstado()) ? "entregada" : "cancelada")
                    + " y ya no se puede modificar");
        return o;
    }

    private ServicioOrdenFotoEntity fotoDe(Long id, Long fotoId, Integer empresaId) {
        return fotoRepo.findById(fotoId)
                .filter(f -> f.getOrdenId().equals(id) && f.getEmpresaId().equals(empresaId))
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Foto no encontrada"));
    }

    private void validarCabecera(GuardarOrden dto, Integer empresaId) {
        if (!query.terceroDeEmpresa(dto.getClienteId(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El cliente no existe");
        if (dto.getTecnicoId() != null && !query.usuarioDeEmpresa(dto.getTecnicoId(), empresaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El técnico no es un usuario activo de la empresa");
    }

    /** Valida y copia los datos del equipo. */
    private void copiarEquipo(Equipo dto, ServicioOrdenEquipoEntity e) {
        String tipo = dto.getTipo() == null ? "CELULAR" : dto.getTipo().trim().toUpperCase();
        if (!ReglasOrdenServicio.TIPOS_EQUIPO.contains(tipo))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Tipo de equipo no válido");
        try {
            String imei = ReglasOrdenServicio.imei(dto.getImei(), "El IMEI");
            String imei2 = ReglasOrdenServicio.imei(dto.getImei2(), "El IMEI 2");
            if (imei2 != null && imei2.equals(imei))
                throw new IllegalArgumentException("El IMEI 2 es igual al IMEI 1");
            mapper.copiar(dto, e);
            e.setTipo(tipo);
            e.setMarca(dto.getMarca().trim());
            e.setModelo(dto.getModelo().trim());
            e.setImei(imei);
            e.setImei2(imei2);
            e.setSerial(vacioANull(dto.getSerial()));
            e.setColor(vacioANull(dto.getColor()));
            e.setAccesorios(ReglasOrdenServicio.unirLista(dto.getAccesorios(), 300));
            e.setEstadoFisico(ReglasOrdenServicio.unirLista(dto.getEstadoFisico(), 300));
            e.setProblemasReportados(dto.getProblemasReportados().trim());
        } catch (IllegalArgumentException ex) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /** Recalcula los totales desde las líneas. */
    private void recalcular(ServicioOrdenEntity o) {
        BigDecimal[] t = query.totales(o.getId(), o.getEmpresaId());
        o.setTotalRepuestos(t[0]);
        o.setTotalManoObra(t[1]);
        o.setTotal(t[2]);
        ordenRepo.saveAndFlush(o);
    }

    private void historial(ServicioOrdenEntity o, String tipo, String anterior, String nuevo, String nota,
            Long usuarioId) {
        ServicioOrdenHistorialEntity h = new ServicioOrdenHistorialEntity();
        h.setEmpresaId(o.getEmpresaId());
        h.setOrdenId(o.getId());
        h.setTipo(tipo);
        h.setEstadoAnterior(anterior);
        h.setEstadoNuevo(nuevo);
        h.setNota(recortar(nota, 1000));
        h.setUsuarioId(usuarioId);
        historialRepo.save(h);
    }

    private void borrarObjeto(Integer empresaId, String objectKey) {
        try {
            almacenamiento.eliminar(empresaId, objectKey);
        } catch (RuntimeException e) {
            // La fila ya no existe; el objeto huérfano no se puede leer sin ella.
            log.warn("Servicio técnico: no se pudo borrar la foto {} del bucket: {}", objectKey, e.getMessage());
        }
    }

    private static void limpiarContacto(ServicioOrdenEntity o) {
        o.setContactoTelefono(vacioANull(o.getContactoTelefono()));
        o.setContactoEmail(vacioANull(o.getContactoEmail()));
        o.setDiagnostico(vacioANull(o.getDiagnostico()));
        o.setTrabajoRealizado(vacioANull(o.getTrabajoRealizado()));
        o.setObservaciones(vacioANull(o.getObservaciones()));
    }

    private static String prioridad(String p) {
        String v = p == null || p.isBlank() ? "NORMAL" : p.trim().toUpperCase();
        if (!ReglasOrdenServicio.PRIORIDADES.contains(v))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Prioridad no válida");
        return v;
    }

    /** Nombre seguro para la foto (sin rutas ni caracteres raros). */
    private static String nombreFoto(String original, String mime) {
        String ext = mime.substring(mime.indexOf('/') + 1).replace("jpeg", "jpg");
        String base = original == null ? "" : original.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "").trim();
        int punto = base.lastIndexOf('.');
        if (punto > 0) base = base.substring(0, punto);
        if (base.isEmpty()) base = "foto";
        if (base.length() > 120) base = base.substring(0, 120);
        return base + "." + ext;
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(ZONA);
    }

    private GlobalException noEncontrada() {
        return new GlobalException(HttpStatus.NOT_FOUND, "Orden de servicio no encontrada");
    }
}
