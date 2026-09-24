import os
import re

# 1. Update Usuario.java
usuario_path = 'src/main/java/com/pucp/skillb_ia/model/Usuario.java'
with open(usuario_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'@Column\(length = 100\)\s*private String cargo;', '@ManyToOne(fetch = FetchType.LAZY)\n    @JoinColumn(name = "cargo_id")\n    private Cargo cargo;', content)
content = re.sub(r'public String getCargo\(\)\s*\{ return cargo; \}', 'public Cargo getCargo() { return cargo; }', content)
content = re.sub(r'public void setCargo\(String cargo\)\s*\{ this\.cargo = cargo; \}', 'public void setCargo(Cargo cargo) { this.cargo = cargo; }', content)

with open(usuario_path, 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Update DB Schema
sql_path = 'BDs_SQL/skillbridge_db_v4.sql'
with open(sql_path, 'r', encoding='utf-8') as f:
    sql = f.read()

# Add Cargo Table
cargo_table_sql = '''
-- =====================================================================
-- 1.5 CARGO
-- =====================================================================
CREATE TABLE cargo (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(100) NOT NULL UNIQUE,
    sueldo_junior       DECIMAL(10,2) NULL,
    sueldo_semi_senior  DECIMAL(10,2) NULL,
    sueldo_senior       DECIMAL(10,2) NULL,
    activo              BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

INSERT INTO cargo (nombre, sueldo_junior, sueldo_semi_senior, sueldo_senior) VALUES
    ('Backend Developer', 2000.00, 3500.00, 5000.00),
    ('Frontend Developer', 1800.00, 3200.00, 4800.00),
    ('UX Designer', 1900.00, 3300.00, 4900.00),
    ('DevOps Engineer', 2500.00, 4000.00, 6000.00);

'''
if 'CREATE TABLE cargo (' not in sql:
    sql = sql.replace('-- 2. USUARIO', cargo_table_sql + '-- 2. USUARIO')

# Modify Usuario table definition
sql = re.sub(r'cargo\s+VARCHAR\(100\)\s+NULL,', 'cargo_id                    BIGINT        NULL,', sql)
# Add FK to Usuario table definition
if 'fk_usuario_cargo' not in sql:
    sql = re.sub(r'CONSTRAINT fk_usuario_rol\s+FOREIGN KEY \(rol_id\) REFERENCES rol\(id\),', 
                 'CONSTRAINT fk_usuario_rol\n        FOREIGN KEY (rol_id) REFERENCES rol(id),\n\n    CONSTRAINT fk_usuario_cargo\n        FOREIGN KEY (cargo_id) REFERENCES cargo(id),', sql)

# Modify user inserts
sql = sql.replace('cargo, horas_disponibles', 'cargo_id, horas_disponibles')
sql = sql.replace('\'Backend Developer\'', '1') # Map Carlos Lopez to 'Backend Developer' (ID 1)

with open(sql_path, 'w', encoding='utf-8') as f:
    f.write(sql)

print('Refactor part 1 done.')
