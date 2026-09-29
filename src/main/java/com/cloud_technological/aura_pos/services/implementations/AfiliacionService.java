package com.cloud_technological.aura_pos.services.implementations;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.AfiliacionDto;
import com.cloud_technological.aura_pos.dto.nomina.afiliacion.AfiliacionDtos.EntidadDto;
import com.cloud_technological.aura_pos.entity.ContratoAfiliacionEntity;
import com.cloud_technological.aura_pos.entity.ContratoLaboralEntity;
import com.cloud_technological.aura_pos.entity.EntidadSeguridadSocialEntity;
import com.cloud_technological.aura_pos.entity.EntidadSeguridadSocialEntity.Tipo;
import com.cloud_technological.aura_pos.entity.TerceroEntity;
import com.cloud_technological.aura_pos.entity.TerceroRolEntity.Rol;
import com.cloud_technological.aura_pos.repositories.nomina.AfiliacionJPARepositories.ContratoAfiliacionRepo;
import com.cloud_technological.aura_pos.repositories.nomina.AfiliacionJPARepositories.EntidadSeguridadSocialRepo;
import com.cloud_technological.aura_pos.repositories.terceros.TerceroJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.extern.slf4j.Slf4j;

/**
 * Afiliaciones a seguridad social (Fase 5.5).
 *
 * <p><b>Sin esto PILA no es construible.</b> PILA es, en esencia, un reporte de
 * cuánto le corresponde a cada entidad por cada trabajador: sin saber a qué EPS,
 * fondo y caja está afiliado cada quien, no hay nada que reportar.
 *
 * <h2>Las validaciones son el punto</h2>
 * Se valida <b>al guardar la afiliación</b>, no al generar la planilla. Si no,
 * el error aparece a fin de mes con 200 empleados y el operador rechaza el
 * archivo con un mensaje que no dice cuál falló.
 */
@Slf4j
@Service
public class AfiliacionService {

    private static final List<String> TIPOS_OBLIGATORIOS = List.of(Tipo.EPS, Tipo.AFP, Tipo.CCF, Tipo.ARL);

    private final ContratoAfiliacionRepo afiliacionRepo;
    private final EntidadSeguridadSocialRepo entidadRepo;
    private final TerceroJPARepository terceroRepo;
    private final TerceroRolService terceroRolService;

    public AfiliacionService(ContratoAfiliacionRepo afiliacionRepo,
                             EntidadSeguridadSocialRepo entidadRepo,
                             TerceroJPARepository terceroRepo,
                             TerceroRolService terceroRolService) {
        this.afiliacionRepo = afiliacionRepo;
        this.entidadRepo = entidadRepo;
        this.terceroRepo = terceroRepo;
        this.terceroRolService = terceroRolService;
    }

    // ── Catálogo nacional ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<EntidadSeguridadSocialEntity> catalogoDe(String tipo) {
        return entidadRepo.findByTipoAndActivoTrueOrderByNombre(tipo);
    }

    /** Catálogo de un tipo, ya mapeado para el front. */
    @Transactional(readOnly = true)
    public List<EntidadDto> catalogoDtoDe(String tipo) {
        return catalogoDe(tipo).stream().map(e -> {
            EntidadDto d = new EntidadDto();
            d.setId(e.getId());
            d.setTipo(e.getTipo());
            d.setCodigoOficial(e.getCodigoOficial());
            d.setNit(e.getNit());
            d.setNombre(e.getNombre());
            return d;
        }).toList();
    }

    /**
     * Entidades de un tipo, como <b>terceros con rol</b> (V120).
     *
     * <p>Este es el flujo vigente: las EPS/AFP/CCF/ARL se crean en Terceros con
     * su rol y su código UGPP. El {@code id} que devuelve es el del tercero, y es
     * lo que espera {@link #afiliar}.
     */
    @Transactional(readOnly = true)
    public List<EntidadDto> entidadesDe(String tipo, Integer empresaId) {
        return terceroRepo.findByRolAndEmpresa(tipo, empresaId).stream().map(t -> {
            EntidadDto d = new EntidadDto();
            d.setId(t.getId());
            d.setTipo(tipo);
            d.setCodigoOficial(t.getCodigoSeguridadSocial());
            d.setNit(t.getNumeroDocumento());
            d.setNombre(nombreDe(t));
            return d;
        }).toList();
    }

    private String nombreDe(TerceroEntity t) {
        if (t.getRazonSocial() != null && !t.getRazonSocial().isBlank()) return t.getRazonSocial();
        return java.util.stream.Stream.of(t.getNombres(), t.getApellidos())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
    }

    /**
     * Afiliaciones de un contrato, mapeadas dentro de la transacción.
     *
     * <p>El mapeo va aquí y no en el controller para no chocar con el lazy
     * loading de {@code tercero} y su entidad de catálogo.
     */
    @Transactional(readOnly = true)
    public List<AfiliacionDto> listarAfiliaciones(Long contratoId) {
        return afiliacionRepo.findByContratoId(contratoId).stream()
                .map(this::aDto)
                .toList();
    }

    private AfiliacionDto aDto(ContratoAfiliacionEntity a) {
        AfiliacionDto d = new AfiliacionDto();
        d.setId(a.getId());
        d.setTipo(a.getTipo());
        d.setFechaDesde(a.getFechaDesde());
        d.setFechaHasta(a.getFechaHasta());
        d.setVigente(a.getFechaHasta() == null);
        d.setCodigoOficial(a.codigoOficial());
        TerceroEntity t = a.getTercero();
        if (t != null) {
            d.setEntidadId(t.getId());
            d.setEntidadNombre(t.getRazonSocial());
            d.setEntidadNit(t.getNumeroDocumento());
        }
        return d;
    }

    /**
     * Crea o recupera el tercero de una entidad de seguridad social.
     *
     * <p>A la entidad se le paga: tiene que existir como tercero en la empresa
     * para que tesorería le gire, el asiento tenga su NIT y exógena la reporte.
     * Un catálogo aislado obligaría a duplicarla igual el día del pago.
     */
    @Transactional
    public TerceroEntity terceroDeEntidad(Long entidadId, Integer empresaId) {
        EntidadSeguridadSocialEntity entidad = entidadRepo.findById(entidadId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "Entidad de seguridad social no encontrada"));

        List<TerceroEntity> existentes = terceroRepo.findByDocumento(empresaId, "NIT", entidad.getNit());
        if (existentes.size() > 1) {
            throw new GlobalException(HttpStatus.CONFLICT,
                    "Hay " + existentes.size() + " terceros con el NIT " + entidad.getNit()
                    + ". Resuelva el duplicado antes de afiliar.");
        }

        TerceroEntity t;
        if (existentes.size() == 1) {
            t = existentes.get(0);
        } else {
            t = new TerceroEntity();
            var empresa = new com.cloud_technological.aura_pos.entity.EmpresaEntity();
            empresa.setId(empresaId);
            t.setEmpresa(empresa);
            t.setTipoDocumento("NIT");
            t.setNumeroDocumento(entidad.getNit());
            t.setRazonSocial(entidad.getNombre());
            t.setTipoPersona("JURIDICA");
            t.setActivo(Boolean.TRUE);
            t.setEsCliente(Boolean.FALSE);
            t.setEsProveedor(Boolean.TRUE);   // se le paga: es acreedor
            t.setEsEmpleado(Boolean.FALSE);
            t.setEsBanco(Boolean.FALSE);
        }

        // El enlace al catálogo: de aquí sale el código oficial para PILA.
        t.setEntidadSeguridadSocial(entidad);
        t = terceroRepo.save(t);

        terceroRolService.sincronizarDesdeBooleanos(t);
        terceroRolService.agregarRol(t.getId(), rolDe(entidad.getTipo()));
        return t;
    }

    private String rolDe(String tipoEntidad) {
        return switch (tipoEntidad) {
            case Tipo.EPS -> Rol.EPS;
            case Tipo.AFP -> Rol.AFP;
            case Tipo.CCF -> Rol.CCF;
            case Tipo.ARL -> Rol.ARL;
            default -> throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Tipo de entidad desconocido: " + tipoEntidad);
        };
    }

    // ── Afiliaciones ────────────────────────────────────────────────────────

    /**
     * Afilia un contrato a una entidad.
     *
     * <p>Si ya había una afiliación vigente del mismo tipo, la cierra: eso es
     * un <b>traslado</b>, y PILA lo detecta comparando las fechas. Por eso no se
     * sobrescribe la fila — se cierra y se abre otra.
     */
    /**
     * Afilia (o traslada) un contrato a una entidad, que es un <b>tercero con
     * rol</b> (V120).
     *
     * @param terceroId el tercero EPS/AFP/CCF/ARL elegido del selector
     * @param tipo      EPS | AFP | CCF | ARL — el tercero debe tener ese rol
     */
    @Transactional
    public ContratoAfiliacionEntity afiliar(ContratoLaboralEntity contrato, Long terceroId,
                                            Integer empresaId, String tipo, LocalDate desde) {
        TerceroEntity tercero = terceroRepo.findByIdAndEmpresaId(terceroId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND,
                        "La entidad (tercero) no existe"));

        if (!terceroRolService.tieneRol(terceroId, tipo)) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "El tercero seleccionado no tiene el rol " + tipo
                    + ". Márcalo como " + tipo + " en Terceros.");
        }
        if (tercero.getCodigoSeguridadSocial() == null || tercero.getCodigoSeguridadSocial().isBlank()) {
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "La entidad no tiene código UGPP: PILA lo exige. Complétalo en Terceros.");
        }

        afiliacionRepo.findVigente(contrato.getId(), tipo).ifPresent(vigente -> {
            if (!desde.isAfter(vigente.getFechaDesde())) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Ya hay afiliación " + tipo + " vigente desde " + vigente.getFechaDesde()
                        + ". El traslado debe ser posterior.");
            }
            // Traslado: se cierra la anterior. PILA lo derivará de estas fechas.
            vigente.setFechaHasta(desde.minusDays(1));
            afiliacionRepo.save(vigente);
        });

        ContratoAfiliacionEntity a = new ContratoAfiliacionEntity();
        a.setContrato(contrato);
        a.setTercero(tercero);
        a.setTipo(tipo);
        a.setFechaDesde(desde);
        return afiliacionRepo.save(a);
    }

    @Transactional(readOnly = true)
    public ContratoAfiliacionEntity vigenteEnFecha(Long contratoId, String tipo, LocalDate fecha) {
        return afiliacionRepo.findEnFecha(contratoId, tipo, fecha).orElse(null);
    }

    /**
     * ¿Hubo traslado de un tipo dentro del período?
     *
     * <p>Alimenta las banderas {@code tde}/{@code tae} (EPS) y {@code tdp}/{@code tap}
     * (pensión) de PILA.
     */
    @Transactional(readOnly = true)
    public boolean huboTrasladoEn(Long contratoId, String tipo, LocalDate desde, LocalDate hasta) {
        return afiliacionRepo.findCambiosEnPeriodo(contratoId, desde, hasta).stream()
                .anyMatch(a -> tipo.equals(a.getTipo()) && a.getFechaDesde().isAfter(desde));
    }

    // ── Validación ──────────────────────────────────────────────────────────

    /**
     * Verifica que un contrato esté listo para PILA.
     *
     * <p>Se llama al liquidar, no al generar la planilla: es la diferencia entre
     * un error accionable y un archivo rechazado a fin de mes.
     *
     * @return lista de problemas. Vacía = listo.
     */
    @Transactional(readOnly = true)
    public List<String> validarParaPila(ContratoLaboralEntity contrato, LocalDate fecha) {
        List<String> problemas = new ArrayList<>();

        if (contrato.getTipoCotizante() == null) {
            problemas.add("Falta el tipo de cotizante (código UGPP)");
        }

        for (String tipo : TIPOS_OBLIGATORIOS) {
            ContratoAfiliacionEntity a = vigenteEnFecha(contrato.getId(), tipo, fecha);
            if (a == null) {
                problemas.add("Sin afiliación " + tipo + " vigente en " + fecha);
                continue;
            }
            // El caso silencioso: hay afiliación, pero el tercero no está
            // enlazado al catálogo nacional → PILA no resuelve el código y el
            // archivo sale incompleto.
            if (a.codigoOficial() == null) {
                problemas.add("La entidad " + tipo + " ("
                        + (a.getTercero() != null ? a.getTercero().getRazonSocial() : "?")
                        + ") no está enlazada al catálogo nacional: sin código oficial para PILA");
            }
        }

        if (contrato.getNivelRiesgoArl() == null && contrato.getCentroTrabajoId() == null) {
            problemas.add("Sin nivel de riesgo ARL ni centro de trabajo");
        }

        return problemas;
    }
}
