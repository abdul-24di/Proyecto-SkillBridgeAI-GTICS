/* Comportamiento compartido para los modales del Resource Manager. */
document.querySelectorAll(".rm-modal").forEach(modal => {
  modal.addEventListener("show.bs.modal", event => {
    const trigger = event.relatedTarget;
    if (!trigger) return;

    modal.querySelectorAll("[data-rm-source]").forEach(target => {
      const source = target.dataset.rmSource;
      if (source && trigger.dataset[source] !== undefined) {
        target.textContent = trigger.dataset[source];
      }
    });
  });

  modal.addEventListener("hidden.bs.modal", () => {
    modal.querySelectorAll(".rm-modal-form").forEach(form => {
      form.reset();
      form.classList.remove("was-validated");
    });
  });
});

document.querySelectorAll(".rm-modal-form").forEach(form => {
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

document.querySelectorAll("[data-rm-required-field]").forEach(button => {
  const selector = button.dataset.rmRequiredField;
  const field = document.querySelector(selector);
  if (!field) return;

  button.addEventListener("click", event => {
    const hasValue = field.value.trim().length > 0;
    field.classList.toggle("is-invalid", !hasValue);

    if (!hasValue) {
      event.preventDefault();
      field.focus();
    }
  });

  field.addEventListener("input", () => {
    if (field.value.trim()) field.classList.remove("is-invalid");
  });
});

document.querySelectorAll("[data-rm-clear-validation]").forEach(button => {
  button.addEventListener("click", () => {
    const field = document.querySelector(button.dataset.rmClearValidation);
    field?.classList.remove("is-invalid");
  });
});
