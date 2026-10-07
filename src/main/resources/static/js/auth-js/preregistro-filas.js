/*
 * Filas dinámicas del pre-registro (activar-cuenta): experiencia, certificados y formación.
 * Cada sección es un contenedor [data-filas-wrap="<prefijo>"] con filas [data-fila], un botón
 * [data-fila-agregar="<prefijo>"] y botones [data-fila-quitar]. Spring enlaza las filas por
 * nombre (prefijo[0].campo, prefijo[1].campo, ...), por eso se renumeran al agregar/quitar.
 */
document.addEventListener("DOMContentLoaded", function () {
  const MAX_FILAS = 10;

  document.querySelectorAll("[data-filas-wrap]").forEach(function (wrap) {
    const prefijo = wrap.dataset.filasWrap;
    const btnAgregar = wrap.querySelector('[data-fila-agregar="' + prefijo + '"]');
    if (!btnAgregar) return;

    const reIndice = new RegExp(prefijo + "\\[\\d+\\]", "g");
    const reId = new RegExp(prefijo + "\\d+\\.", "g");
    const filas = () => Array.from(wrap.querySelectorAll("[data-fila]"));

    function reindexar() {
      filas().forEach(function (fila, i) {
        fila.querySelectorAll("[name], [id], label[for]").forEach(function (el) {
          ["name", "id", "for"].forEach(function (attr) {
            const valor = el.getAttribute(attr);
            if (valor) {
              el.setAttribute(attr, valor
                .replace(reIndice, prefijo + "[" + i + "]")
                .replace(reId, prefijo + i + "."));
            }
          });
        });
      });
      const unica = filas().length === 1;
      wrap.querySelectorAll("[data-fila-quitar]").forEach(function (b) { b.style.display = unica ? "none" : ""; });
      btnAgregar.disabled = filas().length >= MAX_FILAS;
    }

    // "Trabajo aquí actualmente" (solo experiencia): deshabilita la fecha de fin.
    function sincronizarActual(fila) {
      const actual = fila.querySelector("input[type=checkbox]");
      const fin = fila.querySelector("input[type=date][name$='.fechaFin']");
      if (actual && fin) {
        fin.disabled = actual.checked;
        if (actual.checked) fin.value = "";
      }
    }

    wrap.addEventListener("change", function (e) {
      if (e.target.matches("input[type=checkbox]")) sincronizarActual(e.target.closest("[data-fila]"));
    });

    wrap.addEventListener("click", function (e) {
      if (e.target.matches("[data-fila-quitar]") && filas().length > 1) {
        e.target.closest("[data-fila]").remove();
        reindexar();
      }
    });

    btnAgregar.addEventListener("click", function () {
      const todas = filas();
      if (todas.length >= MAX_FILAS) return;
      const nueva = todas[todas.length - 1].cloneNode(true);
      nueva.querySelectorAll("input[type=text], input[type=date], input[type=file], textarea").forEach(function (el) {
        el.value = "";
        el.disabled = false;
      });
      nueva.querySelectorAll("select").forEach(function (el) { el.selectedIndex = 0; });
      nueva.querySelectorAll("input[type=checkbox]").forEach(function (el) { el.checked = false; });
      nueva.querySelectorAll(".auth-field-error").forEach(function (el) { el.remove(); });
      todas[todas.length - 1].after(nueva);
      reindexar();
    });

    filas().forEach(sincronizarActual);
    reindexar();
  });
});
