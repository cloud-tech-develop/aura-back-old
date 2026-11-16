package com.cloud_technological.aura_pos.services.nube;

import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Espacio;
import com.cloud_technological.aura_pos.repositories.nube.NubeQueryRepository.Uso;

/**
 * Cuenta de la cuota (la usan la pantalla de documentos y la campana, para que
 * digan lo mismo). Lo de la papelera cuenta: se libera al vaciarla.
 */
final class NubeCuota {

    static final long BYTES_MB = 1024L * 1024L;

    private NubeCuota() {
    }

    static long cuotaBytes(Espacio espacio) {
        return espacio.cuotaMb() * BYTES_MB;
    }

    /** Porcentaje usado con un decimal. */
    static double porcentaje(Uso uso, Espacio espacio) {
        long cuota = cuotaBytes(espacio);
        return cuota == 0 ? 100 : Math.round(uso.bytes() * 1000.0 / cuota) / 10.0;
    }

    /** null, "80", "95" o "LLENO" (por bytes o por cantidad de archivos). */
    static String aviso(Uso uso, Espacio espacio) {
        if (uso.bytes() >= cuotaBytes(espacio) || uso.archivos() >= espacio.maxArchivos()) return "LLENO";
        double pct = porcentaje(uso, espacio);
        return pct >= 95 ? "95" : pct >= 80 ? "80" : null;
    }
}
