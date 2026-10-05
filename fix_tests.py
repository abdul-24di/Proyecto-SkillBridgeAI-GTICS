import sys

# Fix RmAsignacionTests.java
file_path = 'src/test/java/com/pucp/skillb_ia/RmAsignacionTests.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# The old call: finalizar(id, motivo, observacion, rmId)
# The new call: finalizar(id, motivo, observacion, calificacion, feedback, rmId)
content = content.replace(
    'asignacionService.finalizar(\n                  asignacion.getId(), MotivoFinalizacion.OTRO, "Fin de participaci\u00f3n acordado.", rm.getId());',
    'asignacionService.finalizar(\n                  asignacion.getId(), MotivoFinalizacion.OTRO, "Fin de participaci\u00f3n acordado.", null, null, rm.getId());'
)
content = content.replace(
    'asignacionService.finalizar(\n                  asignacion.getId(), MotivoFinalizacion.OTRO, "Fin de participacin acordado.", rm.getId());',
    'asignacionService.finalizar(\n                  asignacion.getId(), MotivoFinalizacion.OTRO, "Fin de participaci\u00f3n acordado.", null, null, rm.getId());'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# Fix RmDashboardTests.java
file_path2 = 'src/test/java/com/pucp/skillb_ia/RmDashboardTests.java'
with open(file_path2, 'r', encoding='utf-8') as f:
    content2 = f.read()

# Add EvaluacionService mock and pass to constructor
if 'EvaluacionService evaluacionService' not in content2:
    content2 = content2.replace(
        'import com.pucp.skillb_ia.service.rm.RmPresupuestoService;',
        'import com.pucp.skillb_ia.service.rm.RmPresupuestoService;\nimport com.pucp.skillb_ia.service.EvaluacionService;'
    )
    content2 = content2.replace(
        'RmPresupuestoService presupuestoService;',
        'RmPresupuestoService presupuestoService;\n    @Mock\n    EvaluacionService evaluacionService;'
    )

content2 = content2.replace(
    'RmViewController controller = new RmViewController(\n                  perfilService, colaboradorService, proyectoService, revisionService,\n                  asignacionService, solicitudService, certificadoService, foroService,\n                  reporteService, reporteExportService, cursoService, presupuestoService);',
    'RmViewController controller = new RmViewController(\n                  perfilService, colaboradorService, proyectoService, revisionService,\n                  asignacionService, solicitudService, certificadoService, foroService,\n                  reporteService, reporteExportService, cursoService, presupuestoService, evaluacionService);'
)

with open(file_path2, 'w', encoding='utf-8') as f:
    f.write(content2)

print("Done")
