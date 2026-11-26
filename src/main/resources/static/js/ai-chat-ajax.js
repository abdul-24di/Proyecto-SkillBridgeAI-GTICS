
(function () {
    var RUTA = /\/asistente\/(mensaje|limpiar)$/;
    var ocupado = false;

    function caja() {
        return document.getElementById('aiMessages');
    }

    function bajar() {
        var c = caja();
        if (c) c.scrollTop = c.scrollHeight;
    }

    function rutaDe(form) {
        try {
            return new URL(form.action, window.location.href).pathname;
        } catch (e) {
            return '';
        }
    }


    function fila(texto, propia, esError) {
        var c = caja();
        var rm = c.classList.contains('rm-ai-messages');
        var f = document.createElement('div');
        f.className = (rm ? 'rm-ai-msg' : 'ai-message') + (propia ? ' own' : '');
        if (!propia) {
            var icono = document.createElement(rm ? 'span' : 'div');
            icono.className = rm ? 'ai-note-icon' : 'assistant-logo small-logo';
            icono.textContent = rm ? 'IA' : 'AI';
            f.appendChild(icono);
        }
        var b = document.createElement('div');
        b.className = rm ? 'rm-ai-bubble' : 'ai-bubble';
        if (esError) b.style.color = '#b42318';
        b.textContent = texto;
        f.appendChild(b);
        c.appendChild(f);
        bajar();
        return f;
    }

    function bloquear(si) {
        for (var i = 0; i < document.forms.length; i++) {
            var f = document.forms[i];
            if (!RUTA.test(rutaDe(f))) continue;
            var els = f.querySelectorAll('button, input[type=text]');
            for (var j = 0; j < els.length; j++) els[j].disabled = si;
        }
    }

    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || form.tagName !== 'FORM' || !caja()) return;
        var ruta = rutaDe(form);
        if (!RUTA.test(ruta)) return;

        e.preventDefault();
        if (ocupado) return;

        var esLimpiar = /limpiar$/.test(ruta);
        var boton = e.submitter || null;
        var campo = form.elements['mensaje'];
        if (campo && campo.tagName !== 'INPUT') campo = null;

        var texto = '';
        if (!esLimpiar) {
            texto = (boton && boton.name === 'mensaje') ? boton.value : (campo ? campo.value : '');
            texto = (texto || '').trim();
            if (!texto) return;
        }


        var datos = new URLSearchParams();
        for (var i = 0; i < form.elements.length; i++) {
            var el = form.elements[i];
            if (!el.name || el.disabled || el.type === 'submit' || el.type === 'button') continue;
            datos.append(el.name, el.value);
        }
        if (!esLimpiar) datos.set('mensaje', texto);

        ocupado = true;
        bloquear(true);
        var pensando = null;
        if (!esLimpiar) {
            fila(texto, true, false);
            pensando = fila('Pensando…', false, false);
            if (campo) campo.value = '';
        }

        fetch(new URL(form.action, window.location.href).href, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: datos.toString()
        })
            .then(function (r) {
                if (r.redirected && /\/login/.test(new URL(r.url).pathname)) {
                    window.location.href = r.url; // la sesión expiró
                    return null;
                }
                if (!r.ok) throw new Error('HTTP ' + r.status);
                return r.text();
            })
            .then(function (html) {
                if (html === null) return;
                var doc = new DOMParser().parseFromString(html, 'text/html');
                var nueva = doc.getElementById('aiMessages');
                if (!nueva) throw new Error('Respuesta inesperada');
                caja().innerHTML = nueva.innerHTML;
                var alerta = doc.querySelector('.alert-danger');
                if (alerta && alerta.textContent.trim()) fila(alerta.textContent.trim(), false, true);
                bajar();
            })
            .catch(function () {
                if (pensando) pensando.remove();
                fila('No pudimos obtener la respuesta. Inténtalo de nuevo.', false, true);
            })
            .then(function () {
                ocupado = false;
                bloquear(false);
                if (campo) campo.focus();
            });
    });


    bajar();
})();