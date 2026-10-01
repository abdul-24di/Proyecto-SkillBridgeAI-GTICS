/*
 * Los filtros de esta vista se aplican en el servidor (formulario GET).
 * Este archivo solo controla la paginación de las dos tablas ya renderizadas,
 * cada una con su propio estado independiente, y el comportamiento del modal
 * de exportación (aviso de filtros sin aplicar y cierre al descargar).
 */
document.addEventListener("DOMContentLoaded", () => {
  const pageSize = 10;

  function setupPagination(itemSelector, paginationId, infoId, label) {
    const pagination = document.getElementById(paginationId);
    const paginationInfo = document.getElementById(infoId);
    const items = Array.from(document.querySelectorAll(itemSelector));
    let currentPage = 1;

    if (!pagination || !paginationInfo) return;

    function getFilteredItems() {
      return items;
    }

    function render() {
      const filteredItems = getFilteredItems();
      const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize));
      currentPage = Math.min(currentPage, totalPages);

      items.forEach(item => item.classList.add("d-none"));

      const start = (currentPage - 1) * pageSize;
      filteredItems.slice(start, start + pageSize)
        .forEach(item => item.classList.remove("d-none"));

      const firstItem = filteredItems.length ? start + 1 : 0;
      const lastItem = Math.min(start + pageSize, filteredItems.length);
      paginationInfo.textContent =
        `Mostrando ${firstItem}-${lastItem} de ${filteredItems.length} ${label}`;

      renderPagination(totalPages, filteredItems.length);
    }

    function renderPagination(totalPages, resultCount) {
      pagination.innerHTML = "";
      if (resultCount === 0) return;

      pagination.appendChild(createPageItem("Anterior", currentPage === 1, false, () => {
        currentPage--;
        render();
      }));

      for (let page = 1; page <= totalPages; page++) {
        pagination.appendChild(createPageItem(String(page), false, page === currentPage, () => {
          currentPage = page;
          render();
        }));
      }

      pagination.appendChild(createPageItem("Siguiente", currentPage === totalPages, false, () => {
        currentPage++;
        render();
      }));
    }

    function createPageItem(label, disabled, active, action) {
      const item = document.createElement("li");
      item.className = `page-item${disabled ? " disabled" : ""}${active ? " active" : ""}`;

      const link = document.createElement("a");
      link.className = "page-link";
      link.href = "#";
      link.textContent = label;
      link.addEventListener("click", event => {
        event.preventDefault();
        if (!disabled) action();
      });

      item.appendChild(link);
      return item;
    }

    render();
  }

  setupPagination(".proyecto-row", "paginationProyectos", "paginationInfoProyectos", "proyectos");
  setupPagination(".detalle-row", "paginationDetalles", "paginationInfoDetalles", "registros");
});

/*
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
