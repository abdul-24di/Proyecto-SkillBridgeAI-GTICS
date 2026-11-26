/*
 * Vista "Mis proyectos" del PM. Los filtros y la paginación ahora se hacen en el
 * servidor (PmViewController#proyectos). Aquí solo queda el modal de cancelación.
 */
const cancelModal = document.getElementById('cancelProjectModal');
if (cancelModal) {
    cancelModal.addEventListener('show.bs.modal', function(event) {
        const button = event.relatedTarget;
        const projectId = button.getAttribute('data-id');

        const confirmBtn = this.querySelector('.btn-danger');

        confirmBtn.onclick = function() {
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '/pm/proyectos/' + projectId + '/cancelar';
            // Antes el motivo escrito en el modal no se enviaba.
            const motivo = document.createElement('input');
            motivo.type = 'hidden';
            motivo.name = 'motivo';
            motivo.value = document.getElementById('cancelProjectReason')?.value || '';
            form.appendChild(motivo);
            agregarCsrfAFormulario(form);
            document.body.appendChild(form);
            form.submit();
        };
    });
}
