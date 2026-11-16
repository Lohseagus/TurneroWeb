# Suite Cucumber de CheTurno

Los tests de `testing/` pegan contra el backend real (`/api`). `npm test` corre todos los escenarios salvo los marcados `@known-bug`, `@wip` o `@production-only`.

La URL base sale de `API_BASE_URL` y por defecto es `http://localhost:8080/api`. El reset de datos no usa los endpoints que ya no existen (`DELETE /deleteAll`, `DELETE /especialidades/reset`): corre por `psql` los scripts de `staging/` (`deleteAll.sql`, `userinit.sql`).

## Qué hace falta

- Node 20 o superior
- `psql` (cliente de PostgreSQL)
- Postgres en `localhost:5432`, usuario `APP`, password `APP`, base `labprog`
- Backend Spring Boot en el puerto 8080, levantado **después** de un arranque limpio

Hibernate guarda en memoria el bloque de ids (`GenerationType.AUTO`, allocationSize 50). Un `TRUNCATE` no lo reinicia. Si volvés a correr `npm test` contra el mismo proceso, los centros dejan de nacer con id 1 y falla `4b_EsquemasTurno`. Reiniciá el backend antes de cada corrida.

## Correr en local con Docker

Desde la raíz del repo:

```bash
docker compose -f docker-compose.e2e.yml up -d --build --wait
./testing/scripts/run-e2e.sh
```

`run-e2e.sh` levanta Postgres y el backend, espera `GET /`, ejecuta `npm test` y al final carga la población demo.

## Correr contra un backend que ya está arriba

```bash
export API_BASE_URL=http://localhost:8080/api
export PGHOST=127.0.0.1 PGPORT=5432 PGUSER=APP PGPASSWORD=APP PGDATABASE=labprog

cd testing
npm ci
npm test
```

Variables útiles:

| Variable | Default |
| --- | --- |
| `API_BASE_URL` | `http://localhost:8080/api` |
| `PGHOST` / `PGPORT` / `PGUSER` / `PGPASSWORD` / `PGDATABASE` | `127.0.0.1` / `5432` / `APP` / `APP` / `labprog` |

Para volver a cargar solo el seed multi-tenant: `npm run db:reset`.

## Población demo

`npm run db:demo` carga `staging/poblacion_demo.sql`: 53 centros de todo el país (50 son hospitales y sanatorios reales, con datos de OpenStreetMap), unos 140 médicos, ~1800 pacientes y turnos pasados y futuros con fechas relativas a hoy. Es re-ejecutable, no toca las cuentas de `users` existentes y se puede correr con el backend levantado. Las cuentas para entrar están en `staging/CREDENCIALES.md`.

`run-e2e.sh` la carga al terminar los tests, así la base local queda lista para probar a mano (`SKIP_DEMO=1` lo saltea). La suite en sí nunca debe correrse contra una base con datos reales: empieza vaciando todas las tablas.

## Tags

```bash
npm test -- --tags "@smoke"
npm run test:known-bugs
```

`npm test` excluye `@known-bug`, `@wip` y `@production-only`. Esos últimos dos describen escenarios que solo aplican con `security.dev.mode=false`, o steps que todavía no están escritos.

Los usuarios de `staging/userinit.sql` usan password `password`. El superadmin de la corrida legacy es `superadmin@turnero.com`.

## CI

`.github/workflows/e2e-cucumber.yml` levanta Postgres como service, corre el jar del backend y después `npm test` en cada pull request. El workflow es independiente de los otros pipelines del repo.
