# language: es

@recetas @hce @multi-tenant
Característica: Receta electrónica

  El médico emite una receta común, la farmacia verifica el código público
  y el paciente descarga el PDF. Una receta emitida no se edita.

  @recetas @multi-tenant
  Escenario: El médico emite una receta y no puede editarla
    Dado que preparo el turno de historia clínica del centro 1
    Y que el paciente de la receta tiene sexo y fecha de nacimiento
    Y que me autentico como "medico@turnero.com" con password "password"
    Cuando creo una consulta de historia clínica con diagnóstico CIE-10
    Entonces recibo status_code 201
    Cuando creo una receta de ibuprofeno para esa consulta
    Entonces recibo status_code 201
    Cuando emito la receta electrónica
    Entonces recibo status_code 200
    Y la receta emitida tiene identificador y huella
    Cuando intento modificar la receta emitida
    Entonces recibo status_code 409

  @recetas @multi-tenant
  Escenario: La farmacia verifica el código público sin ver la historia
    Dado que preparo el turno de historia clínica del centro 1
    Y que el paciente de la receta tiene sexo y fecha de nacimiento
    Y que me autentico como "medico@turnero.com" con password "password"
    Cuando creo una consulta de historia clínica con diagnóstico CIE-10
    Y creo una receta de ibuprofeno para esa consulta
    Y emito la receta electrónica
    Cuando verifico la receta sin autenticarme
    Entonces recibo status_code 200
    Y la verificación pública muestra el medicamento y no el diagnóstico

  @recetas @multi-tenant
  Escenario: El paciente descarga su receta y otra persona no
    Dado que preparo el turno de historia clínica del centro 1
    Y que el paciente de la receta tiene sexo y fecha de nacimiento
    Y que me autentico como "medico@turnero.com" con password "password"
    Cuando creo una consulta de historia clínica con diagnóstico CIE-10
    Y creo una receta de ibuprofeno para esa consulta
    Y emito la receta electrónica
    Dado que me autentico como "paciente1.santamaria@turnero.com" con password "password"
    Cuando descargo el PDF de la receta
    Entonces recibo un PDF de receta
    Dado que me autentico como "paciente2.santamaria@turnero.com" con password "password"
    Cuando descargo el PDF de la receta
    Entonces recibo status_code 403

  @recetas @multi-tenant
  Escenario: El médico anula la receta con motivo
    Dado que preparo el turno de historia clínica del centro 1
    Y que el paciente de la receta tiene sexo y fecha de nacimiento
    Y que me autentico como "medico@turnero.com" con password "password"
    Cuando creo una consulta de historia clínica con diagnóstico CIE-10
    Y creo una receta de ibuprofeno para esa consulta
    Y emito la receta electrónica
    Cuando anulo la receta sin motivo
    Entonces recibo status_code 400
    Cuando anulo la receta con motivo "Error de dosis"
    Entonces recibo status_code 200
    Y la receta figura anulada
