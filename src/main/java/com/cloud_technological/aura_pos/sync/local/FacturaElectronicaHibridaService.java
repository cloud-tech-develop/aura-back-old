package com.cloud_technological.aura_pos.sync.local;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.cloud_technological.aura_pos.dto.factus.FacturaElectronicaResponseDto;
import com.cloud_technological.aura_pos.entity.VentaEntity;
import com.cloud_technological.aura_pos.repositories.ventas.VentaJPARepository;
import com.cloud_technological.aura_pos.services.implementations.VentaFacturaService;
import com.cloud_technological.aura_pos.sync.SyncProperties;
import com.cloud_technological.aura_pos.sync.TiposComando;
import com.cloud_technological.aura_pos.utils.GlobalException;

/**
 * Punto único para pedir la factura electrónica de una venta.
 *
 * <ul>
 * <li><b>Nube</b>: la envía a Factus como siempre.</li>
 * <li><b>Nodo local</b>: nunca llama a Factus. La venta queda PENDIENTE_SYNC y
 * se encola FE_EMITIR; si hay internet se sube de una vez y la respuesta trae el
 * CUFE. Sin internet, la factura sale cuando vuelva la conexión.</li>
 * </ul>
 */
@Service
public class FacturaElectronicaHibridaService {

    public static final String PENDIENTE_SYNC = "PENDIENTE_SYNC";

    private final SyncProperties props;
    private final VentaFacturaService ventaFacturaService;
    private final SyncOutboxService outbox;
    private final SincronizadorLocal sincronizador;
    private final VentaJPARepository ventas;
    private final TransactionTemplate tx;

    public FacturaElectronicaHibridaService(SyncProperties props, VentaFacturaService ventaFacturaService,
            SyncOutboxService outbox, SincronizadorLocal sincronizador, VentaJPARepository ventas,
            PlatformTransactionManager transactionManager) {
        this.props = props;
        this.ventaFacturaService = ventaFacturaService;
        this.outbox = outbox;
        this.sincronizador = sincronizador;
        this.ventas = ventas;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /**
     * Genera (nube) o solicita (nodo) la factura electrónica de la venta.
     *
     * @param ventaId   venta
     * @param empresaId empresa
     * @param usuarioId quien la pide
     * @return datos de la factura; en el nodo sin conexión, estado PENDIENTE_SYNC
     */
    public FacturaElectronicaResponseDto generar(Long ventaId, Integer empresaId, Long usuarioId) {
        if (!props.esNodoLocal()) return ventaFacturaService.generarFacturaElectronica(ventaId, empresaId);

        // 1. Marcar y encolar, confirmado antes de intentar subir.
        tx.executeWithoutResult(s -> {
            VentaEntity v = ventas.findByIdAndEmpresaId(ventaId, empresaId)
                    .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Venta no encontrada"));
            if (!v.getEmpresa().isFacturaElectronica())
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Esta empresa no tiene habilitada la facturación electrónica");
            if ("EMITIDA".equals(v.getEstadoDian()))
                throw new GlobalException(HttpStatus.CONFLICT,
                        "Esta venta ya tiene factura electrónica emitida: " + v.getCufe());
            // Ya pedida: no se encola otra vez, solo se reintenta subir.
            if (PENDIENTE_SYNC.equals(v.getEstadoDian())) return;
            v.setEstadoDian(PENDIENTE_SYNC);
            ventas.save(v);
            outbox.registrar(TiposComando.FE_EMITIR, ventaId, empresaId, usuarioId, Map.of());
        });

        // 2. Con internet, la nube la emite ahora y devuelve el CUFE.
        sincronizador.empujarAhora();

        // 3. Lo que haya quedado.
        VentaEntity v = tx.execute(s -> ventas.findByIdAndEmpresaId(ventaId, empresaId).orElseThrow());
        FacturaElectronicaResponseDto r = new FacturaElectronicaResponseDto();
        r.setVentaId(ventaId);
        r.setEstadoDian(v.getEstadoDian());
        r.setCufe(v.getCufe());
        r.setQr(v.getQrData());
        r.setPdfUrl(v.getFactusUrl());
        r.setFacturaNumero(v.getFactusNumero());
        return r;
    }
}
