package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Item;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.PorCajero;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.ProductoAbandonado;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Registrar;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Reporte;
import com.cloud_technological.aura_pos.entity.CarritoAbandonadoEntity;
import com.cloud_technological.aura_pos.entity.CarritoAbandonadoItemEntity;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoJPARepository;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoQueryRepository;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoQueryRepository.CarritoConCajero;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Carritos del POS que se vaciaron sin vender: cuánto tiempo estuvieron
 * armados y qué productos tenían.
 *
 * <p>Se guardan todos; el reporte decide desde cuántos minutos cuenta como
 * abandono (5 por defecto). Así, un carrito que se arma y se corrige en un
 * minuto no ensucia el reporte, y cambiar el umbral no pierde datos.
 */
@Service
@RequiredArgsConstructor
public class CarritoAbandonadoService {

    public static final int MINUTOS_POR_DEFECTO = 5;
    private static final int MAX_ITEMS = 500;

    private final CarritoAbandonadoJPARepository repo;
    private final CarritoAbandonadoQueryRepository queryRepo;

    @Transactional
    public void registrar(Integer empresaId, Integer usuarioId, Registrar req) {
        String motivo = req.getMotivo() != null ? req.getMotivo().trim().toUpperCase() : "";
        if (!motivo.equals(CarritoAbandonadoEntity.MOTIVO_VACIADO)
                && !motivo.equals(CarritoAbandonadoEntity.MOTIVO_PESTANA_CERRADA)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Motivo no válido: " + req.getMotivo());
        }
        List<Item> items = req.getItems() != null ? req.getItems() : List.of();
        if (items.isEmpty()) return; // un carrito vacío no es abandono
        if (items.size() > MAX_ITEMS) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "El carrito trae demasiados productos");
        }

        LocalDateTime vaciado = req.getVaciadoAt() != null ? req.getVaciadoAt() : LocalDateTime.now();
        LocalDateTime iniciado = req.getIniciadoAt() != null && !req.getIniciadoAt().isAfter(vaciado)
                ? req.getIniciadoAt() : vaciado;

        CarritoAbandonadoEntity c = new CarritoAbandonadoEntity();
        c.setEmpresaId(empresaId);
        c.setUsuarioId(usuarioId);
        c.setSucursalId(req.getSucursalId());
        c.setTurnoCajaId(req.getTurnoCajaId());
        c.setClienteId(req.getClienteId());
        c.setMotivo(motivo);
        c.setIniciadoAt(iniciado);
        c.setVaciadoAt(vaciado);
        c.setDuracionSegundos((int) Math.min(Integer.MAX_VALUE,
                Duration.between(iniciado, vaciado).getSeconds()));

        BigDecimal total = BigDecimal.ZERO;
        for (Item it : items) {
            CarritoAbandonadoItemEntity e = new CarritoAbandonadoItemEntity();
            e.setCarrito(c);
            e.setProductoId(it.getProductoId());
            e.setPresentacionId(it.getPresentacionId());
            String nombre = it.getNombre() != null && !it.getNombre().isBlank() ? it.getNombre().trim() : "Producto";
            e.setNombre(nombre.length() > 255 ? nombre.substring(0, 255) : nombre);
            e.setCantidad(nz(it.getCantidad()));
            e.setPrecio(nz(it.getPrecio()).setScale(2, RoundingMode.HALF_UP));
            BigDecimal sub = it.getSubtotal() != null ? it.getSubtotal()
                    : e.getCantidad().multiply(e.getPrecio());
            e.setSubtotal(sub.setScale(2, RoundingMode.HALF_UP));
            e.setAgregadoAt(it.getAgregadoAt());
            c.getDetalle().add(e);
            total = total.add(e.getSubtotal());
        }
        c.setItems(c.getDetalle().size());
        c.setTotal(total);
        repo.save(c);
    }

    public Reporte reporte(Integer empresaId, LocalDate desde, LocalDate hasta, Integer minutosMinimos,
            Integer sucursalId) {
        LocalDate d = desde != null ? desde : LocalDate.now().withDayOfMonth(1);
        LocalDate h = hasta != null ? hasta : LocalDate.now();
        if (d.isAfter(h)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La fecha inicial es posterior a la final");
        }
        int minimo = minutosMinimos != null && minutosMinimos >= 0 ? minutosMinimos : MINUTOS_POR_DEFECTO;

        Reporte r = new Reporte();
        r.setDesde(d);
        r.setHasta(h);
        r.setMinutosMinimos(minimo);

        Map<Long, ProductoAbandonado> productos = new LinkedHashMap<>();
        Map<Integer, PorCajero> cajeros = new LinkedHashMap<>();
        long segundos = 0;
        for (CarritoConCajero cc : queryRepo.carritos(empresaId, d, h, sucursalId)) {
            if (cc.segundos() < minimo * 60) {
                r.setDescartadosPorTiempo(r.getDescartadosPorTiempo() + 1);
                continue;
            }
            var c = cc.carrito();
            r.getLista().add(c);
            r.setCarritos(r.getCarritos() + 1);
            r.setValor(r.getValor().add(nz(c.getTotal())));
            segundos += cc.segundos();

            PorCajero pc = cajeros.computeIfAbsent(cc.usuarioId() != null ? cc.usuarioId() : -1, k -> {
                PorCajero x = new PorCajero();
                x.setUsuarioId(cc.usuarioId());
                x.setUsuario(c.getUsuario() != null ? c.getUsuario() : "Sin usuario");
                x.setValor(BigDecimal.ZERO);
                return x;
            });
            pc.setCarritos(pc.getCarritos() + 1);
            pc.setValor(pc.getValor().add(nz(c.getTotal())));

            Set<Long> vistosEnCarrito = new HashSet<>();
            for (Item it : c.getDetalle()) {
                r.setProductos(r.getProductos() + 1);
                // Sin producto (línea libre) se agrupa por nombre con un id negativo estable.
                Long clave = it.getProductoId() != null ? it.getProductoId() : (long) -it.getNombre().hashCode();
                ProductoAbandonado pa = productos.computeIfAbsent(clave, k -> {
                    ProductoAbandonado x = new ProductoAbandonado();
                    x.setProductoId(it.getProductoId());
                    x.setNombre(it.getNombre());
                    x.setCantidad(BigDecimal.ZERO);
                    x.setValor(BigDecimal.ZERO);
                    return x;
                });
                pa.setCantidad(pa.getCantidad().add(nz(it.getCantidad())));
                pa.setValor(pa.getValor().add(nz(it.getSubtotal())));
                if (vistosEnCarrito.add(clave)) pa.setCarritos(pa.getCarritos() + 1);
            }
        }
        if (r.getCarritos() > 0) {
            r.setMinutosPromedio(BigDecimal.valueOf(segundos)
                    .divide(BigDecimal.valueOf(60L * r.getCarritos()), 1, RoundingMode.HALF_UP));
        }
        r.getTopProductos().addAll(productos.values().stream()
                .sorted(Comparator.comparing(ProductoAbandonado::getCarritos).reversed()
                        .thenComparing(ProductoAbandonado::getValor, Comparator.reverseOrder()))
                .limit(20).toList());
        r.getPorCajero().addAll(cajeros.values().stream()
                .sorted(Comparator.comparing(PorCajero::getCarritos).reversed()).toList());
        return r;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
