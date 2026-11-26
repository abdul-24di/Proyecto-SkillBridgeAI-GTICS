(function () {
    // Uso: <input type="file" data-foto-preview="#img" data-foto-actual=".avatar-actual" data-foto-aviso="#aviso">
    // La vista previa reemplaza al avatar actual mientras haya un archivo elegido.
    var urlActual = null;

    function liberarUrl() {
        if (urlActual) {
            URL.revokeObjectURL(urlActual);
            urlActual = null;
        }
    }

    function alternar(selector, visible) {
        if (!selector) return;
        document.querySelectorAll(selector).forEach(function (el) {
            el.classList.toggle('d-none', !visible);
        });
    }

    document.addEventListener('change', function (e) {
        var campo = e.target;
        if (!(campo instanceof HTMLInputElement) || campo.type !== 'file' || !campo.dataset.fotoPreview) return;

        var img = document.querySelector(campo.dataset.fotoPreview);
        if (!img) return;
        var aviso = campo.dataset.fotoAviso ? document.querySelector(campo.dataset.fotoAviso) : null;

        liberarUrl();
        if (aviso) {
            aviso.textContent = '';
            aviso.classList.add('d-none');
        }

        // upload-guard.js vacía el campo si el archivo no es válido
        var archivo = campo.files && campo.files[0];
        if (!archivo) {
            img.removeAttribute('src');
            img.classList.add('d-none');
            alternar(campo.dataset.fotoActual, true);
            return;
        }

        img.onload = function () {
            if (aviso && img.naturalWidth !== img.naturalHeight) {
                aviso.textContent = 'La imagen seleccionada no es cuadrada (' + img.naturalWidth + '×' + img.naturalHeight
                    + ' px). Para una mejor presentación, se recomienda usar una imagen cuadrada.';
                aviso.classList.remove('d-none');
            }
        };
        urlActual = URL.createObjectURL(archivo);
        img.src = urlActual;
        alternar(campo.dataset.fotoActual, false);
        img.classList.remove('d-none');
    });
})();
