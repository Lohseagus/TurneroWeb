module.exports = {
  default: {
    formatOptions: { snippetInterface: 'synchronous' },
    // @wip y @production-only ya documentan escenarios que solo aplican
    // con security.dev.mode=false. @known-bug marca fallos reales de la app.
    tags: 'not @known-bug and not @wip and not @production-only',
    timeout: 30000
  }
};
