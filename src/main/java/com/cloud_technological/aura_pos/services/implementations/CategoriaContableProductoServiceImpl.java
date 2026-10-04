package com.cloud_technological.aura_pos.services.implementations;

import com.cloud_technological.aura_pos.utils.ClasificacionItem;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CategoriaContableProductoDto;
import com.cloud_technological.aura_pos.entity.CategoriaContableProductoEntity;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.CategoriaContableProductoJPARepository;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.CategoriaContableProductoService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoriaContableProductoServiceImpl implements CategoriaContableProductoService {

    /**
     * La categoría es la plantilla contable de una clasificación del catálogo
     * (V185): BIEN/INSUMO para mercancía y las demás con el mismo nombre de la
     * clasificación.
     */
    private static final List<String> TIPOS = List.of("BIEN", "SERVICIO", "INSUMO", "ACTIVO_FIJO",
            "INTANGIBLE", "GASTO", "DOTACION", "DIFERIDO");

    private final CategoriaContableProductoJPARepository repo;
    private final PlanCuentaJPARepository planRepo;
    private final CuentaPorDefectoResolver porDefecto;

    @Override
    public List<CategoriaContableProductoDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .map(e -> toDto(empresaId, e))
                .toList();
    }

    @Override
    @Transactional
    public CategoriaContableProductoDto crear(Integer empresaId, CategoriaContableProductoDto dto) {
        if (repo.findByEmpresaIdAndNombre(empresaId, dto.getNombre().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría contable llamada " + dto.getNombre());
        }
        CategoriaContableProductoEntity e = CategoriaContableProductoEntity.builder()
                .empresaId(empresaId)
                .nombre(dto.getNombre().trim())
                .tipo(normalizarTipo(dto.getTipo()))
                .activo(dto.getActivo() == null || dto.getActivo())
                .build();
        aplicarCuentas(empresaId, e, dto);
        return toDto(empresaId, repo.save(e));
    }

    @Override
    @Transactional
    public CategoriaContableProductoDto actualizar(Integer empresaId, Long id,
            CategoriaContableProductoDto dto) {
        CategoriaContableProductoEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría contable no encontrada"));
        e.setNombre(dto.getNombre().trim());
        e.setTipo(normalizarTipo(dto.getTipo()));
        if (dto.getActivo() != null) {
            e.setActivo(dto.getActivo());
        }
        aplicarCuentas(empresaId, e, dto);
        return toDto(empresaId, repo.save(e));
    }

    @Override
    @Transactional
    public CategoriaContableProductoDto copiar(Integer empresaId, Long origenId, String nombre) {
        CategoriaContableProductoEntity o = repo.findByIdAndEmpresaId(origenId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Categoría contable no encontrada"));
        if (nombre == null || nombre.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escriba el nombre de la categoría nueva");
        }
        if (repo.findByEmpresaIdAndNombre(empresaId, nombre.trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una categoría contable llamada " + nombre.trim());
        }
        CategoriaContableProductoEntity n = CategoriaContableProductoEntity.builder()
                .empresaId(empresaId)
                .nombre(nombre.trim())
                .tipo(o.getTipo())
                .cuentaIngresoId(o.getCuentaIngresoId())
                .cuentaInventarioId(o.getCuentaInventarioId())
                .cuentaCostoId(o.getCuentaCostoId())
                .cuentaDevolucionId(o.getCuentaDevolucionId())
                .cuentaDepreciacionId(o.getCuentaDepreciacionId())
                .cuentaGastoDepreciacionId(o.getCuentaGastoDepreciacionId())
                .vidaUtilMeses(o.getVidaUtilMeses())
                .mesesDiferido(o.getMesesDiferido())
                .impuestoId(o.getImpuestoId())
                .activo(true)
                .build();
        return toDto(empresaId, repo.save(n));
    }

    @Override
    @Transactional
    public void seedDefaults(Integer empresaId) {
        if (repo.findByEmpresaIdAndNombre(empresaId, "General").isEmpty()) {
            repo.save(CategoriaContableProductoEntity.builder()
                    .empresaId(empresaId)
                    .nombre("General")
                    .tipo("BIEN")
                    .cuentaIngresoId(idCuenta(empresaId, "4135"))
                    .cuentaInventarioId(idCuenta(empresaId, "1435"))
                    .cuentaCostoId(idCuenta(empresaId, "6135"))
                    .activo(true)
                    .build());
        }

        // Materias primas e insumos de receta: descargan su propio inventario
        // (1405) y no el de mercancías. Si el plan no tiene la 1405, cae a la
        // 1435 para no dejar la categoría sin cuenta.
        if (repo.findByEmpresaIdAndNombre(empresaId, "Insumos").isEmpty()) {
            Long inventario = idCuenta(empresaId, "1405");
            repo.save(CategoriaContableProductoEntity.builder()
                    .empresaId(empresaId)
                    .nombre("Insumos")
                    .tipo("INSUMO")
                    .cuentaIngresoId(idCuenta(empresaId, "4135"))
                    .cuentaInventarioId(inventario != null ? inventario : idCuenta(empresaId, "1435"))
                    .cuentaCostoId(idCuenta(empresaId, "6135"))
                    .activo(true)
                    .build());
        }
    }

    @Override
    public void validarCuentasProducto(Integer empresaId, Long categoriaContableId, Long cuentaIngresoId,
            Long cuentaCostoId, Long cuentaInventarioId, ClasificacionItem clasificacion) {
        if (categoriaContableId != null) {
            repo.findByIdAndEmpresaId(categoriaContableId, empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La categoría contable no existe o está inactiva."));
        }
        validar(empresaId, cuentaIngresoId, "de ingreso", "4");
        validar(empresaId, cuentaCostoId, "de costo", "5", "6", "7");
        // La cuenta de la compra va en la clase de su clasificación: inventario
        // (14) para la mercancía, 15 para un activo fijo, 5 para un gasto…
        ClasificacionItem c = clasificacion != null ? clasificacion : ClasificacionItem.PRODUCTO;
        validar(empresaId, cuentaInventarioId,
                c == ClasificacionItem.PRODUCTO ? "de inventario" : "de la compra", c.prefijosCompra());
    }

    /** Guardarraíles (ADR-006): cada cuenta en su clase PUC. */
    private void aplicarCuentas(Integer empresaId, CategoriaContableProductoEntity e,
            CategoriaContableProductoDto dto) {
        e.setCuentaIngresoId(validar(empresaId, dto.getCuentaIngresoId(), "de ingreso", "4"));
        e.setCuentaInventarioId(validar(empresaId, dto.getCuentaInventarioId(),
                "BIEN".equals(e.getTipo()) || "INSUMO".equals(e.getTipo()) ? "de inventario" : "de la compra",
                prefijosCompra(e.getTipo())));
        e.setCuentaCostoId(validar(empresaId, dto.getCuentaCostoId(), "de costo", "5", "6", "7"));
        e.setCuentaDevolucionId(validar(empresaId, dto.getCuentaDevolucionId(), "de devolución", "4"));

        // Activos e intangibles: depreciación acumulada y su gasto; se copian a
        // la ficha que crea la compra (V185).
        e.setCuentaDepreciacionId(validar(empresaId, dto.getCuentaDepreciacionId(),
                "de depreciación acumulada", "1592", "1597", "1598", "1698"));
        e.setCuentaGastoDepreciacionId(validar(empresaId, dto.getCuentaGastoDepreciacionId(),
                "de gasto por depreciación", "51", "52", "72", "73"));
        e.setVidaUtilMeses(positivoONull(dto.getVidaUtilMeses(), "La vida útil"));
        e.setMesesDiferido(positivoONull(dto.getMesesDiferido(), "Los meses del diferido"));
    }

    /** Clase del PUC de la cuenta de la compra según el tipo de la categoría. */
    private static String[] prefijosCompra(String tipo) {
        return switch (tipo != null ? tipo : "BIEN") {
            // Compatibilidad: antes de V185 la categoría admitía toda la clase 1.
            case "BIEN", "INSUMO" -> new String[] { "1" };
            default -> ClasificacionItem.de(tipo).prefijosCompra();
        };
    }

    private static Integer positivoONull(Integer valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (valor <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, campo + " debe ser mayor que cero.");
        }
        return valor;
    }

    private Long validar(Integer empresaId, Long cuentaId, String rol, String... prefijos) {
        if (cuentaId == null) {
            return null;
        }
        PlanCuentaEntity cuenta = planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La cuenta " + rol + " no existe en el plan de cuentas."));
        if (!Boolean.TRUE.equals(cuenta.getActiva()) || !Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + rol + " debe ser una auxiliar activa (de movimiento).");
        }
        for (String p : prefijos) {
            if (cuenta.getCodigo() != null && cuenta.getCodigo().startsWith(p)) {
                return cuentaId;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "La cuenta " + rol + " debe empezar por " + String.join("/", prefijos)
                        + "; la cuenta " + cuenta.getCodigo() + " no aplica.");
    }

    private String normalizarTipo(String tipo) {
        String t = tipo != null ? tipo.trim().toUpperCase() : "BIEN";
        if (!TIPOS.contains(t)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tipo inválido: use BIEN, SERVICIO, INSUMO, ACTIVO_FIJO, INTANGIBLE, GASTO, DOTACION o DIFERIDO.");
        }
        return t;
    }

    /** La cuenta por defecto: su auxiliar en el catálogo propio, o el código de siempre. */
    private Long idCuenta(Integer empresaId, String codigo) {
        return porDefecto.idCuenta(empresaId, codigo);
    }

    private String etiqueta(Integer empresaId, Long cuentaId) {
        if (cuentaId == null) {
            return null;
        }
        return planRepo.findByIdAndEmpresaId(cuentaId, empresaId)
                .map(c -> c.getCodigo() + " - " + c.getNombre()).orElse("#" + cuentaId);
    }

    private CategoriaContableProductoDto toDto(Integer empresaId, CategoriaContableProductoEntity e) {
        return CategoriaContableProductoDto.builder()
                .id(e.getId())
                .nombre(e.getNombre())
                .tipo(e.getTipo())
                .cuentaIngresoId(e.getCuentaIngresoId())
                .cuentaIngreso(etiqueta(empresaId, e.getCuentaIngresoId()))
                .cuentaInventarioId(e.getCuentaInventarioId())
                .cuentaInventario(etiqueta(empresaId, e.getCuentaInventarioId()))
                .cuentaCostoId(e.getCuentaCostoId())
                .cuentaCosto(etiqueta(empresaId, e.getCuentaCostoId()))
                .cuentaDevolucionId(e.getCuentaDevolucionId())
                .cuentaDevolucion(etiqueta(empresaId, e.getCuentaDevolucionId()))
                .cuentaDepreciacionId(e.getCuentaDepreciacionId())
                .cuentaDepreciacion(etiqueta(empresaId, e.getCuentaDepreciacionId()))
                .cuentaGastoDepreciacionId(e.getCuentaGastoDepreciacionId())
                .cuentaGastoDepreciacion(etiqueta(empresaId, e.getCuentaGastoDepreciacionId()))
                .vidaUtilMeses(e.getVidaUtilMeses())
                .mesesDiferido(e.getMesesDiferido())
                .activo(e.getActivo())
                .build();
    }
}
