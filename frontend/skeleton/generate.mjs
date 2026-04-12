/**
 * Skeleton bones generator.
 *
 * Writes `.bones.json` files consumed at runtime by `<Skeleton>` from boneyard-js.
 * Run with `npm run skeleton` (from frontend/) or `node skeleton/generate.mjs`.
 *
 * See ./README.md for the full documentation (bone format, container-width
 * breakpoints, 2-shade hierarchy via container flag, etc.).
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BONES_DIR = path.resolve(__dirname, '../src/bones')

const round = n => Math.round(n * 10000) / 10000

function writeBones(filename, payload) {
  payload._hash = 'manual-' + Date.now()
  const out = path.join(BONES_DIR, filename)
  fs.writeFileSync(out, JSON.stringify(payload, null, 2) + '\n')
  console.log(`wrote ${filename}`)
}

// ============================================================
// Event card visual template — used by event-cards and search-results.
// 2-shade hierarchy via container flag:
//   - Card outer = container (lighter) → card surface
//   - Banner     = leaf      (darker)  → image zone
//   - Badge/Title= containers (lighter) → light elements on banner
//   - Meta/Desc  = leaves    (darker)  → dark text on card bg
// ============================================================
const CARD_H = 388
const ROW_GAP = 20

function buildCard(containerW, cardX_pct, cardW_pct, cardW_px, y0) {
  const pctX = px => round(cardX_pct + px * 100 / containerW)
  const pctW = px => round(px * 100 / containerW)
  const innerW = cardW_px - 40
  const b = []
  // Card surface (container, lighter)
  b.push([cardX_pct, y0, cardW_pct, CARD_H, 24, true])
  // Banner (leaf, darker)
  b.push([cardX_pct, y0, cardW_pct, 208, 24])
  // Badge pill (container, lighter)
  b.push([pctX(16), y0 + 16, pctW(96), 24, 9999, true])
  // Title 1 + 2 (containers, lighter — anchored near banner bottom)
  b.push([pctX(20), y0 + 140, pctW(Math.round(cardW_px * 0.72)), 22, 6, true])
  b.push([pctX(20), y0 + 170, pctW(Math.round(cardW_px * 0.48)), 16, 6, true])
  // Meta rows (leaves, darker)
  b.push([pctX(20), y0 + 232, pctW(16), 16, 4])
  b.push([pctX(44), y0 + 234, pctW(Math.round(innerW * 0.62)), 14, 4])
  b.push([pctX(20), y0 + 258, pctW(16), 16, 4])
  b.push([pctX(44), y0 + 260, pctW(Math.round(innerW * 0.48)), 14, 4])
  b.push([pctX(20), y0 + 284, pctW(16), 16, 4])
  b.push([pctX(44), y0 + 286, pctW(Math.round(innerW * 0.34)), 14, 4])
  // Separator + description (leaves)
  b.push([pctX(20), y0 + 316, pctW(innerW), 1, 0])
  b.push([pctX(20), y0 + 332, pctW(innerW), 12, 4])
  b.push([pctX(20), y0 + 350, pctW(Math.round(innerW * 0.82)), 12, 4])
  return b
}

// Build a per-breakpoint payload keyed by CONTAINER width.
// All percentages are relative to that container width — at runtime, boneyard
// applies them to the measured container width, so rendered pixels match.
function makeCardsPayload(name, containerW, layout) {
  const { cardW_px, cards, rows } = layout
  const cardW_pct = round(cardW_px * 100 / containerW)
  const height = rows * CARD_H + (rows - 1) * ROW_GAP
  const allBones = []
  for (let r = 0; r < rows; r++) {
    for (const card of cards) {
      const x_pct = round(card.x_px * 100 / containerW)
      const y = r * (CARD_H + ROW_GAP)
      allBones.push(...buildCard(containerW, x_pct, cardW_pct, cardW_px, y))
    }
  }
  return { name, viewportWidth: containerW, width: containerW, height, bones: allBones }
}

// ============================================================
// EVENT CARDS — grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5
// ============================================================
function autoFitLayout(containerW, totalCards) {
  const MIN = 280, MAX = 320, GAP = 20
  const cols = Math.max(1, Math.floor((containerW + GAP) / (MIN + GAP)))
  const rawTrackW = (containerW - (cols - 1) * GAP) / cols
  const trackW = Math.min(MAX, rawTrackW)
  const totalRowW = cols * trackW + (cols - 1) * GAP
  const margin = Math.max(0, (containerW - totalRowW) / 2)
  const cardsX = []
  for (let i = 0; i < cols; i++) cardsX.push(margin + i * (trackW + GAP))
  const rows = Math.ceil(totalCards / cols)
  return { cardW_px: Math.round(trackW), cards: cardsX.map(x => ({ x_px: Math.round(x) })), rows }
}

// Container widths spanning the auto-fit grid transitions
// (1col → 2col @580, → 3col @880, → 4col @1180).
const EVENT_CARDS_CONTAINERS = [320, 432, 552, 720, 852, 976, 1132, 1216]

function genCards() {
  const out = { breakpoints: {} }
  for (const cw of EVENT_CARDS_CONTAINERS) {
    out.breakpoints[String(cw)] = makeCardsPayload('event-cards', cw, autoFitLayout(cw, 6))
  }
  writeBones('event-cards.bones.json', out)
}

// ============================================================
// SEARCH RESULTS — md:grid-cols-2 xl:grid-cols-3 gap-4 (1fr cols, no max-width)
// Container is shrunk by the lg sidebar (w-60 = 240px + gap-6 = 24px) from lg+.
// ============================================================
function fixedColsLayout(containerW, cols, gap, totalCards) {
  const trackW = (containerW - (cols - 1) * gap) / cols
  const cardsX = []
  for (let i = 0; i < cols; i++) cardsX.push(i * (trackW + gap))
  const rows = Math.ceil(totalCards / cols)
  return { cardW_px: Math.round(trackW), cards: cardsX.map(x => ({ x_px: Math.round(x) })), rows }
}

function genSearch() {
  const out = { breakpoints: {} }
  const SEARCH_BPS = [
    { containerW: 327, cols: 1 }, // mobile, no sidebar
    { containerW: 480, cols: 2 }, // md, no sidebar
    { containerW: 700, cols: 2 }, // lg+ with sidebar
    { containerW: 950, cols: 3 }, // xl+ with sidebar
  ]
  for (const { containerW, cols } of SEARCH_BPS) {
    const layout = fixedColsLayout(containerW, cols, 16, 6)
    out.breakpoints[String(containerW)] = makeCardsPayload('search-results', containerW, layout)
  }
  writeBones('search-results.bones.json', out)
}

// ============================================================
// EVENT CALENDAR — mirrors react-big-calendar custom toolbar + grid.
// Parent enforces h-[680px] (CalendarPage), so all breakpoints use height=680.
// Layout:
//   - Toolbar (stacked <sm, row ≥sm): nav buttons + title left, view tabs right
//   - Day headers row: LUN. MAR. ... left-aligned in each column
//   - Grid 5×7: thin grid lines + day number top-right + events as colored pills
// ============================================================
const CAL_HEIGHT = 680

// Event distribution to look populated. No "today" / no greyed cells (state-dependent).
// (row, col, count)
const CAL_EVENTS = [
  [0, 1, 1], [0, 3, 2], [0, 5, 1],
  [1, 0, 1], [1, 2, 1], [1, 4, 2], [1, 6, 1],
  [2, 1, 1], [2, 3, 1], [2, 5, 2],
  [3, 0, 1], [3, 2, 2], [3, 4, 1], [3, 6, 1],
  [4, 1, 1], [4, 3, 1], [4, 5, 2],
]

const CAL_CONTAINERS = [320, 720, 976, 1216]

function buildCalendarToolbarStacked(containerW, pctX, pctW, bones) {
  // Row 1 (y=24): nav buttons + month title
  bones.push([pctX(16),  24, pctW(40),  40, 12, true])
  bones.push([pctX(64),  24, pctW(88),  40, 12, true])
  bones.push([pctX(160), 24, pctW(40),  40, 12, true])
  bones.push([pctX(212), 32, pctW(96),  24, 4])
  // Row 2 (y=78): 4 view tabs full width
  const padX = 16, gap = 6
  const tabW = (containerW - padX * 2 - gap * 3) / 4
  for (let i = 0; i < 4; i++) {
    bones.push([pctX(padX + i * (tabW + gap)), 78, pctW(tabW), 40, 12, true])
  }
}

function buildCalendarToolbarRow(containerW, pctX, pctW, bones) {
  // Single row at y=20
  bones.push([pctX(24),  20, pctW(40),  40, 12, true])
  bones.push([pctX(72),  20, pctW(120), 40, 12, true])
  bones.push([pctX(200), 20, pctW(40),  40, 12, true])
  bones.push([pctX(256), 28, pctW(140), 24, 4])
  // Right-aligned view tabs (4 tabs in a flex container)
  const tabWs = [64, 88, 64, 88]
  const gap = 4
  const totalW = tabWs.reduce((a, b) => a + b, 0) + gap * 3
  let cur = containerW - 24 - totalW
  for (const w of tabWs) {
    bones.push([pctX(cur), 20, pctW(w), 40, 12, true])
    cur += w + gap
  }
}

function buildCalendar(containerW) {
  const pctX = px => round(px * 100 / containerW)
  const pctW = px => round(px * 100 / containerW)
  const bones = []
  // Outer card surface (container, lighter)
  bones.push([0, 0, 100, CAL_HEIGHT, 24, true])

  const stacked = containerW < 640
  const toolbarH = stacked ? 130 : 80

  if (stacked) buildCalendarToolbarStacked(containerW, pctX, pctW, bones)
  else buildCalendarToolbarRow(containerW, pctX, pctW, bones)

  // Day headers row (LUN. MAR. ... left-aligned)
  const headerY = toolbarH
  const headerH = 40
  const colW = containerW / 7
  for (let i = 0; i < 7; i++) {
    const labelW = stacked ? 24 : 36
    bones.push([pctX(colW * i + 12), headerY + 14, pctW(labelW), 14, 4])
  }

  // Grid 5×7
  const gridY = toolbarH + headerH
  const gridH = CAL_HEIGHT - gridY
  const rowH = gridH / 5
  // Thin grid lines — vertical separators between columns
  for (let c = 1; c < 7; c++) {
    bones.push([pctX(colW * c), headerY, pctW(1), CAL_HEIGHT - headerY, 0])
  }
  // Thin grid lines — horizontal separators (header bottom + row bottoms)
  bones.push([0, gridY, 100, 1, 0])
  for (let r = 1; r < 5; r++) {
    bones.push([0, gridY + rowH * r, 100, 1, 0])
  }
  // Day numbers (top-right of each cell)
  for (let r = 0; r < 5; r++) {
    for (let c = 0; c < 7; c++) {
      const numW = stacked ? 16 : 22
      bones.push([pctX(colW * (c + 1) - numW - 12), gridY + rowH * r + 12, pctW(numW), 16, 4])
    }
  }
  // Event pills
  const pillH = stacked ? 14 : 18
  const pillGap = 4
  const pillTopOffset = stacked ? 36 : 40
  for (const [r, c, count] of CAL_EVENTS) {
    for (let e = 0; e < count; e++) {
      bones.push([
        pctX(colW * c + 4),
        gridY + rowH * r + pillTopOffset + e * (pillH + pillGap),
        pctW(colW - 8),
        pillH,
        4,
      ])
    }
  }
  return bones
}

function genCalendar() {
  const out = { breakpoints: {} }
  for (const cw of CAL_CONTAINERS) {
    out.breakpoints[String(cw)] = {
      name: 'event-calendar',
      viewportWidth: cw,
      width: cw,
      height: CAL_HEIGHT,
      bones: buildCalendar(cw),
    }
  }
  writeBones('event-calendar.bones.json', out)
}

genCards()
genSearch()
genCalendar()
