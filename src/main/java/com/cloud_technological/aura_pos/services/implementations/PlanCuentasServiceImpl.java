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

    @Autowired
    private com.cloud_technological.aura_pos.services.ConfiguracionContableService configuracionContableService;

    @Autowired
    private com.cloud_technological.aura_pos.services.FormaPagoContableService formaPagoContableService;

    @Autowired
    private com.cloud_technological.aura_pos.services.CategoriaContableProductoService categoriaContableProductoService;

    @Autowired
    private com.cloud_technological.aura_pos.services.ImpuestoService impuestoService;

    @Autowired
    private com.cloud_technological.aura_pos.contabilidad.infrastructure.exogena.ExogenaService exogenaService;

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
        if (repo.findByEmpresaIdOrderByCodigoAsc(empresaId).isEmpty()) {
            seedCuentas(empresaId);
        }
        // Siembra/actualiza el mapeo concepto→cuenta por defecto (idempotente),
        // también para empresas que ya tenían PUC pero no configuración.
        configuracionContableService.seedDefaults(empresaId);
        formaPagoContableService.seedDefaults(empresaId);
        categoriaContableProductoService.seedDefaults(empresaId);
        impuestoService.seedDefaults(empresaId);
        exogenaService.seedDefaults(empresaId);
    }

    private void seedCuentas(Integer empresaId) {
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

        // Subcuentas comunes. IMPORTANTE: cada cuenta padre (nivel 2) debe
        // aparecer ANTES que sus hijas, porque el padreId se resuelve de forma
        // incremental sobre idsByCodigo a medida que se recorre el arreglo.
        Object[][] grupos = {
            // código, nombre, tipo, naturaleza, nivel, codigoPadre
            // ── Clase 1 · Activo ──────────────────────────────────────────────
            { "11", "Disponible",                       "ACTIVO",  "DEBITO",  2, "1" },
            { "1105", "Caja",                           "ACTIVO",  "DEBITO",  3, "11" },
            { "1110", "Bancos",                         "ACTIVO",  "DEBITO",  3, "11" },
            { "13", "Deudores",                         "ACTIVO",  "DEBITO",  2, "1" },
            { "1305", "Clientes",                       "ACTIVO",  "DEBITO",  3, "13" },
            { "1355", "Anticipo de Impuestos y Retenciones","ACTIVO","DEBITO",3, "13" },
            { "14", "Inventarios",                      "ACTIVO",  "DEBITO",  2, "1" },
            { "1435", "Mercancias no Fabricadas",       "ACTIVO",  "DEBITO",  3, "14" },
            { "15", "Propiedad Planta y Equipo",        "ACTIVO",  "DEBITO",  2, "1" },
            { "1592", "Depreciacion Acumulada",         "ACTIVO",  "CREDITO", 3, "15" },
            // ── Clase 2 · Pasivo ──────────────────────────────────────────────
            { "21", "Obligaciones Financieras",         "PASIVO",  "CREDITO", 2, "2" },
            { "2105", "Bancos Nacionales",              "PASIVO",  "CREDITO", 3, "21" },
            { "22", "Proveedores",                      "PASIVO",  "CREDITO", 2, "2" },
            { "2205", "Proveedores Nacionales",         "PASIVO",  "CREDITO", 3, "22" },
            { "23", "Cuentas por Pagar",                "PASIVO",  "CREDITO", 2, "2" },
            { "2360", "Dividendos o Participaciones por Pagar","PASIVO","CREDITO",3,"23" },
            { "2365", "Retencion en la Fuente",         "PASIVO",  "CREDITO", 3, "23" },
            { "2367", "Impuesto a las Ventas Retenido", "PASIVO",  "CREDITO", 3, "23" },
            { "2368", "Impuesto de Industria y Comercio Retenido","PASIVO","CREDITO",3,"23" },
            { "24", "Impuestos Gravamenes y Tasas",     "PASIVO",  "CREDITO", 2, "2" },
            { "2404", "Impuesto de Renta por Pagar",    "PASIVO",  "CREDITO", 3, "24" },
            { "2408", "IVA por Pagar",                  "PASIVO",  "CREDITO", 3, "24" },
            // E5: el IVA generado y el descontable van a subcuentas separadas
            // para que el reporte de IVA neto no se mezcle en 2408.
            { "240801", "IVA Generado",                 "PASIVO",  "CREDITO", 4, "2408" },
            { "240802", "IVA Descontable",              "PASIVO",  "CREDITO", 4, "2408" },
            { "25", "Obligaciones Laborales",           "PASIVO",  "CREDITO", 2, "2" },
            { "2505", "Salarios por Pagar",             "PASIVO",  "CREDITO", 3, "25" },
            // ── Clase 3 · Patrimonio ──────────────────────────────────────────
            { "31", "Capital Social",                   "PATRIMONIO","CREDITO",2, "3" },
            { "3105","Capital",                         "PATRIMONIO","CREDITO",3, "31" },
            // ── Cierre anual (E8) ─────────────────────────────────────────────
            { "33", "Reservas",                         "PATRIMONIO","CREDITO",2, "3" },
            { "3305","Reservas Obligatorias",           "PATRIMONIO","CREDITO",3, "33" },
            { "330505","Reserva Legal",                 "PATRIMONIO","CREDITO",4, "3305" },
            { "36", "Resultados del Ejercicio",         "PATRIMONIO","CREDITO",2, "3" },
            { "3605","Utilidad del Ejercicio",          "PATRIMONIO","CREDITO",3, "36" },
            { "37", "Resultados de Ejercicios Anteriores","PATRIMONIO","CREDITO",2, "3" },
            { "3705","Resultados de Ejercicios Anteriores","PATRIMONIO","CREDITO",3, "37" },
            // ── Devengo (E6) ──────────────────────────────────────────────────
            { "1330","Anticipos a Proveedores",         "ACTIVO",  "DEBITO",  3, "13" },
            { "1399","Provisión Cartera (deterioro)",   "ACTIVO",  "CREDITO", 3, "13" },
            { "1499","Provisión Inventarios",           "ACTIVO",  "CREDITO", 3, "14" },
            { "17",  "Diferidos",                       "ACTIVO",  "DEBITO",  2, "1" },
            { "1705","Gastos Pagados por Anticipado",   "ACTIVO",  "DEBITO",  3, "17" },
            { "28",  "Otros Pasivos",                   "PASIVO",  "CREDITO", 2, "2" },
            { "2805","Anticipos de Clientes",           "PASIVO",  "CREDITO", 3, "28" },
            // ── Clase 4 · Ingresos ────────────────────────────────────────────
            { "41", "Operacionales",                    "INGRESO", "CREDITO", 2, "4" },
            { "4135","Comercio al por Menor",           "INGRESO", "CREDITO", 3, "41" },
            { "42", "No Operacionales",                 "INGRESO", "CREDITO", 2, "4" },
            { "4210","Financieros",                     "INGRESO", "CREDITO", 3, "42" },
            // E9: intereses que abona el banco (ajuste de conciliación)
            { "421005","Intereses",                     "INGRESO", "CREDITO", 4, "4210" },
            { "4295","Ingresos Diversos",               "INGRESO", "CREDITO", 3, "42" },
            // ── Clase 5 · Gastos ──────────────────────────────────────────────
            { "51", "Gastos Operacionales Admon",       "GASTO",   "DEBITO",  2, "5" },
            { "5105","Gastos de Personal",              "GASTO",   "DEBITO",  3, "51" },
            { "5160","Depreciaciones",                  "GASTO",   "DEBITO",  3, "51" },
            { "5195","Otros Gastos",                    "GASTO",   "DEBITO",  3, "51" },
            { "5199","Provisiones y Deterioros",        "GASTO",   "DEBITO",  3, "51" },
            { "53", "Gastos No Operacionales",          "GASTO",   "DEBITO",  2, "5" },
            { "5305","Financieros",                     "GASTO",   "DEBITO",  3, "53" },
            // E9: cargos del banco que nacen del extracto (conciliación)
            { "530515","Comisiones",                    "GASTO",   "DEBITO",  4, "5305" },
            { "530595","Gravamen a los Movimientos Financieros","GASTO","DEBITO",4,"5305" },
            { "54", "Impuesto de Renta y Complementarios","GASTO", "DEBITO",  2, "5" },
            { "5405","Impuesto de Renta y Complementarios","GASTO","DEBITO",  3, "54" },
            // ── Clase 6 · Costos ──────────────────────────────────────────────
            { "61", "Costo de Ventas y Prest.",         "COSTO",   "DEBITO",  2, "6" },
            { "6135","Costo de Mercancias Vend.",       "COSTO",   "DEBITO",  3, "61" },
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
