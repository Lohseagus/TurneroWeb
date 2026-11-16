# Credenciales de acceso - CheTurno

Todas las cuentas de esta página usan la contraseña `password`.

- `staging/poblacion_demo.sql` crea todas las cuentas (las que falten) y los datos para que cada panel tenga contenido: 53 centros en todo el país, médicos con agenda, pacientes y turnos pasados y futuros.
- `staging/userinit.sql` es el juego mínimo de la suite multi-tenant: solo crea las marcadas con ✓ en la columna "userinit".

El backend además crea al arrancar un superadmin propio con las variables `ADMIN_SEED_*` (por defecto `admin@turneroweb.com`).

## Superadmin (acceso a todos los centros)

| Email | Nombre | userinit |
|-------|--------|----------|
| superadmin@turnero.com | Super Administrador | ✓ |

## Administradores y operadores (un centro cada uno)

| Centro | Administrador | Operadores | userinit |
|--------|---------------|------------|----------|
| Clínica Santa María | admin.santamaria@turnero.com | operador1.santamaria@turnero.com, operador2.santamaria@turnero.com | ✓ |
| Clínica del Sur | admin.delsur@turnero.com | operador1.delsur@turnero.com, operador2.delsur@turnero.com | ✓ |
| Consultorios del Sol | admin.delsol@turnero.com | operador1.delsol@turnero.com, operador2.delsol@turnero.com | ✓ |

Los otros 50 centros de la población (hospitales y sanatorios reales de todo el país) no tienen administrador ni operador propio: se ven y se gestionan con el superadmin.

## Médicos

| Email | Nombre | Especialidad | Atiende en | userinit |
|-------|--------|--------------|------------|----------|
| medico@turnero.com | Dr. Juan Pérez | Cardiología | Clínica Santa María (lun, mié y vie 8-11), Clínica del Sur (mar y jue 8-11), Consultorios del Sol (lun y mié 14-17) | ✓ |
| medico.pediatria@turnero.com | María García | Pediatría | Clínica Santa María (mar y jue 8-11) | |
| medico.traumatologia@turnero.com | Ricardo Benítez | Traumatología | Clínica Santa María (lun y mié 14-17) | |
| medico.clinica@turnero.com | Andrea Suárez | Clínica Médica | Clínica Santa María (mar y jue 14-17) | |

Estos cuatro tienen 9 meses de historial; el panel del médico muestra los turnos del primer centro de la lista. La población agrega unos 135 médicos más (nombres ficticios, sin cuenta de login), entre 2 y 4 por centro.

## Pacientes

| Email | Nombre | Centro donde tiene sus turnos | userinit |
|-------|--------|-------------------------------|----------|
| paciente1.santamaria@turnero.com | María González | Clínica Santa María | ✓ |
| paciente2.santamaria@turnero.com | José Ramírez | Clínica Santa María | ✓ |
| paciente1.delsur@turnero.com | Lucía Morales | Clínica del Sur | ✓ |
| paciente2.delsur@turnero.com | Fernando Silva | Clínica del Sur | ✓ |
| paciente1.delsol@turnero.com | Sofía Vargas | Consultorios del Sol | ✓ |
| paciente2.delsol@turnero.com | Diego Ortiz | Consultorios del Sol | ✓ |

Cada uno tiene unos 10 turnos pasados, 2 o 3 próximos (confirmados) y notificaciones. `userinit.sql` crea además `aguspalqui@hotmail.com`; la población no le asigna turnos. Los ~1800 pacientes generados usan `@example.com` y no tienen login.

## Cargar la población

```bash
cd testing && npm run db:demo        # contra la base de PGHOST/PGUSER/PGPASSWORD/PGDATABASE
./lpl staging poblacion_demo         # contra el stack de desarrollo
```

Se puede correr con el backend levantado y repetir cuando haga falta: borra los datos de dominio generados (turnos, agendas, médicos y pacientes sin login) y los regenera con fechas relativas a hoy. No borra ni modifica cuentas de `users`.
