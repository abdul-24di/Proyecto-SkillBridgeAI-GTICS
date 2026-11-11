

$(document).ready(function () {


    var apiBase = $("#apiBase").val();
    if (!apiBase) { apiBase = "/colaborador/api/chat"; }
    var paginaChat = $("#paginaChat").val();
    if (!paginaChat) { paginaChat = "/colaborador/chat"; }


    $(".channel-item[data-channel-id]").on("click", function () {
        window.location.href = paginaChat + "?proyectoId=" + $(this).attr("data-channel-id");
    });

    var proyectoId = $("#activeProjectId").val();
    if (!proyectoId) {
        return;
    }

    var usuarioId = "";
    var usuarioInicial = $("#activeUserId").val();
    var nombreProyecto = $("#activeProjectName").val();
    if (!nombreProyecto) {
        nombreProyecto = $(".channel-item[data-channel-id='" + proyectoId + "']").attr("data-channel-name");
    }
    if (!nombreProyecto) {
        nombreProyecto = "Equipo";
    }

    $("#messageInput").attr("maxlength", 1000);
    $("#chatTitulo").text(" " + nombreProyecto);


    function textoError(xhr, porDefecto) {
        if (xhr.responseJSON && xhr.responseJSON.error) {
            return xhr.responseJSON.error;
        }
        return porDefecto;
    }

    function datosChat() {
        var datos = { proyectoId: proyectoId };
        if (usuarioId !== "") {
            datos.usuarioId = usuarioId;
        }
        return datos;
    }


    function cargarContactos() {
        $.ajax({
            url: apiBase + "/companeros",
            method: "GET",
            data: { proyectoId: proyectoId },
            success: function (contactos) {
                mostrarContactos(contactos);
            },
            error: function () {
                $("#contactList").html('<div class="text-danger small p-3">No se pudo cargar la lista.</div>');
            }
        });
    }

    function mostrarContactos(contactos) {
        var lista = $("#contactList");
        lista.empty();

        var grupo = $('<button type="button" class="channel-item contacto-item active"></button>');
        grupo.attr("data-usuario-id", "");
        grupo.attr("data-nombre", "# " + nombreProyecto);
        grupo.attr("data-subtitulo", "Chat grupal del equipo");
        grupo.text("# Chat del equipo");
        lista.append(grupo);

        lista.append('<div class="channel-group-label">Mensajes directos</div>');

        for (var i = 0; i < contactos.length; i++) {
            var c = contactos[i];
            var boton = $('<button type="button" class="channel-item contacto-item"></button>');
            boton.attr("data-usuario-id", c.id);
            boton.attr("data-nombre", c.nombre);
            boton.attr("data-subtitulo", "Mensaje directo · " + c.rol);
            boton.text(c.nombre + " (" + c.rol + ")");
            lista.append(boton);
        }


        if (usuarioInicial) {
            var elegido = $('#contactList .contacto-item[data-usuario-id="' + usuarioInicial + '"]');
            if (elegido.length > 0) {
                elegido.click();
            }
            usuarioInicial = "";
        }
    }


    $("#contactList").on("click", ".contacto-item", function () {
        $(".contacto-item").removeClass("active");
        $(this).addClass("active");

        usuarioId = $(this).attr("data-usuario-id");
        $("#chatTitulo").text($(this).attr("data-nombre"));
        $("#chatSubtitulo").text($(this).attr("data-subtitulo"));
        $("#messageList").html('<div class="text-center py-5 text-secondary">Cargando mensajes...</div>');
        cargarMensajes();
    });

    // ---------- mensajes ----------
    function cargarMensajes() {
        var chatPedido = usuarioId;
        $.ajax({
            url: apiBase + "/mensajes",
            method: "GET",
            data: datosChat(),
            success: function (mensajes) {
                if (chatPedido !== usuarioId) {
                    return;
                }
                mostrarMensajes(mensajes);
            },
            error: function (xhr) {
                if (chatPedido !== usuarioId) {
                    return;
                }
                $("#messageList").html($('<div class="text-center py-5 text-danger"></div>')
                    .text(textoError(xhr, "No se pudo cargar el chat")));
            }
        });
    }

    function mostrarMensajes(mensajes) {
        var lista = $("#messageList");

        if (mensajes.length === 0) {
            lista.html('<div class="text-center py-5 text-secondary">Aún no hay mensajes en este chat.</div>');
            return;
        }

        var estabaAbajo = (lista[0].scrollHeight - lista.scrollTop() <= lista[0].clientHeight + 50);
        lista.empty();

        for (var i = 0; i < mensajes.length; i++) {
            var msg = mensajes[i];

            var fila = $('<div class="message-row"></div>');
            var avatar = $('<span class="avatar avatar-sm"></span>').text(msg.autorIniciales);

            var meta = $('<div class="message-meta"></div>');
            meta.append($("<strong></strong>").text(msg.autorNombre));
            meta.append($("<span></span>").text(msg.hora));

            var burbuja = $('<div class="message-bubble"></div>').text(msg.contenido);
            var caja = $("<div></div>").append(meta).append(burbuja);

            if (msg.esPropio) {
                fila.addClass("own");
                avatar.addClass("bg-blue text-white");
                fila.append(caja).append(avatar);
            } else {
                avatar.addClass("bg-blue-lt text-blue");
                burbuja.addClass("bg-light");
                fila.append(avatar).append(caja);
            }
            lista.append(fila);
        }

        if (estabaAbajo) {
            lista.scrollTop(lista[0].scrollHeight);
        }
    }

    // ---------- enviar ----------
    $("#chatForm").on("submit", function (e) {
        e.preventDefault();

        var texto = $.trim($("#messageInput").val());
        if (texto === "") {
            return;
        }

        var datos = datosChat();
        datos.contenido = texto;

        $("#btnEnviar").prop("disabled", true);

        $.ajax({
            url: apiBase + "/enviar",
            method: "POST",
            headers: csrfHeaders(),
            data: datos,
            success: function () {
                $("#messageInput").val("");
                cargarMensajes();
            },
            error: function (xhr) {
                alert(textoError(xhr, "No se pudo enviar el mensaje"));
            },
            complete: function () {
                $("#btnEnviar").prop("disabled", false);
                $("#messageInput").focus();
            }
        });
    });


    cargarContactos();
    cargarMensajes();
    setInterval(cargarMensajes, 5000); // se actualiza cada 5 segundos
});