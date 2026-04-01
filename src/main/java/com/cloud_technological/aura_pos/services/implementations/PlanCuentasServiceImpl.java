package com.cloud_technological.aura_pos.services.implementations;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.contabilidad.CreatePlanCuentaDto;
import com.cloud_technological.aura_pos.dto.contabilidad.PlanCuentaDto;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.services.PlanCuentasService;

@Service
public class PlanCuentasServiceImpl implements PlanCuentasService {

    @Autowired
    private PlanCuentaJPARepository repo;

    @Override
    public List<PlanCuentaDto> listar(Integer empresaId) {
        return repo.findByEmpresaIdOrderByCodigoAsc(empresaId)
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PlanCuentaDto crear(Integer empresaId, CreatePlanCuentaDto dto) {
        if (repo.existsByEmpresaIdAndCodigo(empresaId, dto.getCodigo().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una cuenta con el código " + dto.getCodigo());
        }
        PlanCuentaEntity e = PlanCuentaEntity.builder()
                .empresaId(empresaId)
                .codigo(dto.getCodigo().trim())
                .nombre(dto.getNombre().trim())
                .tipo(dto.getTipo())
                .naturaleza(dto.getNaturaleza())
                .nivel(dto.getNivel())
                .padreId(dto.getPadreId())
                .auxiliar(dto.getAuxiliar() != null && dto.getAuxiliar())
                .codigoDian(dto.getCodigoDian())
                .activa(true)
                .build();
        return toDto(repo.save(e));
    }

    @Override
    @Transactional
    public PlanCuentaDto actualizar(Long id, Integer empresaId, CreatePlanCuentaDto dto) {
        PlanCuentaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        e.setNombre(dto.getNombre().trim());
        e.setTipo(dto.getTipo());
        e.setNaturaleza(dto.getNaturaleza());
        e.setNivel(dto.getNivel());
        e.setPadreId(dto.getPadreId());
        if (dto.getAuxiliar() != null) e.setAuxiliar(dto.getAuxiliar());
        e.setCodigoDian(dto.getCodigoDian());
        return toDto(repo.save(e));
    }

    @Override
    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        PlanCuentaEntity e = repo.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        e.setActiva(false);
        repo.save(e);
    }

    /** Siembra el PUC básico colombiano de 9 clases para una empresa nueva */
    @Override
    @Transactional
    public void seedPUC(Integer empresaId) {
        if (!repo.findByEmpresaIdOrderByCodigoAsc(empresaId).isEmpty()) return;

        // Clase → nombre, tipo, naturaleza
        Object[][] clases = {
            { "1", "Activo",                         "ACTIVO",     "DEBITO"  },
            { "2", "Pasivo",                         "PASIVO",     "CREDITO" },
            { "3", "Patrimonio",                     "PATRIMONIO", "CREDITO" },
            { "4", "Ingresos",                       "INGRESO",    "CREDITO" },
            { "5", "Gastos",                         "GASTO",      "DEBITO"  },
            { "6", "Costo de Ventas",                "COSTO",      "DEBITO"  },
            { "7", "Costos de Producción",           "COSTO",      "DEBITO"  },
            { "8", "Cuentas de Orden Deudoras",      "ORDEN",      "DEBITO"  },
            { "9", "Cuentas de Orden Acreedoras",    "ORDEN",      "CREDITO" },
        };

        // Subcuentas comunes
        Object[][] grupos = {
            // código, nombre, tipo, naturaleza, nivel, codigoPadre
            { "11", "Disponible",                "ACTIVO",  "DEBITO",  2, "1" },
            { "1105", "Caja",                    "ACTIVO",  "DEBITO",  3, "11" },
            { "1110", "Bancos",                  "ACTIVO",  "DEBITO",  3, "11" },
            { "13", "Deudores",                  "ACTIVO",  "DEBITO",  2, "1" },
            { "1305", "Clientes",                "ACTIVO",  "DEBITO",  3, "13" },
            { "14", "Inventarios",               "ACTIVO",  "DEBITO",  2, "1" },
            { "1435", "Mercancias no Fabricadas","ACTIVO",  "DEBITO",  3, "14" },
            { "22", "Proveedores",               "PASIVO",  "CREDITO", 2, "2" },
            { "2205", "Proveedores Nacionales",  "PASIVO",  "CREDITO", 3, "22" },
            { "23", "Cuentas por Pagar",         "PASIVO",  "CREDITO", 2, "2" },
            { "24", "Impuestos por Pagar",       "PASIVO",  "CREDITO", 2, "2" },
            { "2408", "IVA por Pagar",           "PASIVO",  "CREDITO", 3, "24" },
            { "31", "Capital Social",            "PATRIMONIO","CREDITO",2, "3" },
            { "3605","Utilidad del Ejercicio",   "PATRIMONIO","CREDITO",3, "36" },
            { "36", "Resultados del Ejercicio",  "PATRIMONIO","CREDITO",2, "3" },
            { "41", "Operacionales",             "INGRESO", "CREDITO", 2, "4" },
            { "4135","Comercio al por Menor",    "INGRESO", "CREDITO", 3, "41" },
            { "51", "Gastos Operacionales Admón","GASTO",   "DEBITO",  2, "5" },
            { "5105","Gastos de Personal",       "GASTO",   "DEBITO",  3, "51" },
            { "5195","Otros Gastos",             "GASTO",   "DEBITO",  3, "51" },
            { "61", "Costo de Ventas y Prest.",  "COSTO",   "DEBITO",  2, "6" },
            { "6135","Costo de Mercancias Vend.","COSTO",   "DEBITO",  3, "61" },
        };

        // Guardar clases principales
        java.util.Map<String, Long> idsByCodigo = new java.util.HashMap<>();
        for (Object[] c : clases) {
            PlanCuentaEntity e = PlanCuentaEntity.builder()
                    .empresaId(empresaId)
                    .codigo((String) c[0])
                    .nombre((String) c[1])
                    .tipo((String) c[2])
                    .naturaleza((String) c[3])
                    .nivel((short) 1)
                    .activa(true)
                    .auxiliar(false)
                    .build();
            PlanCuentaEntity saved = repo.save(e);
            idsByCodigo.put(saved.getCodigo(), saved.getId());
        }

        // Guardar subcuentas
        for (Object[] g : grupos) {
            String codigo = (String) g[0];
            String codigoPadre = (String) g[5];
            Long padreId = idsByCodigo.get(codigoPadre);
            PlanCuentaEntity e = PlanCuentaEntity.builder()
                    .empresaId(empresaId)
                    .codigo(codigo)
                    .nombre((String) g[1])
                    .tipo((String) g[2])
                    .naturaleza((String) g[3])
                    .nivel(((Integer) g[4]).shortValue())
                    .padreId(padreId)
                    .activa(true)
                    .auxiliar(((Integer) g[4]) >= 3)
                    .build();
            PlanCuentaEntity saved = repo.save(e);
            idsByCodigo.put(codigo, saved.getId());
        }
    }

    private PlanCuentaDto toDto(PlanCuentaEntity e) {
        PlanCuentaDto dto = new PlanCuentaDto();
        dto.setId(e.getId());
        dto.setCodigo(e.getCodigo());
        dto.setNombre(e.getNombre());
        dto.setTipo(e.getTipo());
        dto.setNaturaleza(e.getNaturaleza());
        dto.setNivel(e.getNivel());
        dto.setPadreId(e.getPadreId());
        dto.setActiva(e.getActiva());
        dto.setAuxiliar(e.getAuxiliar());
        dto.setCodigoDian(e.getCodigoDian());
        return dto;
    }
}
