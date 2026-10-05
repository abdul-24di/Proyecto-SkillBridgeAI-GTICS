lines = open('src/main/java/com/pucp/skillb_ia/model/ProyectoHabilidadRequerida.java', encoding='utf-8').readlines()
new_lines = []
for line in lines:
    if 'public ProyectoHabilidadRequeridaId getId()' in line:
        new_lines.append('    @Column(name = "horas_semanales", precision = 5, scale = 2)\n')
        new_lines.append('    private java.math.BigDecimal horasSemanales = new java.math.BigDecimal("20.00");\n\n')
    new_lines.append(line)
    if 'setCantidadPersonas(int' in line:
        new_lines.append('    public java.math.BigDecimal getHorasSemanales() { return horasSemanales; }\n')
        new_lines.append('    public void setHorasSemanales(java.math.BigDecimal horasSemanales) { this.horasSemanales = horasSemanales; }\n')
with open('src/main/java/com/pucp/skillb_ia/model/ProyectoHabilidadRequerida.java', 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

# Now Proyecto.java to add documentoUrl
lines2 = open('src/main/java/com/pucp/skillb_ia/model/Proyecto.java', encoding='utf-8').readlines()
new_lines2 = []
for line in lines2:
    if 'public Long getId()' in line:
        new_lines2.append('    @Column(name = "documento_contexto_url", length = 500)\n')
        new_lines2.append('    private String documentoContextoUrl;\n\n')
    new_lines2.append(line)
    if 'setFechaCreacion(LocalDateTime' in line:
        new_lines2.append('\n    public String getDocumentoContextoUrl() { return documentoContextoUrl; }\n')
        new_lines2.append('    public void setDocumentoContextoUrl(String documentoContextoUrl) { this.documentoContextoUrl = documentoContextoUrl; }\n')
with open('src/main/java/com/pucp/skillb_ia/model/Proyecto.java', 'w', encoding='utf-8') as f:
    f.writelines(new_lines2)
