const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const priorityFilter = document.getElementById("priorityFilter");
const clearFilters = document.getElementById("clearFilters");
const emptyState = document.getElementById("emptyState");

function filterProjects() {
    const query = searchInput.value.trim().toLowerCase();
    const status = statusFilter.value;
    const priority = priorityFilter.value;
    
    let visibleCount = 0;
    
    document.querySelectorAll('.project-item').forEach(item => {
        const name = item.getAttribute('data-name') || '';
        const desc = item.getAttribute('data-desc') || '';
        const itemStatus = item.getAttribute('data-status') || '';
        const itemPriority = item.getAttribute('data-priority') || '';
        
        const matchQuery = name.includes(query) || desc.includes(query);
        const matchStatus = (status === 'all' || itemStatus === status);
        const matchPriority = (priority === 'all' || itemPriority === priority);
        
        if (matchQuery && matchStatus && matchPriority) {
            item.style.display = 'block';
            visibleCount++;
        } else {
            item.style.display = 'none';
        }
    });
    
    emptyState.style.display = visibleCount === 0 ? 'block' : 'none';
}

[searchInput, statusFilter, priorityFilter].forEach(el => {
    if (el) {
        el.addEventListener(el.tagName === "INPUT" ? "input" : "change", filterProjects);
    }
});

if (clearFilters) {
    clearFilters.addEventListener('click', () => {
        searchInput.value = "";
        statusFilter.value = "all";
        priorityFilter.value = "all";
        filterProjects();
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
