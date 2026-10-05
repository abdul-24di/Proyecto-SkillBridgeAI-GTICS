# Resumen de Avances y Modificaciones - 05 de Octubre 2026

A continuación se detalla el registro de todos los cambios, correcciones y nuevas funcionalidades implementadas en el proyecto durante la sesión de hoy en la rama eature/cruds.

## 1. Refactorización de Validaciones (Backend Nativo Spring)
Cumpliendo con las restricciones académicas de la profesora (prohibición de validaciones mediante JavaScript en el frontend), se ha migrado la lógica de validación del Project Manager (PM) al backend:
* **Dependencia añadida:** Se agregó spring-boot-starter-validation al pom.xml.
* **Nuevos DTOs (Data Transfer Objects):** Se crearon clases DTO para manejar los formularios del PM (PmProyectoForm, PmActividadForm, PmPerfilDatosForm, PmPerfilPasswordForm) utilizando anotaciones JSR-380 (@NotNull, @NotBlank, @Size, @NotEmpty, etc.).
* **Controladores:** Se actualizó PmViewController para interceptar las solicitudes con @Valid y BindingResult, re-poblando los modelos y devolviendo los errores nativos a las vistas de Thymeleaf.
* **Vistas HTML:** Se reemplazó el uso de alertas y scripts JS por las etiquetas nativas 	h:field y 	h:errors para mostrar los mensajes de error directamente debajo de cada input de forma dinámica.

## 2. Creación de Proyectos (Reglas de Negocio)
* Se corrigió la vulnerabilidad que permitía crear un proyecto sin haber definido los requisitos de talento.
* Ahora es **obligatorio** registrar colaboradores y habilidades requeridas antes de poder guardar un proyecto. Si no se completa esta información (o la información base obligatoria), el backend rechaza la solicitud y notifica los errores en el formulario nativo de Spring.

## 3. Visor de Evidencias Integrado (Lightbox)
* El comportamiento anterior abría las evidencias en una nueva pestaña o requería usar el botón de 'Atrás' del navegador.
* **Nueva funcionalidad:** Se implementó un *Lightbox* puro (HTML/CSS/JS nativo, sin depender de los modales de Bootstrap para evitar conflictos de superposición o cierres inesperados en modales anidados).
* **Soporte PDF:** Ahora, al dar clic en 'Ver evidencia', la imagen o documento PDF se abre *por encima* de la ventana actual dentro de la misma pestaña. Para cerrarla, basta con hacer clic fuera del visor.
* **Tests unitarios actualizados:** Se actualizó la lógica de aserciones en RmCursoTests.java para que evalúe y acepte este nuevo comportamiento sin hacer fallar el comando mvn package / mvn test.

## 4. UI/UX: Mejora en Fotos de Perfil
* Se identificó un problema global con los estilos por defecto de *Tabler* que aplicaban un zoom indeseado (object-fit: cover) recortando los rostros en los avatares.
* Se forzó globalmente (inyectado en todas las plantillas .html) la regla object-fit: contain !important; background-size: contain !important; en las clases .avatar. Ahora las fotos de perfil se ven completas y centradas en todos los roles sin recortes raros.

## 5. Perfil de Usuario (PM)
* **Campo Teléfono:** Se añadió la opción para que el PM pueda registrar un 'Teléfono móvil' en su perfil, homologando esta funcionalidad con la que ya poseía el RM.
* **Validación de número:** Tiene la misma validación estricta (entre 7 y 20 caracteres numéricos, permitiendo un + opcional al inicio).
* **Persistencia:** Se modificaron PmPerfilDatosForm, PmPerfilService.java y PmViewController.java para que el campo se guarde correctamente en la entidad Usuario y se refleje en la base de datos de AWS.
* **Corrección de Error 500:** Se solucionó el Whitelabel Error Page al acceder al perfil del PM, causado por la ausencia del 	h:object en la etiqueta del formulario.

## Próximos pasos recomendados
* Las validaciones backend para el rol PM están completas. Convendría que el equipo revise si algún otro rol (RM o Colaborador) todavía está dependiendo de JS para validaciones críticas y aplicar el mismo patrón de DTOs.
* Probar el flujo completo en la instancia EC2 de AWS para garantizar que las subidas y lecturas de evidencias S3 funcionen con el nuevo Lightbox en el entorno de producción.
