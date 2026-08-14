package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.producto_composicion.CreateProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.GuardarRecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.ProductoComposicionTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaComponenteDetalleDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaComponenteDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaCosteoDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaCosteoLineaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.RecetaResumenTableDto;
import com.cloud_technological.aura_pos.dto.producto_composicion.UpdateProductoComposicionDto;
import com.cloud_technological.aura_pos.entity.ProductoComposicionEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.ProductoPresentacionEntity;
import com.cloud_technological.aura_pos.entity.UnidadMedidaEntity;
import com.cloud_technological.aura_pos.mappers.ProductoComposicionMapper;
import com.cloud_technological.aura_pos.repositories.producto_presentacion.ProductoPresentacionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos.ProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionJPARepository;
import com.cloud_technological.aura_pos.repositories.productos_composicion.ProductoComposicionQueryRepository;
import com.cloud_technological.aura_pos.repositories.unidad_medida.UnidadMedidaJPARepository;
import com.cloud_technological.aura_pos.services.ProductoComposicionService;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;

import jakarta.transaction.Transactional;

@Service
public class ProductoComposicionServiceImpl implements ProductoComposicionService {

    /** Mismo scale que usa el motor de ventas al convertir presentaciones. */
    private static final int SCALE_CANTIDAD = 6;
    private static final int SCALE_DINERO = 2;
    private static final BigDecimal CIEN = new BigDecimal("100");

    /**
     * Tope de anidamiento al explotar subrecetas. Los ciclos ya los corta el
     * set de visitados; esto es el cinturón para datos legacy raros.
     */
    private static final int PROFUNDIDAD_MAX = 10;

    private final ProductoComposicionQueryRepository composicionRepository;
    private final ProductoComposicionJPARepository composicionJPARepository;
    private final ProductoJPARepository productoJPARepository;
    private final UnidadMedidaJPARepository unidadMedidaJPARepository;
    private final ProductoPresentacionJPARepository presentacionJPARepository;
    private final ProductoComposicionMapper composicionMapper;

    @Autowired
    public ProductoComposicionServiceImpl(
            ProductoComposicionQueryRepository composicionRepository,
            ProductoComposicionJPARepository composicionJPARepository,
            ProductoJPARepository productoJPARepository,
            UnidadMedidaJPARepository unidadMedidaJPARepository,
            ProductoPresentacionJPARepository presentacionJPARepository,
            ProductoComposicionMapper composicionMapper) {
        this.composicionRepository = composicionRepository;
        this.composicionJPARepository = composicionJPARepository;
        this.productoJPARepository = productoJPARepository;
        this.unidadMedidaJPARepository = unidadMedidaJPARepository;
        this.presentacionJPARepository = presentacionJPARepository;
        this.composicionMapper = composicionMapper;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Línea individual (endpoints originales)
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public PageImpl<ProductoComposicionTableDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return composicionRepository.listar(pageable, empresaId);
    }

    @Override
    public ProductoComposicionDto obtenerPorId(Long id, Integer empresaId) {
        ProductoComposicionEntity entity = composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));
        return composicionMapper.toDto(entity);
    }

    @Override
    public List<ProductoComposicionTableDto> listarPorPadre(Long productoPadreId) {
        return composicionRepository.listarPorPadre(productoPadreId);
    }

    @Override
    @Transactional
    public ProductoComposicionDto crear(CreateProductoComposicionDto dto, Integer empresaId) {
        if (dto.getProductoPadreId().equals(dto.getProductoHijoId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El producto padre y el hijo no pueden ser el mismo");

        if (composicionJPARepository.existsByProductoPadreIdAndProductoHijoId(dto.getProductoPadreId(), dto.getProductoHijoId()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Este producto hijo ya hace parte de la composición");

        ProductoEntity padre = productoJPARepository.findByIdAndEmpresaId(dto.getProductoPadreId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto padre no encontrado"));

        ProductoEntity hijo = productoJPARepository.findByIdAndEmpresaId(dto.getProductoHijoId(), empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Producto hijo no encontrado"));

        validarSinCiclo(padre.getId(), hijo.getId(), hijo.getNombre());

        ProductoComposicionEntity entity = composicionMapper.toEntity(dto);
        entity.setProductoPadre(padre);
        entity.setProductoHijo(hijo);

        aplicarUnidad(entity, hijo, dto.getUnidadMedidaId(), dto.getProductoPresentacionId(),
                dto.getFactorUnidad(), empresaId);
        entity.setMermaPorcentaje(normalizarMerma(dto.getMermaPorcentaje()));
        entity.setCantidad(calcularCantidadPorUnidad(entity, rendimientoDe(padre), hijo.getNombre()));

        return composicionMapper.toDto(composicionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public ProductoComposicionDto actualizar(Long id, UpdateProductoComposicionDto dto, Integer empresaId) {
        ProductoComposicionEntity entity = composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));

        composicionMapper.updateEntityFromDto(dto, entity);

        ProductoEntity hijo = entity.getProductoHijo();
        aplicarUnidad(entity, hijo, dto.getUnidadMedidaId(), dto.getProductoPresentacionId(),
                dto.getFactorUnidad(), empresaId);
        entity.setMermaPorcentaje(normalizarMerma(dto.getMermaPorcentaje()));
        entity.setCantidad(calcularCantidadPorUnidad(entity, rendimientoDe(entity.getProductoPadre()), hijo.getNombre()));

        return composicionMapper.toDto(composicionJPARepository.save(entity));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        composicionJPARepository.findByIdAndProductoPadreEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Composición no encontrada"));
        composicionJPARepository.deleteById(id);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Receta completa
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public PageImpl<RecetaResumenTableDto> listarRecetas(PageableDto<Object> pageable, Integer empresaId) {
        return composicionRepository.listarRecetas(pageable, empresaId);
    }

    @Override
    @Transactional
    public RecetaDto obtenerReceta(Long productoPadreId, Integer empresaId) {
        ProductoEntity padre = buscarProducto(productoPadreId, empresaId, "Producto no encontrado");
        return armarReceta(padre, empresaId);
    }

    /**
     * Guarda la receta entera de un producto.
     *
     * Hace diff por producto hijo: la línea que ya existía se actualiza (conserva
     * su id), la nueva se inserta y la que desapareció del payload se borra. Todo
     * en una transacción, así que una receta de 12 ingredientes es 1 request y no
     * 12 — que era el problema original.
     */
    @Override
    @Transactional
    public RecetaDto guardarReceta(Long productoPadreId, GuardarRecetaDto dto, Integer empresaId) {
        ProductoEntity padre = buscarProducto(productoPadreId, empresaId, "Producto padre no encontrado");

        List<RecetaComponenteDto> componentes = dto.getComponentes() != null
                ? dto.getComponentes()
                : List.of();

        // El rendimiento se fija ANTES de calcular las líneas: es el divisor.
        BigDecimal rendimiento = dto.getRendimiento() != null ? dto.getRendimiento() : rendimientoDe(padre);
        padre.setRendimientoReceta(rendimiento);
        productoJPARepository.save(padre);

        validarPayload(productoPadreId, componentes);

        Map<Long, ProductoComposicionEntity> existentes = new LinkedHashMap<>();
        for (ProductoComposicionEntity linea : composicionJPARepository.findByProductoPadreIdOrderByOrdenAscIdAsc(productoPadreId)) {
            existentes.put(linea.getProductoHijo().getId(), linea);
        }

        List<ProductoComposicionEntity> aGuardar = new ArrayList<>();
        int orden = 0;

        for (RecetaComponenteDto componente : componentes) {
            orden++;
            Long hijoId = componente.getProductoHijoId();

            ProductoEntity hijo = buscarProducto(hijoId, empresaId,
                    "Componente no encontrado: " + hijoId);

            validarSinCiclo(productoPadreId, hijoId, hijo.getNombre());

            // Reutilizar la fila existente conserva el id y evita que el índice
            // único padre+hijo choque entre el DELETE y el INSERT.
            ProductoComposicionEntity entity = existentes.remove(hijoId);
            if (entity == null) {
                entity = new ProductoComposicionEntity();
                entity.setProductoPadre(padre);
                entity.setProductoHijo(hijo);
            }

            entity.setTipo(dto.getTipo());
            entity.setCantidadReceta(componente.getCantidadReceta());
            entity.setMermaPorcentaje(normalizarMerma(componente.getMermaPorcentaje()));
            entity.setOrden(componente.getOrden() != null ? componente.getOrden() : orden);
            entity.setNota(componente.getNota());

            aplicarUnidad(entity, hijo, componente.getUnidadMedidaId(), componente.getProductoPresentacionId(),
                    componente.getFactorUnidad(), empresaId);
            entity.setCantidad(calcularCantidadPorUnidad(entity, rendimiento, hijo.getNombre()));

            aGuardar.add(entity);
        }

        // Lo que quedó en el mapa ya no está en la receta.
        if (!existentes.isEmpty()) {
            composicionJPARepository.deleteAll(existentes.values());
            composicionJPARepository.flush();
        }

        composicionJPARepository.saveAll(aGuardar);

        // armarReceta lee por JDBC plano, que NO dispara el auto-flush de
        // Hibernate: sin este flush devolvería los valores anteriores de las
        // líneas que solo se actualizaron.
        composicionJPARepository.flush();

        return armarReceta(padre, empresaId);
    }

    @Override
    @Transactional
    public RecetaDto duplicarReceta(Long productoOrigenId, Long productoDestinoId, Integer empresaId) {
        if (productoOrigenId.equals(productoDestinoId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El producto origen y el destino no pueden ser el mismo");

        ProductoEntity origen = buscarProducto(productoOrigenId, empresaId, "Producto origen no encontrado");
        buscarProducto(productoDestinoId, empresaId, "Producto destino no encontrado");

        List<ProductoComposicionEntity> lineasOrigen =
                composicionJPARepository.findByProductoPadreIdOrderByOrdenAscIdAsc(productoOrigenId);

        if (lineasOrigen.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El producto '" + origen.getNombre() + "' no tiene receta para copiar");

        GuardarRecetaDto copia = new GuardarRecetaDto();
        copia.setTipo(lineasOrigen.get(0).getTipo());
        copia.setRendimiento(rendimientoDe(origen));

        List<RecetaComponenteDto> componentes = new ArrayList<>();
        for (ProductoComposicionEntity linea : lineasOrigen) {
            RecetaComponenteDto componente = new RecetaComponenteDto();
            componente.setProductoHijoId(linea.getProductoHijo().getId());
            componente.setCantidadReceta(linea.getCantidadReceta());
            componente.setUnidadMedidaId(linea.getUnidadMedida() != null ? linea.getUnidadMedida().getId() : null);
            // El factor ya está resuelto: se copia tal cual en vez de volver a
            // derivarlo de la presentación, que pudo cambiar desde entonces.
            componente.setFactorUnidad(linea.getFactorUnidad());
            componente.setMermaPorcentaje(linea.getMermaPorcentaje());
            componente.setOrden(linea.getOrden());
            componente.setNota(linea.getNota());
            componentes.add(componente);
        }
        copia.setComponentes(componentes);

        return guardarReceta(productoDestinoId, copia, empresaId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Costeo
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public RecetaCosteoDto costear(Long productoPadreId, Integer empresaId) {
        ProductoEntity padre = buscarProducto(productoPadreId, empresaId, "Producto no encontrado");
        return calcularCosteo(padre, empresaId);
    }

    @Override
    @Transactional
    public RecetaCosteoDto aplicarCosto(Long productoPadreId, Integer empresaId) {
        ProductoEntity padre = buscarProducto(productoPadreId, empresaId, "Producto no encontrado");
        RecetaCosteoDto costeo = calcularCosteo(padre, empresaId);

        if (Boolean.TRUE.equals(costeo.getCostoIncompleto()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No se puede aplicar el costo: hay componentes sin costo cargado. "
                    + "Revise el detalle del costeo antes de aplicarlo.");

        padre.setCosto(costeo.getCostoUnitario());
        productoJPARepository.save(padre);

        costeo.setCostoActual(costeo.getCostoUnitario());
        return costeo;
    }

    private RecetaCosteoDto calcularCosteo(ProductoEntity padre, Integer empresaId) {
        List<ProductoComposicionEntity> lineas =
                composicionJPARepository.findByProductoPadreIdOrderByOrdenAscIdAsc(padre.getId());

        if (lineas.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El producto '" + padre.getNombre() + "' no tiene receta para costear");

        BigDecimal rendimiento = rendimientoDe(padre);
        List<RecetaCosteoLineaDto> detalle = new ArrayList<>();
        BigDecimal costoUnitario = BigDecimal.ZERO;
        boolean incompleto = false;

        for (ProductoComposicionEntity linea : lineas) {
            ProductoEntity hijo = linea.getProductoHijo();
            BigDecimal cantidad = linea.getCantidad() != null ? linea.getCantidad() : BigDecimal.ZERO;

            Set<Long> visitados = new HashSet<>();
            visitados.add(padre.getId());
            boolean tieneSubreceta = composicionJPARepository.existsByProductoPadreId(hijo.getId());
            BigDecimal costoHijo = costoUnitarioResuelto(hijo, visitados, 0);

            RecetaCosteoLineaDto dtoLinea = new RecetaCosteoLineaDto();
            dtoLinea.setProductoHijoId(hijo.getId());
            dtoLinea.setProductoHijoNombre(hijo.getNombre());
            dtoLinea.setCantidad(cantidad);
            dtoLinea.setCantidadLote(cantidad.multiply(rendimiento).setScale(SCALE_CANTIDAD, RoundingMode.HALF_UP));
            dtoLinea.setCostoUnitario(costoHijo);
            dtoLinea.setCostoTotal(cantidad.multiply(costoHijo).setScale(SCALE_DINERO, RoundingMode.HALF_UP));
            dtoLinea.setCostoDerivadoDeReceta(tieneSubreceta);

            if (costoHijo.compareTo(BigDecimal.ZERO) <= 0) {
                incompleto = true;
                dtoLinea.setAdvertencia("Sin costo registrado: no suma al total");
            }

            costoUnitario = costoUnitario.add(cantidad.multiply(costoHijo));
            detalle.add(dtoLinea);
        }

        costoUnitario = costoUnitario.setScale(SCALE_DINERO, RoundingMode.HALF_UP);

        for (RecetaCosteoLineaDto dtoLinea : detalle) {
            dtoLinea.setParticipacion(costoUnitario.compareTo(BigDecimal.ZERO) > 0
                    ? dtoLinea.getCostoTotal().multiply(CIEN)
                            .divide(costoUnitario, SCALE_DINERO, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);
        }

        RecetaCosteoDto costeo = new RecetaCosteoDto();
        costeo.setProductoPadreId(padre.getId());
        costeo.setProductoPadreNombre(padre.getNombre());
        costeo.setRendimiento(rendimiento);
        costeo.setLineas(detalle);
        costeo.setCostoUnitario(costoUnitario);
        costeo.setCostoLote(costoUnitario.multiply(rendimiento).setScale(SCALE_DINERO, RoundingMode.HALF_UP));
        costeo.setCostoActual(padre.getCosto());
        costeo.setCostoIncompleto(incompleto);

        BigDecimal precio = padre.getPrecio();
        costeo.setPrecioVenta(precio);
        if (precio != null && precio.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal utilidad = precio.subtract(costoUnitario);
            costeo.setUtilidadUnitaria(utilidad.setScale(SCALE_DINERO, RoundingMode.HALF_UP));
            costeo.setMargenPorcentaje(utilidad.multiply(CIEN)
                    .divide(precio, SCALE_DINERO, RoundingMode.HALF_UP));
        }

        return costeo;
    }

    /**
     * Costo de 1 unidad de un producto.
     *
     * Si el producto tiene receta propia se explota (la "masa madre" del panadero
     * vale lo que valen su harina y su levadura de hoy, no lo que quedó grabado
     * en `costo` hace seis meses). Si no la tiene, se usa `producto.costo`.
     */
    private BigDecimal costoUnitarioResuelto(ProductoEntity producto, Set<Long> visitados, int profundidad) {
        BigDecimal costoPropio = producto.getCosto() != null ? producto.getCosto() : BigDecimal.ZERO;

        if (profundidad >= PROFUNDIDAD_MAX || !visitados.add(producto.getId()))
            return costoPropio;

        List<ProductoComposicionEntity> subLineas =
                composicionJPARepository.findByProductoPadreId(producto.getId());

        if (subLineas.isEmpty())
            return costoPropio;

        BigDecimal acumulado = BigDecimal.ZERO;
        for (ProductoComposicionEntity sub : subLineas) {
            BigDecimal cantidad = sub.getCantidad() != null ? sub.getCantidad() : BigDecimal.ZERO;
            acumulado = acumulado.add(
                    cantidad.multiply(costoUnitarioResuelto(sub.getProductoHijo(), visitados, profundidad + 1)));
        }

        // Una subreceta sin ningún costo abajo no debe borrar el costo que el
        // producto sí tenía cargado a mano.
        return acumulado.compareTo(BigDecimal.ZERO) > 0
                ? acumulado.setScale(SCALE_DINERO, RoundingMode.HALF_UP)
                : costoPropio;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Cálculo y validaciones
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Traduce lo que escribió el usuario a la unidad que entiende el inventario:
     *
     * <pre>
     *   cantidad = (cantidadReceta × factorUnidad ÷ (1 − merma)) ÷ rendimiento
     * </pre>
     *
     * El resultado conserva la semántica histórica de la columna `cantidad`
     * —consumo en unidad base del hijo por 1 unidad del padre— para que el
     * descuento de inventario en venta siga funcionando sin cambios.
     */
    private BigDecimal calcularCantidadPorUnidad(ProductoComposicionEntity entity, BigDecimal rendimiento,
            String nombreHijo) {
        BigDecimal cantidadReceta = entity.getCantidadReceta();
        if (cantidadReceta == null || cantidadReceta.compareTo(BigDecimal.ZERO) <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cantidad de '" + nombreHijo + "' debe ser mayor que cero");

        BigDecimal factor = entity.getFactorUnidad() != null ? entity.getFactorUnidad() : BigDecimal.ONE;
        BigDecimal consumo = cantidadReceta.multiply(factor);

        BigDecimal merma = entity.getMermaPorcentaje();
        if (merma != null && merma.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal aprovechado = BigDecimal.ONE.subtract(merma.divide(CIEN, 6, RoundingMode.HALF_UP));
            consumo = consumo.divide(aprovechado, SCALE_CANTIDAD, RoundingMode.HALF_UP);
        }

        BigDecimal cantidad = consumo.divide(rendimiento, SCALE_CANTIDAD, RoundingMode.HALF_UP);

        // Con scale 6, redondear a cero significa que ese ingrediente jamás
        // descontaría stock. Mejor fallar que dejar una receta que miente.
        if (cantidad.compareTo(BigDecimal.ZERO) <= 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cantidad de '" + nombreHijo + "' es demasiado pequeña para el rendimiento indicado: "
                    + "se redondea a cero y no descontaría inventario. Use una unidad más pequeña.");

        return cantidad;
    }

    /**
     * Resuelve el factor de conversión de la línea.
     *
     * Si viene una presentación del componente, el factor sale de ella con la
     * misma convención que ya usa el motor de ventas: `factor_conversion` es
     * cuántas unidades escritas caben en 1 unidad base de stock (bulto de 50 kg
     * → 50), así que la equivalencia es su inverso.
     */
    private void aplicarUnidad(ProductoComposicionEntity entity, ProductoEntity hijo, Long unidadMedidaId,
            Long presentacionId, BigDecimal factorUnidad, Integer empresaId) {

        if (unidadMedidaId != null) {
            UnidadMedidaEntity unidad = unidadMedidaJPARepository.findByIdAndActivoTrue(unidadMedidaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST,
                            "Unidad de medida no encontrada o inactiva"));
            entity.setUnidadMedida(unidad);
        } else {
            entity.setUnidadMedida(hijo.getUnidadMedidaBase());
        }

        if (presentacionId != null) {
            ProductoPresentacionEntity presentacion = presentacionJPARepository
                    .findByIdAndProductoEmpresaId(presentacionId, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Presentación no encontrada"));

            if (!presentacion.getProducto().getId().equals(hijo.getId()))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La presentación seleccionada no pertenece a '" + hijo.getNombre() + "'");

            BigDecimal factorConversion = presentacion.getFactorConversion();
            if (factorConversion == null || factorConversion.compareTo(BigDecimal.ZERO) <= 0)
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La presentación '" + presentacion.getNombre() + "' no tiene factor de conversión válido");

            entity.setProductoPresentacion(presentacion);
            entity.setFactorUnidad(BigDecimal.ONE.divide(factorConversion, SCALE_CANTIDAD, RoundingMode.HALF_UP));
        } else {
            entity.setProductoPresentacion(null);
            entity.setFactorUnidad(factorUnidad != null ? factorUnidad : BigDecimal.ONE);
        }
    }

    private void validarPayload(Long productoPadreId, List<RecetaComponenteDto> componentes) {
        Set<Long> vistos = new HashSet<>();
        for (RecetaComponenteDto componente : componentes) {
            Long hijoId = componente.getProductoHijoId();

            if (productoPadreId.equals(hijoId))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Un producto no puede ser componente de sí mismo");

            if (!vistos.add(hijoId))
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "El componente " + hijoId + " está repetido en la receta. "
                        + "Súmelo en una sola línea.");
        }
    }

    /**
     * Impide que la receta se muerda la cola (A lleva B, B lleva A).
     *
     * Sin esto, el costeo recursivo y cualquier explosión futura de inventario
     * entrarían en bucle. Recorre hacia abajo desde el hijo buscando al padre.
     */
    private void validarSinCiclo(Long productoPadreId, Long productoHijoId, String nombreHijo) {
        if (productoPadreId.equals(productoHijoId))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Un producto no puede ser componente de sí mismo");

        Set<Long> visitados = new HashSet<>();
        Deque<Long> pendientes = new ArrayDeque<>();
        pendientes.push(productoHijoId);

        while (!pendientes.isEmpty()) {
            Long actual = pendientes.pop();
            if (!visitados.add(actual))
                continue;

            for (Long nieto : composicionJPARepository.findHijoIdsByPadreId(actual)) {
                if (productoPadreId.equals(nieto))
                    throw new GlobalException(HttpStatus.BAD_REQUEST,
                            "Ciclo en la composición: '" + nombreHijo + "' ya contiene, directa o "
                            + "indirectamente, al producto que está editando");
                pendientes.push(nieto);
            }
        }
    }

    private BigDecimal normalizarMerma(BigDecimal merma) {
        return merma != null ? merma : BigDecimal.ZERO;
    }

    private BigDecimal rendimientoDe(ProductoEntity producto) {
        BigDecimal rendimiento = producto.getRendimientoReceta();
        return rendimiento != null && rendimiento.compareTo(BigDecimal.ZERO) > 0 ? rendimiento : BigDecimal.ONE;
    }

    private ProductoEntity buscarProducto(Long id, Integer empresaId, String mensaje) {
        return productoJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, mensaje));
    }

    private RecetaDto armarReceta(ProductoEntity padre, Integer empresaId) {
        List<RecetaComponenteDetalleDto> componentes =
                composicionRepository.obtenerComponentes(padre.getId(), empresaId);

        RecetaDto receta = new RecetaDto();
        receta.setProductoPadreId(padre.getId());
        receta.setProductoPadreNombre(padre.getNombre());
        receta.setRendimiento(rendimientoDe(padre));
        receta.setUnidadBaseAbreviatura(padre.getUnidadMedidaBase() != null
                ? padre.getUnidadMedidaBase().getAbreviatura()
                : null);
        receta.setComponentes(componentes);

        // El tipo vive en cada línea por historia; para la cabecera basta el de
        // la primera. RECETA es el default de un producto que aún no tiene.
        receta.setTipo(componentes.isEmpty()
                ? "RECETA"
                : composicionJPARepository.findByProductoPadreIdOrderByOrdenAscIdAsc(padre.getId())
                        .get(0).getTipo());

        return receta;
    }
}
