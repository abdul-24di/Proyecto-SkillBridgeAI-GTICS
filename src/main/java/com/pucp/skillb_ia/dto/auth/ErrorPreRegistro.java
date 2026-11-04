package com.pucp.skillb_ia.dto.auth;

// Un error del formulario de activación, listo para la lista de la parte superior: en qué paso está, cómo se
// llama el campo para el usuario, qué pasó, y el "name" del campo al que lleva el enlace.
public record ErrorPreRegistro(int paso, String etiqueta, String mensaje, String campo) {
}
