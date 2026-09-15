/* Comportamiento compartido para los modales del Administrador. */
document.querySelectorAll(".admin-modal").forEach(modal => {
  modal.addEventListener("show.bs.modal", event => {
    const trigger = event.relatedTarget;
    if (!trigger) return;

    modal.querySelectorAll("[data-admin-source]").forEach(target => {
      const source = target.dataset.adminSource;
      if (!source || trigger.dataset[source] === undefined) return;

      const value = trigger.dataset[source];
      if (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.tagName === "SELECT") {
        target.value = value;
      } else {
        target.textContent = value;
      }
    });
  });

  modal.addEventListener("hidden.bs.modal", () => {
    modal.querySelectorAll(".admin-modal-form").forEach(form => {
      form.reset();
      form.classList.remove("was-validated");
    });
  });
});

document.querySelectorAll(".admin-modal-form").forEach(form => {
  form.addEventListener("submit", event => {
    event.preventDefault();
    event.stopPropagation();
    form.classList.add("was-validated");

    if (!form.checkValidity()) return;

    const modal = form.closest(".modal");
    const Modal = window.bootstrap?.Modal || window.tabler?.bootstrap?.Modal;
    if (modal && Modal) {
      Modal.getOrCreateInstance(modal).hide();
    }
  });
});
