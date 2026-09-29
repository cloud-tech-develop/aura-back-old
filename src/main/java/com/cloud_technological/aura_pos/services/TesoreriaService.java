package com.cloud_technological.aura_pos.services;

import java.time.LocalDate;
import java.util.List;

import com.cloud_technological.aura_pos.dto.tesoreria.ConciliacionResumenDto;
import com.cloud_technological.aura_pos.dto.tesoreria.CreateMovimientoDto;
import com.cloud_technological.aura_pos.dto.tesoreria.TesoreriaMovimientoDto;

public interface TesoreriaService {
    List<TesoreriaMovimientoDto> listarEgresos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    List<TesoreriaMovimientoDto> listarRecaudos(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    TesoreriaMovimientoDto crearEgreso(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto);
    TesoreriaMovimientoDto crearRecaudo(Integer empresaId, Integer usuarioId, CreateMovimientoDto dto);

    /**
     * Registra en la cuenta bancaria el movimiento que produjo un documento
     * (compra, gasto, abono, venta): ajusta el saldo <b>y</b> deja el registro
     * en el extracto interno.
     *
     * <p>Existe porque los documentos hacían solo lo primero: bajaban
     * `saldo_actual` con un `subtract` suelto y nunca creaban el
     * `tesoreria_movimiento`. El saldo de la cuenta cambiaba sin que apareciera
     * nada que lo explicara, y la conciliación bancaria no veía esos pagos.
     *
     * <p>No publica evento de contabilización: el documento que lo origina ya
     * genera su propio asiento con la línea del banco, y contabilizarlo aquí
     * duplicaría el movimiento contable.
     *
     * <p>Los datos van completos a propósito: un movimiento sin beneficiario ni
     * categoría obliga a abrir el documento de origen para saber a quién se le
     * pagó, y deja los reportes por categoría cojos.
     */
    void registrarMovimientoDeDocumento(Integer empresaId, Integer usuarioId, MovimientoDocumento mov);

    /**
     * Movimiento bancario producido por un documento.
     *
     * @param egreso       true si el dinero sale de la cuenta; false si entra
     * @param beneficiario a quién se le pagó o de quién se recibió
     * @param categoria    para agrupar en los reportes de tesorería
     */
    record MovimientoDocumento(
            Long cuentaBancariaId,
            boolean egreso,
            java.math.BigDecimal monto,
            String concepto,
            String beneficiario,
            String referencia,
            String categoria) {
    }
    void anular(Long id, Integer empresaId);
    List<TesoreriaMovimientoDto> listarParaConciliacion(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
    void toggleConciliado(Long id, Integer empresaId);
    void conciliarLote(List<Long> ids, Integer empresaId);
    ConciliacionResumenDto getResumen(Integer empresaId, Long cuentaId, LocalDate desde, LocalDate hasta);
}
