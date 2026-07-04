package com.cloud_technological.aura_pos.services;

import java.util.List;

import org.springframework.data.domain.PageImpl;

import com.cloud_technological.aura_pos.dto.cuentas_cobrar.AbonoCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarTableDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CuentaCobrarResumenDto;
import com.cloud_technological.aura_pos.dto.cuentas_cobrar.CreateCuentaCobrarDto;
import com.cloud_technological.aura_pos.utils.PageableDto;

public interface CuentaCobrarService {
    PageImpl<CuentaCobrarTableDto> listar(PageableDto<Object> pageable, Integer empresaId);
    PageImpl<CuentaCobrarTableDto> listarConFiltros(PageableDto<Object> pageable, Integer empresaId, 
            String fechaDesde, String fechaHasta, Long clienteId, String estado);
    CuentaCobrarDto obtenerPorId(Long id, Integer empresaId);

    /** Aplica un cruce (abono) desde un comprobante, sin generar contabilidad propia. */
    void aplicarCruce(Long cuentaId, java.math.BigDecimal monto, Integer empresaId, Integer usuarioId, String referencia);
    CuentaCobrarDto crear(CreateCuentaCobrarDto dto, Integer empresaId, Long usuarioId);
    CuentaCobrarDto actualizar(Long id, CreateCuentaCobrarDto dto, Integer empresaId);
    
    // Abonos
    AbonoCobrarDto registrarAbono(Long cuentaId, AbonoCobrarDto dto, Integer empresaId, Long usuarioId);
    List<AbonoCobrarDto> listarAbonos(Long cuentaId, Integer empresaId);
    void eliminarAbono(Long cuentaId, Long abonoId, Integer empresaId);

    // Anula la cuenta por cobrar asociada a una venta (al anular la venta).
    // Lanza error si la cuenta ya tiene abonos registrados.
    void anularPorVenta(Long ventaId, Integer empresaId);
    
    // Resumen
    CuentaCobrarResumenDto obtenerResumen(Integer empresaId, String fechaDesde, String fechaHasta, Long clienteId, String estado);
    List<CuentaCobrarTableDto> obtenerVencidas(Integer empresaId);
}
