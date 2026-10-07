/*
 * Detalle del proyecto (PM) — solo visual (TASK-058).
 * Sin JS, el modal "Solicitar personal" se abre con CSS :target. Con JS, Bootstrap lo controla;
 * si la página llega con "#requestStaffModal" (error de validación), se abre como modal normal.
 */
document.addEventListener("DOMContentLoaded", function () {
  var modal = document.getElementById("requestStaffModal");
  var Modal = (window.bootstrap && window.bootstrap.Modal)
    || (window.tabler && window.tabler.bootstrap && window.tabler.bootstrap.Modal);
  if (!modal || !Modal) return;
  modal.classList.add("js-modal");
  if (window.location.hash === "#requestStaffModal") {
    history.replaceState(null, "", window.location.pathname + window.location.search);
    Modal.getOrCreateInstance(modal).show();
  }
});
