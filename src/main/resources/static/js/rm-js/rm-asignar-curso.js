/* Modal "Asignar curso" del RM (fragments/rm-curso-modales :: modalAsignarCurso, TASK-047).
   Solo ayuda de UX: el servidor vuelve a validar el colaborador, el curso y los duplicados. */
(function () {
  'use strict';

  const modal = document.getElementById('asignarCursoModal');
  if (!modal) return;

  const form = document.getElementById('asignarCursoForm');
  const colaboradorSelect = document.getElementById('asignarCursoColaborador');
  const cursoSelect = document.getElementById('asignarCursoCurso');
  const colaboradorId = document.getElementById('asignarCursoColaboradorId');
  const origen = document.getElementById('asignarCursoOrigen');
  const aviso = document.getElementById('asignarCursoDuplicado');
  const confirmar = document.getElementById('asignarCursoConfirmar');
  const foto = document.getElementById('asignarCursoFoto');
  const iniciales = document.getElementById('asignarCursoIniciales');
  const sinOpciones = confirmar.disabled;

  function pintar(atributo, datos, vacio) {
    modal.querySelectorAll('[' + atributo + ']').forEach(function (campo) {
      const clave = campo.getAttribute(atributo);
      const valor = datos ? datos[clave] : undefined;
      campo.textContent = valor && valor.trim() ? valor : (clave === 'nombre' ? vacio : (clave === 'descripcion' || clave === 'cargo' ? '' : '—'));
    });
  }

  function actualizar() {
    const colaborador = colaboradorSelect.value ? colaboradorSelect.selectedOptions[0] : null;
    const curso = cursoSelect.value ? cursoSelect.selectedOptions[0] : null;
    colaboradorId.value = colaboradorSelect.value;

    pintar('data-curso-campo', curso ? curso.dataset : null, 'Sin seleccionar');
    pintar('data-colaborador-campo', colaborador ? colaborador.dataset : null, 'Sin seleccionar');

    const urlFoto = colaborador ? colaborador.dataset.foto : '';
    if (urlFoto) {
      foto.src = urlFoto;
      foto.classList.remove('d-none');
      iniciales.classList.add('d-none');
    } else {
      foto.removeAttribute('src');
      foto.classList.add('d-none');
      iniciales.classList.remove('d-none');
      iniciales.textContent = colaborador ? colaborador.dataset.iniciales : '—';
    }

    const bloqueados = colaborador && colaborador.dataset.bloqueados
      ? colaborador.dataset.bloqueados.split(',') : [];
    const duplicado = Boolean(curso) && bloqueados.includes(cursoSelect.value);
    aviso.classList.toggle('d-none', !duplicado);
    confirmar.disabled = sinOpciones || duplicado;
  }

  modal.addEventListener('show.bs.modal', function (event) {
    const boton = event.relatedTarget;
    const datos = boton ? boton.dataset : {};
    form.reset();
    form.classList.remove('was-validated');
    origen.value = datos.origen || 'catalogo';
    cursoSelect.value = datos.cursoId || '';
    colaboradorSelect.value = datos.colaboradorId || '';
    // Desde el perfil, el colaborador queda elegido y bloqueado.
    colaboradorSelect.disabled = datos.colaboradorFijo === 'true' && Boolean(colaboradorSelect.value);
    actualizar();
  });

  colaboradorSelect.addEventListener('change', actualizar);
  cursoSelect.addEventListener('change', actualizar);

  form.addEventListener('submit', function (event) {
    colaboradorId.value = colaboradorSelect.value;
    if (!form.checkValidity() || !colaboradorId.value || confirmar.disabled) {
      event.preventDefault();
      event.stopPropagation();
    }
    form.classList.add('was-validated');
  });
})();
