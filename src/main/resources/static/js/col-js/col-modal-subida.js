

// Busca una alerta de la página (la que manda el servidor), ignorando las que están dentro de modales.
function buscarAlerta(pagina, selector) {
    const alertas = pagina.querySelectorAll(selector);
    for (let i = 0; i < alertas.length; i++) {
        if (!alertas[i].closest(".modal")) {
            return alertas[i];
        }
    }
    return null;
}

document.querySelectorAll(".modal form[enctype='multipart/form-data']").forEach(form => {
    // Caja roja que se crea sola al inicio del cuerpo del modal
    const cajaError = document.createElement("div");
    cajaError.className = "alert alert-danger small d-none";
    cajaError.setAttribute("role", "alert");
    form.querySelector(".modal-body").prepend(cajaError);

    function mostrarError(texto) {
        cajaError.textContent = texto;
        cajaError.classList.remove("d-none");
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        cajaError.classList.add("d-none");

        // Los campos obligatorios los avisa validacion-formularios.js debajo de cada campo
        if (!form.checkValidity()) {
            return;
        }

        const boton = form.querySelector("button[type='submit']");
        boton.disabled = true;

        try {
            const respuesta = await fetch(form.action, { method: "POST", body: new FormData(form) });

            // Si la sesión expiró, el servidor manda al login
            if (respuesta.url.includes("/login")) {
                window.location.assign(respuesta.url);
                return;
            }

            const html = await respuesta.text();
            const pagina = new DOMParser().parseFromString(html, "text/html");

            const alertaError = buscarAlerta(pagina, ".alert-danger");
            if (alertaError) {
                mostrarError(alertaError.textContent.trim());
                boton.disabled = false;
                return;
            }
            if (!respuesta.ok) {
                mostrarError("No se pudo subir el archivo. Verifica el formato y el tamaño e inténtalo de nuevo.");
                boton.disabled = false;
                return;
            }

            // Éxito: guardamos el mensaje y recargamos para ver los datos nuevos
            const alertaExito = buscarAlerta(pagina, ".alert-success");
            if (alertaExito) {
                sessionStorage.setItem("mensajeExitoModal", alertaExito.textContent.trim());
            }
            window.location.assign(respuesta.url);
        } catch (e) {
            mostrarError("No se pudo enviar el archivo. Revisa tu conexión e inténtalo de nuevo.");
            boton.disabled = false;
        }
    });
});

// Al limpiar: cada vez que se abre un modal, se oculta el error anterior
document.querySelectorAll(".modal").forEach(modal => {
    modal.addEventListener("show.bs.modal", () => {
        const caja = modal.querySelector(".alert.alert-danger.d-none, .alert.alert-danger.small");
        if (caja && caja.parentElement && caja.parentElement.classList.contains("modal-body")) {
            caja.classList.add("d-none");
        }
    });
});

// Mensaje de éxito después de recargar
const mensajeExitoModal = sessionStorage.getItem("mensajeExitoModal");
if (mensajeExitoModal) {
    sessionStorage.removeItem("mensajeExitoModal");
    const aviso = document.createElement("div");
    aviso.className = "alert alert-success";
    aviso.textContent = mensajeExitoModal;
    const cabecera = document.querySelector("main .page-header");
    if (cabecera) {
        cabecera.after(aviso);
    } else {
        document.querySelector("main").prepend(aviso);
    }
}