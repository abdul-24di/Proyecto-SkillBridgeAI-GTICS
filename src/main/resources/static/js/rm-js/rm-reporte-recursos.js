/*
 * Los filtros y la paginación de las dos tablas se resuelven en el servidor (enlaces GET).
 * Modal de exportación: la descarga es un formulario GET con los filtros aplicados y las
 * casillas de contenido (funciona sin JS; el servidor valida que haya al menos una hoja).
 * En los filtros, "Hasta" no deja elegir meses anteriores a "Desde" (limitarHasta).
 * Aquí solo se avisa si los selectores de la página cambiaron sin pulsar "Aplicar",
 * se desactivan las columnas y el resumen de una hoja desmarcada, se impide descargar
 * sin hojas y se cierra el modal al iniciar la descarga.
 */
document.addEventListener("DOMContentLoaded", () => {
  limitarHasta();

  const modal = document.getElementById("exportReportModal");
  const form = document.getElementById("exportReportForm");
  const alerta = document.getElementById("exportUnappliedAlert");
  if (!modal || !form || !alerta) return;

  const filtros = [
    ["proyecto", modal.dataset.appliedProyecto],
    ["estado", modal.dataset.appliedEstado],
    ["prioridad", modal.dataset.appliedPrioridad]
  ];

  modal.addEventListener("show.bs.modal", () => {
    const desde = periodoElegido("anioDesde", "mesDesde");
    const hasta = periodoElegido("anioHasta", "mesHasta");
    // Un solo mes se aplica con "hasta" vacío.
    const hastaAplicado = modal.dataset.appliedHasta || modal.dataset.appliedPeriodo;
    const cambiados = (desde !== null && desde !== modal.dataset.appliedPeriodo)
      || (hasta !== null && hasta !== hastaAplicado)
      || filtros.some(([id, aplicado]) => {
        const selector = document.getElementById(id);
        return selector && selector.value !== (aplicado || "");
      });
    alerta.classList.toggle("d-none", !cambiados);
  });

  const hojas = Array.from(form.querySelectorAll(".export-hoja"));
  const resumen = document.getElementById("exportResumen");
  const hojaProyectos = document.getElementById("exportHojaProyectos");
  const sinHojas = document.getElementById("exportSinHojas");
  const boton = document.getElementById("exportExcelButton");

  const actualizarContenido = () => {
    form.querySelectorAll(".export-columnas").forEach((grupo) => {
      const hoja = document.getElementById(grupo.dataset.hoja);
      grupo.disabled = !(hoja && hoja.checked);
    });
    if (resumen && hojaProyectos) resumen.disabled = !hojaProyectos.checked;
    const ninguna = hojas.length > 0 && hojas.every((hoja) => !hoja.checked);
    if (sinHojas) sinHojas.classList.toggle("d-none", !ninguna);
    if (boton) boton.disabled = ninguna;
  };
  hojas.forEach((hoja) => hoja.addEventListener("change", actualizarContenido));
  actualizarContenido();

  form.addEventListener("submit", (evento) => {
    if (hojas.length > 0 && hojas.every((hoja) => !hoja.checked)) {
      evento.preventDefault();
      return;
    }
    const Modal = (window.bootstrap && window.bootstrap.Modal)
      || (window.tabler && window.tabler.bootstrap && window.tabler.bootstrap.Modal);
    const instancia = Modal && Modal.getInstance(modal);
    if (instancia) setTimeout(() => instancia.hide(), 0);
  });
});

/** "AAAA-MM" de un par de selectores de año y mes, o null si no están en la página. */
function periodoElegido(idAnio, idMes) {
  const anio = document.getElementById(idAnio);
  const mes = document.getElementById(idMes);
  return anio && mes ? `${anio.value}-${mes.value}` : null;
}

/*
 * "Hasta" no deja elegir un mes anterior a "Desde", posterior al mes actual ni más allá del máximo
 * de meses; "Desde" tampoco deja elegir meses futuros. Esas opciones se desactivan y, si lo elegido
 * queda fuera, se ajusta al límite. El servidor aplica la misma regla a "Hasta". El mes actual
 * viene del servidor (data-mes-actual) para no depender del reloj del navegador.
 */
function limitarHasta() {
  const ids = ["anioDesde", "mesDesde", "anioHasta", "mesHasta"];
  const [anioDesde, mesDesde, anioHasta, mesHasta] = ids.map((id) => document.getElementById(id));
  const grupo = document.getElementById("reportHasta");
  if (!anioDesde || !mesDesde || !anioHasta || !mesHasta || !grupo) return;
  const maximo = Number(grupo.dataset.maximoMeses) || 12;
  const indice = (anio, mes) => Number(anio) * 12 + Number(mes) - 1;
  const [anioActual, mesActual] = (grupo.dataset.mesActual || "").split("-");
  const actual = anioActual && mesActual ? indice(anioActual, mesActual) : Infinity;

  const ajustar = () => {
    Array.from(mesDesde.options).forEach((opcion) => {
      opcion.disabled = indice(anioDesde.value, opcion.value) > actual;
    });
    if (indice(anioDesde.value, mesDesde.value) > actual) {
      mesDesde.value = mesActual;
    }
    const inicio = indice(anioDesde.value, mesDesde.value);
    const fin = Math.max(inicio, Math.min(inicio + maximo - 1, actual));
    Array.from(anioHasta.options).forEach((opcion) => {
      const anio = Number(opcion.value);
      opcion.disabled = anio * 12 + 11 < inicio || anio * 12 > fin;
    });
    let elegido = indice(anioHasta.value, mesHasta.value);
    if (elegido < inicio || elegido > fin) {
      elegido = elegido < inicio ? inicio : fin;
      const anio = String(Math.floor(elegido / 12));
      // Si el año límite no está en la lista (fin en un año futuro), se queda en el último disponible.
      if (Array.from(anioHasta.options).some((opcion) => opcion.value === anio)) {
        anioHasta.value = anio;
        mesHasta.value = String((elegido % 12) + 1).padStart(2, "0");
      }
    }
    Array.from(mesHasta.options).forEach((opcion) => {
      const valor = indice(anioHasta.value, opcion.value);
      opcion.disabled = valor < inicio || valor > fin;
    });
    if (mesHasta.selectedOptions[0] && mesHasta.selectedOptions[0].disabled) {
      const primera = Array.from(mesHasta.options).find((opcion) => !opcion.disabled);
      if (primera) mesHasta.value = primera.value;
    }
  };

  [anioDesde, mesDesde, anioHasta, mesHasta].forEach((selector) => selector.addEventListener("change", ajustar));
  ajustar();
}
