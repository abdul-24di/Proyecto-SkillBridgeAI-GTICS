/* TASK-018: cambiar un presupuesto ya asignado exige confirmar en un modal con el valor anterior y el nuevo.
   Solo es una ayuda de interfaz: el servidor vuelve a validar el monto. */
(() => {
  const modal = document.getElementById("budgetChangeModal");
  if (!modal) return;

  const anteriorTexto = modal.querySelector("[data-budget-anterior]");
  const nuevoTexto = modal.querySelector("[data-budget-nuevo]");
  const confirmar = modal.querySelector("[data-budget-confirmar]");
  const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
  const DECIMAL = /^\d{1,10}(\.\d{1,2})?$/;
  let formularioPendiente = null;

  // Clave comparable sin pasar por Number: "045000.5" y "45000.50" son el mismo monto.
  const normalizar = valor => {
    if (!DECIMAL.test(valor)) return valor;
    const [entero, decimales = ""] = valor.split(".");
    return (entero.replace(/^0+(?=\d)/, "")) + "." + decimales.padEnd(2, "0");
  };

  // Solo para mostrar: "S/ 12,345.00". El valor enviado sigue siendo el decimal crudo.
  const formatear = valor => {
    if (!DECIMAL.test(valor)) return valor;
    const [entero, decimales] = normalizar(valor).split(".");
    return "S/ " + entero.replace(/\B(?=(\d{3})+(?!\d))/g, ",") + "." + decimales;
  };

  document.querySelectorAll("form[data-rm-budget-form]").forEach(form => {
    form.addEventListener("submit", event => {
      const anterior = (form.dataset.presupuestoAnterior || "").trim();
      const campo = form.querySelector("[name='presupuesto']");
      const nuevo = campo ? campo.value.trim() : "";
      // Primera asignación o mismo monto: no es un cambio y sigue el flujo normal.
      if (!anterior || normalizar(anterior) === normalizar(nuevo)) return;

      event.preventDefault();
      anteriorTexto.textContent = formatear(anterior);
      nuevoTexto.textContent = formatear(nuevo);
      formularioPendiente = form;
      if (Modal) {
        Modal.getOrCreateInstance(modal).show();
      } else if (window.confirm("¿Cambiar el presupuesto de " + formatear(anterior) + " a " + formatear(nuevo) + "?")) {
        form.submit();
      }
    });
  });

  confirmar.addEventListener("click", () => {
    if (!formularioPendiente) return;
    const form = formularioPendiente;
    formularioPendiente = null;
    confirmar.disabled = true;
    // submit() no vuelve a disparar el evento "submit", así que no reabre el modal.
    form.submit();
  });

  // Cancelar (o cerrar) descarta el cambio pendiente sin enviar nada.
  modal.addEventListener("hidden.bs.modal", () => {
    formularioPendiente = null;
  });
})();
