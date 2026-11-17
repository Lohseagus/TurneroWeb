# CheTurno - Sistema de Gestión de Turnos Médicos (SaaS)

CheTurno es una plataforma integral de gestión de turnos médicos diseñada bajo una arquitectura Software as a Service (SaaS). Permite a múltiples Centros de Atención Médica (tenants) operar de manera segura, aislada y eficiente sobre una única infraestructura compartida.

Este proyecto fue desarrollado como Trabajo Final de Desarrollo de Software, enfocándose en la escalabilidad, seguridad y la experiencia de usuario.

## Características Principales

### Para Pacientes
- **Autogestión 24/7**: Reserva, visualización y cancelación de turnos desde cualquier dispositivo.
- **Autenticación Flexible**: Acceso rápido con Google (OAuth2) o credenciales propias.
- **Notificaciones**: Confirmaciones y recordatorios automáticos por correo electrónico.
- **Lista de Espera Inteligente**: Inscripción automática a listas de espera cuando no hay cupo, con notificaciones de disponibilidad.

### Para Centros Médicos (SaaS)
- **Arquitectura Multi-Tenant**: Gestión aislada de múltiples clínicas (datos, médicos y pacientes segregados).
- **Gestión de Agenda**: Configuración avanzada de disponibilidad médica, excepciones (feriados/licencias) y prevención de conflictos.
- **Dashboard de Gestión**: Métricas clave (KPIs) de ocupación, ausentismo y satisfacción.
- **Auditoría Total**: Registro inmutable de todas las acciones críticas para trazabilidad y seguridad.

## Stack Tecnológico

El sistema implementa una arquitectura moderna de tres capas, dockerizada para facilitar el despliegue.

### Frontend
- **Framework**: Angular 19
- **Estilos**: CSS Moderno, Diseño Responsivo.
- **Gestión de Estado**: Servicios reactivos (RxJS).

### Backend
- **Framework**: Spring Boot 3 (Java 17)
- **Seguridad**: Spring Security + JWT (Stateless) + OAuth2.
- **Persistencia**: JPA / Hibernate.

### Base de Datos
- **PostgreSQL**.

### Infraestructura & DevOps
- **Contenedores**: Docker y Docker Compose.
- **Borde**: Caddy termina HTTPS y manda `/api` al backend. Nginx solo sirve el frontend.
- **CI/CD**: GitHub Actions. El detalle está en [docs/CI_CD.md](docs/CI_CD.md).

## Arquitectura del Sistema

El proyecto sigue una arquitectura limpia y modular.

### Modelo de Datos (DER)
![Diagrama ER](https://i.imgur.com/TL4dgqK.png)

## Instalación y Despliegue Local

Para ejecutar el proyecto en tu máquina local, asegúrate de tener instalado Docker y Docker Compose.

### Clonar el repositorio:
```bash
git clone https://github.com/Lohseagus/TurneroWeb.git
cd TurneroWeb
```

### Configurar Variables de Entorno:
Crea un archivo `.env` en la raíz con las credenciales de base de datos y claves de API (Google OAuth, SMTP).

El repo no trae secretos por defecto. Variables del backend que conviene conocer:

| Variable | Si falta |
|----------|----------|
| `SECURITY_DEV_MODE` | Vale `false`: se aplica el control de roles. `docker-compose.yml` (desarrollo) la pone en `true`, que deja todos los endpoints públicos. |
| `JWT_SECRET` | Con `SECURITY_DEV_MODE=true` el backend genera una clave temporal y los tokens se invalidan en cada reinicio. Con `false` no arranca. Generala con `openssl rand -base64 48`. |
| `MAIL_PASSWORD` | El backend arranca, pero no puede enviar mails por un SMTP con autenticación. |
| `ADMIN_SEED_ENABLED`, `ADMIN_SEED_PASSWORD` | No se crea ningún SUPERADMIN al arrancar. Para crearlo: `ADMIN_SEED_ENABLED=true` y una contraseña de 12 caracteres o más. En desarrollo alcanza con las cuentas de `staging/userinit.sql`. |


### Acceder a la Aplicación:
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080
- **Producción**: https://cheturno.site

## CI/CD

Cada PR a `master` compila el backend (`mvn test`, Java 17) y hace el build de producción del frontend. Al mergear, GitHub Actions arma las imágenes, las publica en GHCR y las despliega por SSH en la VM de `https://cheturno.site`. El `.env` y el volumen de Postgres no se tocan.

Cómo cargar los secrets, preparar el servidor la primera vez, proteger `master` y hacer rollback está en [docs/CI_CD.md](docs/CI_CD.md).

## Uso del Script `lpl`

El proyecto incluye un script `lpl` para simplificar la gestión de contenedores Docker. Este script proporciona comandos abreviados para las operaciones comunes de Docker Compose.

### Preparación:
Asegúrate de que el script tenga permisos de ejecución:
```bash
chmod +x lpl
```

### Comandos Disponibles:
- `./lpl build`: Construye las imágenes Docker del proyecto.
- `./lpl up`: Inicia todos los servicios en segundo plano.
- `./lpl down`: Detiene y elimina todos los contenedores.
- `./lpl restart <servicio>`: Reinicia un servicio específico (ej: `./lpl restart backend`).
- `./lpl restart-all`: Reinicia todos los servicios.
- `./lpl logs`: Muestra los logs en tiempo real de todos los servicios.
- `./lpl log <servicio>`: Muestra los logs de un servicio específico.
- `./lpl sh <servicio>`: Conecta a la shell interactiva de un contenedor (ej: `./lpl sh backend`).
- `./lpl mvn <comando>`: Ejecuta comandos Maven en el contenedor backend (ej: `./lpl mvn compile`).
- `./lpl test`: Ejecuta todos los tests BDD.
- `./lpl test --tags "<tag>"`: Ejecuta tests con un tag específico.
- `./lpl staging <archivo>`: Carga datos de staging desde `./staging/<archivo>` (sin extension .sql) a la base de datos.

Este script facilita el desarrollo local al reducir la necesidad de recordar comandos largos de Docker Compose.

## 🧪 Calidad y Testing

El proyecto sigue una estrategia de Desarrollo Guiado por Comportamiento (BDD) utilizando Cucumber y Gherkin.

- **Smoke Tests**: Verificación rápida de salud del sistema.
- **Pruebas de Aislamiento**: Garantía de seguridad entre tenants.
- **Flujos de Negocio**: Validación E2E de reserva y cancelación de turnos.

Para ejecutar los tests:
```bash
./lpl test
```

## 📚 Documentación

- [Documento Final de Ingeniería de Software](DFIS%20CheTurno-2.pdf)
- [Manual Técnico](MANUAL%20T%C3%89CNICO-1.pdf)

