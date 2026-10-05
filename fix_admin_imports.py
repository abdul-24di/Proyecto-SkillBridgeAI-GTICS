import sys
import re

file_path = 'src/main/java/com/pucp/skillb_ia/controller/AdminViewController.java'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'import org.springframework.web.bind.annotation.RequestParam;',
    'import org.springframework.web.bind.annotation.RequestParam;\nimport org.springframework.web.bind.annotation.ModelAttribute;'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
