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

// ============================================================
// EVENT DETAIL — reworked 2-column grid page
// Layout (from EventDetailPage.tsx):
//   - SectionWrapper (size="lg" → max-w-5xl, container ≈ 960 at desktop)
//   - SectionHeader (NOT skeletonned — rendered outside <Skeleton>)
//   - Grid: `grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1`
//     - Main col (order-2 on mobile): banner (h-72/h-80) + description card + 4 S5 shells
//     - Sidebar col (order-1 on mobile): infos card + attendance card + ICS button
// Breakpoints (container widths inside SectionWrapper size="lg"):
//   - 343  → mobile, 1 col (banner h-72=288)
//   - 720  → tablet, 1 col (banner h-72=288)
//   - 960  → desktop, 2 cols (banner h-80=320)
// ============================================================

// Heights — must match what the fixture produces so scaleY=1
const BANNER_H_MOBILE = 288  // h-72
const BANNER_H_DESKTOP = 320 // h-80
const DESC_H = 160
const S5_SHELL_H = 80
const S5_SHELL_GAP = 12
const MAIN_GAP = 20          // gap-5
const INFOS_CARD_H = 288
const ATTENDANCE_CARD_H = 176
const ICS_CARD_H = 236       // IcsExportButton is a full card: header + 3 option buttons
const STATS_CARD_H = 104     // ComingSoonBlock with 3 stat boxes in grid-cols-3
const SIDEBAR_GAP = 16       // gap-4
const GRID_GAP = 24          // gap-6

function mainColH(bannerH) {
  const s5Block = 4 * S5_SHELL_H + 3 * S5_SHELL_GAP // 356
  return bannerH + MAIN_GAP + DESC_H + MAIN_GAP + s5Block
}
function sidebarColH() {
  return INFOS_CARD_H + SIDEBAR_GAP + ATTENDANCE_CARD_H + SIDEBAR_GAP + ICS_CARD_H + SIDEBAR_GAP + STATS_CARD_H
}

// Builder for a Banner bone group (leaf rect + badge + 2 title lines on top)
function pushBanner(bones, pct, colX, colW, y0, bannerH) {
  // Banner = leaf (darker) → represents the image zone
  bones.push([pct.x(colX), y0, pct.w(colW), bannerH, 24])
  // Category badge pill (container, lighter)
  bones.push([pct.x(colX + 16), y0 + 16, pct.w(110), 24, 9999, true])
  // Title line 1 (container, lighter) — anchored near banner bottom (p-6)
  const titleBottomPad = 24
  const t1H = 28
  const t2H = 22
  const t2Y = y0 + bannerH - titleBottomPad - t2H
  const t1Y = t2Y - 8 - t1H
  bones.push([pct.x(colX + 24), t1Y, pct.w(Math.round(colW * 0.75)), t1H, 6, true])
  bones.push([pct.x(colX + 24), t2Y, pct.w(Math.round(colW * 0.55)), t2H, 6, true])
}

// Builder for the Description card
function pushDescriptionCard(bones, pct, colX, colW, y0) {
  // Card surface (container, lighter)
  bones.push([pct.x(colX), y0, pct.w(colW), DESC_H, 24, true])
  // "À propos" header (leaf)
  bones.push([pct.x(colX + 24), y0 + 24, pct.w(48), 12, 4])
  // 4 description lines (leaves)
  const lineWs = [1.0, 0.95, 0.88, 0.72]
  const innerW = colW - 48
  for (let i = 0; i < 4; i++) {
    bones.push([pct.x(colX + 24), y0 + 56 + i * 20, pct.w(Math.round(innerW * lineWs[i])), 12, 4])
  }
}

// Builder for a ComingSoon shell (S5 style — dashed border, faded content)
function pushComingSoonShell(bones, pct, colX, colW, y0) {
  // Shell surface (container, lighter — but subtle: dashed bg/[0.018] in reality)
  bones.push([pct.x(colX), y0, pct.w(colW), S5_SHELL_H, 16, true])
  // Header row: icon + label on left, sprint badge on right
  bones.push([pct.x(colX + 16), y0 + 16, pct.w(16), 16, 4])                      // icon
  bones.push([pct.x(colX + 40), y0 + 18, pct.w(Math.round(colW * 0.45)), 12, 4]) // label
  bones.push([pct.x(colX + colW - 46), y0 + 16, pct.w(30), 16, 9999])            // sprint badge
  // Body (faded content)
  bones.push([pct.x(colX + 16), y0 + 48, pct.w(Math.round((colW - 32) * 0.6)), 12, 4])
}

// Builder for the Infos clés sidebar card
function pushInfosClesCard(bones, pct, colX, colW, y0) {
  bones.push([pct.x(colX), y0, pct.w(colW), INFOS_CARD_H, 24, true])
  // 3 info rows (icon + text)
  for (let i = 0; i < 3; i++) {
    const ry = y0 + 24 + i * 28
    bones.push([pct.x(colX + 20), ry, pct.w(16), 16, 4])
    const textW = Math.round((colW - 64) * [0.82, 0.62, 0.5][i])
    bones.push([pct.x(colX + 44), ry + 2, pct.w(textW), 12, 4])
  }
  // Separator 1
  bones.push([pct.x(colX + 20), y0 + 120, pct.w(colW - 40), 1, 0])
  // Organizer row: avatar + 2 text lines
  bones.push([pct.x(colX + 20), y0 + 136, pct.w(36), 36, 9999])                // avatar
  bones.push([pct.x(colX + 68), y0 + 140, pct.w(60), 10, 4])                   // "Organisé par"
  bones.push([pct.x(colX + 68), y0 + 156, pct.w(Math.round((colW - 88) * 0.6)), 14, 4]) // name
  // Separator 2
  bones.push([pct.x(colX + 20), y0 + 188, pct.w(colW - 40), 1, 0])
  // Coming soon "Places dispo" header
  bones.push([pct.x(colX + 20), y0 + 204, pct.w(16), 16, 4])
  bones.push([pct.x(colX + 44), y0 + 206, pct.w(Math.round((colW - 64) * 0.5)), 12, 4])
  bones.push([pct.x(colX + colW - 46), y0 + 204, pct.w(30), 16, 9999])
  // Body pills (available / waitlist)
  bones.push([pct.x(colX + 20), y0 + 236, pct.w(Math.round((colW - 40) * 0.48)), 24, 8, true])
  bones.push([pct.x(colX + 20 + Math.round((colW - 40) * 0.48) + 8), y0 + 236, pct.w(Math.round((colW - 40) * 0.38)), 24, 8, true])
}

// Builder for the Attendance + Participants sidebar card
function pushAttendanceCard(bones, pct, colX, colW, y0) {
  bones.push([pct.x(colX), y0, pct.w(colW), ATTENDANCE_CARD_H, 24, true])
  // Buttons row: 3 attendance circles + favoris button
  // AttendanceButtons — group of 3 circular buttons
  for (let i = 0; i < 3; i++) {
    bones.push([pct.x(colX + 20 + i * 44), y0 + 20, pct.w(36), 36, 9999, true])
  }
  // Favoris button (flex-1)
  const favX = colX + 20 + 3 * 44 + 8
  const favW = colW - 20 - (favX - colX)
  bones.push([pct.x(favX), y0 + 22, pct.w(favW), 32, 12, true])
  // Separator
  bones.push([pct.x(colX + 20), y0 + 76, pct.w(colW - 40), 1, 0])
  // Coming soon "Participants" header
  bones.push([pct.x(colX + 20), y0 + 92, pct.w(16), 16, 4])
  bones.push([pct.x(colX + 44), y0 + 94, pct.w(Math.round((colW - 64) * 0.55)), 12, 4])
  bones.push([pct.x(colX + colW - 46), y0 + 92, pct.w(30), 16, 9999])
  // Avatars stack (3 overlapping circles) + counter text
  for (let i = 0; i < 3; i++) {
    bones.push([pct.x(colX + 20 + i * 20), y0 + 124, pct.w(32), 32, 9999, true])
  }
  bones.push([pct.x(colX + 90), y0 + 134, pct.w(Math.round((colW - 110) * 0.7)), 12, 4])
}

// Builder for the "Ajouter au calendrier" card (IcsExportButton — full card with 3 options)
function pushIcsCard(bones, pct, colX, colW, y0) {
  // Card surface (container, lighter)
  bones.push([pct.x(colX), y0, pct.w(colW), ICS_CARD_H, 24, true])
  // Header row: calendar icon + "Ajouter au calendrier" label
  bones.push([pct.x(colX + 24), y0 + 26, pct.w(16), 16, 4])                               // icon (leaf)
  bones.push([pct.x(colX + 44), y0 + 28, pct.w(Math.round((colW - 68) * 0.55)), 14, 4])   // label (leaf)
  // 3 option buttons (containers = bordered button surfaces)
  // y=24 (p-6 top) + 20 (header) + 16 (gap-4) = y0+60, each button 40 tall + 16 gap = 56 step
  for (let i = 0; i < 3; i++) {
    const btnY = y0 + 60 + i * 56
    bones.push([pct.x(colX + 24), btnY, pct.w(colW - 48), 40, 12, true])
    // Icon + label inside the button (leaves for contrast on the lighter button surface)
    bones.push([pct.x(colX + 40), btnY + 12, pct.w(16), 16, 4])
    bones.push([pct.x(colX + 64), btnY + 14, pct.w(Math.round((colW - 88) * 0.5)), 12, 4])
  }
}

// Builder for the Main column (banner + description + 4 S5 shells)
function pushMainCol(bones, pct, colX, colW, y0, bannerH) {
  let y = y0
  pushBanner(bones, pct, colX, colW, y, bannerH); y += bannerH + MAIN_GAP
  pushDescriptionCard(bones, pct, colX, colW, y);  y += DESC_H + MAIN_GAP
  for (let i = 0; i < 4; i++) {
    pushComingSoonShell(bones, pct, colX, colW, y)
    y += S5_SHELL_H + (i < 3 ? S5_SHELL_GAP : 0)
  }
}

// Builder for the "Statistiques de participation" coming soon card (S6)
// ComingSoonBlock shell with 3 stat boxes in grid-cols-3
function pushStatsCard(bones, pct, colX, colW, y0) {
  // Shell (container, lighter — dashed border in reality)
  bones.push([pct.x(colX), y0, pct.w(colW), STATS_CARD_H, 16, true])
  // Header row: icon + label + sprint badge
  bones.push([pct.x(colX + 16), y0 + 14, pct.w(16), 16, 4])                                    // icon
  bones.push([pct.x(colX + 40), y0 + 16, pct.w(Math.round((colW - 88) * 0.8)), 12, 4])         // label
  bones.push([pct.x(colX + colW - 46), y0 + 14, pct.w(30), 16, 9999])                          // sprint badge
  // 3 stat boxes (containers — each a bordered mini-card with value + label)
  const bodyY = y0 + 42
  const gapX = 8
  const innerX = colX + 16
  const innerW = colW - 32
  const boxW = (innerW - 2 * gapX) / 3
  for (let i = 0; i < 3; i++) {
    const bx = innerX + i * (boxW + gapX)
    bones.push([pct.x(bx), bodyY, pct.w(boxW), 44, 12, true])                                  // box surface
    bones.push([pct.x(bx + (boxW - 16) / 2), bodyY + 10, pct.w(16), 14, 4])                    // value (centered)
    bones.push([pct.x(bx + (boxW - 28) / 2), bodyY + 28, pct.w(28), 10, 4])                    // label (centered)
  }
}

// Builder for the Sidebar column (infos + attendance + ICS card + stats card)
function pushSidebarCol(bones, pct, colX, colW, y0) {
  let y = y0
  pushInfosClesCard(bones, pct, colX, colW, y);  y += INFOS_CARD_H + SIDEBAR_GAP
  pushAttendanceCard(bones, pct, colX, colW, y); y += ATTENDANCE_CARD_H + SIDEBAR_GAP
  pushIcsCard(bones, pct, colX, colW, y);        y += ICS_CARD_H + SIDEBAR_GAP
  pushStatsCard(bones, pct, colX, colW, y)
}

function buildEventDetail(containerW) {
  const pct = {
    x: px => round(px * 100 / containerW),
    w: px => round(px * 100 / containerW),
  }
  const bones = []
  const desktop = containerW >= 900
  if (desktop) {
    // 2 cols: grid-cols-[3fr_2fr] gap-6
    const avail = containerW - GRID_GAP
    const mainW = Math.round(avail * 3 / 5)
    const sideW = Math.round(avail * 2 / 5)
    const sideX = mainW + GRID_GAP
    pushMainCol(bones, pct, 0, mainW, 0, BANNER_H_DESKTOP)
    pushSidebarCol(bones, pct, sideX, sideW, 0)
  } else {
    // 1 col, sidebar first (order-1), then main (order-2)
    pushSidebarCol(bones, pct, 0, containerW, 0)
    const mainY = sidebarColH() + GRID_GAP
    pushMainCol(bones, pct, 0, containerW, mainY, BANNER_H_MOBILE)
  }
  return bones
}

function detailHeight(containerW) {
  const desktop = containerW >= 900
  if (desktop) return Math.max(mainColH(BANNER_H_DESKTOP), sidebarColH())
  return sidebarColH() + GRID_GAP + mainColH(BANNER_H_MOBILE)
}

const DETAIL_CONTAINERS = [343, 720, 960]

function genEventDetail() {
  const out = { breakpoints: {} }
  for (const cw of DETAIL_CONTAINERS) {
    out.breakpoints[String(cw)] = {
      name: 'event-detail',
      viewportWidth: cw,
      width: cw,
      height: detailHeight(cw),
      bones: buildEventDetail(cw),
    }
  }
  writeBones('event-detail.bones.json', out)
}

genCards()
genSearch()
genCalendar()
genEventDetail()
