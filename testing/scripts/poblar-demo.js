#!/usr/bin/env node
/**
 * Carga la población demo (staging/poblacion_demo.sql) en la base que indican
 * PGHOST / PGPORT / PGUSER / PGPASSWORD / PGDATABASE.
 * Se puede correr con el backend levantado y todas las veces que haga falta:
 * cada corrida regenera los datos con fechas relativas a hoy.
 * Uso: npm run db:demo
 */
const path = require('path');
const { runFile } = require('../support/db');

const resumen = runFile(path.resolve(__dirname, '../../staging/poblacion_demo.sql'));
process.stdout.write(resumen);
console.log('Población demo cargada. Cuentas de acceso: staging/CREDENCIALES.md');
