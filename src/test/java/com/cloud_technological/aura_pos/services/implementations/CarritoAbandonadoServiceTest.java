package com.cloud_technological.aura_pos.services.implementations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Carrito;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Item;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Registrar;
import com.cloud_technological.aura_pos.dto.carrito.CarritoAbandonadoDtos.Reporte;
import com.cloud_technological.aura_pos.entity.CarritoAbandonadoEntity;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoJPARepository;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoQueryRepository;
import com.cloud_technological.aura_pos.repositories.carrito.CarritoAbandonadoQueryRepository.CarritoConCajero;

@ExtendWith(MockitoExtension.class)
class CarritoAbandonadoServiceTest {

    @Mock private CarritoAbandonadoJPARepository repo;
    @Mock private CarritoAbandonadoQueryRepository queryRepo;
    private CarritoAbandonadoService service;

    @BeforeEach
    void setUp() {
        service = new CarritoAbandonadoService(repo, queryRepo);
    }

    private static Item item(Long productoId, String nombre, String cant, String subtotal) {
        Item it = new Item();
        it.setProductoId(productoId);
        it.setNombre(nombre);
        it.setCantidad(new BigDecimal(cant));
        it.setPrecio(new BigDecimal(subtotal).divide(new BigDecimal(cant)));
        it.setSubtotal(new BigDecimal(subtotal));
        return it;
    }

    private static CarritoConCajero carrito(long id, int usuario, int segundos, Item... items) {
        Carrito c = new Carrito();
        c.setId(id);
        c.setUsuario("cajero" + usuario);
        c.setTotal(List.of(items).stream().map(Item::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        c.getDetalle().addAll(List.of(items));
        return new CarritoConCajero(c, usuario, segundos);
    }

    @Test
    void registraDuracionTotalYProductos() {
        Registrar req = new Registrar();
        req.setMotivo("vaciado");
        req.setIniciadoAt(LocalDateTime.of(2026, 9, 27, 10, 0, 0));
        req.setVaciadoAt(LocalDateTime.of(2026, 9, 27, 10, 7, 30));
        req.setItems(List.of(item(1L, "Arroz", "2", "5000"), item(2L, "Aceite", "1", "12000")));

        service.registrar(1, 5, req);

        ArgumentCaptor<CarritoAbandonadoEntity> cap = ArgumentCaptor.forClass(CarritoAbandonadoEntity.class);
        verify(repo).save(cap.capture());
        assertEquals(450, cap.getValue().getDuracionSegundos());
        assertEquals(0, new BigDecimal("17000").compareTo(cap.getValue().getTotal()));
        assertEquals(2, cap.getValue().getItems());
        assertEquals("VACIADO", cap.getValue().getMotivo());
    }

    @Test
    void carritoVacioNoSeRegistra() {
        Registrar req = new Registrar();
        req.setMotivo("PESTANA_CERRADA");
        service.registrar(1, 5, req);
        verify(repo, never()).save(any());
    }

    @Test
    void reporteSoloCuentaLosQueDuraronElMinimoYOrdenaLosProductos() {
        LocalDate hoy = LocalDate.of(2026, 9, 27);
        when(queryRepo.carritos(1, hoy, hoy, null)).thenReturn(List.of(
                carrito(1, 7, 6 * 60, item(1L, "Arroz", "2", "5000"), item(2L, "Aceite", "1", "12000")),
                carrito(2, 7, 10 * 60, item(1L, "Arroz", "1", "2500")),
                carrito(3, 8, 90, item(3L, "Pan", "1", "1000"))));   // 1,5 min: no cuenta

        Reporte r = service.reporte(1, hoy, hoy, 5, null);

        assertEquals(2, r.getCarritos());
        assertEquals(1, r.getDescartadosPorTiempo());
        assertEquals(0, new BigDecimal("19500").compareTo(r.getValor()));
        assertEquals(0, new BigDecimal("8.0").compareTo(r.getMinutosPromedio()));
        // Arroz estuvo en los dos carritos: va primero
        assertEquals("Arroz", r.getTopProductos().get(0).getNombre());
        assertEquals(2, r.getTopProductos().get(0).getCarritos());
        assertEquals(1, r.getPorCajero().size());
    }
}
