(function () {
    const API = '/colaborador/api/asistente';
    const form = document.getElementById('aiForm');
    const input = document.getElementById('aiInput');
    const sendBtn = document.getElementById('aiSend');
    const messages = document.getElementById('aiMessages');
    const clearBtn = document.getElementById('aiClear');
    const quickButtons = Array.from(document.querySelectorAll('[data-prompt]'));
    const iniciales = (function () {
        const a = document.querySelector('.user-avatar');
        const t = a ? a.textContent.trim() : '';
        return t || 'Yo';
    })();


    function el(tag, clase, texto) {
        const e = document.createElement(tag);
        if (clase) e.className = clase;
        if (texto !== undefined && texto !== null) e.textContent = texto;
        return e;
    }

    function inline(padre, texto) {
        const partes = texto.split('**');
        for (let i = 0; i < partes.length; i++) {
            if (partes[i] === '') continue;
            padre.appendChild(i % 2 === 1 ? el('strong', null, partes[i]) : document.createTextNode(partes[i]));
        }
    }

    function renderTexto(contenedor, texto) {
        const lineas = texto.split('\n');
        let lista = null;
        for (let i = 0; i < lineas.length; i++) {
            const linea = lineas[i].trim();
            const esItem = /^([-*•])\s+/.test(linea);
            if (esItem) {
                if (!lista) {
                    lista = el('ul', 'ai-list');
                    contenedor.appendChild(lista);
                }
                const li = el('li');
                inline(li, linea.replace(/^([-*•])\s+/, ''));
                lista.appendChild(li);
            } else {
                lista = null;
                if (linea === '') continue;
                const p = el('p', 'ai-par');
                inline(p, linea);
                contenedor.appendChild(p);
            }
        }
    }

    function scrollAbajo() {
        messages.scrollTop = messages.scrollHeight;
    }

    function agregarMensaje(propio, texto) {
        const wrap = el('div', propio ? 'ai-message own' : 'ai-message');
        const burbuja = el('div', 'ai-bubble');
        if (propio) {
            burbuja.textContent = texto;
            wrap.appendChild(burbuja);
            wrap.appendChild(el('span', 'avatar avatar-sm bg-blue text-white', iniciales));
        } else {
            wrap.appendChild(el('div', 'assistant-logo small-logo', 'AI'));
            if (texto) renderTexto(burbuja, texto);
            wrap.appendChild(burbuja);
        }
        messages.appendChild(wrap);
        scrollAbajo();
        return burbuja;
    }

    async function post(url, cuerpo) {
        const r = await fetch(url, {
            method: 'POST',
            headers: window.csrfHeaders({'Content-Type': 'application/json'}),
            body: JSON.stringify(cuerpo || {})
        });
        let datos = null;
        try { datos = await r.json(); } catch (e) { /* sin cuerpo */ }
        if (!r.ok) {
            const msg = (datos && datos.error) ||
                (r.status === 401 ? 'Tu sesión expiró. Vuelve a iniciar sesión.' : 'No pudimos completar la acción.');
            throw new Error(msg);
        }
        return datos;
    }


    function tarjetaCurso(c, acciones) {
        const card = el('div', 'ai-card');
        card.appendChild(el('div', 'ai-card-title', c.nombre));
        const meta = [c.categoria, c.modalidad, c.horas ? c.horas + ' h' : null].filter(Boolean).join(' · ');
        if (meta) card.appendChild(el('div', 'ai-card-meta', meta));
        card.appendChild(el('div', 'ai-card-meta',
            'Inicio: ' + (c.fechaInicio || 'No registrada') + ' · Fin: ' + (c.fechaFin || 'No registrada')));
        card.appendChild(el('div', 'ai-card-meta', 'Tu estado: ' + c.estado));
        if (c.descripcion) card.appendChild(el('div', 'ai-card-desc', c.descripcion));
        for (let i = 0; i < acciones.length; i++) {
            if (acciones[i].tipo === 'SOLICITAR_CURSO' && acciones[i].id === c.id) {
                card.appendChild(botonAccion(acciones[i]));
            }
        }
        return card;
    }

    function tarjetaProyecto(p, acciones) {
        const card = el('div', 'ai-card');
        card.appendChild(el('div', 'ai-card-title', p.nombre));
        card.appendChild(el('div', 'ai-card-meta', 'Estado: ' + p.estado));
        if (p.url && p.url.indexOf('/colaborador/') === 0) {
            const a = el('a', 'ai-link', 'Ver en Explorar');
            a.href = p.url;
            card.appendChild(a);
        }
        for (let i = 0; i < acciones.length; i++) {
            if (acciones[i].tipo === 'SOLICITAR_PROYECTO' && acciones[i].id === p.id) {
                card.appendChild(botonAccion(acciones[i]));
            }
        }
        return card;
    }

    function listaFuentes(fuentes) {
        const caja = el('div', 'ai-card');
        caja.appendChild(el('div', 'ai-card-title', 'Publicaciones de los foros de SkillBridge'));
        for (let i = 0; i < fuentes.length; i++) {
            const f = fuentes[i];
            const fila = el('div', 'ai-source');
            if (f.url && f.url.indexOf('/colaborador/') === 0) {
                const a = el('a', 'ai-link', f.titulo);
                a.href = f.url;
                fila.appendChild(a);
            } else {
                fila.appendChild(el('span', null, f.titulo));
            }
            fila.appendChild(el('span', 'ai-card-meta', ' · ' + f.foro + (f.resuelta ? ' · ✓ Resuelta' : '')));
            caja.appendChild(fila);
        }
        return caja;
    }


    function botonAccion(accion) {
        const contenedor = el('div', 'ai-action');
        const boton = el('button', 'btn btn-sm btn-primary', accion.etiqueta);
        boton.type = 'button';
        contenedor.appendChild(boton);

        boton.addEventListener('click', function () {
            boton.style.display = 'none';
            const confirmacion = el('div', 'ai-confirm');
            confirmacion.appendChild(el('div', 'small', 'Confirma: ' + accion.etiqueta + '. Se enviará tu solicitud para aprobación.'));
            const motivo = el('input', 'form-control form-control-sm mt-1');
            motivo.type = 'text';
            motivo.maxLength = 300;
            motivo.placeholder = accion.tipo === 'SOLICITAR_CURSO' ? 'Justificación (opcional)' : 'Mensaje (opcional)';
            confirmacion.appendChild(motivo);
            const ok = el('button', 'btn btn-sm btn-success mt-2 me-2', 'Confirmar');
            ok.type = 'button';
            const cancelar = el('button', 'btn btn-sm btn-outline-secondary mt-2', 'Cancelar');
            cancelar.type = 'button';
            confirmacion.appendChild(ok);
            confirmacion.appendChild(cancelar);
            contenedor.appendChild(confirmacion);

            cancelar.addEventListener('click', function () {
                confirmacion.remove();
                boton.style.display = '';
            });
            ok.addEventListener('click', async function () {
                ok.disabled = true;
                cancelar.disabled = true;
                try {
                    const url = accion.tipo === 'SOLICITAR_CURSO'
                        ? API + '/acciones/solicitar-curso' : API + '/acciones/solicitar-proyecto';
                    const cuerpo = accion.tipo === 'SOLICITAR_CURSO'
                        ? {cursoId: accion.id, justificacion: motivo.value}
                        : {proyectoId: accion.id, habilidadId: accion.habilidadId, mensaje: motivo.value};
                    const datos = await post(url, cuerpo);
                    confirmacion.remove();
                    contenedor.appendChild(el('div', 'text-success small', datos.mensaje));
                } catch (e) {
                    ok.disabled = false;
                    cancelar.disabled = false;
                    confirmacion.appendChild(el('div', 'text-danger small mt-1', e.message));
                }
            });
        });
        return contenedor;
    }


    async function enviar(texto) {
        texto = texto.trim();
        if (!texto) return;
        agregarMensaje(true, texto);
        input.value = '';
        input.disabled = true;
        sendBtn.disabled = true;
        const pensando = agregarMensaje(false, 'Pensando…');
        try {
            const r = await post(API + '/mensaje', {mensaje: texto});
            pensando.parentElement.remove();
            const burbuja = agregarMensaje(false, r.respuesta);

            const etiqueta = el('div', 'ai-origin ' + (r.iaDisponible ? '' : 'ai-origin-warn'),
                !r.iaDisponible ? 'Modo sin IA: solo datos de SkillBridge'
                    : (r.usoDatosInternos ? 'Incluye información de SkillBridge' : 'Conocimiento general de la IA'));
            burbuja.appendChild(etiqueta);

            for (let i = 0; i < r.cursos.length; i++) burbuja.appendChild(tarjetaCurso(r.cursos[i], r.acciones));
            for (let i = 0; i < r.proyectos.length; i++) burbuja.appendChild(tarjetaProyecto(r.proyectos[i], r.acciones));
            if (r.fuentes.length > 0) burbuja.appendChild(listaFuentes(r.fuentes));
            scrollAbajo();
        } catch (e) {
            pensando.parentElement.remove();
            const b = agregarMensaje(false, '');
            b.appendChild(el('span', 'text-danger', e.message));
        } finally {
            input.disabled = false;
            sendBtn.disabled = false;
            input.focus();
        }
    }

    form.addEventListener('submit', function (e) {
        e.preventDefault();
        enviar(input.value);
    });

    quickButtons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            const p = btn.dataset.prompt;
            if (p.endsWith(' ')) {
                input.value = p;
                input.focus();
            } else {
                enviar(p);
            }
        });
    });

    clearBtn.addEventListener('click', async function () {
        try { await post(API + '/historial/limpiar', {}); } catch (e) { /* se limpia igual la pantalla */ }
        messages.innerHTML = '';
        bienvenida();
    });

    function bienvenida() {
        agregarMensaje(false,
            '¡Hola! Soy tu asistente SkillBridge. Puedo orientarte sobre tu perfil, recomendarte cursos del catálogo, ' +
            'decirte qué te falta para un proyecto, buscar en los foros y resolver dudas técnicas.\n' +
            '- Lo que sale de SkillBridge lo marco como "Según SkillBridge".\n' +
            '- Lo que explico por mi cuenta lo marco como "Conocimiento general (IA)".\n' +
            '- No cambio tu perfil ni te inscribo en nada sin tu confirmación.');
    }


    (async function iniciar() {
        bienvenida();
        try {
            const r = await fetch(API + '/historial', {headers: {'Accept': 'application/json'}});
            if (!r.ok) return;
            const historial = await r.json();
            for (let i = 0; i < historial.length; i++) agregarMensaje(i % 2 === 0, historial[i]);
        } catch (e) { /* sin historial */ }
    })();
})();