package com.cloud_technological.aura_pos.services;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.entity.InventarioEntity;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioJPARepository;
import com.cloud_technological.aura_pos.repositories.inventario.InventarioStockQueryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Único punto para tomar el saldo de un producto en una bodega antes de
 * moverlo.
 *
 * <p>Todos los documentos hacían leer → sumar → guardar sin bloqueo: dos cajas
 * vendiendo el mismo producto leían 10, cada una guardaba 9 y una salida se
 * perdía (y la validación de stock negativo se saltaba). Ahora la fila queda
 * bloqueada ({@code SELECT … FOR UPDATE}) hasta que termina la transacción del
 * documento: la segunda caja espera y lee el saldo ya descontado.
 *
 * <p>Uso: en lugar de {@code inventarioRepo.findByBodegaIdAndProductoId(b, p)}
 * llamar {@code inventarioStock.bloquear(b, p)}; devuelve lo mismo.
 */
@Service
public class InventarioStockService {

    private final InventarioStockQueryRepository queryRepo;
    private final InventarioJPARepository inventarioRepo;

    @PersistenceContext
    private EntityManager em;

    public InventarioStockService(InventarioStockQueryRepository queryRepo, InventarioJPARepository inventarioRepo) {
        this.queryRepo = queryRepo;
        this.inventarioRepo = inventarioRepo;
    }

    /**
     * Bloquea y devuelve el saldo (bodega, producto) con su valor vigente en la
     * base; vacío si no existe (el llamador lo crea como antes).
     */
    public Optional<InventarioEntity> bloquear(Long bodegaId, Long productoId) {
        boolean enTransaccion = em.isJoinedToTransaction();
        // Lo que este mismo documento ya movió y sigue en memoria se escribe
        // primero: el refresh de abajo no puede descartarlo.
        if (enTransaccion) em.flush();

        Long id = queryRepo.bloquear(bodegaId, productoId);
        if (id == null) return Optional.empty();

        Optional<InventarioEntity> inv = inventarioRepo.findById(id);
        // Si la entidad ya estaba cargada en esta transacción (leída antes del
        // bloqueo), su saldo puede ser viejo: se recarga el que dejó quien
        // tenía el bloqueo.
        inv.ifPresent(e -> {
            if (enTransaccion && em.contains(e)) em.refresh(e);
        });
        return inv;
    }
}
