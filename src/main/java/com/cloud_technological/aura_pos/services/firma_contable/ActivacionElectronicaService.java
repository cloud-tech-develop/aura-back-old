package com.cloud_technological.aura_pos.services.firma_contable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloud_technological.aura_pos.dto.firma_contable.SolicitarActivacionDto;
import com.cloud_technological.aura_pos.entity.EmpresaEntity;
import com.cloud_technological.aura_pos.entity.SolicitudActivacionElectronicaEntity;
import com.cloud_technological.aura_pos.repositories.empresas.EmpresaJPARepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.ActivacionElectronicaQueryRepository;
import com.cloud_technological.aura_pos.repositories.firma_contable.ActivacionElectronicaQueryRepository.SolicitudActivacionFila;
import com.cloud_technological.aura_pos.repositories.firma_contable.SolicitudActivacionElectronicaJPARepository;
import com.cloud_technological.aura_pos.services.permisos.BitacoraService;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Documentos electrónicos de un cliente gestionado (docs/PLAN_FIRMA_CONTABLE.md §10).
 * Factus le entrega las credenciales a Aura, así que la firma no las pone: pide la
 * activación, la solicitud llega a la campana de la plataforma y queda ATENDIDA sola
 * cuando la plataforma guarda las credenciales con la facturación electrónica activa
 * (o la rechaza con motivo). Resolución y rangos los pone la firma en "Mi perfil" del
 * cliente.
 */
@Service
@RequiredArgsConstructor
public class ActivacionElectronicaService {

    private static final String CLAVE_BITACORA = "caja.usuarios";

    private final GestionadoService gestionados;
    private final EmpresaJPARepository empresaRepo;
    private final SolicitudActivacionElectronicaJPARepository solicitudRepo;
    private final ActivacionElectronicaQueryRepository query;
    private final BitacoraService bitacora;

    /** Lo que se puede pedir: la cuenta de Factus de la empresa cubre los tres. */
    public enum ServicioElectronico {
        FACTURA_VENTA("Factura electrónica de venta"),
        NOMINA_ELECTRONICA("Nómina electrónica"),
        DOCUMENTO_SOPORTE("Documento soporte");

        private final String nombre;

        ServicioElectronico(String nombre) {
            this.nombre = nombre;
        }

        public String getNombre() {
            return nombre;
        }
    }

    /**
     * Estado electrónico del gestionado para la firma.
     *
     * @param activa       la plataforma ya activó la facturación electrónica (credenciales puestas)
     * @param resolucion   la firma ya digitó la resolución DIAN en "Mi perfil"
     * @param rangoFactura la firma ya digitó el rango de facturación de Factus
     * @param rangoNomina  la firma ya digitó el rango de nómina electrónica
     * @param ultima       la última solicitud (pendiente o respondida), o null
     */
    public record EstadoElectronica(boolean activa, boolean resolucion, boolean rangoFactura, boolean rangoNomina,
            SolicitudActivacionFila ultima) {
    }

    /** Estado electrónico del gestionado y su última solicitud. */
    public EstadoElectronica estado(Integer firmaId, Integer clienteId) {
        gestionados.exigirGestionado(firmaId, clienteId);
        EmpresaEntity c = cliente(clienteId);
        return new EstadoElectronica(c.isFacturaElectronica(), lleno(c.getResolucionNumero()),
                c.getFactusNumberingRangeId() != null, lleno(c.getFactusNominaNumberingRangeId()),
                query.ultimaDelCliente(clienteId));
    }

    /**
     * La firma pide la activación. Una sola pendiente por cliente; se puede pedir aunque
     * ya esté activa (por ejemplo, para sumar la nómina electrónica).
     */
    @Transactional
    public EstadoElectronica solicitar(Integer firmaId, Integer clienteId, SolicitarActivacionDto dto, Integer actorId) {
        gestionados.exigirGestionado(firmaId, clienteId);
        if (query.pendienteDelCliente(clienteId) != null)
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Ya hay una solicitud pendiente para este cliente. Aura la está revisando");
        List<ServicioElectronico> servicios = parsear(dto.getServicios());
        SolicitudActivacionElectronicaEntity s = new SolicitudActivacionElectronicaEntity();
        s.setClienteEmpresaId(clienteId);
        s.setFirmaEmpresaId(firmaId);
        s.setServicios(String.join(",", servicios.stream().map(Enum::name).toList()));
        s.setObservacion(limpiar(dto.getObservacion()));
        s.setSolicitadoPor(actorId);
        solicitudRepo.save(s);
        String nombres = String.join(", ", servicios.stream().map(ServicioElectronico::getNombre).toList());
        bitacora.registrarEnEmpresa(clienteId, CLAVE_BITACORA, "SOLICITUD_ELECTRONICA", "empresa", clienteId,
                "La firma pidió activar: " + nombres, null, null);
        return estado(firmaId, clienteId);
    }

    /** La firma cancela su solicitud pendiente. */
    @Transactional
    public EstadoElectronica cancelar(Integer firmaId, Integer clienteId, Integer actorId) {
        gestionados.exigirGestionado(firmaId, clienteId);
        Long id = query.pendienteDelCliente(clienteId);
        if (id == null) throw new GlobalException(HttpStatus.BAD_REQUEST, "No hay una solicitud pendiente");
        responder(solicitudRepo.findById(id).orElseThrow(), SolicitudActivacionElectronicaEntity.CANCELADA, null,
                actorId);
        return estado(firmaId, clienteId);
    }

    // ── Plataforma ──────────────────────────────────────────────────────────

    /** Pendientes de todas las firmas, la más antigua primero. */
    public List<SolicitudActivacionFila> pendientes() {
        return query.pendientes();
    }

    /** Cuántas esperan respuesta (campana del panel de plataforma). */
    public int contarPendientes() {
        return query.contarPendientes();
    }

    /** La plataforma rechaza la solicitud; el motivo le llega a la firma. */
    @Transactional
    public void rechazar(Long solicitudId, String motivo, Integer actorId) {
        if (limpiar(motivo) == null)
            throw new GlobalException(HttpStatus.BAD_REQUEST, "Escriba el motivo del rechazo para la firma");
        SolicitudActivacionElectronicaEntity s = pendiente(solicitudId);
        responder(s, SolicitudActivacionElectronicaEntity.RECHAZADA, motivo, actorId);
        bitacora.registrarEnEmpresa(s.getClienteEmpresaId(), CLAVE_BITACORA, "SOLICITUD_ELECTRONICA", "empresa",
                s.getClienteEmpresaId(), "Aura rechazó la activación electrónica: " + motivo.trim(), null, null);
    }

    /**
     * La plataforma la marca atendida a mano. Exige que la empresa ya tenga la
     * facturación electrónica activa: si no, la firma creería que ya puede facturar.
     */
    @Transactional
    public void atender(Long solicitudId, Integer actorId) {
        SolicitudActivacionElectronicaEntity s = pendiente(solicitudId);
        if (!cliente(s.getClienteEmpresaId()).isFacturaElectronica())
            throw new GlobalException(HttpStatus.BAD_REQUEST,
                    "Primero active la facturación electrónica y guarde las credenciales de Factus de la empresa");
        responder(s, SolicitudActivacionElectronicaEntity.ATENDIDA, null, actorId);
    }

    /**
     * Al guardar la empresa en la plataforma: si quedó con la facturación electrónica
     * activa y las credenciales completas, su solicitud pendiente queda atendida.
     */
    @Transactional
    public void atenderSiQuedoActiva(EmpresaEntity empresa, Integer actorId) {
        if (!empresa.isFacturaElectronica() || !credencialesCompletas(empresa)) return;
        Long id = query.pendienteDelCliente(empresa.getId());
        if (id == null) return;
        responder(solicitudRepo.findById(id).orElseThrow(), SolicitudActivacionElectronicaEntity.ATENDIDA, null,
                actorId);
        bitacora.registrarEnEmpresa(empresa.getId(), CLAVE_BITACORA, "SOLICITUD_ELECTRONICA", "empresa",
                empresa.getId(), "Aura activó los documentos electrónicos", null, null);
    }

    /** Respondidas a la firma en los últimos días (campana de la firma). */
    public List<SolicitudActivacionFila> respondidasALaFirma(Integer firmaId, int dias) {
        return query.respondidasALaFirma(firmaId, dias);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private void responder(SolicitudActivacionElectronicaEntity s, String estado, String motivo, Integer actorId) {
        s.setEstado(estado);
        s.setMotivo(limpiar(motivo));
        s.setRespondidoPor(actorId);
        s.setRespondidoAt(LocalDateTime.now());
        solicitudRepo.save(s);
    }

    private SolicitudActivacionElectronicaEntity pendiente(Long solicitudId) {
        SolicitudActivacionElectronicaEntity s = solicitudRepo.findById(solicitudId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));
        if (!SolicitudActivacionElectronicaEntity.PENDIENTE.equals(s.getEstado()))
            throw new GlobalException(HttpStatus.BAD_REQUEST, "La solicitud ya fue respondida");
        return s;
    }

    private EmpresaEntity cliente(Integer clienteId) {
        return empresaRepo.findById(clienteId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Empresa no encontrada"));
    }

    private static boolean credencialesCompletas(EmpresaEntity e) {
        return lleno(e.getFactusClientId()) && lleno(e.getFactusClientSecret())
                && lleno(e.getFactusUsername()) && lleno(e.getFactusPassword());
    }

    private static List<ServicioElectronico> parsear(List<String> codigos) {
        Set<ServicioElectronico> out = new LinkedHashSet<>();
        for (String c : codigos != null ? codigos : List.<String>of()) {
            if (c == null || c.isBlank()) continue;
            try {
                out.add(ServicioElectronico.valueOf(c.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Documento electrónico no válido: " + c);
            }
        }
        if (out.isEmpty()) throw new GlobalException(HttpStatus.BAD_REQUEST, "Elija al menos un documento electrónico");
        return new ArrayList<>(out);
    }

    private static boolean lleno(String v) {
        return v != null && !v.isBlank();
    }

    private static String limpiar(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
