import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/dto/PmProyectoView.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'private final int cantidadPersonas;',
    'private final int cantidadPersonas;\n        private final java.math.BigDecimal horasSemanales;'
)

content = content.replace(
    'public RequisitoHabilidad(Long habilidadId, String habilidad, String nivel, int cantidadPersonas) {',
    'public RequisitoHabilidad(Long habilidadId, String habilidad, String nivel, int cantidadPersonas, java.math.BigDecimal horasSemanales) {'
)

content = content.replace(
    'this.cantidadPersonas = cantidadPersonas;\n        }',
    'this.cantidadPersonas = cantidadPersonas;\n            this.horasSemanales = horasSemanales;\n        }'
)

content = content.replace(
    'public int getCantidadPersonas() { return cantidadPersonas; }',
    'public int getCantidadPersonas() { return cantidadPersonas; }\n        public java.math.BigDecimal getHorasSemanales() { return horasSemanales; }'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

file_path2 = 'src/main/java/com/pucp/skillb_ia/service/pm/PmProyectoConsultaService.java'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

content2 = content2.replace(
    'new PmProyectoView.RequisitoHabilidad(\n                            req.getHabilidad().getId(),\n                            req.getHabilidad().getNombre(),\n                            req.getNivelRequerido().name(),\n                            req.getCantidadPersonas()))',
    'new PmProyectoView.RequisitoHabilidad(\n                            req.getHabilidad().getId(),\n                            req.getHabilidad().getNombre(),\n                            req.getNivelRequerido().name(),\n                            req.getCantidadPersonas(),\n                            req.getHorasSemanales()))'
)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)

