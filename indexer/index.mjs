#!/usr/bin/env node
/**
 * Bundled Adonis Idea indexer — zero dependency on @shamar/* packages.
 * Usage: node index.mjs --path <appRoot> [--json]
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative, resolve, sep, extname, basename } from 'node:path'

const args = process.argv.slice(2)
function opt(name, fallback = null) {
  const i = args.indexOf(name)
  if (i >= 0 && args[i + 1]) return args[i + 1]
  return fallback
}
const asJson = args.includes('--json') || !process.stdout.isTTY
const root = resolve(opt('--path', process.cwd()))

function walk(dir, pred, out = []) {
  if (!existsSync(dir)) return out
  let entries
  try {
    entries = readdirSync(dir, { withFileTypes: true })
  } catch {
    return out
  }
  for (const e of entries) {
    if (e.name === 'node_modules' || e.name === '.git' || e.name === 'build' || e.name === 'dist') continue
    const p = join(dir, e.name)
    if (e.isDirectory()) walk(p, pred, out)
    else if (pred(p, e.name)) out.push(p)
  }
  return out
}

function read(p) {
  try {
    return readFileSync(p, 'utf8')
  } catch {
    return ''
  }
}

function lineOf(text, index) {
  if (index < 0) return 0
  return text.slice(0, index).split(/\r?\n/).length
}

function isAdonisApp(base) {
  return (
    existsSync(join(base, 'adonisrc.ts')) ||
    existsSync(join(base, 'adonisrc.js')) ||
    existsSync(join(base, 'ace.js')) ||
    existsSync(join(base, 'ace'))
  )
}

function packageJson(base) {
  const p = join(base, 'package.json')
  if (!existsSync(p)) return {}
  try {
    return JSON.parse(read(p))
  } catch {
    return {}
  }
}

function detectShamar(pkg, base) {
  const deps = { ...pkg.dependencies, ...pkg.devDependencies }
  return Boolean(
    deps['@shamar/adonis'] ||
      deps['@shamar/core'] ||
      existsSync(join(base, 'app', 'panels')) ||
      existsSync(join(base, 'config', 'shamar.ts')),
  )
}

/** Wire layer: package and/or app/wire (or wire views). */
function detectWire(pkg, base) {
  const deps = { ...pkg.dependencies, ...pkg.devDependencies }
  return Boolean(
    deps['@shamar/wire'] ||
      existsSync(join(base, 'app', 'wire')) ||
      existsSync(join(base, 'resources', 'views', 'wire')),
  )
}

function detectOrm(pkg, base) {
  const deps = { ...pkg.dependencies, ...pkg.devDependencies }
  const shamarCfg = join(base, 'config', 'shamar.ts')
  if (existsSync(shamarCfg)) {
    const text = read(shamarCfg)
    const m = text.match(/orm\s*:\s*['"`](lucid|mongoose)['"`]/)
    if (m) return m[1]
  }
  if (deps['mongoose'] || deps['@shamar/mongoose']) return 'mongoose'
  if (deps['@adonisjs/lucid']) return 'lucid'
  if (existsSync(join(base, 'database', 'migrations'))) return 'lucid'
  return 'unknown'
}

function indexViews(base) {
  const views = {}
  const components = {}
  const roots = [
    join(base, 'resources', 'views'),
    join(base, 'resources', 'views', 'wire'),
  ]
  // Also scan package views under node_modules/@shamar/adonis if present (optional)
  for (const rootDir of roots) {
    for (const file of walk(rootDir, (p) => p.endsWith('.edge'))) {
      let rel = relative(join(base, 'resources', 'views'), file).replace(/\\/g, '/')
      if (rel.startsWith('..')) continue
      const name = rel.replace(/\.edge$/, '').replace(/\//g, '.')
      views[name] = file
      // dotted + slash forms
      views[rel.replace(/\.edge$/, '')] = file
      // Tag components: @layouts.app, @components.button, @page (via components map)
      if (
        name.startsWith('components.') ||
        name.startsWith('layouts.') ||
        name.startsWith('partials.')
      ) {
        components[name] = file
        const short = name.replace(/^(components|layouts|partials)\./, '')
        if (short && !components[short]) components[short] = file
      }
    }
  }
  return { views, components }
}

function indexRoutes(base) {
  const routes = {}
  const files = [
    join(base, 'start', 'routes.ts'),
    join(base, 'start', 'routes.js'),
    ...walk(join(base, 'start', 'routes'), (p) => /\.(ts|js)$/.test(p)),
  ].filter(existsSync)

  const named =
    /\.as\(\s*['"`]([^'"`]+)['"`]\s*\)|router\.(get|post|put|patch|delete|any)\(\s*['"`]([^'"`]+)['"`]/g
  const asOnly = /\.as\(\s*['"`]([^'"`]+)['"`]\s*\)/g

  for (const file of files) {
    const text = read(file)
    let m
    while ((m = asOnly.exec(text))) {
      const name = m[1]
      // try to find preceding path
      const before = text.slice(Math.max(0, m.index - 200), m.index)
      const pathMatch = before.match(/['"`](\/[^'"`]*)['"`]\s*[,)]\s*$/)
      const methodMatch = before.match(/\.(get|post|put|patch|delete|any)\s*\(\s*$/)
      routes[name] = {
        name,
        uri: pathMatch?.[1] || '',
        methods: methodMatch ? [methodMatch[1].toUpperCase()] : [],
        path: file,
        line: lineOf(text, m.index),
      }
    }
    // also router.get('/path') without .as — index by path for soft completion
    const bare = /router\.(get|post|put|patch|delete|any)\(\s*['"`]([^'"`]+)['"`]/g
    while ((m = bare.exec(text))) {
      const uri = m[2]
      const key = uri.startsWith('/') ? uri : `/${uri}`
      if (!Object.values(routes).some((r) => r.uri === key)) {
        routes[`__path:${key}`] = {
          name: key,
          uri: key,
          methods: [m[1].toUpperCase()],
          path: file,
          line: lineOf(text, m.index),
        }
      }
    }
  }
  return routes
}

function ensureEnvKey(envKeys, name, patch = {}) {
  const cur = envKeys[name] || {
    path: null,
    line: 0,
    kind: 'env',
    detail: '',
    used_by: [],
  }
  const used = new Set(cur.used_by || [])
  for (const u of patch.used_by || []) used.add(u)
  envKeys[name] = {
    path: patch.path != null ? patch.path : cur.path,
    line: patch.line != null ? patch.line : cur.line,
    kind: patch.kind || cur.kind,
    detail: patch.detail || cur.detail,
    used_by: [...used],
  }
}

function indexEnv(base) {
  const envKeys = {}
  const envOptions = {}

  // 1) Declarations in dotenv files (0-based lines for OpenFileDescriptor).
  for (const name of ['.env', '.env.example', '.env.local', '.env.test']) {
    const file = join(base, name)
    if (!existsSync(file)) continue
    const text = read(file)
    text.split(/\r?\n/).forEach((line, idx) => {
      const m = line.match(/^\s*([A-Z][A-Z0-9_]*)\s*=(.*)$/)
      if (!m) return
      // Prefer .env over .env.example for navigation target.
      const existing = envKeys[m[1]]
      if (existing?.path && name !== '.env' && existing.detail === '.env') {
        return
      }
      ensureEnvKey(envKeys, m[1], {
        path: file,
        line: idx,
        kind: 'env',
        detail: name,
      })
    })
  }

  // 2) Schema keys from start/env.ts (Env.create / Env.schema).
  for (const rel of ['start/env.ts', 'start/env.js']) {
    const file = join(base, rel)
    if (!existsSync(file)) continue
    const text = read(file)
    const schemaRe =
      /^\s*([A-Z][A-Z0-9_]*)\s*:\s*Env\.schema\./gm
    let m
    while ((m = schemaRe.exec(text))) {
      const key = m[1]
      const already = envKeys[key]
      ensureEnvKey(envKeys, key, {
        path: already?.path || file,
        line: already?.path ? already.line : Math.max(0, lineOf(text, m.index) - 1),
        kind: already?.kind === 'env' ? 'env' : 'schema',
        detail: already?.detail || 'start/env',
        used_by: [`${rel}:${lineOf(text, m.index)}`],
      })
    }
  }

  // 3) Usages in app / config / start — env.get / Env.get / process.env.
  const usageDirs = ['config', 'start', 'app', 'database']
  const usageRe =
    /(?:(?:env|Env)\.get\s*\(\s*['"]([A-Z][A-Z0-9_]*)['"]|process\.env\.([A-Z][A-Z0-9_]*))/g
  for (const dirName of usageDirs) {
    const dir = join(base, dirName)
    for (const file of walk(dir, (p) => /\.(ts|js|mjs|cjs)$/.test(p))) {
      const text = read(file)
      const rel = relative(base, file).replace(/\\/g, '/')
      let m
      usageRe.lastIndex = 0
      while ((m = usageRe.exec(text))) {
        const key = m[1] || m[2]
        if (!key) continue
        ensureEnvKey(envKeys, key, {
          kind: envKeys[key]?.kind || 'code',
          detail: envKeys[key]?.detail || 'code',
          used_by: [`${rel}:${lineOf(text, m.index)}`],
        })
      }
    }
  }

  // Common Adonis option catalogs (completion for env.get second arg / .env values).
  Object.assign(envOptions, {
    NODE_ENV: ['development', 'production', 'test'],
    SESSION_DRIVER: ['cookie', 'memory', 'redis', 'database'],
    CACHE_STORE: ['memory', 'redis', 'file'],
    QUEUE_CONNECTION: ['redis', 'database', 'memory'],
    DRIVE_DISK: ['fs', 's3', 'gcs'],
    DB_CONNECTION: ['pg', 'mysql', 'sqlite', 'mssql', 'libsql'],
    LOG_LEVEL: ['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent'],
  })
  return { envKeys, envOptions }
}

function indexConfig(base) {
  const configKeys = []
  const configFiles = {}
  const configLocations = {}
  const dir = join(base, 'config')
  for (const file of walk(dir, (p) => /\.(ts|js)$/.test(p))) {
    const stem = basename(file).replace(/\.(ts|js)$/, '')
    configFiles[stem] = file
    const text = read(file)
    // top-level export keys: foo: or 'foo':
    const keyRe = /^\s*(?:export\s+)?(?:const\s+)?([a-zA-Z_][\w]*)\s*[:=]/gm
    let m
    while ((m = keyRe.exec(text))) {
      const key = `${stem}.${m[1]}`
      configKeys.push(key)
      configLocations[key] = { path: file, line: lineOf(text, m.index) }
    }
    // nested object keys roughly: key:
    const nested = /^\s+([a-zA-Z_][\w]*)\s*:/gm
    while ((m = nested.exec(text))) {
      const key = `${stem}.${m[1]}`
      if (!configKeys.includes(key)) {
        configKeys.push(key)
        configLocations[key] = { path: file, line: lineOf(text, m.index) }
      }
    }
  }
  return { configKeys, configFiles, configLocations }
}

function indexMiddleware(base) {
  const aliases = []
  for (const file of [join(base, 'start', 'kernel.ts'), join(base, 'start', 'kernel.js')]) {
    if (!existsSync(file)) continue
    const text = read(file)
    const re = /['"`]([a-zA-Z_][\w-]*)['"`]\s*:/g
    let m
    while ((m = re.exec(text))) {
      if (!aliases.includes(m[1])) aliases.push(m[1])
    }
  }
  return aliases
}

function indexModels(base, orm) {
  const models = {}
  const tables = {}
  const relations = {}
  const modelDir = join(base, 'app', 'models')
  for (const file of walk(modelDir, (p) => /\.(ts|js)$/.test(p))) {
    const text = read(file)
    const classMatch = text.match(/export\s+default\s+class\s+(\w+)/) || text.match(/class\s+(\w+)/)
    const name = classMatch?.[1] || basename(file).replace(/\.(ts|js)$/, '')
    const tableMatch = text.match(/static\s+table\s*=\s*['"`]([^'"`]+)['"`]/)
    const table = tableMatch?.[1] || pluralize(snake(name))
    const cols = {}
    // declare foo: type / public foo: / @column() foo
    const colRe = /(?:@column[^\n]*\n\s*|declare\s+|public\s+|readonly\s+)([a-zA-Z_]\w*)\s*[?:]/g
    let m
    while ((m = colRe.exec(text))) {
      cols[m[1]] = { path: file, line: lineOf(text, m.index) }
    }
    // mongoose Schema paths
    const schemaRe = /(\w+)\s*:\s*\{[^}]*type\s*:/g
    while ((m = schemaRe.exec(text))) {
      cols[m[1]] = { path: file, line: lineOf(text, m.index) }
    }
    const relNames = []
    const relRe =
      /(?:@hasMany|@belongsTo|@hasOne|@manyToMany|hasMany|belongsTo|hasOne|manyToMany)\s*(?:<[^>]*>)?\s*\(\s*(?:\(\)\s*=>\s*)?(\w+)/g
    while ((m = relRe.exec(text))) {
      // method name before decorator call is harder; capture assign names
    }
    const methodRel =
      /(?:async\s+)?([a-zA-Z_]\w*)\s*\([^)]*\)\s*(?::\s*[^{]+)?\s*\{\s*return\s+this\.(hasMany|belongsTo|hasOne|manyToMany)/g
    const relationLines = {}
    while ((m = methodRel.exec(text))) {
      relNames.push(m[1])
      relationLines[m[1]] = lineOf(text, m.index)
    }
    // mongoose populate-ish
    const populate = /ref:\s*['"`](\w+)['"`]/g
    while ((m = populate.exec(text))) {
      if (!relNames.includes(m[1])) relNames.push(m[1])
    }

    models[name] = {
      fillable: Object.keys(cols),
      guarded: [],
      hidden: [],
      casts: {},
      relations: relNames,
      relation_lines: relationLines,
      module: name,
      path: file,
      table,
      orm,
    }
    relations[name] = relNames
    tables[table] = {
      columns: cols,
      detail: orm,
      path: file,
      line: 1,
      model: name,
    }
  }
  // migrations enrich columns
  const migDir = join(base, 'database', 'migrations')
  for (const file of walk(migDir, (p) => /\.(ts|js)$/.test(p))) {
    const text = read(file)
    const tableMatch = text.match(/\.create(?:Table)?\(\s*['"`]([^'"`]+)['"`]/) ||
      text.match(/\.alter(?:Table)?\(\s*['"`]([^'"`]+)['"`]/) ||
      text.match(/schema\.createTable\(\s*['"`]([^'"`]+)['"`]/)
    if (!tableMatch) continue
    const table = tableMatch[1]
    const entry = tables[table] || { columns: {}, detail: 'migration', path: file, line: 1, model: null }
    const colRe = /(?:table|t)\.(\w+)\(\s*['"`]([^'"`]+)['"`]/g
    let m
    while ((m = colRe.exec(text))) {
      if (['increments', 'integer', 'string', 'text', 'boolean', 'timestamp', 'timestamps', 'uuid', 'float', 'decimal', 'json', 'jsonb', 'date', 'datetime', 'enum', 'foreign'].includes(m[1]) || true) {
        if (!['create', 'alter', 'drop', 'rename', 'raw'].includes(m[1])) {
          entry.columns[m[2]] = { path: file, line: lineOf(text, m.index) }
        }
      }
    }
    // Lucid: table.string('email')
    tables[table] = entry
  }
  return { models, tables, relations }
}

function snake(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/__/g, '_')
    .toLowerCase()
}

function pluralize(s) {
  if (s.endsWith('y') && !/[aeiou]y$/i.test(s)) return s.slice(0, -1) + 'ies'
  if (s.endsWith('s')) return s + 'es'
  return s + 's'
}

const CONTROLLER_METHOD_SKIP = new Set([
  'constructor',
  'get',
  'set',
  'if',
  'else',
  'for',
  'while',
  'switch',
  'catch',
  'try',
  'do',
  'return',
  'typeof',
  'instanceof',
  'new',
  'await',
  'yield',
  'case',
  'default',
  'function',
  'class',
])

function indexControllers(base) {
  const controllers = {}
  const locations = {}
  const dir = join(base, 'app', 'controllers')
  for (const file of walk(dir, (p) => /\.(ts|js)$/.test(p))) {
    const text = read(file)
    const classMatch = text.match(/export\s+default\s+class\s+(\w+)/)
    const name = classMatch?.[1] || basename(file).replace(/\.(ts|js)$/, '')
    const classLine = classMatch ? lineOf(text, classMatch.index) : 1
    const actions = []
    // Class methods are indented; skips top-level helpers and control-flow `if (`.
    const re = /^\s+(?:async\s+)?([a-zA-Z_]\w*)\s*\([^)]*\)\s*(?::\s*[^{]+)?\s*\{/gm
    let m
    while ((m = re.exec(text))) {
      const action = m[1]
      if (CONTROLLER_METHOD_SKIP.has(action)) continue
      actions.push(action)
      // 0-based lines for OpenFileDescriptor / AdonisSymbolResolver.Target
      locations[`${name}@${action}`] = {
        path: file,
        line: Math.max(0, lineOf(text, m.index) - 1),
      }
    }
    controllers[name] = [...new Set(actions)]
    locations[name] = { path: file, line: Math.max(0, classLine - 1) }
  }
  return { actions: controllers, locations }
}

function indexWire(base) {
  const wire = {}
  const classDir = join(base, 'app', 'wire')
  for (const file of walk(classDir, (p) => /\.(ts|js)$/.test(p))) {
    const rel = relative(classDir, file).replace(/\\/g, '/').replace(/\.(ts|js)$/, '')
    const name = rel.replace(/\//g, '.').replace(/_/g, '-')
    const text = read(file)
    const props = []
    const methods = []
    const propRe = /^\s+([a-zA-Z_]\w*)\s*(?:=|:)/gm
    let m
    while ((m = propRe.exec(text))) {
      if (!['static', 'constructor', 'get', 'set'].includes(m[1])) props.push(m[1])
    }
    const methodRe = /^\s+(?:async\s+)?([a-zA-Z_]\w*)\s*\(/gm
    while ((m = methodRe.exec(text))) {
      if (!['constructor', 'render', 'mount'].includes(m[1])) methods.push(m[1])
    }
    const viewGuess = join(base, 'resources', 'views', 'wire', `${rel.replace(/\./g, '/')}.edge`)
    wire[name] = {
      path: file,
      view: existsSync(viewGuess) ? viewGuess : null,
      props: [...new Set(props)],
      methods: [...new Set(methods)],
    }
  }
  return wire
}

function indexShamar(base, hasShamar) {
  const empty = {
    panels: [],
    resources: {},
    pages: {},
    nav_groups: [],
    field_types: [],
    column_types: [],
    icons: [],
  }
  if (!hasShamar && !existsSync(join(base, 'app', 'panels'))) return empty

  const panels = []
  const resources = {}
  const pages = {}
  const navGroups = new Set()
  const icons = new Set()

  const panelsDir = join(base, 'app', 'panels')
  if (existsSync(panelsDir)) {
    for (const entry of readdirSync(panelsDir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue
      const id = entry.name
      panels.push(id)
      const resDir = join(panelsDir, id, 'resources')
      for (const file of walk(resDir, (p, n) => /resource\.(ts|js)$/i.test(n) || /_resource\.(ts|js)$/i.test(n))) {
        const text = read(file)
        const classMatch = text.match(/class\s+(\w+)/)
        const slug = text.match(/static\s+slug\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const label = text.match(/static\s+label\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const group = text.match(/static\s+navigationGroup\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const icon = text.match(/static\s+icon\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const model = text.match(/static\s+model\s*=\s*(\w+)/)?.[1]
        const key = slug || basename(file).replace(/\.(ts|js)$/, '')
        if (group) navGroups.add(group)
        if (icon) icons.add(icon)
        resources[key] = {
          class: classMatch?.[1] || key,
          panel: id,
          slug: slug || key,
          label: label || key,
          navigationGroup: group || null,
          icon: icon || null,
          model: model || null,
          path: file,
        }
      }
      const pageDir = join(panelsDir, id, 'pages')
      for (const file of walk(pageDir, (p, n) => /page\.(ts|js)$/i.test(n) || /_page\.(ts|js)$/i.test(n))) {
        const text = read(file)
        const classMatch = text.match(/class\s+(\w+)/)
        const slug = text.match(/static\s+slug\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const label = text.match(/static\s+label\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const group = text.match(/static\s+navigationGroup\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const icon = text.match(/static\s+icon\s*=\s*['"`]([^'"`]+)['"`]/)?.[1]
        const key = slug || basename(file).replace(/\.(ts|js)$/, '')
        if (group) navGroups.add(group)
        if (icon) icons.add(icon)
        pages[key] = {
          class: classMatch?.[1] || key,
          panel: id,
          slug: slug || key,
          label: label || key,
          navigationGroup: group || null,
          icon: icon || null,
          path: file,
        }
      }
    }
  }

  // Convention Shamar routes
  for (const panel of panels) {
    // indexed later in routes merge by caller if needed
  }

  return {
    panels,
    resources,
    pages,
    nav_groups: [...navGroups],
    field_types: [
      'TextInput', 'Textarea', 'Select', 'Toggle', 'Checkbox', 'Radio', 'CheckboxList',
      'DatePicker', 'DateTimePicker', 'TimePicker', 'FileUpload', 'FilePicker',
      'RichEditor', 'MarkdownEditor', 'CodeEditor', 'Repeater', 'KeyValue', 'Slider',
      'Rating', 'ToggleButtons', 'ColorPicker', 'TagsInput', 'Hidden', 'RelationTable',
      'PermissionsAssignment', 'AbilitiesAssignment', 'WeekPicker', 'MonthPicker',
    ],
    column_types: [
      'TextColumn', 'IconColumn', 'ImageColumn', 'ColorColumn',
    ],
    icons: [...icons],
  }
}

function indexVite(base) {
  const entries = {}
  for (const file of [join(base, 'vite.config.ts'), join(base, 'vite.config.js')]) {
    if (!existsSync(file)) continue
    const text = read(file)
    const re = /['"`](resources\/[^'"`]+)['"`]/g
    let m
    while ((m = re.exec(text))) {
      entries[m[1]] = join(base, m[1])
    }
  }
  return entries
}

function indexAceCommands(base) {
  // Static known Adonis Ace make:* — never requires running ace
  const commands = [
    'make:controller', 'make:model', 'make:migration', 'make:seeder', 'make:factory',
    'make:middleware', 'make:validator', 'make:exception', 'make:listener',
    'make:provider', 'make:command', 'make:test', 'make:view', 'make:event',
    'make:policy', 'make:service', 'list:routes', 'migration:run', 'migration:rollback',
    'db:seed', 'serve', 'build', 'test',
    // Shamar (soft — shown when shamar detected)
    'make:wire', 'make:panel', 'publish:auth',
  ]
  return commands
}

const EDGE_DIRECTIVES = [
  'if', 'elseif', 'else', 'unless', 'each', 'component', 'slot',
  'include', 'includeIf', 'includeWhen', 'includeUnless',
  'inject', 'eval', 'let', 'assign', 'vite', 'stack', 'pushTo', 'svg',
  'debugger', 'newError', 'dump', 'section', 'layout', 'page',
  // Shamar / Wire tags (always known to lexer; completions gated elsewhere)
  'wire', 'persist', 'end',
]

/** Globals Adonis / Edge share with every template (seeded; not scanned). */
const EDGE_VIEW_HELPERS = Object.fromEntries(
  [
    'excerpt', 'truncate', 'nl2br', 'inspect',
    'html', 'route', 'signedRoute', 'auth', 'request',
    'camelCase', 'snakeCase', 'dashCase', 'pascalCase', 'capitalCase',
    'sentenceCase', 'dotCase', 'noCase', 'titleCase',
    '$slots', '$context', '$caller', 'props', 'state',
  ].map((name) => [name, { kind: 'helper' }]),
)

function build() {
  if (!isAdonisApp(root)) {
    return {
      ok: false,
      error: `Not an AdonisJS app (missing adonisrc.ts / ace.js): ${root}`,
      base_path: root,
    }
  }
  const pkg = packageJson(root)
  const shamar = detectShamar(pkg, root)
  const orm = detectOrm(pkg, root)
  const { envKeys, envOptions } = indexEnv(root)
  const { configKeys, configFiles, configLocations } = indexConfig(root)
  const { models, tables, relations } = indexModels(root, orm)
  const shamarIndex = indexShamar(root, shamar)
  const wireComponents = indexWire(root)
  const wire = detectWire(pkg, root) || Object.keys(wireComponents).length > 0
  const routes = indexRoutes(root)
  const controllers = indexControllers(root)

  // Inject convention Shamar route names
  if (shamar || shamarIndex.panels.length) {
    for (const panel of shamarIndex.panels) {
      for (const [slug] of Object.entries(shamarIndex.resources)) {
        const names = [
          `shamar.${panel}.dashboard`,
          `shamar.${panel}.resources.${slug}.index`,
          `shamar.${panel}.resources.${slug}.create`,
          `shamar.${panel}.resources.${slug}.store`,
          `shamar.${panel}.resources.${slug}.show`,
          `shamar.${panel}.resources.${slug}.edit`,
          `shamar.${panel}.resources.${slug}.update`,
          `shamar.${panel}.resources.${slug}.destroy`,
        ]
        for (const n of names) {
          if (!routes[n]) {
            routes[n] = { name: n, uri: '', methods: ['GET'], path: null, line: 0 }
          }
        }
      }
    }
  }

  // Add shamar:: / wire:: view aliases when present
  const { views, components } = indexViews(root)
  if (existsSync(join(root, 'node_modules', '@shamar', 'adonis', 'resources', 'views', 'shamar'))) {
    const shamarViews = join(root, 'node_modules', '@shamar', 'adonis', 'resources', 'views', 'shamar')
    for (const file of walk(shamarViews, (p) => p.endsWith('.edge'))) {
      const rel = relative(shamarViews, file).replace(/\\/g, '/').replace(/\.edge$/, '')
      views[`shamar::${rel.replace(/\//g, '.')}`] = file
      views[`shamar::${rel}`] = file
    }
  }
  for (const [k, v] of Object.entries(views)) {
    if (k.startsWith('wire.') || k.startsWith('wire/')) {
      views[`wire::${k.replace(/^wire[./]/, '')}`] = v
    }
  }

  return {
    ok: true,
    error: null,
    base_path: root,
    framework: {
      adonis: true,
      shamar: shamar || shamarIndex.panels.length > 0,
      wire,
      orm,
    },
    views,
    routes,
    config_keys: configKeys,
    config_files: configFiles,
    config_locations: configLocations,
    translation_keys: [],
    middleware_aliases: indexMiddleware(root),
    env_keys: envKeys,
    env_options: envOptions,
    tables,
    model_metadata: models,
    relations,
    casts: ['string', 'number', 'boolean', 'date', 'datetime', 'json'],
    components,
    gates: [],
    disks: envOptions.DRIVE_DISK || ['fs', 's3'],
    queues: ['redis', 'database', 'memory'],
    caches: ['memory', 'redis', 'file'],
    mailers: ['smtp', 'resend', 'mailgun'],
    inertia_pages: [],
    ace_commands: indexAceCommands(root),
    validation_rules: ['required', 'email', 'minLength', 'maxLength', 'unique', 'confirmed', 'trim', 'optional'],
    directives: EDGE_DIRECTIVES,
    view_helpers: EDGE_VIEW_HELPERS,
    view_shared: {
      auth: { kind: 'shared' },
      request: { kind: 'shared' },
    },
    view_data: {},
    vite_entries: indexVite(root),
    controller_actions: controllers.actions,
    controller_locations: controllers.locations,
    wire_components: wireComponents,
    shamar: shamarIndex,
  }
}

const payload = build()
if (asJson) {
  process.stdout.write(JSON.stringify(payload))
} else {
  if (!payload.ok) {
    console.error(payload.error)
    process.exit(1)
  }
  console.log(`Index for ${payload.base_path}`)
  console.log(`  views      ${Object.keys(payload.views).length}`)
  console.log(`  routes     ${Object.keys(payload.routes).length}`)
  console.log(`  models     ${Object.keys(payload.model_metadata).length}`)
  console.log(`  wire       ${Object.keys(payload.wire_components).length}`)
  console.log(`  shamar     ${payload.framework.shamar}`)
  console.log('ide:index ok')
}
