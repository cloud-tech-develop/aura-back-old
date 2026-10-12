package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.retenciones.CreateTarifaRetencionDto;
import com.cloud_technological.aura_pos.dto.retenciones.RetencionesSugeridasDto;
import com.cloud_technological.aura_pos.dto.retenciones.TarifaRetencionDto;
import com.cloud_technological.aura_pos.entity.PlanCuentaEntity;
import com.cloud_technological.aura_pos.entity.TarifaRetencionEntity;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.repositories.contabilidad.PlanCuentaJPARepository;
import com.cloud_technological.aura_pos.repositories.retenciones.TarifaRetencionJPARepository;
import com.cloud_technological.aura_pos.repositories.retenciones.TarifaRetencionQueryRepository;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;
import com.cloud_technological.aura_pos.utils.PageableDto;
import java.util.ArrayList;

@Service
public class TarifaRetencionServiceImpl {

    @Autowired private TarifaRetencionJPARepository tarifaJPARepository;
    @Autowired private TarifaRetencionQueryRepository tarifaQueryRepository;
    @Autowired private TerceroJPARepository terceroJPARepository;
    @Autowired private PlanCuentaJPARepository planCuentaRepository;
    @Autowired private AcreedorImpuestoService acreedores;

    /** Grupo del PUC al que debe pertenecer la cuenta de cada tipo de retención. */
    private static final Map<String, String> GRUPO_CUENTA = Map.of(
            "RETEFUENTE", "2365",
            "RETEIVA", "2367",
            "RETEICA", "2368");

    // ── CRUD ────────────────────────────────────────────────────────────────────

    public PageImpl<TarifaRetencionDto> listar(PageableDto<Object> pageable, Integer empresaId) {
        return tarifaQueryRepository.listar(pageable, empresaId);
    }

    public List<TarifaRetencionDto> listarTodas(Integer empresaId) {
        List<TarifaRetencionDto> activas = tarifaQueryRepository.activas(empresaId);
        if (!activas.isEmpty()) return activas;
        // Empresa sin catálogo (creada sin PUC): se le siembra al primer uso
        // para que los formularios tengan conceptos que elegir.
        seedDefaults(empresaId);
        return tarifaQueryRepository.activas(empresaId);
    }

    @Transactional
    public TarifaRetencionDto crear(CreateTarifaRetencionDto dto, Integer empresaId) {
        if (tarifaJPARepository.existsByEmpresaIdAndTipoAndConcepto(empresaId, dto.getTipo(), dto.getConcepto()))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe una tarifa de " + dto.getTipo() + " con el concepto '" + dto.getConcepto() + "'");

        validarTipo(dto.getTipo());
        TarifaRetencionEntity entity = fromDto(dto, empresaId);
        entity.setCuentaContableId(validarCuenta(dto.getCuentaContableId(), dto.getTipo(), empresaId));
        entity.setTerceroAcreedorId(acreedores.validar(empresaId, dto.getTerceroAcreedorId()));
        return toDto(tarifaJPARepository.save(entity));
    }

    @Transactional
    public TarifaRetencionDto actualizar(Long id, CreateTarifaRetencionDto dto, Integer empresaId) {
        TarifaRetencionEntity entity = tarifaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tarifa no encontrada"));

        if (tarifaJPARepository.existsByEmpresaIdAndTipoAndConceptoAndIdNot(empresaId, dto.getTipo(), dto.getConcepto(), id))
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya existe otra tarifa de " + dto.getTipo() + " con ese concepto");

        validarTipo(dto.getTipo());
        entity.setTipo(dto.getTipo());
        entity.setCuentaContableId(validarCuenta(dto.getCuentaContableId(), dto.getTipo(), empresaId));
        entity.setTerceroAcreedorId(acreedores.validar(empresaId, dto.getTerceroAcreedorId()));
        entity.setConcepto(dto.getConcepto());
        entity.setCodigoConcepto(dto.getCodigoConcepto());
        entity.setTarifaNatural(dto.getTarifaNatural());
        entity.setTarifaJuridica(dto.getTarifaJuridica());
        entity.setBaseMinima(dto.getBaseMinima() != null ? dto.getBaseMinima() : BigDecimal.ZERO);
        entity.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        return toDto(tarifaJPARepository.save(entity));
    }

    /**
     * Da de alta un municipio para reteICA en un paso: crea su cuenta auxiliar
     * (236805xx "Rete ICA &lt;municipio&gt;") y copia las tarifas de reteICA que no
     * tienen municipio, ahora a nombre de su secretaría de hacienda y en esa
     * cuenta. Así la declaración de reteICA sale separada por municipio.
     */
    @Transactional
    public List<TarifaRetencionDto> agregarMunicipioReteica(Integer empresaId, Long terceroAcreedorId,
            String municipio, List<CreateTarifaRetencionDto> tarifas) {
        String nombre = municipio != null ? municipio.trim() : "";
        if (nombre.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el nombre del municipio.");
        if (terceroAcreedorId == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Elija la secretaría de hacienda del municipio (el tercero al que se le paga).");
        Long acreedor = acreedores.validar(empresaId, terceroAcreedorId);

        // Plantilla: las tarifas que mande el front, o las de reteICA sin municipio.
        List<CreateTarifaRetencionDto> base = tarifas != null && !tarifas.isEmpty() ? tarifas
                : tarifaQueryRepository.activas(empresaId).stream()
                        .filter(t -> "RETEICA".equals(t.getTipo()) && t.getTerceroAcreedorId() == null)
                        .map(t -> {
                            CreateTarifaRetencionDto d = new CreateTarifaRetencionDto();
                            d.setConcepto(t.getConcepto());
                            d.setCodigoConcepto(t.getCodigoConcepto());
                            d.setTarifaNatural(t.getTarifaNatural());
                            d.setTarifaJuridica(t.getTarifaJuridica());
                            d.setBaseMinima(t.getBaseMinima());
                            return d;
                        })
                        .toList();
        if (base.isEmpty())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "No hay tarifas de reteICA para copiar: escriba al menos una tarifa del municipio.");

        var sig = tarifaQueryRepository.siguienteAuxiliarReteica(empresaId);
        Object padre = sig.get("padre_id");
        int n = ((Number) sig.get("siguiente")).intValue();
        if (padre == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El plan de cuentas no tiene la subcuenta 236805 (ICA retenido): cárguela primero.");
        if (n > 99)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La subcuenta 236805 ya no tiene auxiliares libres.");
        PlanCuentaEntity cuenta = planCuentaRepository.save(PlanCuentaEntity.builder()
                .empresaId(empresaId)
                .codigo(String.format("236805%02d", n))
                .nombre(recortar("Rete ICA " + nombre, 200))
                .tipo("PASIVO")
                .naturaleza("CREDITO")
                .nivel((short) 5)
                .padreId(((Number) padre).longValue())
                .auxiliar(true)
                .esMedioPago(false)
                .activa(true)
                .build());

        List<TarifaRetencionDto> out = new ArrayList<>();
        for (CreateTarifaRetencionDto t : base) {
            String concepto = recortar((t.getConcepto() != null ? t.getConcepto() : "ReteICA") + " — " + nombre, 100);
            if (tarifaJPARepository.existsByEmpresaIdAndTipoAndConcepto(empresaId, "RETEICA", concepto)) continue;
            TarifaRetencionEntity e = new TarifaRetencionEntity();
            e.setEmpresaId(empresaId);
            e.setTipo("RETEICA");
            e.setConcepto(concepto);
            e.setCodigoConcepto(t.getCodigoConcepto());
            e.setTarifaNatural(t.getTarifaNatural() != null ? t.getTarifaNatural() : BigDecimal.ZERO);
            e.setTarifaJuridica(t.getTarifaJuridica() != null ? t.getTarifaJuridica() : e.getTarifaNatural());
            e.setBaseMinima(t.getBaseMinima() != null ? t.getBaseMinima() : BigDecimal.ZERO);
            e.setActivo(true);
            e.setCuentaContableId(cuenta.getId());
            e.setTerceroAcreedorId(acreedor);
            out.add(toDto(tarifaJPARepository.save(e)));
        }
        return out;
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    @Transactional
    public void eliminar(Long id, Integer empresaId) {
        TarifaRetencionEntity entity = tarifaJPARepository.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tarifa no encontrada"));
        entity.setActivo(false);
        tarifaJPARepository.save(entity);
    }

    /**
     * Catálogo inicial de tarifas (compras, servicios, honorarios, arriendos,
     * reteIVA, reteICA…) con la cuenta de su concepto. Idempotente: agrega las
     * que falten y completa la cuenta de las del catálogo que no la tengan.
     */
    @Transactional
    public void seedDefaults(Integer empresaId) {
        tarifaQueryRepository.sembrarCatalogo(empresaId);
        tarifaQueryRepository.completarCuentasCatalogo(empresaId);
        // Retefuente y reteIVA se le pagan a la DIAN; el reteICA, al municipio
        // que el contador elija en cada tarifa.
        acreedores.asegurarDian(empresaId);
    }

    // ── Sugeridas ────────────────────────────────────────────────────────────────

    /**
     * Devuelve los porcentajes de retención sugeridos para un tercero,
     * basado en su tipoPersona (NATURAL / JURIDICA) y la primera tarifa activa
     * de cada tipo (RETEFUENTE, RETEIVA, RETEICA).
     */
    public RetencionesSugeridasDto obtenerSugeridas(Long terceroId, Integer empresaId) {
        TerceroEntity tercero = terceroJPARepository.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Tercero no encontrado"));

        boolean esNatural = !"JURIDICA".equalsIgnoreCase(tercero.getTipoPersona());

        BigDecimal refPct = primeraTarifa("RETEFUENTE", empresaId, esNatural);
        BigDecimal rivPct = primeraTarifa("RETEIVA",    empresaId, esNatural);
        BigDecimal ricPct = primeraTarifa("RETEICA",    empresaId, esNatural);

        String refConcepto = primerConcepto("RETEFUENTE", empresaId);
        String rivConcepto = primerConcepto("RETEIVA",    empresaId);
        String ricConcepto = primerConcepto("RETEICA",    empresaId);

        return RetencionesSugeridasDto.builder()
                .terceroId(terceroId)
                .tipoPersona(tercero.getTipoPersona() != null ? tercero.getTipoPersona() : "NATURAL")
                .retefuentePct(refPct)
                .retefuenteConcepto(refConcepto)
                .reteivaPct(rivPct)
                .reteivaConcepto(rivConcepto)
                .reteicaPct(ricPct)
                .reteicaConcepto(ricConcepto)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private BigDecimal primeraTarifa(String tipo, Integer empresaId, boolean esNatural) {
        return tarifaJPARepository.findByEmpresaIdAndTipoAndActivoTrue(empresaId, tipo)
                .stream().findFirst()
                .map(t -> esNatural ? t.getTarifaNatural() : t.getTarifaJuridica())
                .orElse(BigDecimal.ZERO);
    }

    private String primerConcepto(String tipo, Integer empresaId) {
        return tarifaJPARepository.findByEmpresaIdAndTipoAndActivoTrue(empresaId, tipo)
                .stream().findFirst()
                .map(TarifaRetencionEntity::getConcepto)
                .orElse(null);
    }

    private void validarTipo(String tipo) {
        if (!GRUPO_CUENTA.containsKey(tipo)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Tipo de retención inválido: use RETEFUENTE, RETEIVA o RETEICA");
        }
    }

    /**
     * La cuenta de la tarifa es una auxiliar activa del grupo de su tipo: la
     * retefuente en la 2365, la reteIVA en la 2367 y la reteICA en la 2368.
     */
    private Long validarCuenta(Long cuentaId, String tipo, Integer empresaId) {
        if (cuentaId == null) return null;
        PlanCuentaEntity cuenta = planCuentaRepository.findByIdAndEmpresaId(cuentaId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.BAD_REQUEST, "La cuenta elegida no existe"));
        if (!Boolean.TRUE.equals(cuenta.getActiva()) || !Boolean.TRUE.equals(cuenta.getAuxiliar())) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta " + cuenta.getCodigo() + " debe ser una auxiliar activa");
        }
        String grupo = GRUPO_CUENTA.get(tipo);
        if (cuenta.getCodigo() == null || !cuenta.getCodigo().startsWith(grupo)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La cuenta de una tarifa de " + tipo + " debe ser del grupo " + grupo
                            + "; la cuenta " + cuenta.getCodigo() + " no lo es");
        }
        return cuentaId;
    }

    private TarifaRetencionEntity fromDto(CreateTarifaRetencionDto dto, Integer empresaId) {
        TarifaRetencionEntity e = new TarifaRetencionEntity();
        e.setEmpresaId(empresaId);
        e.setTipo(dto.getTipo());
        e.setConcepto(dto.getConcepto());
        e.setCodigoConcepto(dto.getCodigoConcepto());
        e.setTarifaNatural(dto.getTarifaNatural());
        e.setTarifaJuridica(dto.getTarifaJuridica());
        e.setBaseMinima(dto.getBaseMinima() != null ? dto.getBaseMinima() : BigDecimal.ZERO);
        e.setActivo(dto.getActivo() != null ? dto.getActivo() : true);
        return e;
    }

    private TarifaRetencionDto toDto(TarifaRetencionEntity e) {
        TarifaRetencionDto dto = new TarifaRetencionDto();
        dto.setId(e.getId());
        dto.setEmpresaId(e.getEmpresaId());
        dto.setTipo(e.getTipo());
        dto.setConcepto(e.getConcepto());
        dto.setCodigoConcepto(e.getCodigoConcepto());
        dto.setTarifaNatural(e.getTarifaNatural());
        dto.setTarifaJuridica(e.getTarifaJuridica());
        dto.setBaseMinima(e.getBaseMinima());
        dto.setActivo(e.getActivo());
        dto.setCuentaContableId(e.getCuentaContableId());
        dto.setTerceroAcreedorId(e.getTerceroAcreedorId());
        dto.setTerceroAcreedorNombre(acreedores.etiqueta(e.getEmpresaId(), e.getTerceroAcreedorId()));
        if (e.getCuentaContableId() != null) {
            planCuentaRepository.findByIdAndEmpresaId(e.getCuentaContableId(), e.getEmpresaId()).ifPresent(c -> {
                dto.setCuentaCodigo(c.getCodigo());
                dto.setCuentaNombre(c.getNombre());
            });
        }
        return dto;
    }
}
