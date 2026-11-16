# language: es

@hce @multi-tenant
Característica: Historia clínica electrónica

  La evolución firmada no se reescribe. Operador, administrador, superadmin
  y un médico de otro centro no ven el contenido clínico. El paciente lee la propia.

  @hce @multi-tenant
  Escenario: El médico registra una consulta, no puede reescribirla y agrega una enmienda
    Dado que preparo el turno de historia clínica del centro 1
    Y que me autentico como "medico@turnero.com" con password "password"
    Cuando creo una consulta de historia clínica para ese turno con motivo "Cefalea"
    Entonces recibo status_code 201
    Cuando cierro la consulta de historia clínica
    Entonces recibo status_code 200
    Cuando intento modificar la consulta de historia clínica cerrada
    Entonces recibo status_code 409
    Cuando agrego una enmienda de historia clínica con motivo "Error de transcripción"
    Entonces recibo status_code 201
    Cuando consulto la consulta de historia clínica
    Entonces la consulta de historia clínica tiene una enmienda

  @hce @multi-tenant
  Escenario: Operador, administrador, superadmin y médico de otro centro no ven el contenido
    Dado que preparo una consulta cerrada de historia clínica en el centro 1
    Y que me autentico como "operador1.santamaria@turnero.com" con password "password"
    Cuando consulto la consulta de historia clínica
    Entonces recibo status_code 403
    Dado que me autentico como "admin.santamaria@turnero.com" con password "password"
    Cuando consulto la consulta de historia clínica
    Entonces recibo status_code 403
    Dado que me autentico como "superadmin@turnero.com" con password "password"
    Cuando consulto la consulta de historia clínica
    Entonces recibo status_code 403
    Cuando consulto los metadatos de historia clínica del paciente de prueba
    Entonces recibo status_code 200
    Dado que me autentico como "hce.otro.centro@turnero.com" con password "password"
    Cuando consulto la consulta de historia clínica
    Entonces recibo status_code 403

  @hce @multi-tenant
  Escenario: El paciente lee su historia y no la de otra persona
    Dado que preparo una consulta cerrada de historia clínica en el centro 1
    Y que me autentico como "paciente1.santamaria@turnero.com" con password "password"
    Cuando consulto mi historia clínica
    Entonces recibo status_code 200
    Cuando descargo el PDF de mi historia clínica
    Entonces recibo un PDF de historia clínica
    Dado que me autentico como "paciente2.santamaria@turnero.com" con password "password"
    Cuando consulto la consulta de historia clínica
    Entonces recibo status_code 403
