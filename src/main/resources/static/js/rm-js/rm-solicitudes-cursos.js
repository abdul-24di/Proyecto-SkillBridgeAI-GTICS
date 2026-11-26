/*
 * Los filtros y la paginación de esta vista se aplican en el servidor (formulario GET y enlaces).
 * Este archivo solo controla los modales de aprobar/rechazar.
 */

/*
 * Modales de aprobar y rechazar: toman la acción y los datos de la fila que los abrió.
 * En los modales con campo de motivo, "Confirmar" queda deshabilitado hasta que haya uno
 * (el servidor también lo exige). La validación de evidencia no pide motivo.
 */
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll(".course-decision-modal").forEach(modal => {
    const form = modal.querySelector("form");
    const motivo = modal.querySelector(".js-motivo");
    const confirmar = modal.querySelector(".js-confirmar");

    const actualizarConfirmar = () => {
      confirmar.disabled = motivo !== null && motivo.value.trim() === "";
    };

    modal.addEventListener("show.bs.modal", event => {
      const boton = event.relatedTarget;
      if (!boton) return;
      form.action = boton.dataset.action;
      modal.querySelector(".js-colaborador").textContent = boton.dataset.colaborador;
      modal.querySelector(".js-curso").textContent = boton.dataset.curso;
      if (motivo) motivo.value = "";
      actualizarConfirmar();
    });

    if (motivo) {
      modal.addEventListener("shown.bs.modal", () => motivo.focus());
      motivo.addEventListener("input", actualizarConfirmar);
    }
    form.addEventListener("submit", event => {
      if (motivo && motivo.value.trim() === "") {
        event.preventDefault();
        actualizarConfirmar();
        return;
      }
      confirmar.disabled = true;
    });
  });
});
