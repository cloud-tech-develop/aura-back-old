package com.cloud_technological.aura_pos.utils;

public class NotaContableTipo {
    public static final int CREDITO = 1;
    public static final int DEBITO = 2;
    
    public static String getNombre(int tipo) {
        return switch (tipo) {
            case CREDITO -> "CRÉDITO";
            case DEBITO -> "DÉBITO";
            default -> "DESCONOCIDO";
        };
    }
    
    public static boolean esValido(int tipo) {
        return tipo == CREDITO || tipo == DEBITO;
    }
}
