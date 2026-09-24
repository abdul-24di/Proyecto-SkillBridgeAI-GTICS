/*
 * Paginación de cliente reutilizable para tablas del Admin, sobre filas ya
 * renderizadas por Thymeleaf (mismo patrón que ya usa RM en sus páginas).
 * Cada tabla pasa su propia función de filtro; este módulo solo se encarga
 * de recortar por página y dibujar los controles.
 */
function crearPaginacionTabla({ filas, filtroFn, paginationEl, infoEl, noResultsEl, pageSize, etiqueta }) {
  let currentPage = 1;

  function filtradas() {
    return filas.filter(filtroFn);
  }

  function render() {
    const visibles = filtradas();
    const totalPaginas = Math.max(1, Math.ceil(visibles.length / pageSize));
    currentPage = Math.min(currentPage, totalPaginas);

    filas.forEach(fila => { fila.style.display = "none"; });

    const inicio = (currentPage - 1) * pageSize;
    visibles.slice(inicio, inicio + pageSize).forEach(fila => { fila.style.display = ""; });

    if (noResultsEl) {
      noResultsEl.classList.toggle("d-none", visibles.length !== 0 || filas.length === 0);
    }

    if (infoEl) {
      const primero = visibles.length ? inicio + 1 : 0;
      const ultimo = Math.min(inicio + pageSize, visibles.length);
      infoEl.textContent = `Mostrando ${primero}-${ultimo} de ${visibles.length} ${etiqueta}`;
    }

    renderControles(totalPaginas, visibles.length);
  }

  function renderControles(totalPaginas, totalResultados) {
    if (!paginationEl) return;
    paginationEl.innerHTML = "";
    if (totalResultados === 0) return;

    paginationEl.appendChild(crearItem("Anterior", currentPage === 1, false, () => {
      currentPage--;
      render();
    }));

    for (let pagina = 1; pagina <= totalPaginas; pagina++) {
      paginationEl.appendChild(crearItem(String(pagina), false, pagina === currentPage, () => {
        currentPage = pagina;
        render();
      }));
    }

    paginationEl.appendChild(crearItem("Siguiente", currentPage === totalPaginas, false, () => {
      currentPage++;
      render();
    }));
  }

  function crearItem(texto, deshabilitado, activo, accion) {
    const item = document.createElement("li");
    item.className = `page-item${deshabilitado ? " disabled" : ""}${activo ? " active" : ""}`;

    const link = document.createElement("a");
    link.className = "page-link";
    link.href = "#";
    link.textContent = texto;
    link.addEventListener("click", evento => {
      evento.preventDefault();
      if (!deshabilitado) accion();
    });

    item.appendChild(link);
    return item;
  }

  function reset() {
    currentPage = 1;
    render();
  }

  render();
  return { render, reset };
}
