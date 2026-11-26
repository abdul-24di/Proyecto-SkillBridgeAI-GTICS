/* Zona de riesgo del detalle del proyecto: "Finalizar" y "Cancelar" solo se habilitan con motivo y el
   nombre exacto del proyecto. Solo es una ayuda de interfaz: el servidor vuelve a validar todo. */
(() => {
  document.querySelectorAll("form[data-rm-cierre-form]").forEach(form => {
    const motivo = form.querySelector("[name='motivo']");
    const confirmacion = form.querySelector("[name='confirmacionNombre']");
    const confirmar = form.querySelector("[data-rm-cierre-confirmar]");
    const nombre = (form.dataset.nombre || "").trim();

    const actualizar = () => {
      confirmar.disabled = !(motivo.value.trim() && nombre && confirmacion.value.trim() === nombre);
    };
    motivo.addEventListener("input", actualizar);
    confirmacion.addEventListener("input", actualizar);

    // Evita un doble envío mientras el servidor responde.
    form.addEventListener("submit", event => {
      if (confirmar.disabled) {
        event.preventDefault();
        return;
      }
      confirmar.disabled = true;
    });

    // Cerrar el modal descarta lo escrito.
    const modal = form.closest(".modal");
    if (modal) {
      modal.addEventListener("hidden.bs.modal", () => {
        form.reset();
        actualizar();
      });
    }
  });
})();
