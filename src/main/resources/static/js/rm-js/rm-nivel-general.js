/*
 * Modales de confirmación del nivel general (perfil y aprobación de certificado).
 * Solo muestra el nivel elegido en el selector; la regla (mantener o subir, nunca
 * bajar) se valida en el servidor.
 */
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("[data-nivel-select]").forEach(modal => {
    modal.addEventListener("show.bs.modal", () => {
      const select = document.getElementById(modal.dataset.nivelSelect);
      const destino = modal.querySelector("[data-nivel-seleccionado]");
      if (!select || !destino) return;
      const opcion = select.options[select.selectedIndex];
      destino.textContent = opcion && opcion.value ? opcion.text : "Sin cambios";
    });
  });
});
