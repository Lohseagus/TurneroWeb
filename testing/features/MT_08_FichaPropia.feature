# language: es

@ficha-propia
Característica: Ficha propia del personal

  El personal crea su ficha de paciente con los datos de su cuenta.
  No puede crear la de otra persona. Si ya existe una con su email, se vincula.

  Escenario: Un médico crea su ficha y no puede tomar la de otro
    Dado que me autentico como "medico@turnero.com" con password "password"
    Cuando consulto mi ficha de paciente
    Entonces no tengo ficha de paciente
    Cuando intento crear la ficha de "paciente1.santamaria@turnero.com"
    Entonces la ficha ajena queda rechazada
    Y el paciente con DNI 44444441 sigue siendo "María"
    Cuando creo mi ficha de paciente
    Entonces mi ficha de paciente queda con el email "medico@turnero.com" y el DNI 33333333
    Cuando vuelvo a crear mi ficha de paciente
    Entonces se vincula la misma ficha sin duplicarla
    Cuando borro la ficha de paciente que acabo de crear
    Y consulto mi ficha de paciente
    Entonces no tengo ficha de paciente
