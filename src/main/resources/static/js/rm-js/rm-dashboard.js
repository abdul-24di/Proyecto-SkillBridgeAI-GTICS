// "Requieren atención": ajusta la altura visible a las 3 primeras alertas; el resto queda en el scroll de la tarjeta.
(function () {
  const ALERTAS_VISIBLES = 3;

  function ajustarAltura() {
    const contenedor = document.querySelector('.attention-scroll');
    if (!contenedor) return;

    const alertas = contenedor.querySelectorAll('.attention-item');
    if (alertas.length <= ALERTAS_VISIBLES) {
      contenedor.style.maxHeight = 'none';
      return;
    }

    const ultimaVisible = alertas[ALERTAS_VISIBLES - 1];
    const estilos = getComputedStyle(contenedor);
    // El contenedor tiene position: relative, así que offsetTop se mide desde su borde superior.
    const alto = ultimaVisible.offsetTop + ultimaVisible.offsetHeight
        + parseFloat(estilos.paddingBottom);
    contenedor.style.maxHeight = Math.ceil(alto) + 'px';
  }

  document.addEventListener('DOMContentLoaded', ajustarAltura);
  window.addEventListener('resize', ajustarAltura);
})();
