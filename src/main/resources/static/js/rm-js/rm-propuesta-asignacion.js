/*
 * Flujo "Asignar colaboradores" del RM (modales de fragments/rm-asignacion-modales.html).
 *
 * Los datos de proyecto y colaborador se toman de data-* :
 *   - #propuestaContexto: valores fijos de la página (el proyecto en Buscar
 *     colaboradores, el colaborador en su Perfil) y data-origen.
 *   - el botón que abre el modal: lo que cambia (el candidato o el proyecto elegido).
 * Claves: proyectoId, proyectoNombre, proyectoPm, proyectoInicio, proyectoFin,
 *         proyectoPresupuesto, proyectoDisponible, colaboradorId, colaboradorNombre,
 *         colaboradorCargo, colaboradorNivel, colaboradorHoras, colaboradorCarga,
 *         colaboradorMax, colaboradorSueldo.
 */
document.addEventListener("DOMContentLoaded", function () {
  var Modal = (window.bootstrap && window.bootstrap.Modal)
    || (window.tabler && window.tabler.bootstrap && window.tabler.bootstrap.Modal);
  var contexto = document.getElementById("propuestaContexto");
  var propuestaModal = document.getElementById("propuestaModal");
  if (!Modal || !propuestaModal) return;

  var form = document.getElementById("propuestaForm");
  var horasInput = document.getElementById("propuestaHoras");
  var justificacion = document.getElementById("propuestaJustificacion");
  var capacidadWrap = document.getElementById("propuestaCapacidadWrap");
  var motivoCapacidad = document.getElementById("propuestaMotivoCapacidad");
  var impacto = document.getElementById("propuestaImpacto");
  var enviarBtn = document.getElementById("propuestaEnviar");
  var datosActuales = {};

  function numero(valor) {
    var n = parseFloat(valor);
    return isNaN(n) ? 0 : n;
  }

  function soles(n) {
    return "S/ " + n.toLocaleString("es-PE", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function fecha(valor) {
    if (!valor) return null;
    var partes = valor.split("-");
    return partes.length === 3 ? partes[2] + "/" + partes[1] + "/" + partes[0] : valor;
  }

  function combinar(origen) {
    var datos = {};
    if (contexto) Object.assign(datos, contexto.dataset);
    if (origen) Object.assign(datos, origen.dataset);
    return datos;
  }

  function fila(etiqueta, valor, clase) {
    return '<div class="d-flex justify-content-between"><span class="text-secondary">' + etiqueta
      + '</span><strong class="' + (clase || "") + '">' + valor + "</strong></div>";
  }

  function recalcular() {
    var d = datosActuales;
    var horas = numero(horasInput.value);

    // Capacidad: solo pide la justificación de excepción cuando hace falta.
    var excede = horas > numero(d.colaboradorHoras) || numero(d.colaboradorCarga) >= numero(d.colaboradorMax);
    var mostrarCapacidad = horas > 0 && excede;
    capacidadWrap.classList.toggle("d-none", !mostrarCapacidad);
    motivoCapacidad.required = mostrarCapacidad;

    var presupuesto = numero(d.proyectoPresupuesto);
    var disponible = numero(d.proyectoDisponible);
    var sueldo = numero(d.colaboradorSueldo);

    if (!presupuesto) {
      impacto.innerHTML = '<div class="alert alert-warning mb-0 py-2">El proyecto todavía no tiene un presupuesto asignado.</div>';
      enviarBtn.disabled = true;
      return;
    }
    if (!sueldo || !d.proyectoInicio || !d.proyectoFin) {
      impacto.innerHTML = '<div class="text-danger">No se puede calcular el costo: falta el sueldo base del colaborador o las fechas del proyecto.</div>';
      enviarBtn.disabled = true;
      return;
    }
    if (horas <= 0) {
      impacto.innerHTML = '<div class="text-secondary">Ingresa las horas semanales para ver el costo.</div>'
        + fila("Disponible del proyecto", soles(disponible));
      enviarBtn.disabled = true;
      return;
    }

    // Misma fórmula que RmPresupuestoService.calcularCosto.
    var dias = (new Date(d.proyectoFin) - new Date(d.proyectoInicio)) / 86400000;
    var semanas = Math.max(dias, 0) / 7;
    var valorHora = sueldo / 160;
    var costoSemanal = valorHora * horas;
    var costoTotal = costoSemanal * semanas;
    var despues = disponible - costoTotal;
    var suficiente = despues >= 0;

    impacto.innerHTML =
      fila("Valor por hora", soles(valorHora)) +
      fila("Costo semanal", soles(costoSemanal)) +
      fila("Duración del proyecto", semanas.toFixed(1) + " semanas") +
      fila("Costo total estimado", soles(costoTotal)) +
      '<hr class="my-2">' +
      fila("Disponible antes", soles(disponible)) +
      fila("Disponible después", soles(Math.max(despues, 0)), suficiente ? "text-success" : "text-danger") +
      (suficiente
        ? '<div class="alert alert-success mt-2 mb-0 py-2">Presupuesto suficiente.</div>'
        : '<div class="alert alert-danger mt-2 mb-0 py-2">Presupuesto insuficiente: faltan ' + soles(Math.abs(despues)) + ".</div>");
    enviarBtn.disabled = !suficiente;
  }

  function abrirPropuesta(datos) {
    datosActuales = datos;
    form.reset();
    enviarBtn.textContent = "Confirmar asignación";

    propuestaModal.querySelectorAll("[data-campo]").forEach(function (input) {
      input.value = datos[input.dataset.campo] || "";
    });

    var inicio = fecha(datos.proyectoInicio);
    var fin = fecha(datos.proyectoFin);
    var textos = {
      proyectoNombre: datos.proyectoNombre || "—",
      proyectoPm: datos.proyectoPm || "—",
      proyectoPeriodo: inicio && fin ? inicio + " — " + fin : "Sin fechas definidas",
      proyectoDisponibleTexto: datos.proyectoPresupuesto ? soles(numero(datos.proyectoDisponible)) : "Sin presupuesto",
      colaboradorNombre: datos.colaboradorNombre || "—",
      colaboradorCargo: datos.colaboradorCargo || "—",
      colaboradorNivel: datos.colaboradorNivel || "—",
      colaboradorSueldoTexto: numero(datos.colaboradorSueldo) > 0 ? soles(numero(datos.colaboradorSueldo)) : "No registrado",
      colaboradorHorasTexto: numero(datos.colaboradorHoras) + " h/sem",
      colaboradorCargaTexto: numero(datos.colaboradorCarga) + " / " + numero(datos.colaboradorMax) + " asignaciones"
    };
    propuestaModal.querySelectorAll("[data-texto]").forEach(function (el) {
      el.textContent = textos[el.dataset.texto];
    });

    recalcular();
    Modal.getOrCreateInstance(propuestaModal).show();
  }

  // Cierra un modal y, cuando terminó de ocultarse, abre el de propuesta.
  function cambiarAPropuesta(modalActual, datos) {
    modalActual.addEventListener("hidden.bs.modal", function () {
      abrirPropuesta(datos);
    }, { once: true });
    Modal.getOrCreateInstance(modalActual).hide();
  }

  horasInput.addEventListener("input", recalcular);
  propuestaModal.addEventListener("shown.bs.modal", function () { horasInput.focus(); });
  form.addEventListener("submit", function (event) {
    if (!form.checkValidity() || enviarBtn.disabled) {
      event.preventDefault();
      form.reportValidity();
      return;
    }
    enviarBtn.disabled = true;
    enviarBtn.textContent = "Enviando…";
  });

  // ---- Botones "Proponer" directos (Buscar colaboradores) ----
  document.querySelectorAll("[data-abrir-propuesta]").forEach(function (btn) {
    btn.addEventListener("click", function () { abrirPropuesta(combinar(btn)); });
  });

  // ---- Modal "Ver perfil" (Buscar colaboradores) ----
  var perfilModal = document.getElementById("perfilModal");
  if (perfilModal) {
    var perfilBody = document.getElementById("perfilModalBody");
    var perfilProponer = document.getElementById("perfilModalProponer");
    var perfilOrigen = null;

    document.querySelectorAll("[data-ver-perfil]").forEach(function (btn) {
      btn.addEventListener("click", function () {
        perfilOrigen = btn;
        perfilProponer.disabled = btn.dataset.bloqueado === "true";
        perfilBody.innerHTML = '<div class="text-center text-secondary py-5">Cargando perfil…</div>';
        Modal.getOrCreateInstance(perfilModal).show();
        fetch(btn.dataset.verPerfil, { headers: { "X-Requested-With": "XMLHttpRequest" } })
          .then(function (r) {
            if (!r.ok) throw new Error();
            return r.text();
          })
          .then(function (html) { perfilBody.innerHTML = html; })
          .catch(function () {
            perfilBody.innerHTML = '<div class="alert alert-danger mb-0">No se pudo cargar el perfil. Intenta nuevamente.</div>';
          });
      });
    });

    perfilProponer.addEventListener("click", function () {
      if (perfilOrigen) cambiarAPropuesta(perfilModal, combinar(perfilOrigen));
    });
  }

  // ---- Modal "Seleccionar proyecto" (Perfil del colaborador) ----
  var proyectosModal = document.getElementById("proyectosModal");
  if (proyectosModal) {
    var busqueda = document.getElementById("proyectoBusqueda");
    var filas = Array.prototype.slice.call(proyectosModal.querySelectorAll("[data-seleccionar-proyecto]"));
    var sinResultados = document.getElementById("proyectosSinResultados");

    filas.forEach(function (fila) {
      fila.addEventListener("click", function () {
        if (fila.disabled) return;
        cambiarAPropuesta(proyectosModal, combinar(fila));
      });
    });

    if (busqueda) {
      busqueda.addEventListener("input", function () {
        var termino = busqueda.value.trim().toLowerCase();
        var visibles = 0;
        filas.forEach(function (fila) {
          var mostrar = (fila.dataset.busqueda || "").indexOf(termino) !== -1;
          fila.classList.toggle("d-none", !mostrar);
          if (mostrar) visibles++;
        });
        if (sinResultados) sinResultados.classList.toggle("d-none", visibles !== 0);
      });
    }
  }
});
