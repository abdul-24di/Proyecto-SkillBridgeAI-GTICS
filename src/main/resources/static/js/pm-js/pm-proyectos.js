const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const priorityFilter = document.getElementById("priorityFilter");
const clearFilters = document.getElementById("clearFilters");
const emptyState = document.getElementById("emptyState");
const projectItems = Array.from(document.querySelectorAll(".project-item"));

function coincide(item) {
    const query = (searchInput?.value || "").trim().toLowerCase();
    const status = statusFilter?.value || "all";
    const priority = priorityFilter?.value || "all";

    const name = item.getAttribute('data-name') || '';
    const desc = item.getAttribute('data-desc') || '';
    const itemStatus = item.getAttribute('data-status') || '';
    const itemPriority = item.getAttribute('data-priority') || '';

    const matchQuery = name.includes(query) || desc.includes(query);
    const matchStatus = (status === 'all' || itemStatus === status);
    const matchPriority = (priority === 'all' || itemPriority === priority);

    return matchQuery && matchStatus && matchPriority;
}

const paginacion = projectItems.length ? crearPaginacionTabla({
    filas: projectItems,
    filtroFn: coincide,
    paginationEl: document.getElementById("projectsPagination"),
    infoEl: document.getElementById("projectsPaginationInfo"),
    noResultsEl: emptyState,
    pageSize: 6,
    etiqueta: "proyecto(s)"
}) : null;

[searchInput, statusFilter, priorityFilter].forEach(el => {
    if (el) {
        el.addEventListener(el.tagName === "INPUT" ? "input" : "change", () => paginacion?.reset());
    }
});

if (clearFilters) {
    clearFilters.addEventListener('click', () => {
        searchInput.value = "";
        statusFilter.value = "all";
        priorityFilter.value = "all";
        paginacion?.reset();
    });
}

// Setup cancel project modal action
const cancelModal = document.getElementById('cancelProjectModal');
if (cancelModal) {
    cancelModal.addEventListener('show.bs.modal', function(event) {
        const button = event.relatedTarget;
        const projectId = button.getAttribute('data-id');
        const projectName = button.getAttribute('data-name');

        // update form action
        const confirmBtn = this.querySelector('.btn-danger');

        // we can dynamically build a form or change window.location
        confirmBtn.onclick = function() {
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/pm/proyectos/' + projectId + '/cancelar';
            document.body.appendChild(form);
            form.submit();
        };
    });
}
