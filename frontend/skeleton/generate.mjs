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

// Heights — must match what the fixture produces so scaleY=1.
// IMPORTANT: boneyard 1.7.7 filters bones flagged as containers (`c=true`,
// the 6th array element) at render time. We therefore never set that flag
// on bones we want visible, and instead rely on alpha compounding (each
// overlapping bone adds ~0.06 alpha) to create the lighter/darker hierarchy.
const BANNER_H_MOBILE = 288    // h-72
const BANNER_H_DESKTOP = 320   // h-80
const DESC_H = 160             // "À propos" card (h-40)
const ATTENDEES_COMPACT_H = 90 // <AttendeesList> compact variant: 2 (border) + 32 (py-4) + 28 (h2+mb-3) + 28 (summary row)
const INFO_EXTRA_H = 176       // SCRUM-117 "Informations complémentaires" card (h-44)
const MAIN_GAP = 20            // gap-5 between main-col children
const INFOS_CARD_H = 288       // sidebar "Infos clés" card (h-72)
const ATTENDANCE_CARD_H = 176  // sidebar "Favoris + share + AttendanceButtons" card (h-44)
const ICS_CARD_H = 236         // <IcsExportButton> card: 24+20+16+(40+16+40+16+40)+24+2 ≈ 236
const STATS_CARD_H = 172       // <EventStatsPanel> public stats card (SCRUM-92): header + 3 mini-tiles
const SIDEBAR_GAP = 16         // gap-4 between sidebar children
const GRID_GAP = 24            // grid gap-6

function mainColH(bannerH) {
  return bannerH + MAIN_GAP
       + DESC_H + MAIN_GAP
       + ATTENDEES_COMPACT_H + MAIN_GAP
       + INFO_EXTRA_H
}
function sidebarColH() {
  return INFOS_CARD_H + SIDEBAR_GAP
       + ATTENDANCE_CARD_H + SIDEBAR_GAP
       + ICS_CARD_H + SIDEBAR_GAP
       + STATS_CARD_H
}

// Bone helpers. The rendered colour is the same for every bone (no container
// vs leaf shading in boneyard 1.7.7), so visual hierarchy comes from alpha
// compounding when bones overlap.
const BONE_RADIUS_LG = 24       // rounded-3xl card
const BONE_RADIUS_MD = 16       // rounded-2xl card
const BONE_PILL = 9999          // fully rounded pill (used for badges)

// Leaf rect bone: rendered as `${w}%` of container width.
function rect(x, y, w, h, r = 4) { return [x, y, w, h, r] }
// Circle bone: boneyard locks width to `b.h * scaleY` (absolute pixels) when
// `r === '50%'` AND `(w/100) * BP_width ≈ h`. Used for fixed-size icons/avatars.
function circle(xPct, y, sizePct, sizePx) {
  return [xPct, y, sizePct, sizePx, '50%']
}

// Builder for the banner zone (image + category badge top-left + event title).
function pushBanner(bones, pct, colX, colW, y0, bannerH) {
  // Banner image surface (full-width)
  bones.push(rect(pct.x(colX), y0, pct.w(colW), bannerH, BONE_RADIUS_LG))
  // Category badge pill, top-left (~110px wide, 24h)
  bones.push(rect(pct.x(colX + 16), y0 + 16, pct.w(110), 24, BONE_PILL))
  // Event title (2 lines anchored near the bottom — p-6 = 24px bottom padding)
  const titleBottomPad = 24
  const t1H = 28
  const t2H = 22
  const t2Y = y0 + bannerH - titleBottomPad - t2H
  const t1Y = t2Y - 8 - t1H
  bones.push(rect(pct.x(colX + 24), t1Y, pct.w(Math.round(colW * 0.7)), t1H, 6))
  bones.push(rect(pct.x(colX + 24), t2Y, pct.w(Math.round(colW * 0.5)), t2H, 6))
}

// Builder for the "À propos" description card.
function pushDescriptionCard(bones, pct, colX, colW, y0) {
  // Card surface
  bones.push(rect(pct.x(colX), y0, pct.w(colW), DESC_H, BONE_RADIUS_LG))
  // "À propos" small uppercase header
  bones.push(rect(pct.x(colX + 24), y0 + 24, pct.w(56), 10, 4))
  // 4 description lines, decreasing widths
  const lineWs = [0.96, 0.92, 0.85, 0.62]
  const innerW = colW - 48
  for (let i = 0; i < 4; i++) {
    bones.push(rect(pct.x(colX + 24), y0 + 56 + i * 20, pct.w(Math.round(innerW * lineWs[i])), 12, 4))
  }
}

// Builder for the <AttendeesList> compact (non-organizer) card.
// One short card with a "Participants" header and a summary row of avatars +
// "X personnes participent" label.
function pushAttendeesCard(bones, pct, colX, colW, y0) {
  // Card surface
  bones.push(rect(pct.x(colX), y0, pct.w(colW), ATTENDEES_COMPACT_H, BONE_RADIUS_LG))
  // "Participants" header
  bones.push(rect(pct.x(colX + 24), y0 + 18, pct.w(78), 10, 4))
  // 4 overlapping avatar circles (28x28 each, -space-x-2 → 6px overlap)
  const avSize = 28
  const avOverlap = 22
  for (let i = 0; i < 4; i++) {
    bones.push(circle(pct.x(colX + 24 + i * avOverlap), y0 + 50, pct.w(avSize), avSize))
  }
  // Summary text after avatars
  bones.push(rect(pct.x(colX + 24 + 4 * avOverlap + 12), y0 + 56, pct.w(Math.round((colW - 24 - 4 * avOverlap - 12 - 24) * 0.6)), 14, 4))
}

// Builder for the SCRUM-117 "Informations complémentaires" main-col card.
function pushInfoExtraCard(bones, pct, colX, colW, y0) {
  // Card surface
  bones.push(rect(pct.x(colX), y0, pct.w(colW), INFO_EXTRA_H, BONE_RADIUS_LG))
  // "Informations complémentaires" header
  bones.push(rect(pct.x(colX + 24), y0 + 24, pct.w(168), 10, 4))
  // 4 InfoRow placeholders: 16x16 icon (circle) + text line
  const lineWs = [0.7, 0.55, 0.62, 0.46]
  const innerW = colW - 48 - 24 // minus padding + icon column
  for (let i = 0; i < 4; i++) {
    const ry = y0 + 60 + i * 24
    bones.push(circle(pct.x(colX + 24), ry, pct.w(16), 16))
    bones.push(rect(pct.x(colX + 48), ry + 2, pct.w(Math.round(innerW * lineWs[i])), 12, 4))
  }
}

// Builder for the sidebar "Infos clés" card (date / location / capacity rows
// + organizer + capacity indicator).
function pushInfosClesCard(bones, pct, colX, colW, y0) {
  // Card surface (p-5 = 20px padding)
  bones.push(rect(pct.x(colX), y0, pct.w(colW), INFOS_CARD_H, BONE_RADIUS_LG))
  // 3 InfoRows (icon 16x16 + text line)
  const rowWs = [0.82, 0.62, 0.5]
  for (let i = 0; i < 3; i++) {
    const ry = y0 + 24 + i * 28
    bones.push(circle(pct.x(colX + 20), ry, pct.w(16), 16))
    bones.push(rect(pct.x(colX + 44), ry + 2, pct.w(Math.round((colW - 64) * rowWs[i])), 12, 4))
  }
  // Separator
  bones.push(rect(pct.x(colX + 20), y0 + 116, pct.w(colW - 40), 1, 0))
  // Organizer row: avatar 36x36 (circle) + 2 text lines
  bones.push(circle(pct.x(colX + 20), y0 + 132, pct.w(36), 36))
  bones.push(rect(pct.x(colX + 68), y0 + 138, pct.w(70), 10, 4))
  bones.push(rect(pct.x(colX + 68), y0 + 156, pct.w(Math.round((colW - 88) * 0.6)), 14, 4))
  // Separator
  bones.push(rect(pct.x(colX + 20), y0 + 184, pct.w(colW - 40), 1, 0))
  // Capacity indicator header (icon + label)
  bones.push(circle(pct.x(colX + 20), y0 + 200, pct.w(16), 16))
  bones.push(rect(pct.x(colX + 44), y0 + 202, pct.w(Math.round((colW - 64) * 0.5)), 12, 4))
  // 2 capacity pills (available + waitlist)
  const pill1W = Math.round((colW - 40) * 0.45)
  const pill2W = Math.round((colW - 40) * 0.35)
  bones.push(rect(pct.x(colX + 20), y0 + 232, pct.w(pill1W), 24, 8))
  bones.push(rect(pct.x(colX + 20 + pill1W + 8), y0 + 232, pct.w(pill2W), 24, 8))
}

// Builder for the sidebar Attendance + Favoris card.
function pushAttendanceCard(bones, pct, colX, colW, y0) {
  // Card surface (px-5 py-4)
  bones.push(rect(pct.x(colX), y0, pct.w(colW), ATTENDANCE_CARD_H, BONE_RADIUS_LG))
  // Top row: 2 buttons side-by-side (Favoris | Partager)
  const btnH = 36
  const btnGap = 12
  const btnW = Math.round((colW - 40 - btnGap) / 2)
  bones.push(rect(pct.x(colX + 20), y0 + 16, pct.w(btnW), btnH, 12))
  bones.push(rect(pct.x(colX + 20 + btnW + btnGap), y0 + 16, pct.w(btnW), btnH, 12))
  // 3 attendance buttons (rounded-xl pills, 2 visible variants — Inscrit / Intéressé / Liste)
  const aH = 40
  const aGap = 8
  const aW = Math.round((colW - 40 - aGap * 2) / 3)
  for (let i = 0; i < 3; i++) {
    bones.push(rect(pct.x(colX + 20 + i * (aW + aGap)), y0 + 76, pct.w(aW), aH, 12))
  }
  // Counter text under buttons
  bones.push(rect(pct.x(colX + 20), y0 + 132, pct.w(Math.round((colW - 40) * 0.7)), 12, 4))
}

// Builder for the <IcsExportButton> "Ajouter au calendrier" card. p-6 padding,
// header (icon + label), then 3 stacked option buttons.
function pushIcsCard(bones, pct, colX, colW, y0) {
  // Card surface
  bones.push(rect(pct.x(colX), y0, pct.w(colW), ICS_CARD_H, BONE_RADIUS_LG))
  // Header row: 16x16 calendar icon + label
  bones.push(circle(pct.x(colX + 24), y0 + 26, pct.w(16), 16))
  bones.push(rect(pct.x(colX + 48), y0 + 28, pct.w(Math.round((colW - 72) * 0.5)), 14, 4))
  // 3 option buttons (gap-4 between)
  const btnH = 40
  const btnGap = 16
  for (let i = 0; i < 3; i++) {
    const by = y0 + 24 + 36 + i * (btnH + btnGap)
    bones.push(rect(pct.x(colX + 24), by, pct.w(colW - 48), btnH, 12))
  }
}

// Builder for the Main column (banner + description + attendees compact + info-extra card).
function pushMainCol(bones, pct, colX, colW, y0, bannerH) {
  let y = y0
  pushBanner(bones, pct, colX, colW, y, bannerH);   y += bannerH + MAIN_GAP
  pushDescriptionCard(bones, pct, colX, colW, y);   y += DESC_H + MAIN_GAP
  pushAttendeesCard(bones, pct, colX, colW, y);     y += ATTENDEES_COMPACT_H + MAIN_GAP
  pushInfoExtraCard(bones, pct, colX, colW, y)
}

// Builder for the public "Statistiques de participation" card (SCRUM-92).
// EventStatsPanel: glass card with a header row (BarChart icon + label) and
// a 3-column grid of vertical mini-tiles (icon container + value + label).
// All bones are leaves (no container flag) — the lighter/darker hierarchy
// comes from alpha compounding on overlapping bones.
function pushStatsCard(bones, pct, colX, colW, y0) {
  // Card surface (rounded-3xl, p-5)
  bones.push(rect(pct.x(colX), y0, pct.w(colW), STATS_CARD_H, BONE_RADIUS_LG))
  // Header row: 16x16 BarChart icon + label
  bones.push(circle(pct.x(colX + 20), y0 + 22, pct.w(16), 16))
  bones.push(rect(pct.x(colX + 44), y0 + 24, pct.w(Math.round((colW - 64) * 0.55)), 12, 4))
  // 3 vertical mini-tiles (rounded-2xl): icon container + value + label, centered
  const bodyY = y0 + 56
  const tileH = 96
  const gapX = 8
  const innerX = colX + 20
  const innerW = colW - 40
  const boxW = (innerW - 2 * gapX) / 3
  for (let i = 0; i < 3; i++) {
    const bx = innerX + i * (boxW + gapX)
    bones.push(rect(pct.x(bx), bodyY, pct.w(boxW), tileH, BONE_RADIUS_MD))            // tile surface
    bones.push(rect(pct.x(bx + (boxW - 32) / 2), bodyY + 12, pct.w(32), 32, 8))       // icon container (32x32 rounded-lg)
    bones.push(rect(pct.x(bx + (boxW - 24) / 2), bodyY + 52, pct.w(24), 16, 4))       // value
    bones.push(rect(pct.x(bx + (boxW - 36) / 2), bodyY + 76, pct.w(36), 10, 4))       // label
  }
}

// Builder for the Sidebar column (infos clés + attendance + ICS export + public stats card).
// We model the public variant (no organizer-only actions), since the skeleton
// renders before we know whether the user is the organizer. The stats panel
// is shown to everyone (SCRUM-92 — viewCount/interestedCount are public).
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
// All bones are leaves (no container flag) so they actually render at runtime;
// alpha compounding gives the lighter/darker hierarchy on overlapping bones.

// Banner upload zone (h-52, dashed border in the real form). Centered icon
// badge + 2 text lines underneath.
function pushUploadBanner(bones, pct, x, y, w) {
  bones.push(rect(pct.x(x), y, pct.w(w), BANNER_H, BONE_RADIUS_MD))
  const iconSize = 48
  const iconX = x + (w - iconSize) / 2
  const iconY = y + (BANNER_H - iconSize - 44) / 2
  bones.push(rect(pct.x(iconX), iconY, pct.w(iconSize), iconSize, 12))
  bones.push(rect(pct.x(x + (w - 160) / 2), iconY + 60, pct.w(160), 12, 4))
  bones.push(rect(pct.x(x + (w - 120) / 2), iconY + 80, pct.w(120), 10, 4))
}

// Title field: label + input + counter underneath.
function pushShortField(bones, pct, x, w, y) {
  bones.push(rect(pct.x(x), y + 4, pct.w(40), 12, 4))                       // label
  bones.push(rect(pct.x(x), y + 28, pct.w(w), 44, 12))                      // input surface
  bones.push(rect(pct.x(x + w - 50), y + 76, pct.w(50), 12, 4))             // counter (right)
}

// Description field: label + textarea + counter.
function pushTextareaField(bones, pct, x, w, y) {
  bones.push(rect(pct.x(x), y + 4, pct.w(70), 12, 4))
  bones.push(rect(pct.x(x), y + 28, pct.w(w), 144, 12))
  bones.push(rect(pct.x(x + w - 60), y + 176, pct.w(60), 12, 4))
}

// Standard FormField with label + input (no counter).
function pushNoCounterField(bones, pct, x, w, y, labelW = 40) {
  bones.push(rect(pct.x(x), y + 4, pct.w(labelW), 12, 4))
  bones.push(rect(pct.x(x), y + 28, pct.w(w), 44, 12))
}

// Field with a leading icon inside the input (Lieu / websiteUrl / contactEmail).
function pushIconField(bones, pct, x, w, y, labelW = 40) {
  bones.push(rect(pct.x(x), y + 4, pct.w(labelW), 12, 4))                   // label
  bones.push(rect(pct.x(x), y + 28, pct.w(w), 44, 12))                       // input surface
  bones.push(circle(pct.x(x + 12), y + 42, pct.w(16), 16))                   // 16x16 icon
}

// Date/time input row (date 1fr + HH select + : + MM select).
function pushDateTimeInputs(bones, pct, x, w, y) {
  const selW = 56
  const hhmmW = selW + 8 + selW
  const dateW = w - hhmmW - 12
  bones.push(rect(pct.x(x), y, pct.w(dateW), 44, 12))
  bones.push(rect(pct.x(x + dateW + 12), y, pct.w(selW), 44, 12))
  bones.push(rect(pct.x(x + dateW + 12 + selW + 8), y, pct.w(selW), 44, 12))
}

// Standalone datetime FormField (label + datetime row).
function pushDateTimeField(bones, pct, x, w, y) {
  bones.push(rect(pct.x(x), y + 4, pct.w(40), 12, 4))
  pushDateTimeInputs(bones, pct, x, w, y + 28)
}

// Tags FormField — label + tag input row + counter line.
function pushTagsField(bones, pct, x, w, y) {
  bones.push(rect(pct.x(x), y + 4, pct.w(60), 12, 4))
  bones.push(rect(pct.x(x), y + 28, pct.w(w), 44, 12))
  bones.push(rect(pct.x(x + w - 100), y + 76, pct.w(100), 10, 4))
}

// CTA bar — ml-auto right aligned. State A: flex-wrap drops the wider button
// to a 2nd row.
function pushCtaBar(bones, pct, x, w, y, state) {
  const widths = [96, 96, 128]
  const gap = 12
  if (state === 'A') {
    bones.push(rect(pct.x(x),                   y, pct.w(widths[0]), CTA_BUTTON_H, 12))
    bones.push(rect(pct.x(x + widths[0] + gap), y, pct.w(widths[1]), CTA_BUTTON_H, 12))
    bones.push(rect(pct.x(x), y + CTA_BUTTON_H + gap, pct.w(widths[2]), CTA_BUTTON_H, 12))
    return
  }
  const totalW = widths.reduce((a, b) => a + b, 0) + gap * 2
  let cur = x + w - totalW
  for (const bw of widths) {
    bones.push(rect(pct.x(cur), y, pct.w(bw), CTA_BUTTON_H, 12))
    cur += bw + gap
  }
}

// "Pièces jointes" coming-soon shell — header row + dropzone helper line.
function pushCsAttach(bones, pct, x, w, y) {
  bones.push(rect(pct.x(x), y, pct.w(w), CS_ATTACH, BONE_RADIUS_MD))
  bones.push(circle(pct.x(x + 16), y + 14, pct.w(16), 16))
  bones.push(rect(pct.x(x + 40), y + 16, pct.w(Math.round(w * 0.55)), 12, 4))
  bones.push(rect(pct.x(x + w - 46), y + 14, pct.w(30), 16, BONE_PILL))
  bones.push(rect(pct.x(x + 16), y + 52, pct.w(Math.round((w - 32) * 0.6)), 12, 4))
}

// Date & heure section — rounded card with header row + 2 datetime fields.
function pushDateTimeSection(bones, pct, x, w, y, state) {
  const fieldsH = state === 'A'
    ? FIELD_72 * 2 + FIELD_GAP_4
    : FIELD_72
  const sectionH = SECTION_PADDING_Y + SECTION_HEADER_H + SECTION_INNER_GAP + fieldsH
  // Section card surface
  bones.push(rect(pct.x(x), y, pct.w(w), sectionH, BONE_RADIUS_MD))
  // Header: "Date & heure" label (left) + "Toute la journée" toggle pill (right)
  bones.push(rect(pct.x(x + 16), y + 18, pct.w(96), 14, 4))
  const togglePillW = 120
  bones.push(rect(pct.x(x + w - 16 - togglePillW), y + 18, pct.w(togglePillW), 22, BONE_PILL))
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

// Band 2a — Lieu (FormField with leading MapPin icon)
function pushBand2a(bones, pct, cw, y0) {
  pushIconField(bones, pct, 0, cw, y0)
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
  // Row 1: websiteUrl (Globe icon) + contactEmail (Mail icon)
  if (state === 'A') {
    pushIconField(bones, pct, 0, cw, y, 80); y += FIELD_72 + FIELD_GAP_4
    pushIconField(bones, pct, 0, cw, y, 80); y += FIELD_72 + FIELD_GAP_4
  } else {
    const gap = 16
    const halfW = Math.round((cw - gap) / 2)
    pushIconField(bones, pct, 0, halfW, y, 80)
    pushIconField(bones, pct, halfW + gap, cw - halfW - gap, y, 80)
    y += FIELD_72 + FIELD_GAP_4
  }
  // Row 2: registrationDeadline (full width datetime field)
  pushDateTimeField(bones, pct, 0, cw, y); y += FIELD_72 + FIELD_GAP_4
  // Row 3: tags
  pushTagsField(bones, pct, 0, cw, y); y += TAGS_FIELD_H + FIELD_GAP_4
  // Border-t separator
  bones.push(rect(0, y, 100, 1, 0)); y += BORDER_LINE + FIELD_GAP_4
  // Row 4: ComingSoon Pièces jointes
  pushCsAttach(bones, pct, 0, cw, y)
}

function pushBand5(bones, pct, cw, y0) {
  // Top border separator
  bones.push(rect(0, y0, 100, 1, 0))
  const y = y0 + 1 + 24 // border + pt-6
  // Header row: 16x16 icon + label on left, S8 badge on right
  bones.push(circle(pct.x(0), y, pct.w(16), 16))
  bones.push(rect(pct.x(24), y + 2, pct.w(120), 12, 4))
  bones.push(rect(pct.x(cw - 30), y, pct.w(30), 16, BONE_PILL))
  // Search input mock (max-w-sm = 384)
  const searchW = Math.min(384, cw)
  bones.push(rect(pct.x(0), y + 32, pct.w(searchW), 40, 12))
  bones.push(circle(pct.x(12), y + 44, pct.w(16), 16))
  // Chips row
  const chipsY = y + 32 + 40 + 12
  bones.push(rect(pct.x(0),   chipsY, pct.w(140), 28, 12))
  bones.push(rect(pct.x(152), chipsY, pct.w(120), 28, 12))
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

// ============================================================
// EVENT STATS — organizer dashboard (/events/:id/stats)
// Layout (from EventStatsPage.tsx → EventStatsFixture):
//   SectionWrapper size="lg" (max-w-5xl, container ≤ 960 desktop) wraps
//   `<div className="flex flex-col gap-6">` containing:
//     - Refresh button row (justify-end)
//     - KPI grid (3 cols on ≥sm, stacked on mobile)
//     - Chart card (h-[260px])
//     - Capacity bar card (h-[100px])
//     - Attendees toggle button (h-12)
// 2-shade hierarchy:
//   - Card surfaces, KPI icon containers, refresh button, attendees button
//     → containers (lighter)
//   - Heading lines, bar shapes inside the chart, capacity bar fill,
//     icon/text inside cards → leaves (darker)
// ============================================================

const ES_REFRESH_H = 36       // h-9 (px-3 py-1.5 + text-sm)
const ES_REFRESH_W = 130      // ~width of "Rafraîchir" + icon + padding
const ES_KPI_H = 88           // h-[88px]
const ES_CHART_H = 260        // h-[260px]
const ES_CAPACITY_H = 100     // h-[100px]
const ES_ATTENDEES_H = 48     // h-12
const ES_GAP = 24             // gap-6
const ES_KPI_GAP = 16         // gap-4

// "max-sm:grid-cols-1" → stacked when viewport < 640. Tailwind sm = 640px viewport;
// the SectionWrapper subtracts horizontal padding so the corresponding container
// width is roughly 608px. Below that, KPI cards stack.
const ES_KPI_STACK_THRESHOLD = 608

function pushEsRefreshButton(bones, pct, colW, y0) {
  const x_px = colW - ES_REFRESH_W
  // Button surface (container, lighter)
  bones.push([pct.x(x_px), y0, pct.w(ES_REFRESH_W), ES_REFRESH_H, 12, true])
  // Icon (leaf, darker — 16x16 centered vertically)
  bones.push([pct.x(x_px + 14), y0 + (ES_REFRESH_H - 16) / 2, pct.w(16), 16, 4])
  // Label (leaf, darker — text-sm = 14px tall, ~72px wide)
  bones.push([pct.x(x_px + 38), y0 + (ES_REFRESH_H - 14) / 2, pct.w(72), 14, 4])
}

function pushEsKpiCard(bones, pct, x_px, w_px, y0) {
  // p-4 = 16, items-center vertically. Inner = 88 - 32 = 56.
  // Icon container is 40x40, centered vertically: y = y0 + (88-40)/2 = y0 + 24.
  // Card surface (container, lighter)
  bones.push([pct.x(x_px), y0, pct.w(w_px), ES_KPI_H, 16, true])
  // Icon container (container, lighter — gradient pill 40x40)
  bones.push([pct.x(x_px + 16), y0 + 24, pct.w(40), 40, 12, true])
  // Label (leaf — text-xs = 12, mb-1)
  bones.push([pct.x(x_px + 72), y0 + 30, pct.w(Math.min(80, w_px - 96)), 12, 4])
  // Value (leaf — text-2xl = 24, leading-none)
  bones.push([pct.x(x_px + 72), y0 + 48, pct.w(Math.min(48, w_px - 96)), 22, 4])
}

function pushEsChartCard(bones, pct, colW, y0) {
  // p-6 = 24 padding. Heading text-xs (12) + mb-4 (16). Chart area below.
  // Card surface (container)
  bones.push([pct.x(0), y0, 100, ES_CHART_H, 24, true])
  // Heading "Répartition" (leaf)
  bones.push([pct.x(24), y0 + 28, pct.w(80), 12, 4])
  // Y-axis ticks (4 short leaves on the left)
  for (let i = 0; i < 4; i++) {
    const ty = y0 + 70 + i * 40
    bones.push([pct.x(28), ty, pct.w(8), 10, 4])
  }
  // 3 vertical bars centered in the plot area, varied heights
  const plotX = 60
  const plotW = colW - 60 - 24
  const plotBaseline = y0 + ES_CHART_H - 36 // leave room for x-axis labels
  const barW = 56
  const slotW = plotW / 3
  const heights = [120, 80, 100]
  for (let i = 0; i < 3; i++) {
    const cx = plotX + slotW * (i + 0.5)
    const bx = cx - barW / 2
    const bh = heights[i]
    // Bar (leaf — colored at runtime; skeleton just renders the shape)
    bones.push([pct.x(bx), plotBaseline - bh, pct.w(barW), bh, 6])
    // X-axis label (leaf)
    bones.push([pct.x(cx - 28), plotBaseline + 12, pct.w(56), 10, 4])
  }
}

function pushEsCapacityBar(bones, pct, colW, y0) {
  // p-5 = 20, gap-3 = 12. Heading row + bar + sublabel.
  // Card surface (container)
  bones.push([pct.x(0), y0, 100, ES_CAPACITY_H, 24, true])
  // Heading label (leaf, left) "Taux de remplissage"
  bones.push([pct.x(20), y0 + 22, pct.w(140), 12, 4])
  // Pct value (leaf, right) "33%"
  bones.push([pct.x(colW - 20 - 32), y0 + 22, pct.w(32), 14, 4])
  // Bar background (leaf — darker rail)
  bones.push([pct.x(20), y0 + 48, pct.w(colW - 40), 16, 9999])
  // Bar fill (container, lighter — partial width)
  bones.push([pct.x(20), y0 + 48, pct.w(Math.round((colW - 40) * 0.33)), 16, 9999, true])
  // Sublabel (leaf) "X / Y places"
  bones.push([pct.x(20), y0 + 78, pct.w(80), 10, 4])
}

function pushEsAttendeesToggle(bones, pct, colW, y0) {
  // h-12 = 48. button px-5 py-3.5 with icon + label + chevron.
  // Surface (container)
  bones.push([pct.x(0), y0, 100, ES_ATTENDEES_H, 16, true])
  // Users icon (leaf)
  bones.push([pct.x(20), y0 + 16, pct.w(16), 16, 4])
  // Label (leaf) "Voir les participants"
  bones.push([pct.x(44), y0 + 18, pct.w(160), 12, 4])
  // Chevron (leaf)
  bones.push([pct.x(colW - 20 - 16), y0 + 16, pct.w(16), 16, 4])
}

function buildEventStats(containerW) {
  const pct = {
    x: px => round(px * 100 / containerW),
    w: px => round(px * 100 / containerW),
  }
  const bones = []
  let y = 0

  // Refresh button row (always justify-end)
  pushEsRefreshButton(bones, pct, containerW, y)
  y += ES_REFRESH_H + ES_GAP

  // KPI grid
  const stacked = containerW < ES_KPI_STACK_THRESHOLD
  if (stacked) {
    for (let i = 0; i < 3; i++) {
      pushEsKpiCard(bones, pct, 0, containerW, y)
      y += ES_KPI_H
      if (i < 2) y += ES_KPI_GAP
    }
  } else {
    const cardW = (containerW - 2 * ES_KPI_GAP) / 3
    for (let i = 0; i < 3; i++) {
      const cx = i * (cardW + ES_KPI_GAP)
      pushEsKpiCard(bones, pct, cx, cardW, y)
    }
    y += ES_KPI_H
  }
  y += ES_GAP

  // Chart card
  pushEsChartCard(bones, pct, containerW, y)
  y += ES_CHART_H + ES_GAP

  // Capacity bar
  pushEsCapacityBar(bones, pct, containerW, y)
  y += ES_CAPACITY_H + ES_GAP

  // Attendees toggle
  pushEsAttendeesToggle(bones, pct, containerW, y)
  y += ES_ATTENDEES_H

  return { bones, height: y }
}

const STATS_CONTAINERS = [343, 720, 960]

function genEventStats() {
  const out = { breakpoints: {} }
  for (const cw of STATS_CONTAINERS) {
    const { bones, height } = buildEventStats(cw)
    out.breakpoints[String(cw)] = {
      name: 'event-stats',
      viewportWidth: cw,
      width: cw,
      height,
      bones,
    }
  }
  writeBones('event-stats.bones.json', out)
}

genCards()
genSearch()
genCalendar()
genEventDetail()
genEventEdit()
genPublications()
genEventStats()
