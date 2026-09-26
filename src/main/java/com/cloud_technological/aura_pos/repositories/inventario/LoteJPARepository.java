package com.cloud_technological.aura_pos.repositories.inventario;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.http.HttpStatus;

import com.cloud_technological.aura_pos.entity.LoteEntity;
import com.cloud_technological.aura_pos.utils.GlobalException;

public interface LoteJPARepository extends JpaRepository<LoteEntity, Long> {
    Optional<LoteEntity> findByIdAndSucursalEmpresaId(Long id, Integer empresaId);
    Optional<LoteEntity> findByProductoIdAndBodegaIdAndCodigoLote(Long productoId, Long bodegaId, String codigoLote);

    /** El lote con ese código, sin importar mayúsculas ni espacios (índice uq_lote_codigo). */
    @Query("""
            SELECT l FROM LoteEntity l
             WHERE l.producto.id = :productoId AND l.bodega.id = :bodegaId
               AND UPPER(TRIM(l.codigoLote)) = UPPER(TRIM(:codigo))
             ORDER BY l.id
            """)
    List<LoteEntity> buscarPorCodigo(@Param("productoId") Long productoId,
            @Param("bodegaId") Long bodegaId, @Param("codigo") String codigo);

    /** Lotes con stock en orden de salida: primero el que vence antes; sin vencimiento, al final. */
    @Query("""
            SELECT l FROM LoteEntity l
             WHERE l.producto.id = :productoId AND l.bodega.id = :bodegaId
               AND (l.activo IS NULL OR l.activo = true)
               AND l.stockActual > 0
             ORDER BY l.fechaVencimiento ASC NULLS LAST, l.id ASC
            """)
    List<LoteEntity> enOrdenFefo(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    @Query("""
            SELECT COALESCE(SUM(l.stockActual), 0) FROM LoteEntity l
             WHERE l.producto.id = :productoId AND l.bodega.id = :bodegaId
               AND (l.activo IS NULL OR l.activo = true)
            """)
    BigDecimal stockEnLotes(@Param("productoId") Long productoId, @Param("bodegaId") Long bodegaId);

    /**
     * El lote del que sale una línea de un documento. Tiene que ser de la
     * empresa, del producto de la línea y de la bodega por donde sale, y
     * alcanzar la cantidad. Antes se buscaba solo por id: se podía descontar
     * el lote de otra empresa o de otro producto y dejarlo en negativo.
     */
    default LoteEntity buscarParaSalida(Long loteId, Integer empresaId, Long productoId,
            Long bodegaId, BigDecimal cantidadBase) {
        LoteEntity lote = findByIdAndSucursalEmpresaId(loteId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "Lote no encontrado"));
        if (!lote.getProducto().getId().equals(productoId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El lote " + lote.getCodigoLote() + " no es de " + lote.getProducto().getNombre());
        if (lote.getBodega() == null || !lote.getBodega().getId().equals(bodegaId))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El lote " + lote.getCodigoLote() + " no está en esta bodega");
        if (!Boolean.TRUE.equals(lote.getActivo()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El lote " + lote.getCodigoLote() + " está inactivo");
        BigDecimal stock = lote.getStockActual() != null ? lote.getStockActual() : BigDecimal.ZERO;
        if (cantidadBase != null && stock.compareTo(cantidadBase) < 0)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Stock insuficiente en el lote " + lote.getCodigoLote() + ". Disponible: "
                            + stock.stripTrailingZeros().toPlainString());
        return lote;
    }
}
