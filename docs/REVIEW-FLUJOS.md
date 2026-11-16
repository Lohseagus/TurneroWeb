# Review de flujos: Médico y Paciente

> Revisión de UX de los flujos principales (paciente y médico) pensada para la
> presentación/demo. Basada en lectura del frontend (`app.routes.ts`,
> `config/menu.config.ts`, dashboards y pantalla de reserva).

## Caminos actuales (happy path)

**Paciente:** Home → "Ver turnos disponibles" → pantalla de búsqueda (filtra por
centro / especialidad / médico + mapa) → elegir slot → reservar. Luego:
"Mi Dashboard", "Mi Historial", "Mis Notificaciones".

**Médico:** Login → "Mi Dashboard" (stats de hoy/mañana/semana + lista "Mis Turnos"
con tabs próximos/pasados) → por cada turno ve paciente/centro/consultorio y un
**toggle presente/ausente**. Aparte: "Mis Horarios", "Estadísticas", "Perfil".

---

## Puntos de mejora (priorizados)

### Críticos (confunden en la demo)
1. **"Mi Agenda" del paciente estaba mal nombrada.** El ítem decía "Ver mis turnos
   programados" pero la pantalla es en realidad el **buscador/reserva**. 
   → *Hecho:* renombrado a **"Pedir turno"** ("Buscar disponibilidad y reservar").
2. **Faltaba una acción clara de "Pedir turno" para el paciente logueado.** La acción
   principal solo se llegaba desde el Home público. → El renombre del punto 1 ya la
   hace visible en el menú. *Mejora extra pendiente:* CTA destacado en el dashboard.
3. **El médico no puede "hacer nada médico".** Por turno solo hay toggle
   presente/ausente: no hay registro de la consulta (notas/diagnóstico), ni historia
   del paciente, ni "finalizar atención". Es el hueco más grande del flujo médico.
   → *Pendiente:* campo de observaciones + ver historial del paciente.

### Importantes
4. **Turnos del paciente dispersos** en Dashboard / "Mi Agenda" / "Mi Historial" sin
   quedar claro qué muestra cada uno. → Unificar en 1-2 vistas con tabs "Próximos / Pasados".
5. **Jerga interna en menús de usuario final:** "Sobreturnos", "Staff Médico",
   "Esquema de Turno", "Disponibilidad". → *Hecho (parcial):* "Sobreturnos" →
   **"Turno manual (extra)"** en médico y operador. Resto pendiente.
6. **"Turno manual" en el menú del médico** es una acción tipo admin en el panel del
   doctor. → Evaluar sacarla del flujo médico.
7. **"Mis Horarios" (médico edita su disponibilidad) se pisa con admin/operador.**
   → Definir dueño único de la disponibilidad.

### Pulido
8. **Demasiadas opciones de ubicación** en la reserva ("Mi Ubicación / Manual /
   Arrastre"). → Dejar 1 por defecto, resto como "avanzado".
9. **Lista de espera es un ítem suelto** en vez de ofrecerse en contexto ("no hay
   turnos → ¿te anoto en lista de espera?").
10. **Jerarquía de roles parchada** (hay un TODO en `menu.service.ts`: "MEDICO <
    ADMINISTRADOR no tiene sentido"). Para la demo, usar usuarios de **rol único**.

---

## Recomendación para la presentación
- Usar usuarios de **rol único**: un paciente puro, `medico@turnero.com`, y un admin
  de centro. Evitar el superadmin (menú gigante).
- Guion sugerido: **Paciente** reserva un turno → **Médico** lo ve en su dashboard y
  marca asistencia → **Admin** ve el histograma/métricas. Ese triángulo cuenta la
  historia completa.

---

## Nota de infraestructura (coordinación entre sesiones)
Existe una rama `cursor/ci-cd-github-actions-cae5` que agrega **CI/CD con GitHub
Actions** (`.github/workflows/ci-cd.yml`, `rollback.yml`, `cucumber.yml`), scripts de
deploy por SSH (`deploy/remote-deploy.sh`, `deploy/gha-deploy.sh`), un
`docker-compose.prod.yml` reescrito y `docs/CI_CD.md`. Cuando se mergee, **el deploy
pasa a ser automático por push** y reemplaza el build+scp manual. Coordinar, porque
ambos apuntan al mismo servidor de producción.
