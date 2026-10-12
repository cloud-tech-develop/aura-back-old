package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.cloud_technological.aura_pos.dto.nomina.documento.LineaProyeccionDto;
import com.cloud_technological.aura_pos.dto.nomina.documento.ProyeccionContableDto;
import com.cloud_technological.aura_pos.entity.AsientoDetalleEntity;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoJPARepository;
import com.cloud_technological.aura_pos.repositories.nomina_documentos.NominaDocumentoQueryRepository;
import com.cloud_technological.aura_pos.services.ContabilidadAutoService;
import com.cloud_technological.aura_pos.services.NominaDocumentoContabilidadService;
import com.cloud_technological.aura_pos.services.nomina.contabilidad.AsientoNominaBuilder;
import com.cloud_technological.aura_pos.utils.GlobalException;

/** Implementación de {@link NominaDocumentoContabilidadService}. */
@Service
public class NominaDocumentoContabilidadServiceImpl implements NominaDocumentoContabilidadService {

    private final NominaDocumentoJPARepository documentoRepository;
    private final NominaDocumentoQueryRepository queryRepository;
    private final ContabilidadAutoService contabilidad;

    public NominaDocumentoContabilidadServiceImpl(NominaDocumentoJPARepository documentoRepository,
            NominaDocumentoQueryRepository queryRepository,
            ContabilidadAutoService contabilidad) {
        this.documentoRepository = documentoRepository;
        this.queryRepository = queryRepository;
        this.contabilidad = contabilidad;
    }

    @Override
    public ProyeccionContableDto proyeccion(Long documentoId, Integer empresaId) {
        documentoRepository.findByIdAndEmpresaId(documentoId, empresaId)
                .orElseThrow(() -> new GlobalException(HttpStatus.NOT_FOUND, "Documento de nómina no encontrado"));

        ProyeccionContableDto r = new ProyeccionContableDto();
        Map<String, LineaProyeccionDto> porCuenta = new LinkedHashMap<>();
        Set<String> advertencias = new LinkedHashSet<>();

        for (Long filaId : queryRepository.filasLiquidadas(documentoId)) {
            try {
                AsientoNominaBuilder.Resultado asiento = contabilidad.proyectarNomina(filaId, empresaId);
                for (AsientoDetalleEntity l : asiento.lineas())
                    acumular(porCuenta, l);
                advertencias.addAll(asiento.avisos());
                r.setEmpleados(r.getEmpleados() + 1);
            } catch (ResponseStatusException e) {
                advertencias.add(e.getReason() != null ? e.getReason() : e.getMessage());
            } catch (RuntimeException e) {
                advertencias.add(e.getMessage());
            }
        }

        Map<Long, String[]> cuentas = queryRepository.cuentas(
                porCuenta.values().stream().map(LineaProyeccionDto::getCuentaId).distinct().toList());
        Map<Long, String> terceros = queryRepository.nombresTerceros(porCuenta.values().stream()
                .map(LineaProyeccionDto::getTerceroId).filter(Objects::nonNull).distinct().toList());
        List<LineaProyeccionDto> lineas = new ArrayList<>(porCuenta.values());
        for (LineaProyeccionDto l : lineas) {
            if (l.getTerceroId() != null) l.setTerceroNombre(terceros.get(l.getTerceroId()));
            String[] c = cuentas.get(l.getCuentaId());
            if (c != null) {
                l.setCuentaCodigo(c[0]);
                l.setCuentaNombre(c[1]);
            }
            r.setTotalDebito(r.getTotalDebito().add(l.getDebito()));
            r.setTotalCredito(r.getTotalCredito().add(l.getCredito()));
        }
        // Débitos primero; dentro de cada lado, por código de cuenta.
        lineas.sort((a, b) -> {
            int lado = Boolean.compare(a.getDebito().signum() == 0, b.getDebito().signum() == 0);
            return lado != 0 ? lado : String.valueOf(a.getCuentaCodigo()).compareTo(String.valueOf(b.getCuentaCodigo()));
        });
        r.setLineas(lineas);
        r.setAdvertencias(new ArrayList<>(advertencias));
        return r;
    }

    /** Suma la línea en su grupo (cuenta + tercero + descripción + lado). */
    private static void acumular(Map<String, LineaProyeccionDto> porCuenta, AsientoDetalleEntity l) {
        BigDecimal debito = l.getDebito() != null ? l.getDebito() : BigDecimal.ZERO;
        BigDecimal credito = l.getCredito() != null ? l.getCredito() : BigDecimal.ZERO;
        String clave = l.getCuentaId() + "|" + l.getTerceroId() + "|" + l.getDescripcion() + "|"
                + (debito.signum() != 0 ? "D" : "C");
        LineaProyeccionDto g = porCuenta.computeIfAbsent(clave, k -> {
            LineaProyeccionDto n = new LineaProyeccionDto();
            n.setCuentaId(l.getCuentaId());
            n.setDescripcion(l.getDescripcion());
            n.setTerceroId(l.getTerceroId());
            return n;
        });
        g.setDebito(g.getDebito().add(debito));
        g.setCredito(g.getCredito().add(credito));
    }
}
