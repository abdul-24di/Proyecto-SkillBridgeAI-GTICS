
$(function () {
    var $app = $("#chatApp");
    if (!$app.length) { return; }

    var apiBase = $app.attr("data-api-base") || "/colaborador/api/chat";
    var paginaChat = $app.attr("data-page-url") || "";
    var proyectoId = $app.attr("data-project-id") || "";
    var usuarioInicial = $app.attr("data-user-id") || "";
    var nombreProyecto = $.trim($("#chatTitulo").text()) || $app.attr("data-project-name") || "Equipo";


    $("#proyectoSelect").on("change", function () {
        var id = $(this).val();
        if (id && paginaChat) {
            window.location.href = paginaChat + "?proyectoId=" + encodeURIComponent(id);
        }
    });

    if (!proyectoId) { return; }


    var usuarioId = "";
    var totalIntegrantes = 0;
    var hayContenido = false;
    var ultimaFirma = null;
    var forzarScroll = false;
    var contadorSolicitud = 0;
    var LIMITE = 1000;


    var ICON_GRUPO = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2"/><circle cx="10" cy="7" r="4"/><path d="M21 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>';
    var ICON_CHAT = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>';
    var ICON_ALERTA = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>';


    var COLORES = ["#206bc4", "#0ca678", "#f76707", "#ae3ec9", "#d6336c", "#1098ad", "#5c7cfa", "#e8590c"];

    function colorDe(texto) {
        var h = 0;
        texto = texto || "";
        for (var i = 0; i < texto.length; i++) { h = (h * 31 + texto.charCodeAt(i)) | 0; }
        return COLORES[Math.abs(h) % COLORES.length];
    }

    function avatar(nombre, iniciales, extraClase) {
        return $('<span class="sb-avatar" aria-hidden="true"></span>')
            .addClass(extraClase || "")
            .text(iniciales || "")
            .css("background-color", colorDe(nombre));
    }

    function primerNombre(nombreCompleto) {
        return $.trim(nombreCompleto || "").split(" ")[0] || "";
    }

    function cabecerasCsrf() {
        return (typeof window.csrfHeaders === "function") ? window.csrfHeaders() : {};
    }

    function textoError(xhr, porDefecto) {
        if (xhr && xhr.responseJSON && xhr.responseJSON.error) { return xhr.responseJSON.error; }
        if (xhr && xhr.status === 0) { return "Parece que no hay conexión. Revisa tu internet e inténtalo de nuevo."; }
        if (xhr && (xhr.status === 401 || xhr.status === 403)) { return "Tu sesión pudo haber expirado. Recarga la página para continuar."; }
        return porDefecto || "No pudimos completar la acción. Inténtalo de nuevo en unos segundos.";
    }

    function datosChat() {
        var datos = { proyectoId: proyectoId };
        if (usuarioId !== "") { datos.usuarioId = usuarioId; }
        return datos;
    }

    function subtituloGrupo() {
        if (totalIntegrantes > 0) {
            return "Chat del equipo · " + totalIntegrantes + (totalIntegrantes === 1 ? " integrante" : " integrantes");
        }
        return "Chat del equipo";
    }


    function pintarCabecera(nombre, subtitulo, iniciales, esGrupo) {
        $("#chatTitulo").text(nombre);
        $("#chatSubtitulo").text(subtitulo);
        var $av = $("#chatAvatar").empty().toggleClass("sb-avatar--group", esGrupo);
        if (esGrupo) {
            $av.css("background-color", "").html(ICON_GRUPO);
        } else {
            $av.css("background-color", colorDe(nombre)).text(iniciales || "");
        }
    }


    function mostrarEsqueleto() {
        hayContenido = false;
        var $sk = $('<div class="sb-skel" aria-hidden="true"></div>');
        for (var i = 0; i < 5; i++) { $sk.append('<div class="sb-skel__row"></div>'); }
        $("#messageList").empty().append($sk);
    }

    function mostrarVacio() {
        var esGrupo = usuarioId === "";
        var titulo = esGrupo ? "Aún no hay mensajes" : "Inicia la conversación";
        var texto = esGrupo
            ? "Sé la primera persona en escribirle al equipo."
            : "Escríbele a " + primerNombre($("#chatTitulo").text()) + " para empezar.";
        var $estado = $('<div class="sb-state"></div>')
            .append($('<div class="sb-state__icon"></div>').html(ICON_CHAT))
            .append($("<h3></h3>").text(titulo))
            .append($("<p></p>").text(texto));
        $("#messageList").empty().append($estado);
    }

    function mostrarErrorPanel(mensaje) {
        hayContenido = false;
        var $estado = $('<div class="sb-state sb-state--error"></div>')
            .append($('<div class="sb-state__icon"></div>').html(ICON_ALERTA))
            .append($("<h3></h3>").text("No pudimos cargar la conversación"))
            .append($("<p></p>").text(mensaje))
            .append('<button type="button" class="btn btn-outline-primary btn-sm js-reintentar">Reintentar</button>');
        $("#messageList").empty().append($estado);
    }

    $("#messageList").on("click", ".js-reintentar", function () {
        mostrarEsqueleto();
        cargarMensajes();
    });

    function mostrarError(mensaje) { $("#chatError").text(mensaje).prop("hidden", false); }
    function ocultarError() { $("#chatError").prop("hidden", true).text(""); }
    function marcarConexion(ok) { $("#chatEstadoConexion").prop("hidden", ok); }


    function cargarContactos() {
        $.ajax({ url: apiBase + "/companeros", method: "GET", data: { proyectoId: proyectoId }, cache: false })
            .done(function (contactos) {
                if (!$.isArray(contactos)) { errorLista(); return; }
                totalIntegrantes = contactos.length + 1;
                pintarContactos(contactos);
                actualizarPuntos();
            })
            .fail(errorLista);
    }


    function actualizarPuntos() {
        $.ajax({ url: apiBase + "/noleidos", method: "GET", data: { proyectoId: proyectoId }, cache: false })
            .done(function (conteos) {
                if (!conteos || typeof conteos !== "object") { return; }

                $("#contactList .sb-contact").each(function () {
                    var $boton = $(this);
                    var id = $boton.attr("data-usuario-id") || "";
                    var clave = (id === "") ? "grupo" : id;
                    var cantidad = conteos[clave] || 0;

                    var mostrar = cantidad > 0 && !$boton.hasClass("is-active");
                    $boton.toggleClass("has-unread", mostrar);
                });
            });
    }


    function errorLista() {
        $("#contactList").empty().append($('<p class="sb-chat__hint"></p>')
            .text("No pudimos cargar las conversaciones. Recarga la página para intentarlo de nuevo."));
    }

    function pintarContactos(contactos) {
        var $lista = $("#contactList").empty();

        $lista.append($('<div class="sb-chat__section"></div>').text("Equipo"));
        var $grupo = $('<button type="button" class="sb-contact"></button>')
            .toggleClass("is-active", usuarioId === "")
            .attr("data-usuario-id", "")
            .attr("data-nombre", nombreProyecto)
            .attr("data-subtitulo", subtituloGrupo())
            .attr("data-iniciales", "");
        $grupo.append($('<span class="sb-avatar sb-avatar--group" aria-hidden="true"></span>').html(ICON_GRUPO));
        $grupo.append($('<span class="sb-contact__text"></span>')
            .append($('<span class="sb-contact__name"></span>').text(nombreProyecto))
            .append($('<span class="sb-contact__role"></span>').text("Chat grupal")));
        $lista.append($grupo);

        $lista.append($('<div class="sb-chat__section"></div>').text("Mensajes directos"));
        if (contactos.length === 0) {
            $lista.append($('<p class="sb-chat__hint"></p>').text("Aún no hay otras personas en este proyecto."));
        }
        for (var i = 0; i < contactos.length; i++) {
            var c = contactos[i];
            var $boton = $('<button type="button" class="sb-contact"></button>')
                .attr("data-usuario-id", c.id)
                .attr("data-nombre", c.nombre)
                .attr("data-subtitulo", c.rol)
                .attr("data-iniciales", c.iniciales);
            $boton.append(avatar(c.nombre, c.iniciales));
            $boton.append($('<span class="sb-contact__text"></span>')
                .append($('<span class="sb-contact__name"></span>').text(c.nombre))
                .append($('<span class="sb-contact__role"></span>').text(c.rol)));
            $lista.append($boton);
        }

        if (usuarioId === "") { $("#chatSubtitulo").text(subtituloGrupo()); }


        if (usuarioInicial) {
            var $elegido = $('#contactList .sb-contact[data-usuario-id="' + usuarioInicial + '"]');
            usuarioInicial = "";
            if ($elegido.length > 0) { abrirChat($elegido); }
        }
    }

    $("#contactList").on("click", ".sb-contact", function () { abrirChat($(this)); });

    function abrirChat($boton) {
        var nuevoId = $boton.attr("data-usuario-id") || "";
        if (nuevoId === usuarioId && hayContenido) { return; }

        $("#contactList .sb-contact").removeClass("is-active").removeAttr("aria-current");
        $boton.addClass("is-active").attr("aria-current", "true");

        usuarioId = nuevoId;
        ultimaFirma = null;
        ocultarError();

        var esGrupo = usuarioId === "";
        pintarCabecera($boton.attr("data-nombre"), $boton.attr("data-subtitulo"), $boton.attr("data-iniciales"), esGrupo);
        $("#messageInput").attr("placeholder", esGrupo
            ? "Escribe un mensaje al equipo…"
            : "Escribe un mensaje a " + primerNombre($boton.attr("data-nombre")) + "…");

        mostrarEsqueleto();
        cargarMensajes();
        $("#messageInput").trigger("focus");
    }


    function cargarMensajes() {
        var token = ++contadorSolicitud;
        $.ajax({ url: apiBase + "/mensajes", method: "GET", data: datosChat(), cache: false })
            .done(function (mensajes) {
                if (token !== contadorSolicitud) { return; }
                if (!$.isArray(mensajes)) {
                    // Casi siempre es la página de login: la sesión venció
                    mostrarErrorPanel("Tu sesión pudo haber expirado. Recarga la página para continuar.");
                    return;
                }
                marcarConexion(true);
                pintarMensajes(mensajes);
                actualizarPuntos();

            })
            .fail(function (xhr) {
                if (token !== contadorSolicitud) { return; }
                if (hayContenido) {
                    marcarConexion(false);      // ya hay mensajes en pantalla: solo avisamos con una etiqueta discreta
                } else {
                    mostrarErrorPanel(textoError(xhr, "Inténtalo de nuevo en unos segundos."));
                }
            });
    }

    function pintarMensajes(mensajes) {
        var firma = mensajes.length + ":" + (mensajes.length ? mensajes[mensajes.length - 1].id : 0);
        if (hayContenido && firma === ultimaFirma) { return; }
        ultimaFirma = firma;

        var lista = $("#messageList");
        var estabaAbajo = !hayContenido || forzarScroll ||
            (lista[0].scrollHeight - lista.scrollTop() - lista[0].clientHeight <= 80);
        hayContenido = true;
        forzarScroll = false;

        if (mensajes.length === 0) {
            mostrarVacio();
            return;
        }

        var esGrupo = usuarioId === "";
        var previo = null;
        lista.empty();

        for (var i = 0; i < mensajes.length; i++) {
            var msg = mensajes[i];
            var inicioRacha = !previo || previo.esPropio !== msg.esPropio || previo.autorNombre !== msg.autorNombre;

            var $fila = $('<div class="sb-msg"></div>')
                .addClass(msg.esPropio ? "sb-msg--own" : "sb-msg--other")
                .toggleClass("sb-msg--start", inicioRacha);

            if (!msg.esPropio) {
                if (inicioRacha) {
                    $fila.append(avatar(msg.autorNombre, msg.autorIniciales, "sb-avatar--sm"));
                } else {
                    $fila.append('<span class="sb-avatar-spacer"></span>');
                }
            }

            var $cuerpo = $('<div class="sb-msg__body"></div>');
            if (!msg.esPropio && inicioRacha && esGrupo) {
                $cuerpo.append($('<div class="sb-msg__author"></div>').text(msg.autorNombre));
            }
            $cuerpo.append($('<div class="sb-msg__bubble"></div>')
                .append($('<span class="sb-msg__text"></span>').text(msg.contenido))
                .append($('<time class="sb-msg__time"></time>').text(msg.hora)));

            $fila.append($cuerpo);
            lista.append($fila);
            previo = msg;
        }

        if (estabaAbajo) { lista.scrollTop(lista[0].scrollHeight); }
    }


    function enviando(si) {
        $("#btnEnviar").prop("disabled", si);
        $("#messageInput").prop("readonly", si);
    }

    $("#chatForm").on("submit", function (e) {
        e.preventDefault();

        var texto = $.trim($("#messageInput").val());
        if (texto === "") { return; }
        if (texto.length > LIMITE) {
            mostrarError("El mensaje no puede superar los " + LIMITE + " caracteres.");
            return;
        }

        ocultarError();
        var datos = datosChat();
        datos.contenido = texto;
        enviando(true);

        $.ajax({ url: apiBase + "/enviar", method: "POST", headers: cabecerasCsrf(), data: datos })
            .done(function (respuesta) {
                if (!respuesta || typeof respuesta !== "object") {
                    mostrarError("Tu sesión pudo haber expirado. Recarga la página para continuar.");
                    return;
                }
                $("#messageInput").val("");
                forzarScroll = true;
                ultimaFirma = null;
                cargarMensajes();
            })
            .fail(function (xhr) {
                // El texto se conserva para que la persona pueda reintentar sin reescribirlo
                mostrarError(textoError(xhr, "No pudimos enviar tu mensaje. Inténtalo de nuevo."));
            })
            .always(function () {
                enviando(false);
                $("#messageInput").trigger("focus");
            });
    });

    $("#messageInput").on("input", ocultarError);


    pintarCabecera(nombreProyecto, subtituloGrupo(), "", true);
    mostrarEsqueleto();
    cargarMensajes();
    cargarContactos();


    // El servidor avisa por WebSocket que hay un mensaje nuevo; la pantalla vuelve a pedirlos por AJAX.
    function conectarWebSocket() {
        var protocolo = (window.location.protocol === "https:") ? "wss://" : "ws://";

        var cliente = new StompJs.Client({
            brokerURL: protocolo + window.location.host + "/ws-chat",
            reconnectDelay: 5000
        });

        cliente.onConnect = function () {
            cargarMensajes();

            cliente.subscribe("/user/queue/chat", function (frame) {
                var aviso = JSON.parse(frame.body);

                if (String(aviso.proyectoId) !== String(proyectoId)) { return; }

                var chatDelAviso = (aviso.usuarioId === null) ? "" : String(aviso.usuarioId);
                if (chatDelAviso === usuarioId) {
                    cargarMensajes();
                } else {
                    actualizarPuntos();
                }
            });
        };

        cliente.activate();
    }

    var hayWebSocket = (typeof StompJs !== "undefined");
    if (hayWebSocket) {
        conectarWebSocket();
    }

    setInterval(function () {
        if (!document.hidden) { cargarMensajes(); }
    }, hayWebSocket ? 30000 : 5000);



    document.addEventListener("shown.bs.tab", function (ev) {
        var destino = ev.target && ev.target.getAttribute && ev.target.getAttribute("data-bs-target");
        if (destino === "#tab-chat") {
            var lista = $("#messageList");
            lista.scrollTop(lista[0].scrollHeight);
        }
    });
});