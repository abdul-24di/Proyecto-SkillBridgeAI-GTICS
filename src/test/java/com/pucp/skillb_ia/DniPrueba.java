package com.pucp.skillb_ia;

import java.util.concurrent.atomic.AtomicInteger;

// DNI distintos para las pruebas: la base H2 se comparte entre clases de prueba y el DNI no se puede repetir.
final class DniPrueba {

    private static final AtomicInteger SIGUIENTE = new AtomicInteger(10_000_000);

    private DniPrueba() {
    }

    static String unico() {
        return String.valueOf(SIGUIENTE.getAndIncrement());
    }
}
