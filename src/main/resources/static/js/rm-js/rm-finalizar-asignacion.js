const finalizarAsignacionForm = document.getElementById("finalizarAsignacionForm");

if (finalizarAsignacionForm) {
  const confirmar = finalizarAsignacionForm.querySelector('button[type="submit"]');
  const camposDeTexto = finalizarAsignacionForm.querySelectorAll("textarea[required]");

  function actualizarBoton() {
    confirmar.disabled = !finalizarAsignacionForm.matches(":valid")
      || !Array.from(camposDeTexto).every(campo => campo.value.trim());
  }

  finalizarAsignacionForm.addEventListener("input", actualizarBoton);
  finalizarAsignacionForm.addEventListener("change", actualizarBoton);
  finalizarAsignacionForm.closest(".modal").addEventListener("show.bs.modal", actualizarBoton);
  actualizarBoton();
}
