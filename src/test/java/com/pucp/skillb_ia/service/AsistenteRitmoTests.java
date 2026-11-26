package com.pucp.skillb_ia.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// TASK-074: límite de mensajes por minuto compartido por los asistentes (Colaborador, PM y RM).
class AsistenteRitmoTests {

    private static final String MENSAJE = "Enviaste muchos mensajes seguidos. Espera un momento e inténtalo de nuevo.";

    @Test
    void permiteElMaximoYRechazaElSiguiente() {
        AsistenteRitmoService ritmo = new AsistenteRitmoService(3);
        long ahora = System.currentTimeMillis();
        ritmo.validar(1L, ahora);
        ritmo.validar(1L, ahora + 1);
        ritmo.validar(1L, ahora + 2);

        assertThatThrownBy(() -> ritmo.validar(1L, ahora + 3))
                .isInstanceOf(LimiteMensajesException.class)
                .hasMessage(MENSAJE);
    }

    @Test
    void laApiRespondeElLimiteCon429YElMismoMensaje() {
        var respuesta = new com.pucp.skillb_ia.controller.ApiErrorAdvice()
                .demasiadosMensajes(new LimiteMensajesException(MENSAJE));

        org.assertj.core.api.Assertions.assertThat(respuesta.getStatusCode().value()).isEqualTo(429);
        org.assertj.core.api.Assertions.assertThat(respuesta.getBody()).containsEntry("error", MENSAJE);
    }

    @Test
    void pasadoUnMinutoVuelveAPermitir() {
        AsistenteRitmoService ritmo = new AsistenteRitmoService(2);
        long ahora = System.currentTimeMillis();
        ritmo.validar(1L, ahora);
        ritmo.validar(1L, ahora + 10);
        assertThatThrownBy(() -> ritmo.validar(1L, ahora + 20)).isInstanceOf(IllegalArgumentException.class);

        ritmo.validar(1L, ahora + 60_001);
    }

    @Test
    void cadaUsuarioTieneSuPropioCupo() {
        AsistenteRitmoService ritmo = new AsistenteRitmoService(1);
        long ahora = System.currentTimeMillis();
        ritmo.validar(1L, ahora);
        assertThatThrownBy(() -> ritmo.validar(1L, ahora + 1)).isInstanceOf(IllegalArgumentException.class);

        ritmo.validar(2L, ahora + 1);
    }

    @Test
    void elBarridoRetiraAQuienNoEnviaHaceMasDeUnMinuto() {
        AsistenteRitmoService ritmo = new AsistenteRitmoService(10);
        long ahora = System.currentTimeMillis();
        ritmo.validar(1L, ahora);
        ritmo.validar(2L, ahora);
        ritmo.validar(3L, ahora);
        assertThat(ritmo.usuariosRegistrados()).isEqualTo(3);

        // Pasado más de un minuto, el siguiente envío barre a los inactivos y solo queda quien acaba de escribir.
        ritmo.validar(4L, ahora + 120_000);
        assertThat(ritmo.usuariosRegistrados()).isEqualTo(1);
    }
}
