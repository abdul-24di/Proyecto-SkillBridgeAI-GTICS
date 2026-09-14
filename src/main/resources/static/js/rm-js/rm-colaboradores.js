/*
 * Los datos de cada tarjeta los entrega Spring Boot mediante Thymeleaf.
 * Este archivo solo controla filtros y paginación en la vista ya renderizada.
 */

document.addEventListener("DOMContentLoaded", () => {
  const pageSize = 6;
  let currentPage = 1;

  const searchInput = document.getElementById("searchInput");
  const availabilityFilter = document.getElementById("availabilityFilter");
  const loadFilter = document.getElementById("loadFilter");
  const levelFilter = document.getElementById("levelFilter");
  const clearFilters = document.getElementById("clearFilters");
  const pagination = document.getElementById("pagination");
  const paginationInfo = document.getElementById("paginationInfo");
  const noFilterResults = document.getElementById("noFilterResults");
  const collaboratorItems = Array.from(document.querySelectorAll(".collaborator-item"));

  if (!searchInput || !availabilityFilter || !loadFilter || !levelFilter) return;

  function getFilteredItems() {
    const search = searchInput.value.trim().toLocaleLowerCase("es");
    const availability = availabilityFilter.value;
    const load = loadFilter.value;
    const level = levelFilter.value;

    return collaboratorItems.filter(item => {
      const searchable = (item.dataset.search || "").toLocaleLowerCase("es");
      const availableHours = Number(item.dataset.hours || 0);
      const activeAssignments = Number(item.dataset.load || 0);
      const maxAssignments = Number(item.dataset.max || 0);

      const matchesSearch = searchable.includes(search);

      let matchesAvailability = true;
      if (availability !== "all") {
        const threshold = Number(availability);
        matchesAvailability = threshold === 0
          ? availableHours === 0
          : availableHours >= threshold;
      }

      let matchesLoad = true;
      if (load === "max") {
        matchesLoad = maxAssignments > 0 && activeAssignments >= maxAssignments;
      } else if (load !== "all") {
        matchesLoad = activeAssignments === Number(load);
      }

      const matchesLevel = level === "all" || item.dataset.level === level;
      return matchesSearch && matchesAvailability && matchesLoad && matchesLevel;
    });
  }

  function render() {
    const filteredItems = getFilteredItems();
    const totalPages = Math.max(1, Math.ceil(filteredItems.length / pageSize));
    currentPage = Math.min(currentPage, totalPages);

    collaboratorItems.forEach(item => item.classList.add("d-none"));

    const start = (currentPage - 1) * pageSize;
    filteredItems.slice(start, start + pageSize)
      .forEach(item => item.classList.remove("d-none"));

    if (noFilterResults) {
      noFilterResults.classList.toggle(
        "d-none",
        filteredItems.length !== 0 || collaboratorItems.length === 0
      );
    }

    const firstItem = filteredItems.length ? start + 1 : 0;
    const lastItem = Math.min(start + pageSize, filteredItems.length);
    paginationInfo.textContent =
      `Mostrando ${firstItem}-${lastItem} de ${filteredItems.length} colaboradores`;

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

  [searchInput, availabilityFilter, loadFilter, levelFilter].forEach(element => {
    element.addEventListener(element.tagName === "INPUT" ? "input" : "change", () => {
      currentPage = 1;
      render();
    });
  });

  clearFilters.addEventListener("click", () => {
    searchInput.value = "";
    availabilityFilter.value = "all";
    loadFilter.value = "all";
    levelFilter.value = "all";
    currentPage = 1;
    render();
  });

  render();
});
