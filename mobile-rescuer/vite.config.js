import { createRequire } from 'node:module'
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const { rewriteCjsNamedExports } = createRequire(import.meta.url)('./cjsNamedExportsToEsm.cjs')

/** Utils stay CJS for Node tests; Vite cannot named-import those files without this. */
function workspaceCjsToEsm() {
  return {
    name: 'workspace-cjs-to-esm',
    enforce: 'pre',
    transform(code, id) {
      if (!/[\\/]src[\\/]utils[\\/][^/]+\.js$/.test(id)) return null
      if (!code.includes('module.exports')) return null
      const next = rewriteCjsNamedExports(code)
      if (next === code) return null
      return { code: next, map: null }
    }
  }
}

export default defineConfig({
  plugins: [workspaceCjsToEsm(), uni()],
  server: {
    port: 5174,
    host: true
  }
})
