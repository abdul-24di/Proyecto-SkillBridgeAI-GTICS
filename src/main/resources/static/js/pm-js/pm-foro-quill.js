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

    function selectLocalImage(quillInstance) {
        const input = document.createElement('input');
        input.setAttribute('type', 'file');
        input.setAttribute('accept', 'image/*');
        input.click();

        input.onchange = () => {
            const file = input.files[0];
            if (/^image\//.test(file.type)) {
                uploadToServer(file, quillInstance);
            } else {
                console.warn('You could only upload images.');
            }
        };
    }

    function uploadToServer(file, quillInstance) {
        const fd = new FormData();
        fd.append('image', file);
        
        fetch('/pm/foro/upload-imagen', {
            method: 'POST',
            body: fd
        })
        .then(response => {
            if(!response.ok) throw new Error("Upload failed");
            return response.json();
        })
        .then(result => {
            const range = quillInstance.getSelection(true);
            quillInstance.insertEmbed(range.index, 'image', result.url);
            quillInstance.setSelection(range.index + 1);
        })
        .catch(error => {
            console.error('Error uploading image', error);
            alert('Error al subir la imagen.');
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
