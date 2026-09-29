package com.cloud_technological.aura_pos.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cloud_technological.aura_pos.dto.ventas.CreateVentaDto;
import com.cloud_technological.aura_pos.entity.PedidoVendedorDetalleEntity;
import com.cloud_technological.aura_pos.entity.PedidoVendedorEntity;
import com.cloud_technological.aura_pos.entity.ProductoEntity;
import com.cloud_technological.aura_pos.entity.SucursalEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.UsuarioEntity;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.pedidos_vendedor.PedidoVendedorJPARepository;
import com.cloud_technological.aura_pos.services.implementations.PedidoVendedorServiceImpl;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.MediosPago;

/**
 * El despacho es el hecho económico del pedido: antes de despachar no hay venta,
 * el inventario no se toca y no hay nada que contabilizar. Al despachar sí.
 */
@ExtendWith(MockitoExtension.class)
class PedidoVendedorDespachoTest {

    private static final Integer EMPRESA = 1;

    @Mock private PedidoVendedorJPARepository pedidoJPARepository;
    @Mock private VentaService ventaService;

    @InjectMocks private PedidoVendedorServiceImpl service;

    private PedidoVendedorEntity pedidoDeUnaUnidad(boolean conCliente) {
        SucursalEntity sucursal = new SucursalEntity();
        sucursal.setId(3);

        UsuarioEntity vendedor = new UsuarioEntity();
        vendedor.setId(42);

        ProductoEntity producto = new ProductoEntity();
        producto.setId(900L);

        PedidoVendedorDetalleEntity detalle = new PedidoVendedorDetalleEntity();
        detalle.setProducto(producto);
        detalle.setCantidad(new BigDecimal("2"));
        detalle.setPrecioUnitario(new BigDecimal("10000"));
        detalle.setDescuentoValor(new BigDecimal("500"));
        detalle.setImpuestoValor(new BigDecimal("3705"));

        PedidoVendedorEntity pedido = new PedidoVendedorEntity();
        pedido.setId(77L);
        pedido.setNumeroPedido("PV-000077");
        pedido.setEstado("CREADA");
        pedido.setSucursal(sucursal);
        pedido.setVendedor(vendedor);
        pedido.setTotal(new BigDecimal("23205"));
        pedido.setDetalles(List.of(detalle));

        if (conCliente) {
            TerceroEntity cliente = new TerceroEntity();
            cliente.setId(555L);
            pedido.setCliente(cliente);
        }
        return pedido;
    }

    @Test
    void despacharGeneraLaVentaACreditoConLasLineasDelPedido() {
        PedidoVendedorEntity pedido = pedidoDeUnaUnidad(true);
        when(pedidoJPARepository.findByIdAndEmpresaId(77L, EMPRESA)).thenReturn(Optional.of(pedido));

        service.despachar(77L, EMPRESA);

        ArgumentCaptor<CreateVentaDto> captor = ArgumentCaptor.forClass(CreateVentaDto.class);
        verify(ventaService).crear(captor.capture(), anyInt(), anyLong());
        CreateVentaDto enviado = captor.getValue();

        // Se enlaza al pedido existente para no crear un pedido espejo duplicado.
        assertEquals(77L, enviado.getPedidoVendedorId());
        assertEquals(555L, enviado.getClienteId());
        assertEquals(3, enviado.getSucursalId());

        // Sin turno: la venta nace a crédito, no hay efectivo que exija caja.
        assertNull(enviado.getTurnoCajaId());
        assertEquals(1, enviado.getPagos().size());
        assertEquals(MediosPago.CREDITO, enviado.getPagos().get(0).getMetodoPago());
        assertEquals(new BigDecimal("23205"), enviado.getPagos().get(0).getMonto());

        assertEquals(1, enviado.getDetalles().size());
        assertEquals(900L, enviado.getDetalles().get(0).getProductoId());
        assertEquals(new BigDecimal("2"), enviado.getDetalles().get(0).getCantidad());
        assertEquals(new BigDecimal("500"), enviado.getDetalles().get(0).getDescuentoValor());

        assertEquals("DESPACHADA", pedido.getEstado());
    }

    @Test
    void despacharSinClienteSeRechazaPorqueLaDeudaNecesitaDeudor() {
        PedidoVendedorEntity pedido = pedidoDeUnaUnidad(false);
        when(pedidoJPARepository.findByIdAndEmpresaId(77L, EMPRESA)).thenReturn(Optional.of(pedido));

        assertThrows(GlobalException.class, () -> service.despachar(77L, EMPRESA));

        verify(ventaService, never()).crear(any(), anyInt(), anyLong());
        assertEquals("CREADA", pedido.getEstado());
    }

    @Test
    void pedidoQueYaTieneVentaNoLaVuelveAGenerar() {
        // Caso del pedido espejo creado desde el POS: la venta ya existe y ya
        // movió inventario; despacharlo no puede volver a descontarlo.
        PedidoVendedorEntity pedido = pedidoDeUnaUnidad(true);
        VentaEntity ventaExistente = new VentaEntity();
        ventaExistente.setId(1234L);
        pedido.setVenta(ventaExistente);
        when(pedidoJPARepository.findByIdAndEmpresaId(77L, EMPRESA)).thenReturn(Optional.of(pedido));

        service.despachar(77L, EMPRESA);

        verify(ventaService, never()).crear(any(), anyInt(), anyLong());
        assertEquals("DESPACHADA", pedido.getEstado());
    }
}
