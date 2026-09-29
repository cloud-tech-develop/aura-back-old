package com.cloud_technological.aura_pos.services.implementations;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.cloud_technological.aura_pos.dto.cartera.RetencionRecaudoDto;
import com.cloud_technological.aura_pos.entity.AbonoCobrarEntity;
import com.cloud_technological.aura_pos.repositories.cuentas_cobrar.AbonoCobrarJPARepository;
import com.cloud_technological.aura_pos.utils.GlobalException;

import lombok.RequiredArgsConstructor;

/**
 * Retenciones que un cliente practica al pagar (renta, IVA, ICA).
 *
 * <p>Cada una se guarda como un abono vinculado al abono de efectivo o banco,
 * con el tipo como medio de pago. El saldo de la cartera, que en todo el
 * sistema es total − SUM(abonos), la cuenta como pagada sin tocar esas
 * consultas; el arqueo solo suma efectivo y no la ve. El asiento lo genera el
 * abono principal: DB 1355xx por cada retención, CR clientes por el total.
 */
@Service
@RequiredArgsConstructor
public class RetencionRecaudoService {

    public static final String RETEFUENTE = "RETEFUENTE";
    public static final String RETEIVA = "RETEIVA";
    public static final String RETEICA = "RETEICA";
    private static final Set<String> TIPOS = Set.of(RETEFUENTE, RETEIVA, RETEICA);

    private final AbonoCobrarJPARepository abonoRepo;

    /** true si el medio de pago de un abono es una retención y no dinero. */
    public static boolean esRetencion(String metodoPago) {
        return metodoPago != null && TIPOS.contains(metodoPago.trim().toUpperCase());
    }

    /**
     * Normaliza y valida: tipo conocido, valor positivo, un solo valor por
     * tipo. Las filas en cero se descartan (el front manda las tres casillas).
     */
    public List<RetencionRecaudoDto> validar(List<RetencionRecaudoDto> retenciones) {
        List<RetencionRecaudoDto> out = new ArrayList<>();
        if (retenciones == null) return out;
        Set<String> vistos = new HashSet<>();
        for (RetencionRecaudoDto r : retenciones) {
            if (r == null || r.getValor() == null || r.getValor().signum() == 0) continue;
            String tipo = r.getTipo() != null ? r.getTipo().trim().toUpperCase() : "";
            if (!TIPOS.contains(tipo)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "Tipo de retención desconocido: " + r.getTipo());
            }
            if (r.getValor().signum() < 0) {
                throw new GlobalException(HttpStatus.BAD_REQUEST, "Una retención no puede ser negativa");
            }
            if (!vistos.add(tipo)) {
                throw new GlobalException(HttpStatus.BAD_REQUEST,
                        "La retención " + tipo + " aparece dos veces en el mismo pago");
            }
            out.add(new RetencionRecaudoDto(tipo, r.getValor().setScale(2, RoundingMode.HALF_UP),
                    r.getBase() != null ? r.getBase().setScale(2, RoundingMode.HALF_UP) : null));
        }
        return out;
    }

    public static BigDecimal total(List<RetencionRecaudoDto> retenciones) {
        return retenciones.stream().map(RetencionRecaudoDto::getValor)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Guarda las retenciones como abonos vinculados al principal. */
    public void registrar(AbonoCobrarEntity principal, List<RetencionRecaudoDto> retenciones) {
        for (RetencionRecaudoDto r : retenciones) {
            abonoRepo.save(AbonoCobrarEntity.builder()
                    .cuentaCobrar(principal.getCuentaCobrar())
                    .usuario(principal.getUsuario())
                    .monto(r.getValor())
                    .metodoPago(r.getTipo())
                    .baseRetencion(r.getBase())
                    .referencia(recortar("Retención " + r.getTipo().substring(4).toLowerCase()
                            + (principal.getReferencia() != null ? " · " + principal.getReferencia() : "")))
                    .fechaPago(principal.getFechaPago())
                    .reciboCajaId(principal.getReciboCajaId())
                    .abonoOrigenId(principal.getId())
                    .cajaOtroDia(false)
                    .build());
        }
    }

    public List<AbonoCobrarEntity> deAbono(Long abonoPrincipalId) {
        return abonoRepo.findByAbonoOrigenId(abonoPrincipalId);
    }

    /**
     * Borra las retenciones de un abono principal y devuelve cuánto sumaban,
     * para devolverlo al saldo de la cuenta junto con el efectivo.
     */
    public BigDecimal eliminarDe(Long abonoPrincipalId) {
        List<AbonoCobrarEntity> hijas = abonoRepo.findByAbonoOrigenId(abonoPrincipalId);
        BigDecimal total = hijas.stream().map(AbonoCobrarEntity::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        abonoRepo.deleteAll(hijas);
        return total;
    }

    private static String recortar(String s) {
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
