package com.cloud_technological.aura_pos.services.implementations;

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

    private static final List<String> TIPOS = List.of("BIEN", "SERVICIO", "INSUMO", "ACTIVO_FIJO");

    private final CategoriaContableProductoJPARepository repo;
    private final PlanCuentaJPARepository planRepo;

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
            Long cuentaCostoId, Long cuentaInventarioId) {
        if (categoriaContableId != null) {
            repo.findByIdAndEmpresaId(categoriaContableId, empresaId)
                    .filter(c -> Boolean.TRUE.equals(c.getActivo()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La categoría contable no existe o está inactiva."));
        }
        validar(empresaId, cuentaIngresoId, "de ingreso", "4");
        validar(empresaId, cuentaCostoId, "de costo", "5", "6", "7");
        // En el producto se exige inventario (14): un override es una excepción
        // puntual y no hay razón para mandarlo a otra clase del activo.
        validar(empresaId, cuentaInventarioId, "de inventario", "14");
    }

    /** Guardarraíles (ADR-006): cada cuenta en su clase PUC. */
    private void aplicarCuentas(Integer empresaId, CategoriaContableProductoEntity e,
            CategoriaContableProductoDto dto) {
        e.setCuentaIngresoId(validar(empresaId, dto.getCuentaIngresoId(), "de ingreso", "4"));
        e.setCuentaInventarioId(validar(empresaId, dto.getCuentaInventarioId(), "de inventario", "1"));
        e.setCuentaCostoId(validar(empresaId, dto.getCuentaCostoId(), "de costo", "5", "6", "7"));
        e.setCuentaDevolucionId(validar(empresaId, dto.getCuentaDevolucionId(), "de devolución", "4"));
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
                    "Tipo inválido: use BIEN, SERVICIO, INSUMO o ACTIVO_FIJO.");
        }
        return t;
    }

    private Long idCuenta(Integer empresaId, String codigo) {
        return planRepo.findByEmpresaIdAndCodigo(empresaId, codigo)
                .map(PlanCuentaEntity::getId).orElse(null);
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
                .activo(e.getActivo())
                .build();
    }
}
