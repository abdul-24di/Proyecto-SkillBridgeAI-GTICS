(function () {
  'use strict';

  const collaboratorSelect = document.getElementById('collaboratorSelect');
  const courseSelect = document.getElementById('courseSelect');
  const summaryCollaborator = document.getElementById('summaryCollaborator');
  const summaryCargo = document.getElementById('summaryCargo');
  const summaryCourse = document.getElementById('summaryCourse');

  function update() {
    const collaborator = collaboratorSelect.selectedOptions[0];
    const course = courseSelect.selectedOptions[0];
    summaryCollaborator.textContent = collaborator?.dataset.nombre || 'Sin seleccionar';
    summaryCargo.textContent = collaborator?.dataset.cargo || '';
    summaryCourse.textContent = course?.dataset.nombre || 'Sin seleccionar';
  }

  collaboratorSelect.addEventListener('change', update);
  courseSelect.addEventListener('change', update);
  update();
})();
