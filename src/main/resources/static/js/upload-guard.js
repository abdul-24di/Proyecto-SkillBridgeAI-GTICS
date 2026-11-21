(function () {
    var MAX_MB_POR_DEFECTO = 10;
    var MAX_MB_POR_ENVIO = 60;

    function maxMb(campo) {
        var valor = Number(campo.dataset.maxMb);
        return valor > 0 ? valor : MAX_MB_POR_DEFECTO;
    }

    function formatoPermitido(campo, archivo) {
        var accept = (campo.getAttribute('accept') || '').trim();
        if (!accept) return true;
        var nombre = archivo.name.toLowerCase();
        var tipo = (archivo.type || '').toLowerCase();
        return accept.split(',').some(function (regla) {
            regla = regla.trim().toLowerCase();
            if (!regla) return false;
            if (regla.charAt(0) === '.') return nombre.endsWith(regla);
            if (regla.slice(-2) === '/*') return tipo.indexOf(regla.slice(0, -1)) === 0;
            return tipo === regla;
        });
    }

    function mensajeError(campo, archivo) {
        if (!formatoPermitido(campo, archivo)) return 'Formato no permitido. Revisa los formatos indicados.';
        if (archivo.size > maxMb(campo) * 1024 * 1024) return 'El archivo supera el máximo de ' + maxMb(campo) + ' MB.';
        return null;
    }

    function limpiar(campo) {
        campo.classList.remove('is-invalid');
        var previo = campo.parentNode.querySelector('.js-upload-error');
        if (previo) previo.remove();

        campo.parentNode.querySelectorAll('.invalid-feedback:not(.js-upload-error)').forEach(function (e) {
            e.style.setProperty('display', 'none', 'important');
        });
    }

    function mostrar(campo, texto) {
        limpiar(campo);
        campo.classList.add('is-invalid');
        var aviso = document.createElement('div');
        aviso.className = 'invalid-feedback d-block js-upload-error';
        aviso.textContent = texto;
        campo.insertAdjacentElement('afterend', aviso);
    }


    document.addEventListener('change', function (e) {
        var campo = e.target;
        if (!(campo instanceof HTMLInputElement) || campo.type !== 'file') return;
        limpiar(campo);
        var archivos = Array.from(campo.files);
        for (var i = 0; i < archivos.length; i++) {
            var texto = mensajeError(campo, archivos[i]);
            if (texto) {
                campo.value = '';
                mostrar(campo, texto);
                return;
            }
        }
    }, true);


    document.addEventListener('submit', function (e) {
        var form = e.target;
        if (!form || !form.querySelectorAll) return;
        var total = 0;
        var bloqueado = false;
        form.querySelectorAll('input[type=file]').forEach(function (campo) {
            Array.from(campo.files).forEach(function (archivo) {
                total += archivo.size;
                var texto = mensajeError(campo, archivo);
                if (texto) {
                    campo.value = '';
                    mostrar(campo, texto);
                    bloqueado = true;
                }
            });
        });
        if (!bloqueado && total > MAX_MB_POR_ENVIO * 1024 * 1024) {
            alert('Los archivos suman más de ' + MAX_MB_POR_ENVIO + ' MB. Envía menos archivos a la vez.');
            bloqueado = true;
        }
        if (bloqueado) {
            e.preventDefault();
            e.stopImmediatePropagation();
        }
    }, true);
})();