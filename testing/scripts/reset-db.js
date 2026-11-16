#!/usr/bin/env node
/**
 * Reinicia la base y carga staging/userinit.sql.
 * Uso: npm run db:reset
 */
const { seedMultiTenant } = require('../support/db');

seedMultiTenant();
console.log('Base reiniciada y datos multi-tenant cargados.');
