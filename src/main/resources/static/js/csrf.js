// Token CSRF para las peticiones POST hechas con JavaScript (fetch o formularios creados al vuelo).
// Thymeleaf ya lo agrega solo en los <form th:action>; aquí se lee de los <meta> que pone cada topbar.
(function () {
  function meta(nombre) {
    const elemento = document.querySelector('meta[name="' + nombre + '"]');
    return elemento ? elemento.getAttribute('content') : null;
  }

  // Cabeceras para fetch: fetch(url, {method: 'POST', headers: csrfHeaders()})
  window.csrfHeaders = function (otras) {
    const cabeceras = Object.assign({}, otras);
    const token = meta('_csrf');
    if (token) cabeceras[meta('_csrf_header') || 'X-CSRF-TOKEN'] = token;
    return cabeceras;
  };

  // Campo oculto para un <form> armado con JavaScript.
  window.agregarCsrfAFormulario = function (form) {
    const token = meta('_csrf');
    if (!token) return;
    const campo = document.createElement('input');
    campo.type = 'hidden';
    campo.name = '_csrf';
    campo.value = token;
    form.appendChild(campo);
  };
})();
