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
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const BONES_DIR = path.resolve(__dirname, '../src/bones')

const round = n => Math.round(n * 10000) / 10000

function writeBones(filename, payload) {
  payload._hash = createHash('sha1').update(JSON.stringify(payload)).digest('hex').slice(0, 12)
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
const INFO_EXTRA_H = 176     // SCRUM-117 "Informations complémentaires" card (h-44)
const MAIN_GAP = 20          // gap-5
const INFOS_CARD_H = 288
const ATTENDANCE_CARD_H = 176
const ICS_CARD_H = 104       // simplified IcsExportButton placeholder (single button)
const SIDEBAR_GAP = 16       // gap-4
const GRID_GAP = 24          // gap-6

function mainColH(bannerH) {
  const s5Block = 4 * S5_SHELL_H + 3 * S5_SHELL_GAP // 356
  return bannerH + MAIN_GAP + DESC_H + MAIN_GAP + s5Block + MAIN_GAP + INFO_EXTRA_H
}
function sidebarColH() {
  return INFOS_CARD_H + SIDEBAR_GAP + ATTENDANCE_CARD_H + SIDEBAR_GAP + ICS_CARD_H
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

// Builder for the "Ajouter au calendrier" card (IcsExportButton — simplified placeholder)
function pushIcsCard(bones, pct, colX, colW, y0) {
  // Card surface (container, lighter)
  bones.push([pct.x(colX), y0, pct.w(colW), ICS_CARD_H, 16, true])
  // Header row: calendar icon + "Ajouter au calendrier" label
  bones.push([pct.x(colX + 24), y0 + 26, pct.w(16), 16, 4])                               // icon (leaf)
  bones.push([pct.x(colX + 44), y0 + 28, pct.w(Math.round((colW - 68) * 0.55)), 14, 4])   // label (leaf)
  // Single CTA button placeholder
  bones.push([pct.x(colX + 24), y0 + 56, pct.w(colW - 48), 32, 12, true])
}

// Builder for the "Informations complémentaires" main-col card (SCRUM-117)
// rounded-3xl card with 4 InfoRow placeholders (icon + text)
function pushInfoExtraCard(bones, pct, colX, colW, y0) {
  // Card surface (container, lighter)
  bones.push([pct.x(colX), y0, pct.w(colW), INFO_EXTRA_H, 24, true])
  // "Informations complémentaires" header (leaf)
  bones.push([pct.x(colX + 24), y0 + 24, pct.w(Math.round((colW - 48) * 0.5)), 12, 4])
  // 4 InfoRow placeholders: small icon + text line
  const lineWs = [0.78, 0.62, 0.7, 0.54]
  const innerW = colW - 48
  for (let i = 0; i < 4; i++) {
    const ry = y0 + 60 + i * 24
    bones.push([pct.x(colX + 24), ry, pct.w(16), 16, 4])
    bones.push([pct.x(colX + 48), ry + 2, pct.w(Math.round(innerW * lineWs[i])), 12, 4])
  }
}

// Builder for the Main column (banner + description + 4 attendee shells + extra info card)
function pushMainCol(bones, pct, colX, colW, y0, bannerH) {
  let y = y0
  pushBanner(bones, pct, colX, colW, y, bannerH); y += bannerH + MAIN_GAP
  pushDescriptionCard(bones, pct, colX, colW, y);  y += DESC_H + MAIN_GAP
  for (let i = 0; i < 4; i++) {
    pushComingSoonShell(bones, pct, colX, colW, y)
    y += S5_SHELL_H + (i < 3 ? S5_SHELL_GAP : 0)
  }
  y += MAIN_GAP
  pushInfoExtraCard(bones, pct, colX, colW, y)
}

// Builder for the Sidebar column (infos + attendance + ICS card)
// We model the public variant (no organizer-only actions/stats) so the skeleton
// stays compact regardless of who ends up viewing the page.
function pushSidebarCol(bones, pct, colX, colW, y0) {
  let y = y0
  pushInfosClesCard(bones, pct, colX, colW, y);  y += INFOS_CARD_H + SIDEBAR_GAP
  pushAttendanceCard(bones, pct, colX, colW, y); y += ATTENDANCE_CARD_H + SIDEBAR_GAP
  pushIcsCard(bones, pct, colX, colW, y)
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

// ============================================================
// EVENT EDIT FORM — skeleton for EventEditPage
// Mirrors EventForm.tsx (gap-8 between bands):
//   Band 1: Banner (2fr) | Title + Description (3fr) — max-lg:1col
//   Band 2a: Lieu (FormField with leading icon)
//   Band 2b: Date & heure section (rounded-2xl border with header + allDay toggle,
//            then 2 datetime fields side-by-side, max-sm:stacked)
//   Band 3: Catégorie (w-48) | Faculté (w-56) | Capacité (w-24) — flex-wrap
//   Band 4: Champs additionnels —
//             websiteUrl + contactEmail (grid 2 cols max-sm:1col)
//             + registrationDeadline (datetime field)
//             + tags (FormField + counter)
//             + separator
//             + ComingSoon attachments
//   Band 5: Co-organisateurs shell (edit mode only)
//   CTA bar: ml-auto, 3 buttons (delete/cancel/submit)
//
// Three responsive states matching our 3 bps (container widths):
//   320 → State A (<sm): everything stacked, datetime + websiteUrl/email stacked,
//                        cat/fac/cap wrap onto multiple rows, CTA full-width
//   592 → State B (sm..lg): band 1 stacked, band 2b/3/4-row1 multi-col
//   960 → State C (lg+): full multi-col, band 1 in 2 columns
// ============================================================

const FORM_GAP = 32
const BANNER_H = 208
const BANNER_PT = 28
const TITLE_FIELD_H = 92    // label + input + counter
const DESC_FIELD_H = 192    // label + textarea + counter
const FIELD_GAP_4 = 16
const BAND1_RIGHT_H = TITLE_FIELD_H + FIELD_GAP_4 + DESC_FIELD_H // 300
const FIELD_72 = 72         // standard FormField (label + input, no counter, ~28+44)
const TAGS_FIELD_H = 88     // FormField + TagInput + counter row
const SECTION_PADDING_Y = 32   // px-4 py-4 = 16+16
const SECTION_HEADER_H = 22    // header row (~h-5)
const SECTION_INNER_GAP = 12   // gap-3 inside section
const CS_ATTACH = 92
const BAND5_H = 141
const BORDER_LINE = 1
const CTA_BUTTON_H = 44     // h-11 button row

function band1H(state) {
  if (state === 'C') return Math.max(BANNER_PT + BANNER_H, BAND1_RIGHT_H) // max(236, 300) = 300
  return BANNER_H + 24 + BAND1_RIGHT_H                                    // 208 + 24 + 300 = 532
}

function band2aH() { return FIELD_72 }

function band2bH(state) {
  // rounded-2xl px-4 py-4 with header row + 2 datetime fields
  const fieldsH = state === 'A'
    ? FIELD_72 * 2 + FIELD_GAP_4
    : FIELD_72
  return SECTION_PADDING_Y + SECTION_HEADER_H + SECTION_INNER_GAP + fieldsH
}

function band3H(state) {
  // Cat (192) + Fac (224) + Cap (96) with gap-x-6 (24) → total ~560 single row
  if (state === 'A') return FIELD_72 * 3 + FIELD_GAP_4 * 2 // 248 — wraps to 3 rows
  return FIELD_72                                          // 72 single row
}

function band4H(state) {
  const row1 = state === 'A' ? (FIELD_72 + FIELD_GAP_4 + FIELD_72) : FIELD_72 // 160 or 72
  // gap-4 between children
  return row1 + FIELD_GAP_4
       + FIELD_72 + FIELD_GAP_4
       + TAGS_FIELD_H + FIELD_GAP_4
       + BORDER_LINE + FIELD_GAP_4
       + CS_ATTACH
}

function stateForContainer(cw) {
  if (cw >= 960) return 'C'
  if (cw >= 592) return 'B'
  return 'A'
}

function ctaH(state) {
  // State A: flex-wrap pushes the longest button to a second row (~2 rows of 44 + gap-3).
  // Otherwise single right-aligned row.
  if (state === 'A') return CTA_BUTTON_H * 2 + 12
  return CTA_BUTTON_H
}

function formTotalH(state) {
  return band1H(state)  + FORM_GAP
       + band2aH()      + FORM_GAP
       + band2bH(state) + FORM_GAP
       + band3H(state)  + FORM_GAP
       + band4H(state)  + FORM_GAP
       + BAND5_H        + FORM_GAP
       + ctaH(state)
}

// ── Bone builders per band ─────────────────────────────────

function pushUploadBanner(bones, pct, x, y, w) {
  // Dashed upload zone (container, lighter — simulates the dashed border surface)
  bones.push([pct.x(x), y, pct.w(w), BANNER_H, 16, true])
  // Centered icon badge (container)
  const iconSize = 48
  const iconX = x + (w - iconSize) / 2
  const iconY = y + (BANNER_H - iconSize - 44) / 2
  bones.push([pct.x(iconX), iconY, pct.w(iconSize), iconSize, 16, true])
  // Main text (leaf)
  bones.push([pct.x(x + (w - 160) / 2), iconY + 60, pct.w(160), 12, 4])
  // Sub text (leaf)
  bones.push([pct.x(x + (w - 120) / 2), iconY + 80, pct.w(120), 10, 4])
}

function pushShortField(bones, pct, x, w, y) {
  // Label (leaf)
  bones.push([pct.x(x), y + 4, pct.w(40), 12, 4])
  // Input (container)
  bones.push([pct.x(x), y + 28, pct.w(w), 44, 12, true])
  // Counter right-aligned (leaf)
  bones.push([pct.x(x + w - 50), y + 76, pct.w(50), 12, 4])
}

function pushTextareaField(bones, pct, x, w, y) {
  // Label
  bones.push([pct.x(x), y + 4, pct.w(70), 12, 4])
  // Textarea (container)
  bones.push([pct.x(x), y + 28, pct.w(w), 144, 12, true])
  // Counter
  bones.push([pct.x(x + w - 60), y + 176, pct.w(60), 12, 4])
}

function pushNoCounterField(bones, pct, x, w, y, labelW = 40) {
  bones.push([pct.x(x), y + 4, pct.w(labelW), 12, 4])
  bones.push([pct.x(x), y + 28, pct.w(w), 44, 12, true])
}

// Location field with left icon inside input
function pushLocationField(bones, pct, x, w, y) {
  bones.push([pct.x(x), y + 4, pct.w(40), 12, 4])                // label
  bones.push([pct.x(x), y + 28, pct.w(w), 44, 12, true])          // input surface
  bones.push([pct.x(x + 14), y + 42, pct.w(16), 16, 4])           // left icon inside
}

// Date/time input row (date 1fr + HH + MM)
function pushDateTimeInputs(bones, pct, x, w, y) {
  const selW = 50
  const hhmmW = selW + 8 + selW // 2 selects with ':' gap
  const dateW = w - hhmmW - 12
  bones.push([pct.x(x), y, pct.w(dateW), 44, 12, true])
  bones.push([pct.x(x + dateW + 12), y, pct.w(selW), 44, 12, true])
  bones.push([pct.x(x + dateW + 12 + selW + 8), y, pct.w(selW), 44, 12, true])
}

// Standalone datetime FormField (label + datetime row)
function pushDateTimeField(bones, pct, x, w, y) {
  bones.push([pct.x(x), y + 4, pct.w(40), 12, 4])
  pushDateTimeInputs(bones, pct, x, w, y + 28)
}

// Tags FormField — label + tag input row + counter line
function pushTagsField(bones, pct, x, w, y) {
  bones.push([pct.x(x), y + 4, pct.w(60), 12, 4])
  bones.push([pct.x(x), y + 28, pct.w(w), 44, 12, true])
  bones.push([pct.x(x + w - 100), y + 76, pct.w(100), 10, 4])
}

// CTA bar — ml-auto right aligned. State A: flex-wrap drops the wider button to a 2nd row.
function pushCtaBar(bones, pct, x, w, y, state) {
  const widths = [96, 96, 128]
  const gap = 12
  if (state === 'A') {
    // Row 1: 2 small buttons left-aligned (parent is w-full)
    bones.push([pct.x(x),                  y, pct.w(widths[0]), CTA_BUTTON_H, 12, true])
    bones.push([pct.x(x + widths[0] + gap), y, pct.w(widths[1]), CTA_BUTTON_H, 12, true])
    // Row 2: wide button
    bones.push([pct.x(x), y + CTA_BUTTON_H + gap, pct.w(widths[2]), CTA_BUTTON_H, 12, true])
    return
  }
  // Single row, right-aligned: delete + cancel + submit
  const totalW = widths.reduce((a, b) => a + b, 0) + gap * 2
  let cur = x + w - totalW
  for (const bw of widths) {
    bones.push([pct.x(cur), y, pct.w(bw), CTA_BUTTON_H, 12, true])
    cur += bw + gap
  }
}

// ── ComingSoon shell builders ──────────────────────────────

// "Pièces jointes" shell — header row + dropzone placeholder
function pushCsAttach(bones, pct, x, w, y) {
  bones.push([pct.x(x), y, pct.w(w), CS_ATTACH, 16, true])
  // Header row: icon + label + sprint badge
  bones.push([pct.x(x + 16), y + 14, pct.w(16), 16, 4])
  bones.push([pct.x(x + 40), y + 16, pct.w(Math.round(w * 0.55)), 12, 4])
  bones.push([pct.x(x + w - 46), y + 14, pct.w(30), 16, 9999])
  // Body: subtle helper text line (faded)
  bones.push([pct.x(x + 16), y + 52, pct.w(Math.round((w - 32) * 0.6)), 12, 4])
}

// Date & heure section — rounded card with header row + 2 datetime fields
function pushDateTimeSection(bones, pct, x, w, y, state) {
  const fieldsH = state === 'A'
    ? FIELD_72 * 2 + FIELD_GAP_4
    : FIELD_72
  const sectionH = SECTION_PADDING_Y + SECTION_HEADER_H + SECTION_INNER_GAP + fieldsH
  // Section surface
  bones.push([pct.x(x), y, pct.w(w), sectionH, 16, true])
  // Header: "Date & heure" left, "Toute la journée" toggle right
  bones.push([pct.x(x + 16), y + 18, pct.w(96), 14, 4])
  const togglePillW = 120
  bones.push([pct.x(x + w - 16 - togglePillW), y + 18, pct.w(togglePillW), 18, 9999, true])
  // Datetime fields row
  const fieldsY = y + 16 + SECTION_HEADER_H + SECTION_INNER_GAP
  if (state === 'A') {
    pushDateTimeField(bones, pct, x + 16, w - 32, fieldsY)
    pushDateTimeField(bones, pct, x + 16, w - 32, fieldsY + FIELD_72 + FIELD_GAP_4)
  } else {
    const gap = 16
    const halfW = Math.round((w - 32 - gap) / 2)
    pushDateTimeField(bones, pct, x + 16, halfW, fieldsY)
    pushDateTimeField(bones, pct, x + 16 + halfW + gap, halfW, fieldsY)
  }
}

// ── Band builders ──────────────────────────────────────────

function pushBand1(bones, pct, cw, y0, state) {
  if (state === 'C') {
    // 2 cols: banner (2fr) + right col (3fr), gap-6
    const gap = 24
    const avail = cw - gap
    const leftW = Math.round(avail * 2 / 5)
    const rightW = avail - leftW
    const leftX = 0
    const rightX = leftW + gap
    pushUploadBanner(bones, pct, leftX, y0 + BANNER_PT, leftW)
    pushShortField(bones, pct, rightX, rightW, y0)
    pushTextareaField(bones, pct, rightX, rightW, y0 + TITLE_FIELD_H + FIELD_GAP_4)
  } else {
    // 1 col stacked: banner then right col
    pushUploadBanner(bones, pct, 0, y0, cw)
    const rightY = y0 + BANNER_H + 24
    pushShortField(bones, pct, 0, cw, rightY)
    pushTextareaField(bones, pct, 0, cw, rightY + TITLE_FIELD_H + FIELD_GAP_4)
  }
}

// Band 2a — Lieu (FormField with leading icon)
function pushBand2a(bones, pct, cw, y0) {
  pushLocationField(bones, pct, 0, cw, y0)
}

// Band 2b — Date & heure section (rounded card)
function pushBand2b(bones, pct, cw, y0, state) {
  pushDateTimeSection(bones, pct, 0, cw, y0, state)
}

function pushBand3(bones, pct, cw, y0, state) {
  const catW = 192 // w-48
  const facW = 224 // w-56
  const capW = 96  // w-24
  const gapX = 24  // gap-x-6
  if (state === 'A') {
    // Wraps to 3 rows on narrow viewports
    pushNoCounterField(bones, pct, 0, catW, y0)
    pushNoCounterField(bones, pct, 0, facW, y0 + FIELD_72 + FIELD_GAP_4)
    pushNoCounterField(bones, pct, 0, capW, y0 + (FIELD_72 + FIELD_GAP_4) * 2)
  } else {
    // Single row, wrap-friendly
    pushNoCounterField(bones, pct, 0, catW, y0)
    pushNoCounterField(bones, pct, catW + gapX, facW, y0)
    pushNoCounterField(bones, pct, catW + gapX + facW + gapX, capW, y0)
  }
}

function pushBand4(bones, pct, cw, y0, state) {
  let y = y0
  // Row 1: websiteUrl + contactEmail
  if (state === 'A') {
    pushNoCounterField(bones, pct, 0, cw, y, 80); y += FIELD_72 + FIELD_GAP_4
    pushNoCounterField(bones, pct, 0, cw, y, 80); y += FIELD_72 + FIELD_GAP_4
  } else {
    const gap = 16
    const halfW = Math.round((cw - gap) / 2)
    pushNoCounterField(bones, pct, 0, halfW, y, 80)
    pushNoCounterField(bones, pct, halfW + gap, cw - halfW - gap, y, 80)
    y += FIELD_72 + FIELD_GAP_4
  }
  // Row 2: registrationDeadline (full width datetime field)
  pushDateTimeField(bones, pct, 0, cw, y); y += FIELD_72 + FIELD_GAP_4
  // Row 3: tags
  pushTagsField(bones, pct, 0, cw, y); y += TAGS_FIELD_H + FIELD_GAP_4
  // Border-t line
  bones.push([0, y, 100, 1, 0]); y += BORDER_LINE + FIELD_GAP_4
  // Row 4: ComingSoon Pièces jointes
  pushCsAttach(bones, pct, 0, cw, y)
}

function pushBand5(bones, pct, cw, y0) {
  // Top border
  bones.push([0, y0, 100, 1, 0])
  const y = y0 + 1 + 24 // border + pt-6
  // Header row: icon + label on left, S8 badge on right
  bones.push([pct.x(0),  y,      pct.w(16), 16, 4])
  bones.push([pct.x(24), y + 2,  pct.w(120), 12, 4])
  bones.push([pct.x(cw - 30), y, pct.w(30), 16, 9999])
  // Search input mock (max-w-sm = 384)
  const searchW = Math.min(384, cw)
  bones.push([pct.x(0), y + 32, pct.w(searchW), 40, 12, true])
  // Icon inside search input
  bones.push([pct.x(12), y + 44, pct.w(16), 16, 4])
  // Chips row
  const chipsY = y + 32 + 40 + 12
  bones.push([pct.x(0),   chipsY, pct.w(140), 28, 12, true])
  bones.push([pct.x(152), chipsY, pct.w(120), 28, 12, true])
}

function buildEventEdit(containerW) {
  const state = stateForContainer(containerW)
  const pct = {
    x: px => round(px * 100 / containerW),
    w: px => round(px * 100 / containerW),
  }
  const bones = []
  let y = 0
  pushBand1 (bones, pct, containerW, y, state); y += band1H(state)  + FORM_GAP
  pushBand2a(bones, pct, containerW, y);        y += band2aH()      + FORM_GAP
  pushBand2b(bones, pct, containerW, y, state); y += band2bH(state) + FORM_GAP
  pushBand3 (bones, pct, containerW, y, state); y += band3H(state)  + FORM_GAP
  pushBand4 (bones, pct, containerW, y, state); y += band4H(state)  + FORM_GAP
  pushBand5 (bones, pct, containerW, y);        y += BAND5_H        + FORM_GAP
  pushCtaBar(bones, pct, 0, containerW, y, state)
  return bones
}

const EVENT_EDIT_CONTAINERS = [320, 592, 960]

function genEventEdit() {
  const out = { breakpoints: {} }
  for (const cw of EVENT_EDIT_CONTAINERS) {
    const state = stateForContainer(cw)
    out.breakpoints[String(cw)] = {
      name: 'event-edit',
      viewportWidth: cw,
      width: cw,
      height: formTotalH(state),
      bones: buildEventEdit(cw),
    }
  }
  writeBones('event-edit.bones.json', out)
}

// ============================================================
// MY PUBLICATIONS — PublicationCard skeleton
// Grid: grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5
//
// PublicationCard layout (from MyPublicationsPage.tsx):
//   - Banner h-36 (144px) with category badge top-3 left-3
//   - Body p-4 gap-2:
//       Title (text-base font-bold) + status badge (right-aligned pill)
//       Faculty badge (small pill)
//       Date row: Calendar icon + text (text-xs)
//       Participants row: Users icon + text (text-xs)
//   - Actions footer p-3 border-t: 2-3 action buttons
// ============================================================
const PUB_CARD_H = 324
const PUB_BANNER_H = 144
const PUB_GAP = 20

function buildPublicationCard(containerW, cardX_pct, cardW_pct, cardW_px, y0) {
  const pctX = px => round(cardX_pct + px * 100 / containerW)
  const pctW = px => round(px * 100 / containerW)
  const b = []

  // Card surface (container, lighter)
  b.push([cardX_pct, y0, cardW_pct, PUB_CARD_H, 16, true])

  // Banner (leaf, darker) — h-36 = 144px
  b.push([cardX_pct, y0, cardW_pct, PUB_BANNER_H, 16])

  // Category badge pill on banner (container, lighter) — top-3 left-3
  b.push([pctX(12), y0 + 12, pctW(80), 22, 9999, true])

  // ── Body (p-4 = 16px, gap-2 = 8px) ──
  const bodyY = y0 + PUB_BANNER_H + 16

  // Title (leaf) + status badge (container, lighter)
  b.push([pctX(16), bodyY, pctW(Math.round(cardW_px * 0.55)), 20, 4])
  b.push([pctX(cardW_px - 16 - 64), bodyY + 2, pctW(64), 18, 9999, true])

  // Faculty badge (container, lighter)
  b.push([pctX(16), bodyY + 28, pctW(80), 22, 9999, true])

  // Date row: icon (leaf) + text (leaf)
  b.push([pctX(16), bodyY + 58, pctW(14), 14, 4])
  b.push([pctX(36), bodyY + 58, pctW(Math.round(cardW_px * 0.38)), 14, 4])

  // Participants row: icon (leaf) + text (leaf)
  b.push([pctX(16), bodyY + 80, pctW(14), 14, 4])
  b.push([pctX(36), bodyY + 80, pctW(Math.round(cardW_px * 0.32)), 14, 4])

  // ── Actions footer (border-t + p-3 = 12px) ──
  const actionsY = y0 + PUB_CARD_H - 53

  // Separator (leaf)
  b.push([cardX_pct, actionsY, cardW_pct, 1, 0])

  // Left button "Modifier" (container, lighter)
  b.push([pctX(12), actionsY + 13, pctW(80), 28, 8, true])

  // Right button "Annuler" (container, lighter)
  b.push([pctX(cardW_px - 12 - 76), actionsY + 13, pctW(76), 28, 8, true])

  return b
}

function genPublications() {
  const out = { breakpoints: {} }
  // grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5
  // Container widths inside SectionWrapper (max-w-7xl px-4/6/8):
  //   VP 375 → container ~343 (1 col)
  //   VP 768 (md) → container ~720 (2 cols)
  //   VP 1280 (xl) → container ~1216 (3 cols, capped by max-w-7xl)
  const PUB_BPS = [
    { containerW: 343, cols: 1 },
    { containerW: 720, cols: 2 },
    { containerW: 1216, cols: 3 },
  ]
  for (const { containerW, cols } of PUB_BPS) {
    const layout = fixedColsLayout(containerW, cols, PUB_GAP, 6)
    const { cardW_px, cards, rows } = layout
    const cardW_pct = round(cardW_px * 100 / containerW)
    const height = rows * PUB_CARD_H + (rows - 1) * PUB_GAP
    const allBones = []
    for (let r = 0; r < rows; r++) {
      for (const card of cards) {
        const x_pct = round(card.x_px * 100 / containerW)
        const y = r * (PUB_CARD_H + PUB_GAP)
        allBones.push(...buildPublicationCard(containerW, x_pct, cardW_pct, cardW_px, y))
      }
    }
    out.breakpoints[String(containerW)] = {
      name: 'my-publications',
      viewportWidth: containerW,
      width: containerW,
      height,
      bones: allBones,
    }
  }
  writeBones('my-publications.bones.json', out)
}

genCards()
genSearch()
genCalendar()
genEventDetail()
genEventEdit()
genPublications()
