const searchInput = document.getElementById("searchInput");
const tabs = document.querySelectorAll(".filter-tab");
const tarjetas = document.querySelectorAll(".project-card-wrap");
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

function aplicarFiltros() {
    const texto = searchInput.value.trim().toLowerCase();
    let visibles = 0;

    for (let i = 0; i < tarjetas.length; i++) {
        const tarjeta = tarjetas[i];
        const estado = tarjeta.dataset.status;

        const coincideFiltro = (filtroActual === "Todos") || (estado === filtroActual);
        const coincideTexto = coincideBusqueda(tarjeta, texto);

        if (coincideFiltro && coincideTexto) {
            tarjeta.style.display = "";
            visibles = visibles + 1;
        } else {
            tarjeta.style.display = "none";
        }
    }

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