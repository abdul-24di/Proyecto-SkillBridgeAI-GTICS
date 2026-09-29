/* TASK-021: el "Fin estimado" no puede ser el mismo día ni anterior a la fecha de inicio elegida.
   Solo es una ayuda de interfaz: el servidor vuelve a validar ambas fechas. */
(() => {
  const inicio = document.getElementById("projectStartDate");
  const fin = document.getElementById("projectEndDate");
  if (!inicio || !fin) return;

  // Suma un día a "AAAA-MM-DD" en UTC para no depender de la zona horaria del navegador.
  const diaSiguiente = valor => {
    const fecha = new Date(valor + "T00:00:00Z");
    fecha.setUTCDate(fecha.getUTCDate() + 1);
    return fecha.toISOString().slice(0, 10);
  };

  const actualizarMinimoFin = () => {
    if (inicio.value) fin.min = diaSiguiente(inicio.value);
  };

  inicio.addEventListener("change", actualizarMinimoFin);
  actualizarMinimoFin();
})();
