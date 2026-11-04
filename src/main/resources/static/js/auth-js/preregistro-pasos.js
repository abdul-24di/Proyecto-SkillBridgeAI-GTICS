/*
 * Pasos del pre-registro del colaborador (activar-cuenta). Solo ayuda a recorrer el formulario: muestra un
 * paso a la vez y avisa de lo evidente antes de enviar. La validación real sigue en el servidor (@Valid), y
 * sin JavaScript el formulario se ve completo y funciona igual.
 */
document.addEventListener("DOMContentLoaded", function () {
  const form = document.getElementById("activarCuentaForm");
  if (!form) return;

  const MAX_ENVIO_MB = 55; // algo menos que spring.servlet.multipart.max-request-size
  const esPorPasos = form.dataset.pasos === "4";
  const pasos = Array.from(form.querySelectorAll("[data-paso]"));
  const navegacion = form.querySelector(".auth-pasos-nav");
  const indicador = document.querySelector(".auth-pasos");
  let pasoActual = Number(form.dataset.pasoInicial) || 1;

  function mostrarPaso(numero) {
    pasoActual = numero;
    pasos.forEach(function (seccion) {
      seccion.hidden = Number(seccion.dataset.paso) !== numero;
    });
    indicador.querySelectorAll("[data-ir-paso]").forEach(function (boton) {
      const paso = Number(boton.dataset.irPaso);
      boton.classList.toggle("is-activo", paso === numero);
      boton.classList.toggle("is-hecho", paso < numero);
    });
    navegacion.querySelector("[data-paso-atras]").style.visibility = numero === 1 ? "hidden" : "visible";
    navegacion.querySelector("[data-paso-siguiente]").style.display = numero === pasos.length ? "none" : "";
    navegacion.querySelector("[data-paso-texto]").textContent = "Paso " + numero + " de " + pasos.length;
    window.scrollTo({ top: 0 });
  }

  // Muestra un error debajo del campo, con el mismo estilo que los errores del servidor.
  function marcarError(campo, mensaje) {
    const contenedor = campo.closest(".auth-input-icon") || campo;
    let aviso = contenedor.parentElement.querySelector("[data-error-js]");
    if (!aviso) {
      aviso = document.createElement("div");
      aviso.className = "auth-field-error is-visible";
      aviso.dataset.errorJs = "";
      contenedor.after(aviso);
    }
    aviso.textContent = mensaje;
    campo.classList.add("is-invalid");
  }

  function limpiarError(campo) {
    const contenedor = campo.closest(".auth-input-icon") || campo;
    const aviso = contenedor.parentElement.querySelector("[data-error-js]");
    if (aviso) aviso.remove();
    campo.classList.remove("is-invalid");
  }

  // Comprueba lo obligatorio del paso antes de avanzar, para no descubrirlo recién al enviar.
  function pasoCompleto(numero) {
    const seccion = form.querySelector('[data-paso="' + numero + '"]');
    let primero = null;

    seccion.querySelectorAll("[data-requerido]").forEach(function (campo) {
      limpiarError(campo);
      const valor = campo.value.trim();
      let mensaje = "";
      if (!valor) {
        mensaje = campo.dataset.requerido;
      } else if (campo.dataset.patron && !new RegExp(campo.dataset.patron).test(valor)) {
        mensaje = campo.dataset.patronMensaje;
      }
      if (mensaje) {
        marcarError(campo, mensaje);
        primero = primero || campo;
      }
    });

    const cv = seccion.querySelector("[data-cv-obligatorio]");
    if (cv) {
      limpiarError(cv);
      if (!cv.files.length && !seccion.querySelector(".auth-archivo-ok")) {
        marcarError(cv, "Adjunta tu CV en PDF para continuar.");
        primero = primero || cv;
      }
    }

    if (primero) primero.focus();
    return !primero;
  }

  // Los archivos se revisan al elegirlos: un archivo demasiado grande rechazaría todo el formulario.
  form.addEventListener("change", function (e) {
    const campo = e.target;
    if (campo.type !== "file" || !campo.files.length) return;
    limpiarError(campo);
    const archivo = campo.files[0];
    const tiposPermitidos = campo.accept.split(",");
    if (!tiposPermitidos.includes(archivo.type)) {
      campo.value = "";
      marcarError(campo, "Formato no permitido. Revisa los formatos indicados.");
    } else if (archivo.size > Number(campo.dataset.maxMb) * 1024 * 1024) {
      campo.value = "";
      marcarError(campo, "El archivo supera el máximo de " + campo.dataset.maxMb + " MB.");
    }
  });

  form.addEventListener("submit", function (e) {
    let totalBytes = 0;
    form.querySelectorAll("input[type=file]").forEach(function (campo) {
      if (campo.files.length) totalBytes += campo.files[0].size;
    });
    if (totalBytes > MAX_ENVIO_MB * 1024 * 1024) {
      e.preventDefault();
      alert("Los archivos suman más de " + MAX_ENVIO_MB + " MB. Adjunta algunos ahora y el resto después desde tu perfil.");
      return;
    }
    // Evita enviar dos veces mientras suben los archivos.
    const boton = document.getElementById("activarCuentaEnviar");
    boton.disabled = true;
    boton.textContent = "Enviando…";
  });

  // Cada enlace de la lista de errores abre el paso del campo y lo enfoca.
  document.querySelectorAll("[data-ir-campo]").forEach(function (enlace) {
    enlace.addEventListener("click", function (e) {
      e.preventDefault();
      if (esPorPasos) mostrarPaso(Number(enlace.dataset.irPaso));
      const campo = form.querySelector('[name="' + enlace.dataset.irCampo + '"]');
      if (campo) {
        campo.scrollIntoView({ block: "center" });
        campo.focus();
      }
    });
  });

  if (!esPorPasos) return;

  indicador.hidden = false;
  navegacion.hidden = false;

  indicador.querySelectorAll("[data-ir-paso]").forEach(function (boton) {
    boton.addEventListener("click", function () {
      mostrarPaso(Number(boton.dataset.irPaso));
    });
  });
  navegacion.querySelector("[data-paso-atras]").addEventListener("click", function () {
    mostrarPaso(pasoActual - 1);
  });
  navegacion.querySelector("[data-paso-siguiente]").addEventListener("click", function () {
    if (pasoCompleto(pasoActual)) mostrarPaso(pasoActual + 1);
  });

  // Enter en un campo no debe enviar el formulario antes del último paso.
  form.addEventListener("keydown", function (e) {
    if (e.key === "Enter" && e.target.tagName === "INPUT" && pasoActual < pasos.length) {
      e.preventDefault();
      if (pasoCompleto(pasoActual)) mostrarPaso(pasoActual + 1);
    }
  });

  mostrarPaso(pasoActual);
});
