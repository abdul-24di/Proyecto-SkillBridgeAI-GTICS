document.addEventListener('DOMContentLoaded', function() {
    var toolbarOptions = [
        ['bold', 'italic', 'underline', 'strike'],        // toggled buttons
        ['blockquote', 'code-block'],
        [{ 'header': 1 }, { 'header': 2 }],               // custom button values
        [{ 'list': 'ordered'}, { 'list': 'bullet' }],
        [{ 'color': [] }, { 'background': [] }],          // dropdown with defaults from theme
        ['link', 'image'],
        ['clean']                                         // remove formatting button
    ];

    var TIPOS_IMAGEN = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    var MAX_IMAGEN_MB = 5;


    function mostrarErrorEditor(quillInstance, texto) {
        var contenedor = quillInstance.container.closest('.modal-body') || quillInstance.container.parentNode;
        var aviso = contenedor.querySelector('.js-editor-error');
        if (!aviso) {
            aviso = document.createElement('div');
            aviso.className = 'alert alert-danger small js-editor-error';
            contenedor.insertBefore(aviso, contenedor.firstChild);
        }
        aviso.textContent = texto;
        aviso.style.display = '';
    }

    function limpiarErrorEditor(quillInstance) {
        var contenedor = quillInstance.container.closest('.modal-body') || quillInstance.container.parentNode;
        var aviso = contenedor.querySelector('.js-editor-error');
        if (aviso) aviso.style.display = 'none';
    }

    function selectLocalImage(quillInstance) {
        const input = document.createElement('input');
        input.setAttribute('type', 'file');
        input.setAttribute('accept', 'image/*');
        input.click();

        input.onchange = () => {
            const file = input.files[0];
            if (!file) return;
            limpiarErrorEditor(quillInstance);

            if (TIPOS_IMAGEN.indexOf(file.type) === -1) {
                mostrarErrorEditor(quillInstance, 'Solo se permiten imágenes JPEG, PNG, GIF o WEBP.');
                return;
            }
            if (file.size > MAX_IMAGEN_MB * 1024 * 1024) {
                mostrarErrorEditor(quillInstance, 'La imagen no puede superar los ' + MAX_IMAGEN_MB + ' MB.');
                return;
            }
            uploadToServer(file, quillInstance);
        };
    }

    function uploadToServer(file, quillInstance) {
        const fd = new FormData();
        fd.append('image', file);

        fetch('/colaborador/foro/upload-imagen', {
            method: 'POST',
            headers: csrfHeaders(),
            body: fd
        })
            .then(async response => {
                let datos = null;
                try { datos = await response.json(); } catch (e) {  }
                if (!response.ok || !datos || !datos.url) {
                    throw new Error((datos && datos.error)
                        || 'No se pudo subir la imagen. Verifica que sea una imagen válida de hasta ' + MAX_IMAGEN_MB + ' MB.');
                }
                return datos;
            })
            .then(result => {
                const range = quillInstance.getSelection(true);
                quillInstance.insertEmbed(range.index, 'image', result.url);
                quillInstance.setSelection(range.index + 1);
            })
            .catch(error => {
                console.error('Error uploading image', error);

                const texto = (error instanceof TypeError)
                    ? 'No se pudo subir la imagen. Revisa tu conexión e inténtalo de nuevo.'
                    : error.message;
                mostrarErrorEditor(quillInstance, texto);
            });
    }

    // Inicializar editor para Nueva Publicación
    if(document.getElementById('editor-nueva-publicacion')) {
        var quillNuevo = new Quill('#editor-nueva-publicacion', {
            theme: 'snow',
            modules: {
                toolbar: {
                    container: toolbarOptions,
                    handlers: {
                        image: function() {
                            selectLocalImage(quillNuevo);
                        }
                    }
                }
            }
        });

        // Sync al enviar form
        var formNuevo = document.getElementById('form-nueva-publicacion');
        var inputNuevo = document.getElementById('input-nueva-publicacion');
        formNuevo.onsubmit = function() {
            inputNuevo.value = quillNuevo.root.innerHTML;
        };
    }

    // Inicializar editores para Respuestas
    var replyEditors = document.querySelectorAll('.editor-respuesta');
    replyEditors.forEach(function(editorDiv) {
        var quillRespuesta = new Quill(editorDiv, {
            theme: 'snow',
            modules: {
                toolbar: {
                    container: toolbarOptions,
                    handlers: {
                        image: function() {
                            selectLocalImage(quillRespuesta);
                        }
                    }
                }
            }
        });

        var formId = editorDiv.getAttribute('data-form-id');
        var formResp = document.getElementById(formId);
        var inputResp = document.getElementById('input-' + formId);
        
        formResp.onsubmit = function() {
            inputResp.value = quillRespuesta.root.innerHTML;
        };
    });
});
