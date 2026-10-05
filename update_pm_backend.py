import sys
import re

# UPDATE PM VIEW CONTROLLER
file_path = 'src/main/java/com/pucp/skillb_ia/controller/PmViewController.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import org.springframework.web.bind.annotation.RequestParam;', 'import org.springframework.web.bind.annotation.RequestParam;\nimport org.springframework.web.multipart.MultipartFile;')

content = content.replace(
    '@RequestParam(value = "habilidadesExtra", required = false) String habilidadesExtra,',
    '@RequestParam(value = "horasSemanalesHab", required = false) List<BigDecimal> horasSemanalesHab,\n            @RequestParam(value = "documentoProyecto", required = false) MultipartFile documentoProyecto,\n            @RequestParam(value = "habilidadesExtra", required = false) String habilidadesExtra,'
)

content = content.replace(
    'pmProyectoService.crear(\n                    principal.getUsuario(), nombre, finalDescripcion, fechaInicio, fechaFin,\n                    prioridad, justificacionPrioridad, presupuesto, justPresupuesto,\n                    colaboradoresRequeridos, horasSemanales, habilidadIds, niveles, cantidades);',
    'pmProyectoService.crear(\n                    principal.getUsuario(), nombre, finalDescripcion, fechaInicio, fechaFin,\n                    prioridad, justificacionPrioridad, presupuesto, justPresupuesto,\n                    colaboradoresRequeridos, horasSemanales, habilidadIds, niveles, cantidades, horasSemanalesHab, documentoProyecto);'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# UPDATE PM PROYECTO SERVICE
file_path2 = 'src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoService.java'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace('import org.springframework.stereotype.Service;', 'import org.springframework.stereotype.Service;\nimport org.springframework.web.multipart.MultipartFile;\nimport com.pucp.skillb_ia.service.ArchivoAlmacenamientoService;')

content2 = content2.replace(
    'private final NotificacionService notificacionService;',
    'private final NotificacionService notificacionService;\n    private final ArchivoAlmacenamientoService archivoService;'
)

content2 = content2.replace(
    'NotificacionService notificacionService) {',
    'NotificacionService notificacionService,\n                             ArchivoAlmacenamientoService archivoService) {'
)

content2 = content2.replace(
    'this.notificacionService = notificacionService;\n    }',
    'this.notificacionService = notificacionService;\n        this.archivoService = archivoService;\n    }'
)

content2 = content2.replace(
    'public Proyecto crear(Usuario pm, String nombre, String descripcion, LocalDate fechaInicio, LocalDate fechaFin,\n                          String prioridad, String justificacionPrioridad, BigDecimal presupuesto,\n                          String justPresupuesto, int colabsRequeridos, BigDecimal horasSemanales,\n                          List<Long> habilidadIds, List<String> niveles, List<Integer> cantidades) {',
    'public Proyecto crear(Usuario pm, String nombre, String descripcion, LocalDate fechaInicio, LocalDate fechaFin,\n                          String prioridad, String justificacionPrioridad, BigDecimal presupuesto,\n                          String justPresupuesto, int colabsRequeridos, BigDecimal horasSemanales,\n                          List<Long> habilidadIds, List<String> niveles, List<Integer> cantidades,\n                          List<BigDecimal> horasSemanalesHab, MultipartFile documentoProyecto) {'
)

doc_save_logic = """
        if (documentoProyecto != null && !documentoProyecto.isEmpty()) {
            String url = archivoService.subirArchivo(documentoProyecto, "documentos-proyectos");
            proyecto.setDocumentoContextoUrl(url);
        }
        proyecto = proyectoRepository.save(proyecto);
"""
content2 = content2.replace('proyecto = proyectoRepository.save(proyecto);', doc_save_logic)

# Replace the loop to assign hours
loop_regex = r'for \(int i = 0; i < habilidadIds.size\(\); i\+\+\) \{(.*?)\}'
# Wait, regex dotall
import re
match = re.search(r'for \(int i = 0; i < habilidadIds.size\(\); i\+\+\) \{.*?\}', content2, flags=re.DOTALL)
if match:
    old_loop = match.group(0)
    new_loop = old_loop.replace('hr.setCantidadPersonas(cantidades.get(i));', 'hr.setCantidadPersonas(cantidades.get(i));\n                if (horasSemanalesHab != null && i < horasSemanalesHab.size() && horasSemanalesHab.get(i) != null) {\n                    hr.setHorasSemanales(horasSemanalesHab.get(i));\n                }')
    content2 = content2.replace(old_loop, new_loop)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)

