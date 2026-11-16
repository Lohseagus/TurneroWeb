# language: es

@saas
Característica: Suscripciones SaaS de CheTurno

  Escenario: Los planes públicos muestran precios netos, IVA y prueba de 30 días
    Cuando consulto el catálogo público de planes SaaS
    Entonces el catálogo incluye el plan "PROFESIONAL" con precio base 25000
    Y el catálogo incluye el plan "CONSULTORIO" con precio por profesional 18000
    Y el catálogo incluye el plan "CENTRO" con precio base 200000
    Y el catálogo incluye el plan "RED" con precio base 350000, 3 sedes incluidas y extra 120000
    Y la prueba gratis del catálogo dura 30 días
    Y el IVA del catálogo es 21
    Y el descuento anual del catálogo es 15

  Escenario: Un centro inicia la prueba sin tarjeta
    Cuando registro una organización de prueba con el plan "PROFESIONAL"
    Entonces la suscripción de prueba queda en estado "TRIAL"
    Y la prueba informada dura 30 días

  Escenario: El plan profesional rechaza una segunda sede
    Cuando registro una organización de prueba con el plan "PROFESIONAL"
    Y ingreso con el administrador de esa prueba
    Cuando intento crear una segunda sede en la suscripción de prueba
    Entonces la operación SaaS es rechazada por límite del plan

  Escenario: El pago simulado deja la suscripción activa
    Cuando registro una organización de prueba con el plan "CONSULTORIO"
    Y ingreso con el administrador de esa prueba
    Cuando pago la suscripción de prueba con ciclo "MENSUAL"
    Entonces mi suscripción SaaS queda en estado "ACTIVA"

  Escenario: El superadministrador gestiona suscripciones, prueba y precios
    Cuando registro una organización de prueba con el plan "PROFESIONAL"
    Y ingreso como superadministrador de CheTurno
    Entonces el listado SaaS incluye la organización de prueba
    Cuando el superadministrador suspende la suscripción de prueba
    Entonces la suscripción de prueba queda en estado "SUSPENDIDA"
    Cuando el superadministrador reactiva la suscripción de prueba
    Y el superadministrador cambia la suscripción de prueba al plan "CENTRO"
    Entonces la suscripción de prueba usa el plan "CENTRO"
    Cuando el superadministrador fija el precio base de "PROFESIONAL" en 26000
    Entonces el catálogo público muestra el plan "PROFESIONAL" a 26000
    Cuando el superadministrador restaura los precios y la prueba de referencia
