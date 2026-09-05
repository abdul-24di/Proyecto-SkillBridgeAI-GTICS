# Guía de topbar para vistas de Resource Manager

Esta guía define cómo reutilizar la misma topbar en todas las vistas de Resource Manager, evitando diferencias de ancho, altura, espaciado y comportamiento responsive entre páginas.

## Archivos compartidos

### Fragmento HTML

Ruta:

```text
src/main/resources/templates/fragments/rm-topbar.html
```

Este archivo contiene el fragmento HTML de la topbar. Recibe el parámetro `activeSection` y marca automáticamente como activa la opción correspondiente.

### Hoja de estilos

Ruta:

```text
src/main/resources/static/css/rm/rm-topbar.css
```

Este archivo contiene exclusivamente los estilos compartidos de la topbar. Es la única fuente de estilos para las siguientes clases:

```text
.topbar
.brand
.main-nav
.user-area
.notification-link
.notification-dot
.user-avatar
.user-meta
```

Estas clases no deben volver a declararse en el CSS específico de cada vista.

## Secciones disponibles

Los valores admitidos para `activeSection` son:

```text
dashboard
proyectos
colaboradores
asignaciones
talent-matching
```

Ejemplo para el dashboard:

```html
<header th:replace="~{fragments/rm-topbar :: topbar('dashboard')}"></header>
```

Ejemplo para una pantalla relacionada con asignaciones:

```html
<header th:replace="~{fragments/rm-topbar :: topbar('asignaciones')}"></header>
```

## CSS que debe cargar cada vista

Cada vista debe cargar primero Tabler, después los estilos comunes del RM, luego su CSS específico y finalmente el CSS compartido de la topbar:

```html
<link rel="stylesheet"
      href="../../static/tabler/css/tabler.min.css"
      th:href="@{/tabler/css/tabler.min.css}">

<link rel="stylesheet"
      href="../../static/css/rm/rm-common.css"
      th:href="@{/css/rm/rm-common.css}">

<link rel="stylesheet"
      href="../../static/css/rm/nombre-de-la-vista.css"
      th:href="@{/css/rm/nombre-de-la-vista.css}">

<link rel="stylesheet"
      href="../../static/css/rm/rm-topbar.css"
      th:href="@{/css/rm/rm-topbar.css}">
```

Las referencias relativas bajo `../../static/css/rm/` corresponden a vistas ubicadas en `templates/rm/`. Si una vista se encuentra en otra profundidad de carpetas, se debe ajustar únicamente el atributo `href`. Las expresiones `th:href` conservan la ruta `/css/rm/`.

## Configuración de Thymeleaf

El documento HTML debe declarar el espacio de nombres de Thymeleaf:

```html
<html lang="es" xmlns:th="http://www.thymeleaf.org">
```

## Ubicación dentro de la vista

La topbar debe colocarse dentro de `.app-shell` y antes del contenido principal:

```html
<body>
<div class="app-shell">

  <header th:replace="~{fragments/rm-topbar :: topbar('dashboard')}"></header>

  <main class="page-wrap">
    <!-- Contenido de la vista -->
  </main>

</div>
</body>
```

## Previsualización directa sin Thymeleaf

Las vistas actuales también contienen un fallback dentro del elemento `<header>`, ya que todavía pueden abrirse directamente como archivos HTML.

El patrón es el siguiente:

```html
<header class="topbar"
        th:replace="~{fragments/rm-topbar :: topbar('dashboard')}">

  <!-- Fallback para previsualización sin Thymeleaf -->
  <div class="container-xl">
    <!-- Copia exacta del contenido de rm-topbar.html -->
  </div>

</header>
```

Su comportamiento es:

- Al abrir el HTML directamente, el navegador ignora `th:replace` y muestra el contenido fallback.
- Al servir la vista desde Spring Boot, Thymeleaf reemplaza todo el `<header>` por el fragmento compartido.
- El fallback debe colocar `class="active"` en el enlace correspondiente a la vista.

Para crear nuevas vistas se puede copiar el fallback desde:

- `rm-dashboard.html`, si debe quedar activo `Dashboard`.
- `rm-revision-asignacion.html`, si debe quedar activo `Asignaciones`.

Cuando todas las vistas se sirvan exclusivamente desde controladores Spring, el fallback podrá eliminarse y se podrá dejar únicamente el elemento con `th:replace`.

## Medidas unificadas

La topbar compartida utiliza las siguientes medidas principales:

```css
min-height: 58px;
max-width: 1320px;
padding-left: 24px;
padding-right: 24px;
```

También utiliza:

```css
margin-inline: calc(50% - 50vw);
```

Esta regla hace que el fondo de la topbar ocupe todo el ancho de la ventana, mientras su contenido permanece centrado.

## Comportamiento responsive

- Debajo de `991px`, se oculta la información textual del usuario.
- Debajo de `767px`, la marca, el usuario y la navegación se reorganizan en varias filas.
- La navegación puede desplazarse horizontalmente si no dispone de espacio suficiente.

## Reglas para generar nuevas vistas

1. No copiar estilos de la topbar al CSS específico de la página.
2. Cargar siempre en este orden: Tabler, `rm-common.css`, CSS específico y `rm-topbar.css`.
3. Usar siempre el fragmento `fragments/rm-topbar`.
4. Enviar el valor correcto mediante `activeSection`.
5. Mantener la topbar antes de `<main class="page-wrap">`.
6. No sobrescribir sus dimensiones desde el CSS particular de la vista.
7. Incluir el fallback mientras las páginas necesiten funcionar como archivos HTML directos.
8. Colocar `class="active"` correctamente dentro del fallback.
9. Mantener la codificación UTF-8 para conservar correctamente textos y acentos.
10. No inventar rutas de navegación: los enlaces continúan usando `href="#"` hasta que existan controladores y rutas definitivas.

## Estado actual de la navegación

Los enlaces de la topbar todavía usan:

```html
href="#"
```

Esto se debe a que el proyecto aún no tiene controladores ni rutas definitivas para estas vistas. Cuando se implementen, se deberán actualizar los enlaces del fragmento compartido, preferiblemente usando `th:href`.
