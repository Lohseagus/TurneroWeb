# Auditoría de UX/UI — CheTurno

Auditoría de la plataforma (Angular en `frontend/cli`, Spring Boot en `backend`). No cambia código de la aplicación: describe cómo está armada hoy y cómo conviene simplificarla.

Se revisaron `MenuService`, `menu.config.ts`, `menu-section-state.ts`, `app.routes.ts`, los guards y las pantallas principales. Después se levantó el stack local con la población de `staging/` (53 centros, turnos de hoy y a futuro) y `security.dev.mode=false`, que es el modo en el que los roles se aplican de verdad. Los conteos de links se verificaron en el DOM, con cada panel abierto.

Las capturas están en [`docs/ux-audit/`](ux-audit/).

## Resumen ejecutivo

CheTurno ya tiene una cara pública clara y un módulo SaaS que se entiende. El problema está adentro: el menú no muestra el trabajo del rol, muestra la jerarquía de permisos. Como `SUPERADMIN` hereda administrador, médico, operador y paciente, el sidebar le ofrece **40 links en 7 grupos**. Un administrador de una clínica ve **27**. Varios de esos links son la misma pantalla con otro nombre.

Eso se agrava con tres cosas que se ven al usar la app:

1. **El paciente no puede ver sus turnos** cuando la seguridad está en modo producción. `GET /api/turno/paciente/{id}` cae en la regla `/api/turno/**`, que solo deja pasar a médico, operador y administrador. El dashboard responde con un toast genérico: «No tenés permiso para esa acción.»
2. **Hay varias pantallas para la misma tarea** (agenda, turnos, búsqueda avanzada, días excepcionales; métricas, auditoría, reporte, estadísticas de lista de espera) y varias pantallas que nadie enlaza.
3. **Conviven tres lenguajes visuales**: el dashboard con cards blancas, el listado con encabezado azul, y pantallas viejas en Bootstrap + Font Awesome. El copy mezcla voseo, tuteo y usted.

La salida no es rediseñar todo. Es armar el menú por el rol primario, meter lo heredado detrás de un modo de soporte, y unificar el cascarón de página (encabezado, tabla, vacío, toast).

## Cómo se arma el menú

`Role` en el backend y `ROLE_HIERARCHY` en el frontend dicen que cada rol incluye a los de abajo:

| Rol primario | Roles que viajan en `allRoles` |
| --- | --- |
| PACIENTE | PACIENTE |
| MEDICO | MEDICO, PACIENTE |
| OPERADOR | OPERADOR, PACIENTE |
| ADMINISTRADOR | ADMINISTRADOR, MEDICO, OPERADOR, PACIENTE |
| SUPERADMIN | SUPERADMIN y los cuatro anteriores |

`getMenuSectionsForUser` muestra una sección por cada rol heredado. Hay un parche: si el rol primario es `ADMINISTRADOR`, se ocultan las secciones `medico` y `gestion`. El superadmin no entra en ese parche, así que ve también «Panel de Médico» y «Gestión General».

Los ítems con `requiresPrimaryRole` (los perfiles y «Mi plan») solo aparecen si el rol primario coincide con el de la sección. Por eso un médico no ve «Mi Perfil» de paciente, pero sí el resto del panel de paciente.

Desde el commit que volvió colapsables los paneles, al entrar se abre solo el grupo del rol de mayor privilegio (y el grupo de la ruta activa). El resto sigue en la lista, cerrado. Para el operador, «Panel de Operador» y «Gestión General» se abren los dos, porque los dos están marcados con el rol `OPERADOR`.

«Inicio» (`/`) está en `SHARED_MENU_ITEMS` para cualquier sesión. El logo de la navbar ya va a `/`.

## Mapa por rol

Los números son links cliqueables del sidebar, sin contar divisores. «Al entrar» es lo que queda abierto antes de que la persona toque un panel.

### Público (sin sidebar)

La navbar muestra **Planes** e **Iniciar sesión**. No hay link a registro en la barra: está dentro del login («Crear cuenta»).

| Ruta | Pantalla | Cómo se llega |
| --- | --- | --- |
| `/` | Home | Logo, y «Inicio» si hay sesión |
| `/planes` | Planes y precios | Navbar y home |
| `/registro-saas` | Alta de un centro, 4 pasos | «Empezar la prueba» |
| `/ingresar` | Login en dos pasos + Google | Navbar |
| `/registro-usuario` | Alta de paciente | Desde el login |
| `/recuperar-contrasena` y `/reset-password` | La misma pantalla de recuperación | Login y mail |
| `/activate-account` | Activación | Mail |
| `/link-verificacion` | Puente de deep link | Mail |
| `/paciente-agenda` | Buscador de turnos, sin guard | Home («Ver turnos disponibles») y menú de paciente |

No hay ruta `**`. Una URL inexistente deja el layout vacío.

### PACIENTE — 8 links, 2 grupos

Al entrar está abierto **Panel de Paciente** (7) más **General** (1).

| Grupo | Links |
| --- | --- |
| General | Inicio |
| Panel de Paciente | Mi Dashboard, Pedir turno, Mi Historial, Mi historia clínica, Mis Notificaciones (badge), Mi Perfil, Lista de Espera |

Después del login cae en `/paciente-dashboard`.

### MEDICO — 13 links, 3 grupos

Al entrar está abierto **Panel de Médico**. El de paciente queda cerrado, pero está.

| Grupo | Links |
| --- | --- |
| General | Inicio |
| Panel de Médico | Mi Dashboard, Mis Horarios, Historia clínica, Estadísticas, Turno manual (extra), Mi Perfil |
| Panel de Paciente | Los 6 del paciente, sin «Mi Perfil» |

«Turno manual (extra)» es `/turnos/new`. El médico puede crear un turno y no puede abrir el listado `/turnos` (`AdminOperadorGuard`).

### OPERADOR — 16 links, 4 grupos

Al entrar se abren **Panel de Operador** y **Gestión General**.

| Grupo | Links |
| --- | --- |
| General | Inicio |
| Panel de Paciente | 6 links, sin perfil |
| Panel de Operador | Mi Dashboard, Gestión de Turnos, Turno manual (extra), Pacientes, Mi Perfil, Agenda de turnos, Lista de Espera, Estadísticas Lista Espera |
| Gestión General | Mi Centro → `/mi-centro` → `/centrosAtencion/{id}` |

### ADMINISTRADOR — 27 links, 4 grupos

No ve Panel de Médico ni Gestión General. «Centros de Atención» se reescribe a **Mi Centro** y apunta a `/centrosAtencion/{id}` (verificado: `/centrosAtencion/1` para Clínica Santa María). Al entrar se abre solo **Panel de Administración**.

| Grupo | Links |
| --- | --- |
| General | Inicio |
| Panel de Paciente | 6 |
| Panel de Operador | 7 (sin perfil) |
| Panel de Administración | Dashboard, Panel Auditoria, Mi Centro, Médicos, Staff Médico, Obras Sociales, Operadores, Estadísticas Lista Espera, Metricas, Reporte de Atención, Encuestas, Mi Perfil, Mi plan |

«Estadísticas Lista Espera» está en el panel de operador y otra vez en el de administración. Son 27 links y 26 rutas distintas.

La navbar, además, muestra un botón de escudo que va a `/turnos/audit-dashboard`. El mismo destino ya está en el menú.

### SUPERADMIN — 40 links, 7 grupos

Al entrar se abre solo **Panel SuperAdmin** (9). Los otros cinco grupos de rol quedan como encabezados. Verificado en el DOM:

| Grupo | Links | Notas |
| --- | --- | --- |
| General | 1 | Inicio |
| Panel de Médico | 5 | Sin perfil |
| Panel de Paciente | 6 | Sin perfil |
| Panel de Operador | 7 | Sin perfil |
| Panel de Administración | 11 | Sin perfil ni Mi plan. «Centros de Atención» se renombra a **Gestión de Centros** |
| Panel SuperAdmin | 9 | Tablero SaaS, Gestión de Centros, Gestión de Admins, Auditoría Global, Métricas del Sistema, Suscripciones, Planes y precios, Pagos, Acceso Admin |
| Gestión General | 1 | Mi Centro → `/mi-centro` |

El superadmin no tiene `centroAtencionId`. «Mi Centro» redirige a `/` (`MiCentroRedirectGuard`). El grupo existe solo para mostrar un link que no lleva a ningún lado.

Seis rutas están dos veces, con etiquetas distintas o iguales:

| Ruta | Dónde aparece |
| --- | --- |
| `/turnos/new` | Turno manual, en médico y en operador |
| `/lista-espera-estadisticas` | Operador y administración |
| `/centrosAtencion` | «Gestión de Centros» en administración y otra vez en SuperAdmin, con el mismo texto |
| `/turnos/audit-dashboard` | «Panel Auditoria» y «Auditoría Global» |
| `/dashboard-gestion` | «Metricas» y «Métricas del Sistema» |
| `/admin-dashboard` | «Dashboard» y «Acceso Admin» |

Son 40 links y 34 rutas distintas. El escudo de la navbar suma un tercer acceso a la auditoría.

«Configuración» del menú de usuario no tiene caso `SUPERADMIN` en `goToUserProfile()`. Cae en el `default`, muestra un `alert` y **cierra la sesión**.

## Rutas que el menú no muestra

Detalle y alta (esperado que no estén en el menú): `*/new`, `*/:id`, reagendar, encuesta del turno, consulta de HCE, `saas/suscripciones/:id`.

Pantallas con ruta y guard, sin ítem de menú:

| Ruta | Qué es | Cómo se llega hoy |
| --- | --- | --- |
| `/config` | Ajustes de turnos y notificaciones | Card «Configuración del Sistema» del dashboard de admin |
| `/paciente-preferencias` | Horarios preferidos | Cards del dashboard y un link en la agenda |
| `/turnos/advanced-search` | Búsqueda de auditoría | Botón en turnos y en el dashboard de auditoría |
| `/operador-agenda` | Otra agenda | Solo desde un método del dashboard de operador |
| `/agenda/dias-excepcionales` | Feriados y horarios especiales | **Nadie la enlaza.** La pantalla existe y el servicio se usa por atrás |
| `/especialidades`, `/consultorios`, `/disponibilidades-medico`, `/esquema-turno` | ABMs sueltos | A veces desde las solapas de un centro. También son URLs directas |
| `/debug/tokens` | Estado del JWT | Sin guard. Cualquiera que conozca la URL |

Alias que sí resuelven: `/home`, `/superadmin-dashboard` → `/saas`, `/superadmin-auditoria` → auditoría, `/superadmin-metricas` → `/dashboard-gestion`.

Navegaciones que apuntan a rutas que no existen:

| Desde | Destino roto | Ruta real |
| --- | --- | --- |
| Ficha de centro, «volver» | `/centros-atencion` | `/centrosAtencion` |
| Ficha de centro | `/esquemas-turno/new` | `/esquema-turno/new` |
| Ficha de centro | `/disponibilidad-medicos` | `/disponibilidades-medico` |
| Activación de cuenta | `/registro` | `/registro-usuario` |
| Dashboard, horarios y perfil del médico | `/login` | `/ingresar` |
| Dashboard del médico | `/medico-notificaciones` | No hay pantalla |
| Dashboard del operador | `/operador-notificaciones` | No hay pantalla |

Cards del dashboard de operador que fallan en silencio o sacan de la tarea:

- «Gestionar Centros» va a `/centrosAtencion`. Esa lista es solo de superadmin. El guard manda al home.
- «Ver Reportes» va a `/turnos/audit-dashboard`. `AdminGuard` devuelve `false` sin redirigir: el click no hace nada.
- «Gestionar Operadores» sí entra. Un operador puede administrar a otros operadores.

En el dashboard de admin, «Dashboard de Auditoría» y «Reportes» van al mismo lugar. «Configuración General» abre el perfil, no la configuración. «Gestión de Usuarios» abre solo operadores. Tres de las cuatro tarjetas de «Estado del Sistema» son texto fijo («Sistema Online», «Sin alertas de seguridad», «Óptimo - 98% disponibilidad»). El único dato real es el conteo de usuarios.

## Lo que conviene conservar

**La cara pública y el alta SaaS.** El home separa pacientes y centros, usa voseo en la propuesta de valor y tiene una jerarquía visual clara. Planes muestra neto, IVA y el ciclo mensual/anual, con un plan recomendado. El alta (`/registro-saas`) es un asistente de cuatro pasos con el resumen del precio al lado. «Mi plan» repite ese lenguaje: estado, uso contra el tope, prueba y solo lectura, con `role="meter"` en las barras.

**El centro como ficha con solapas.** Detalle, especialidades, consultorios, staff y organigrama ya viven juntos en `/centrosAtencion/:id`. Esa es la información arquitectónica correcta. El problema es que los mismos conceptos también tienen ABMs sueltos en el menú.

**El shell de la app autenticada.** Navbar con persona y rol, sidebar con íconos Material, badge de notificaciones no leídas, paneles colapsables con `aria-expanded` y teclado, toast global, tokens en `styles.css` (`--space-*`, `--color-accent`) y colores por entidad en `entity-colors.css`. El login en dos pasos (mail, después contraseña, con la cuenta encontrada a la vista) es un buen patrón. Google está integrado con `asl-google-signin-button`.

**La historia clínica como idea.** Línea de tiempo, perfil, consentimiento para compartir entre centros y PDF para el paciente. El médico abre la consulta desde el turno. Falta pulir la pantalla, no inventar otra.

**La población de empty y loading en los listados nuevos.** Operadores, obras sociales, centros y lista de espera tienen estado vacío con ícono y título. El SaaS tiene reintento cuando el catálogo no carga.

## Problemas, por impacto y esfuerzo

Esfuerzo bajo: copy, un link, un guard, sacar un ítem. Esfuerzo medio: juntar dos pantallas o cambiar la regla del menú. Esfuerzo alto: un cascarón compartido para todos los listados y dashboards.

### Alto impacto, esfuerzo bajo

1. **El paciente no carga sus turnos.** Con la seguridad activa, `GET /api/turno/paciente/{id}` responde 403. El interceptor muestra «No tenés permiso para esa acción.» y el dashboard queda sin la lista que es la razón de esa pantalla. La regla de paciente (`/api/turno/historial/**` y `POST /api/turno/asignar`) está antes, pero el listado del paciente no entra ahí. Hay que permitirle leer sus propios turnos y, en el toast, decir qué falló.
2. **«Configuración» echa al superadmin.** `goToUserProfile()` y `getDashboardRoute()` no contemplan `SUPERADMIN`.
3. **«Mi Centro» del superadmin vuelve al home.** El grupo «Gestión General» no debería armarse si no hay `centroAtencionId`.
4. **Links rotos** de la tabla de arriba (`/login`, `/registro`, notificaciones que no existen, kebab-case distinto al de la ruta). Cada uno es un click que parece roto.
5. **Cards que mienten.** Reportes = auditoría. Configuración general = perfil. Gestionar centros, para un operador, lo saca al home. El 98% de disponibilidad no sale de ningún lado.
6. **Acentos y nombres.** «Panel Auditoria», «Metricas», «Panel de Operaciones» en el título contra «Panel de Operador» en el menú y «Mi Dashboard» en el ítem. Tres nombres para la misma puerta.
7. **`/debug/tokens` sin guard.**

### Alto impacto, esfuerzo medio

8. **El menú es la jerarquía, no el trabajo.** Un administrador de clínica ve «Pedir turno» y «Mi historia clínica» porque hereda paciente. Un médico ve el panel de paciente al lado del suyo, con otro «Mi Dashboard». Colapsar los paneles ayuda a escanear; no saca la duplicación. La regla de menú tiene que usar el rol primario. La herencia queda para la API.
9. **Agenda partida en cuatro.** `/agenda`, `/turnos`, `/operador-agenda` y `/turnos/advanced-search` hacen de lista filtrable. «Turno manual (extra)» es un ítem de menú en vez de una acción dentro de la agenda. Días excepcionales es una pantalla huérfana que la agenda necesita.
10. **Métricas partidas.** Dashboard de admin (cards), auditoría, «Metricas» (`/dashboard-gestion`), reporte de atención, estadísticas del médico y estadísticas de lista de espera. El admin no sabe cuál abrir. Auditoría (quién hizo qué) y gestión (ocupación, ausentismo) son dos preguntas distintas y hoy se mezclan en los nombres.
11. **Lista de espera fuera de contexto.** Si la búsqueda no tiene cupo, el vacío dice «Intenta cambiar los filtros». No ofrece anotarse. El formulario del paciente es un ítem suelto y, al abrirse, se recarga solo la página (`sessionStorage` `reloaded-lista-espera`) para «evitar un bug de contexto».
12. **Dos dueños de la disponibilidad.** El médico edita «Mis Horarios». El centro edita disponibilidades, esquemas y staff. No está dicho quién gana si los dos tocan lo mismo.
13. **HCE del médico pide ids crudos** (centro, médico, especialidad) para filtrar la línea de tiempo. La primera pintura es un «Cargando…» suelto, sin el encabezado del resto de la app.

### Impacto medio, esfuerzo bajo o medio

14. **Copy en tres registros.** El SaaS y parte del home usan voseo («Empezá», «pagás», «Elegí», «No tenés»). El interior usa tuteo («Gestiona tu salud», «Puedes combinar», «Intenta cambiar», «Ingresa tu contraseña») y los formularios de backoffice usan usted («Ingrese el nombre», «Seleccione un centro», «Configure feriados»). El botón de Google dice «Sign in with Google». El toast de sesión vencida dice «Su sesión ha expirado». Hay que elegir voseo para paciente y para el alta, y el mismo registro en los formularios del centro.
15. **Mobile.** En 390 px el botón de ingresar de la navbar queda un cuadrado azul vacío: el texto se corta y el ícono no se lee. En el dashboard del paciente el mail se corta y el toast tapa la barra. El sidebar en mobile sí se cierra al navegar; eso está bien.
16. **Mini sidebar.** Al colapsar, la barra angosta solo tiene «Cerrar sesión». No quedan los íconos de las secciones. El botón flotante de menú se superpone al contenido.
17. **Marca repetida.** Navbar, sidebar y, en el login, un tercer logotipo. En `/ingresar` la navbar sigue ofreciendo «Iniciar sesión».
18. **Accesibilidad desigual.** Los paneles del menú tienen `aria-expanded` y Enter/Espacio. Las cards de acción del paciente y del operador son `div` con `(click)`, sin `role` ni teclado; las del admin sí tienen `role="button"`. Hay pocos skip links. Varios filtros de HCE y de formularios viejos usan el placeholder como única etiqueta.
19. **Percepción de velocidad.** El dev server arma un `main.js` de unos 7 MB (sin minificar). En producción el budget de Angular permite hasta 5 MB. Se cargan Bootstrap, Font Awesome (solid, regular y shims), Material Symbols, Leaflet y el CSS del calendario aunque la pantalla no los use. `font-display: block` en los íconos evita el texto «dashboard» suelto, y está bien, pero las dos familias de íconos conviven: Material en lo nuevo y `fa-*` en centro, horarios del médico, días excepcionales y búsqueda avanzada.

### Impacto bajo

20. URLs en camelCase (`/obraSocial`, `/staffMedico`, `/centrosAtencion`) al lado de kebab-case (`/lista-espera`, `/mi-plan`).
21. El formulario de lista de espera y varios `alert()` conviven con el toast y con `ModalService`. El dashboard del paciente arma su propio overlay de error.
22. `app-kpi-card`, `app-filtros-dashboard` y `app-pagination` existen y casi no se reutilizan. Cada listado repite header, filtros, spinner y vacío.
23. Subtítulos de marketing en herramientas de trabajo: «Gestiona tu salud de manera inteligente», «Control total del sistema y usuarios», «Panel de control médico profesional».

## Propuesta de simplificación

Principio: **un rol, un trabajo.** La herencia autoriza endpoints. No apila paneles. El perfil y el cierre de sesión viven en el menú de la persona (arriba a la derecha), no como ítem por cada panel. «Inicio» del sidebar se va: el logo ya cumple, y el home de cada rol es su dashboard.

### Menú propuesto

#### Paciente — de 8 a 5

| Ahora | Propuesto |
| --- | --- |
| Inicio | — (logo) |
| Mi Dashboard | **Mis turnos** (próximos y pasados en solapas; el historial deja de ser otra pantalla) |
| Pedir turno | **Pedir turno** |
| Mi Historial | dentro de Mis turnos |
| Mi historia clínica | **Historia clínica** |
| Mis Notificaciones | **Avisos** (el badge puede pasar a una campana en la navbar) |
| Mi Perfil | menú de usuario |
| Lista de Espera | **Lista de espera**, y también el botón del vacío de «Pedir turno» |

#### Médico — de 13 a 4

Se saca el panel de paciente. Si el médico también se atiende, eso es otra cuenta o un cambio de rol explícito, no seis links extra.

| Ahora | Propuesto |
| --- | --- |
| Mi Dashboard | **Hoy** (turnos del día, presente/ausente, abrir consulta) |
| Historia clínica | **Historias clínicas** |
| Mis Horarios | **Mis horarios** |
| Estadísticas | **Mis números** |
| Turno manual (extra) | acción dentro de Hoy, no un ítem |
| Mi Perfil | menú de usuario |
| Panel de Paciente entero | fuera |

#### Operador — de 16 a 5

| Ahora | Propuesto |
| --- | --- |
| Panel de Paciente | fuera |
| Mi Dashboard | **Hoy** |
| Gestión de Turnos + Agenda de turnos + Turno manual + `/operador-agenda` | **Agenda** (calendario, lista, turno extra y días excepcionales como solapa) |
| Pacientes | **Pacientes** |
| Lista de Espera + Estadísticas | **Lista de espera** (la cola y los números en solapas) |
| Gestión General → Mi Centro | **Mi centro** (la ficha que ya existe) |
| Mi Perfil | menú de usuario |

«Gestionar centros», «Gestionar operadores» y «Ver reportes» salen del dashboard. Un operador no administra la red ni la auditoría global.

#### Administrador de centro — de 27 a 8

Un solo grupo, «El centro». Sin panel de paciente ni de operador: las tareas operativas están acá, una vez.

| Ahora | Propuesto |
| --- | --- |
| Dashboard + cards decorativas | **Hoy** (ocupación del día y accesos reales, sin el 98% inventado) |
| Agenda, turnos, turno manual (hoy en el panel de operador) | **Agenda** |
| Pacientes | **Pacientes** |
| Médicos + Staff + Operadores | **Equipo** (tres solapas) |
| Mi Centro + obras sociales + especialidades/consultorios/esquemas que hoy son rutas sueltas | **Mi centro** |
| Metricas + Reporte de Atención + estadísticas de lista de espera + Encuestas | **Resultados** (gestión, atención, lista de espera y encuestas como solapas) |
| Panel Auditoria + el escudo de la navbar | **Auditoría** (una sola) |
| Mi plan | **Mi plan** |
| Mi Perfil | menú de usuario |
| Panel de Paciente y Panel de Operador | fuera |

#### Superadmin — de 40 a 7

Solo la plataforma. Entrar a una clínica es una acción de soporte, no los cinco paneles heredados.

| Ahora | Propuesto |
| --- | --- |
| Tablero SaaS | **Plataforma** |
| Gestión de Centros (dos veces) + Gestión General / Mi Centro | **Centros** |
| Gestión de Admins | **Administradores** |
| Suscripciones | **Suscripciones** |
| Planes y precios | **Planes** |
| Pagos | **Pagos** |
| Auditoría Global + Métricas del Sistema + Acceso Admin + Panel de Médico + Paciente + Operador + Administración | **Soporte**: elegir un centro y verlo con el menú de ese administrador, con un banner «Estás viendo Clínica X». Al salir, vuelve el menú de plataforma |
| Configuración (hoy cierra la sesión) | menú de usuario, sin logout accidental |

### Pantallas para juntar o sacar del camino principal

| Juntar en | Sale del menú o se elimina como destino principal |
| --- | --- |
| Agenda | `/turnos`, `/operador-agenda`, `/turnos/new` como página suelta, `/agenda/dias-excepcionales` |
| Mis turnos (paciente) | `/paciente-historial` como ruta hermana; puede quedar de alias |
| Lista de espera | `/lista-espera-estadisticas` |
| Equipo | `/medicos`, `/staffMedico`, `/operadores` como ítems separados |
| Mi centro | `/especialidades`, `/consultorios`, `/disponibilidades-medico`, `/esquema-turno` como secciones de primer nivel |
| Resultados | `/dashboard-gestion`, `/admin-reporte-atencion`, `/admin/gestion-encuestas` |
| Auditoría | el botón de escudo y «Acceso Admin» cuando solo duplica `/admin-dashboard` |
| Cuenta | `/paciente-perfil`, `/medico-perfil`, `/operador-perfil`, `/admin-perfil` detrás de un solo `/cuenta` |
| — | `/debug/tokens` fuera de producción |
| — | `/config` pasa a una solapa de Mi centro o de Resultados, con un nombre que no compita con el perfil |

Las rutas viejas pueden quedar como redirect un release, para no romper mails ni marcadores (`/superadmin-*` ya hace eso).

### Componentes para estandarizar

Hoy hay al menos tres encabezados (`dashboard-header`, `listado-header`, `page-header` / `hce-header` / banner del centro), dos sets de íconos y vacíos que a veces son un `<p>` y a veces una card.

| Pieza | Qué unifica | Ya existe y hay que usarla |
| --- | --- | --- |
| `PageHeader` | Título, bajada corta, una acción primaria | El banner azul de planes y de Mi plan es el modelo |
| `DataTable` | Búsqueda, filtros, loading, vacío, paginado | `app-pagination` |
| `KpiCard` | Los números de Hoy, Resultados y Plataforma | `app-kpi-card` |
| `FilterBar` | Filtros de agenda, auditoría y resultados | `app-filtros-dashboard` |
| `EmptyState` | Mismo ícono, título y una acción | Varios listados ya lo tienen, copiado |
| `ConfirmDialog` | Confirmaciones | `ModalService`. Sacar overlays propios y `alert()` |
| Toast | Éxito, error, permiso | Ya es global. El mensaje de 403 tiene que nombrar la acción |
| Íconos | Una sola familia | Material Symbols. Dejar de sumar Font Awesome en pantallas nuevas |
| `StatusBadge` | Estado del turno, de la suscripción, de la consulta | Los badges de Mi plan |

Formularios: label visible, error debajo del campo, voseo en el placeholder. El login ya lo hace. Los ABM de operador, admin y médico todavía dicen «Ingrese…».

## Roadmap

### Fase 1 — quick wins

No cambia la arquitectura de pantallas. Baja la confusión de la semana que viene.

- Dejar que el paciente lea `GET /api/turno/paciente/{id}` (solo el suyo) y ajustar el toast de 403.
- Menú por rol primario: no renderizar secciones heredadas. Es el cambio de mayor efecto sobre los 40 y los 27 links.
- No mostrar «Gestión General» sin centro. No ofrecer «Mi Centro» al superadmin.
- Arreglar `goToUserProfile` / `getDashboardRoute` para `SUPERADMIN`.
- Corregir los `navigate` rotos (`/login`, `/registro`, `/centros-atencion`, esquemas, disponibilidades, notificaciones inexistentes).
- Sacar o guardar `/debug/tokens`.
- En el dashboard de admin, una sola card de auditoría, perfil con el nombre «Perfil», y borrar el estado «98%» hasta que exista el dato.
- En el dashboard de operador, «Mi centro» en vez de «Gestionar centros», y ocultar reportes de auditoría.
- Acentos: Auditoría, Métricas. Un solo nombre por pantalla.
- En el vacío de «Pedir turno», un botón a la lista de espera.
- Pasar a voseo los textos de paciente, login y activación. El botón de Google, si se puede, en español.
- En mobile, que «Iniciar sesión» no colapse a un cuadrado vacío.

### Fase 2 — juntar pantallas

- Agenda única para operador y administrador, con turno extra y días excepcionales adentro.
- Mis turnos del paciente con solapas Próximos / Pasados.
- Lista de espera con solapa de números. Sacar el reload forzado del formulario.
- Equipo y Mi centro como las únicas puertas del catálogo. Los ABM sueltos quedan como rutas internas o redirects.
- Resultados (gestión, reporte, encuestas) separados de Auditoría.
- Modo soporte del superadmin: entra a un centro con el menú de administrador y un banner, en vez de heredar cinco paneles.
- Una ruta `/cuenta`.
- Página 404.

### Fase 3 — sistema de diseño y pulido

- Adoptar `PageHeader`, `DataTable`, `EmptyState` y `KpiCard` en los listados que todavía están en Bootstrap suelto.
- Una familia de íconos.
- Cards de acción como botones o links de verdad.
- Filtros de HCE con nombres, no con ids.
- Definir dueño de la disponibilidad (el centro propone, el médico confirma, o al revés) y dejar una sola pantalla para editarla.
- Revisar el peso del primer load: no cargar Leaflet, calendario y Font Awesome en todas las rutas.
- Pasada de mobile sobre tablas (scroll horizontal anunciado, filtros en drawer) y sobre el toast para que no tape la navbar.

## Qué se miró en el navegador

Stack local, cuentas de `staging/CREDENCIALES.md`, población demo cargada. Seguridad de API en modo producción.

| Captura | Qué muestra |
| --- | --- |
| [Home](ux-audit/public-home.png) | Hero y la tarjeta de búsqueda, que en realidad son tres botones, sin campo |
| [Home en 390 px](ux-audit/public-home-mobile.png) | El texto reflowea; el botón de ingresar queda un cuadrado azul |
| [Login](ux-audit/public-login.png) | Dos pasos, marca repetida, Google en inglés |
| [Planes](ux-audit/public-planes.png) | Neto, IVA, recomendado |
| [Paciente](ux-audit/paciente-dashboard.png) | Toast de permiso al entrar a su propio panel |
| [Médico](ux-audit/medico-dashboard.png) | Números del día, y debajo el panel de paciente heredado |
| [Operador](ux-audit/operador-dashboard.png) | «Gestionar Centros» al lado de la agenda del día |
| [Admin](ux-audit/admin-dashboard.png) | Paneles de paciente y operador además del suyo; «Control total del sistema» |
| [Mi plan](ux-audit/admin-mi-plan.png) | El patrón visual a repetir |
| [SaaS](ux-audit/superadmin-saas.png) | Tablero claro, y a la izquierda seis grupos que no son de plataforma |
| [Sidebar superadmin](ux-audit/superadmin-sidebar.png) | Panel SuperAdmin abierto y «Gestión General» al final |

Los conteos 8 / 13 / 16 / 27 / 40 salieron de leer los `<a class="sidebar__menu-link">` con la sesión de cada rol, no solo del archivo de configuración.
