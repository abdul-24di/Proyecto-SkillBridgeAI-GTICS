/*
 * Filtro de entradas numéricas: impide que queden letras u otros caracteres en el campo mientras
 * se escribe, se pega o se autocompleta. Solo actúa en los inputs que lo piden con data-entrada:
 *   - data-entrada="telefono": dígitos y un + opcional al inicio (regla de PoliticaTelefono).
 *   - data-entrada="monto": dígitos y un punto con hasta 2 decimales (la coma se toma como punto).
 * Respeta el maxlength del campo. La validación real sigue siendo la del servidor.
 */
(function () {
  "use strict";

  var ENTEROS_MONTO = 10;
  var DECIMALES_MONTO = 2;

  function limpiarTelefono(valor) {
    var mas = valor.charAt(0) === "+" ? "+" : "";
    return mas + valor.replace(/\D/g, "");
  }

  function limpiarMonto(valor) {
    var texto = valor.replace(/,/g, ".").replace(/[^\d.]/g, "");
    var punto = texto.indexOf(".");
    if (punto === -1) return texto.slice(0, ENTEROS_MONTO);
    var enteros = texto.slice(0, punto).slice(0, ENTEROS_MONTO);
    var decimales = texto.slice(punto + 1).replace(/\./g, "").slice(0, DECIMALES_MONTO);
    return enteros + "." + decimales;
  }

  var LIMPIADORES = { telefono: limpiarTelefono, monto: limpiarMonto };

  function filtrar(el) {
    var limpiar = LIMPIADORES[el.dataset.entrada];
    if (!limpiar) return;
    var original = el.value;
    var limpio = limpiar(original);
    if (el.maxLength > 0) limpio = limpio.slice(0, el.maxLength);
    if (limpio === original) return;

    // Conserva el cursor: cuenta cuántos caracteres válidos quedaban antes de él.
    var cursor = el.selectionStart;
    var previo = cursor == null ? limpio.length : limpiar(original.slice(0, cursor)).length;
    el.value = limpio;
    try {
      el.setSelectionRange(Math.min(previo, limpio.length), Math.min(previo, limpio.length));
    } catch (e) {
      // Algunos tipos de input no admiten selección; no es necesario hacer nada.
    }
  }

  function preparar(el) {
    if (el.dataset.entradaLista) return;
    el.dataset.entradaLista = "1";
    el.addEventListener("input", function () { filtrar(el); });
    filtrar(el);
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("input[data-entrada]").forEach(preparar);
  });
})();
