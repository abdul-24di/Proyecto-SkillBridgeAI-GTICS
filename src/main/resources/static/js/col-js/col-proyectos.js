const searchInput = document.getElementById("searchInput");
const tabs = document.querySelectorAll(".filter-tab");
const tarjetas = Array.from(document.querySelectorAll(".project-card-wrap"));
const emptyFiltroState = document.getElementById("emptyFiltroState");
const emptyFiltroMensaje = document.getElementById("emptyFiltroMensaje");
let filtroActual = "Todos";

const etiquetasFiltro = {
    "ACTIVA": "Activos",
    "PENDIENTE": "Pendientes de aprobación",
    "FINALIZADA": "Finalizados",
    "RECHAZADA": "Rechazados"
};

function coincideBusqueda(tarjeta, texto) {
    const contenido = tarjeta.textContent.toLowerCase();
    return contenido.includes(texto);
}

function coincideProyecto(tarjeta) {
    const texto = searchInput.value.trim().toLowerCase();
    const estado = tarjeta.dataset.status;

    const coincideFiltro = (filtroActual === "Todos") || (estado === filtroActual);
    const coincideTexto = coincideBusqueda(tarjeta, texto);
    return coincideFiltro && coincideTexto;
}

const paginacionProyectos = tarjetas.length ? crearPaginacionTabla({
    filas: tarjetas,
    filtroFn: coincideProyecto,
    paginationEl: document.getElementById("proyectosPagination"),
    infoEl: document.getElementById("proyectosPaginationInfo"),
    pageSize: 6,
    etiqueta: "proyecto(s)"
}) : null;

function aplicarFiltros() {
    const visibles = tarjetas.filter(coincideProyecto).length;

    if (emptyFiltroState) {
        if (visibles === 0 && tarjetas.length > 0) {
            let etiqueta = "esta búsqueda";
            if (etiquetasFiltro[filtroActual]) {
                etiqueta = etiquetasFiltro[filtroActual];
            }
            emptyFiltroMensaje.textContent = "No tienes proyectos en \"" + etiqueta + "\".";
            emptyFiltroState.style.display = "";
        } else {
            emptyFiltroState.style.display = "none";
        }
    }

    if (paginacionProyectos) {
        paginacionProyectos.reset();
    }
}

for (let i = 0; i < tabs.length; i++) {
    const tab = tabs[i];
    tab.addEventListener("click", () => {
        for (let j = 0; j < tabs.length; j++) {
            tabs[j].classList.remove("active");
        }
        tab.classList.add("active");
        filtroActual = tab.dataset.filter;
        aplicarFiltros();
    });
}

searchInput.addEventListener("input", aplicarFiltros);

aplicarFiltros();