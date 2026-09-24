/*
 * Los filtros de esta vista se aplican en el servidor (formulario GET).
 * Este archivo solo controla la paginación de la tabla ya renderizada.
 */
document.addEventListener("DOMContentLoaded", () => {
  const pageSize = 10;
  let currentPage = 1;

  const pagination = document.getElementById("pagination");
  const paginationInfo = document.getElementById("paginationInfo");
  const solicitudItems = Array.from(document.querySelectorAll(".solicitud-row"));

  if (!pagination || !paginationInfo) return;

  function getFilteredItems() {
    return solicitudItems;
  }

  function render() {
    const filteredItems = getFilteredItems();
    const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize));
    currentPage = Math.min(currentPage, totalPages);

    solicitudItems.forEach(item => item.classList.add("d-none"));

    const start = (currentPage - 1) * pageSize;
    filteredItems.slice(start, start + pageSize)
      .forEach(item => item.classList.remove("d-none"));

    const firstItem = filteredItems.length ? start + 1 : 0;
    const lastItem = Math.min(start + pageSize, filteredItems.length);
    paginationInfo.textContent =
      `Mostrando ${firstItem}-${lastItem} de ${filteredItems.length} solicitudes`;

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
});
