import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/ColaboradorViewController.java'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix the constructor
content = re.sub(
    r'(ColaboradorDocumentoService colaboradorDocumentoService\) \{)',
    r'ColaboradorDocumentoService colaboradorDocumentoService,\n                                     EvaluacionService evaluacionService) {',
    content
)

content = content.replace(
    'this.colaboradorDocumentoService = colaboradorDocumentoService;\n    }',
    'this.colaboradorDocumentoService = colaboradorDocumentoService;\n        this.evaluacionService = evaluacionService;\n    }'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
