/*
 * Validación de formularios con mensajes propios debajo de cada campo, en vez del cuadro nativo
 * del navegador. Reutiliza las reglas que ya declara cada input (required, pattern, minlength,
 * min/max, type=email) a través de la Constraint Validation API; no duplica reglas en JS.
 * La validación real sigue siendo la del servidor: esto solo mejora cómo se muestra el error.
 *
 * Uso: incluir este script. Se aplica a todos los <form> que tengan algún campo con reglas,
 * salvo los que lleven el atributo data-native-validation.
 */
(function () {
  "use strict";

  function mensaje(el) {
    if (el.dataset.msg) return el.dataset.msg;
    const v = el.validity;
    if (v.valueMissing) {
      if (el.type === "checkbox") return "Debes marcar esta casilla para continuar.";
      if (el.type === "file") return "Selecciona un archivo.";
      if (el.tagName === "SELECT") return "Selecciona una opción.";
      return "Este campo es obligatorio.";
    }
    if (v.typeMismatch) return el.type === "email" ? "Ingresa un correo electrónico válido." : "El valor ingresado no es válido.";
    if (v.patternMismatch) return el.title || "El formato ingresado no es válido.";
    if (v.tooShort) return "Debe tener al menos " + el.minLength + " caracteres.";
    if (v.tooLong) return "No puede superar los " + el.maxLength + " caracteres.";
    if (v.rangeUnderflow) return el.type === "date" ? "La fecha no puede ser anterior al " + el.min + "." : "El valor mínimo permitido es " + el.min + ".";
    if (v.rangeOverflow) return el.type === "date" ? "La fecha no puede ser posterior al " + el.max + "." : "El valor máximo permitido es " + el.max + ".";
    if (v.stepMismatch) return "El valor ingresado no es válido.";
    return el.validationMessage || "El valor ingresado no es válido.";
  }

  function limpiar(el) {
    el.classList.remove("is-invalid");
    const aviso = el.parentElement && el.parentElement.querySelector(':scope > .js-field-error[data-for="' + (el.id || el.name) + '"]');
    if (aviso) aviso.remove();
  }

  function mostrar(el) {
    limpiar(el);
    el.classList.add("is-invalid");
    const aviso = document.createElement("div");
    aviso.className = "invalid-feedback d-block js-field-error";
    aviso.dataset.for = el.id || el.name;
    aviso.textContent = mensaje(el);
    el.insertAdjacentElement("afterend", aviso);
  }

  function tieneReglas(form) {
    return form.querySelector("[required], [pattern], [minlength], [min], [max], input[type=email]") !== null;
  }

  function preparar(form) {
    if (form.hasAttribute("data-native-validation") || form.dataset.validacionPropia || !tieneReglas(form)) return;
    form.dataset.validacionPropia = "1";
    form.setAttribute("novalidate", "");

    form.addEventListener("submit", function (e) {
      const invalidos = Array.from(form.elements).filter(el => el.willValidate && !el.validity.valid);
      Array.from(form.elements).forEach(limpiar);
      if (!invalidos.length) return;
      e.preventDefault();
      e.stopImmediatePropagation();
      invalidos.forEach(mostrar);
      invalidos[0].focus();
    }, true);

    form.addEventListener("input", function (e) {
      const el = e.target;
      if (el.classList && el.classList.contains("is-invalid")) {
        if (el.validity.valid) limpiar(el); else mostrar(el);
      }
    });
    form.addEventListener("change", function (e) {
      const el = e.target;
      if (el.classList && el.classList.contains("is-invalid") && el.validity.valid) limpiar(el);
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll("form").forEach(preparar);
  });
})();
