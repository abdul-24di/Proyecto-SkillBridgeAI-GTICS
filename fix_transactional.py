import os

files_to_check = [
    "src/test/java/com/pucp/skillb_ia/ColaboradorCursoSolicitudTests.java",
    "src/test/java/com/pucp/skillb_ia/PmProyectoCancelacionTests.java",
    "src/test/java/com/pucp/skillb_ia/RmEducacionTests.java",
    "src/test/java/com/pucp/skillb_ia/RmNavegacionTests.java",
    "src/test/java/com/pucp/skillb_ia/RmPerfilTelefonoTests.java",
    "src/test/java/com/pucp/skillb_ia/RmProyectoCierreTests.java",
    "src/test/java/com/pucp/skillb_ia/RmTopbarTests.java"
]

for file_path in files_to_check:
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    if '@Transactional' not in content:
        # Add import if missing
        if 'import org.springframework.transaction.annotation.Transactional;' not in content:
            content = content.replace('import org.springframework.boot.test.context.SpringBootTest;',
                                      'import org.springframework.boot.test.context.SpringBootTest;\nimport org.springframework.transaction.annotation.Transactional;')

        # Add @Transactional annotation before class definition
        content = content.replace('@ActiveProfiles("test")\nclass', '@ActiveProfiles("test")\n@Transactional\nclass')
        
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Patched {file_path}")
    else:
        print(f"Skipped {file_path}")
