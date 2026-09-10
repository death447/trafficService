import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

/** Task 3 helpers stay CJS for Node tests; Vite/Rollup cannot named-import that file without this. */
function workspaceCjsToEsm() {
  return {
    name: 'workspace-cjs-to-esm',
    transform(code, id) {
      if (!/[\\/]src[\\/]utils[\\/]workspace\.js$/.test(id)) return null
      if (!code.includes('module.exports')) return null
      const next = code.replace(
        /module\.exports\s*=\s*\{([^}]+)\}/,
        'export { hasRescuerAccess, hasParkingAccess, resolveLoginTarget, shouldStartGps, matchHangtag }'
      )
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
