/*
 * Los filtros y la paginación de las dos tablas se resuelven en el servidor (enlaces GET).
 * Modal de exportación: la descarga es un formulario GET con los filtros aplicados
 * (funciona sin JS). Aquí solo se avisa si los selectores de la página cambiaron
 * sin pulsar "Aplicar" y se cierra el modal al iniciar la descarga.
 */
document.addEventListener("DOMContentLoaded", () => {
  const modal = document.getElementById("exportReportModal");
  const form = document.getElementById("exportReportForm");
  const alerta = document.getElementById("exportUnappliedAlert");
  if (!modal || !form || !alerta) return;

  const filtros = [
    ["periodo", modal.dataset.appliedPeriodo],
    ["proyecto", modal.dataset.appliedProyecto],
    ["estado", modal.dataset.appliedEstado]
  ];

  modal.addEventListener("show.bs.modal", () => {
    const cambiados = filtros.some(([id, aplicado]) => {
      const selector = document.getElementById(id);
      return selector && selector.value !== (aplicado || "");
    });
    alerta.classList.toggle("d-none", !cambiados);
  });

  form.addEventListener("submit", () => {
    const Modal = (window.bootstrap && window.bootstrap.Modal)
      || (window.tabler && window.tabler.bootstrap && window.tabler.bootstrap.Modal);
    const instancia = Modal && Modal.getInstance(modal);
    if (instancia) setTimeout(() => instancia.hide(), 0);
  });
});
