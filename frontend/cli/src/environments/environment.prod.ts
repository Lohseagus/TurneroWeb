// Producción (https://cheturno.site).
// apiUrl es la ruta relativa /api. Caddy reenvía /api/* al backend sin sacarle el prefijo.
// Los servicios arman `${environment.apiUrl}/recurso` → /api/recurso. No agregues otro /api.
export const environment = {
  production: true,
  apiUrl: '/api',
  googleClientId: '792207143081-bjp6agdmp0a7aip4tq0ta35u1j50fuuk.apps.googleusercontent.com'
};
