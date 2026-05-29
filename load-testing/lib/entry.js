// setup / browse / writes / teardown — the integration scenario (spec §8).
//
// SAFETY (prod target): the headline load (browse) is STRICTLY READ-ONLY, so it never pollutes real
// data nor notifies real users. All writes operate ONLY on content created by the test accounts
// (own events) or between test accounts (follow). teardown() best-effort removes test-created data.
import exec from 'k6/execution';
import { sleep } from 'k6';
import { req } from './http.js';
import { think, randItem, futureIso } from './config.js';
import { mintTokenPool } from './auth.js';
import { notificationLag } from './metrics.js';

const E = (endpoint, service, extra) => Object.assign({ endpoint: endpoint, service: service }, extra || {});

function asArray(res) {
  try {
    let j = res.json();
    if (j && !Array.isArray(j) && Array.isArray(j.content)) j = j.content;
    return Array.isArray(j) ? j : [];
  } catch (e) {
    return [];
  }
}

// ----- setup: mint token pool, resolve pool UUIDs, build a READ working set -----
export function setup() {
  const skipAuth = !!__ENV.SKIP_AUTH; // anon-only capacity probe doesn't need the token pool
  const tokens = skipAuth ? [] : mintTokenPool();

  const poolUuids = []; // aligned with tokens (null if resolution failed)
  if (!skipAuth) {
    for (let i = 0; i < tokens.length; i++) {
      const r = req('GET', '/users/me', { token: tokens[i], tags: E('/users/me', 'user'), expect: [200] });
      let id = null;
      try { id = r.json('id'); } catch (e) {}
      poolUuids.push(id || null);
    }
  }

  // use /events/search (NOT rate-limited) so the working set is reliable even if the
  // throttled /events listing window is exhausted for this IP.
  const ws = req('GET', '/events/search?q=a&page=0&size=40', { tags: E('/events/search', 'event'), expect: [200] });
  const evs = asArray(ws);
  const eventIds = evs.map((e) => e.id).filter((x) => x != null);
  const creatorIds = evs.map((e) => e.creatorId || (e.creator && e.creator.id)).filter(Boolean);

  return { tokens: tokens, poolUuids: poolUuids, eventIds: eventIds, creatorIds: creatorIds };
}

function tokenFor(data) { return data.tokens[(exec.vu.idInTest - 1) % data.tokens.length]; }

function otherPoolUuid(data) {
  const self = data.poolUuids[(exec.vu.idInTest - 1) % data.poolUuids.length];
  for (let k = 0; k < 5; k++) {
    const cand = data.poolUuids[Math.floor(Math.random() * data.poolUuids.length)];
    if (cand && cand !== self) return cand;
  }
  return null;
}

// ===================== READ-ONLY browse (50–200 VU headline load) =====================
export function browse(data) {
  if (Math.random() < 0.78) anonRead(data);
  else authRead(data);
  think(1, 4);
}

function anonRead(data) {
  req('GET', '/events?status=PUBLISHED&page=0&size=20', { tags: E('/events', 'event'), expect: [200] });
  if (Math.random() < 0.5)
    req('GET', '/events/search?q=a&page=0&size=20', { tags: E('/events/search', 'event'), expect: [200] });
  if (Math.random() < 0.4)
    req('GET', '/events/featured', { tags: E('/events/featured', 'event'), expect: [200] });

  const id = randItem(data.eventIds);
  if (id) {
    req('GET', '/events/' + id, { tags: E('/events/{id}', 'event'), expect: [200, 404] });
    if (Math.random() < 0.6)
      req('GET', '/events/' + id + '/comments?page=0&size=20', { tags: E('/events/{id}/comments', 'engagement'), expect: [200, 404] });
    if (Math.random() < 0.3)
      req('GET', '/events/' + id + '/occurrences', { tags: E('/events/{id}/occurrences', 'event'), expect: [200, 400, 404] });
  }
  const cid = randItem(data.creatorIds);
  if (cid && Math.random() < 0.4)
    req('GET', '/users/' + cid, { tags: E('/users/{id}', 'user'), expect: [200, 404] });
}

function authRead(data) {
  const t = tokenFor(data);
  req('GET', '/users/me', { token: t, tags: E('/users/me', 'user'), expect: [200] });
  const reads = [
    ['/users/me/favorites', 'event'],
    ['/users/me/events', 'event'],
    ['/users/me/attendances', 'engagement'],
    ['/users/me/participations', 'engagement'],
    ['/users/me/notifications', 'notification'],
  ];
  const n = 2 + Math.floor(Math.random() * 3);
  for (let i = 0; i < n; i++) {
    const p = reads[Math.floor(Math.random() * reads.length)];
    req('GET', p[0], { token: t, tags: E(p[0], p[1]), expect: [200] });
  }
}

// ===================== READ-ONLY capacity probe (un-throttled endpoints only) =====================
// Avoids GET /events (Kong-throttled to 10/min/IP). Anonymous, short think-time to push throughput.
export function browseReadOnly(data) {
  const r = Math.random();
  if (r < 0.45) {
    req('GET', '/events/search?q=a&page=0&size=20', { tags: E('/events/search', 'event'), expect: [200] });
  } else if (r < 0.75) {
    req('GET', '/events/featured', { tags: E('/events/featured', 'event'), expect: [200] });
  } else {
    const id = randItem(data.eventIds);
    if (id) {
      req('GET', '/events/' + id, { tags: E('/events/{id}', 'event'), expect: [200, 404] });
      if (Math.random() < 0.5)
        req('GET', '/events/' + id + '/comments?page=0&size=20', { tags: E('/events/{id}/comments', 'engagement'), expect: [200, 404] });
    } else {
      req('GET', '/events/featured', { tags: E('/events/featured', 'event'), expect: [200] });
    }
  }
  think(0.5, 2);
}

// ===================== WRITES (low rate; self-created content only) =====================
export function writes(data) {
  const t = tokenFor(data);
  const vu = exec.vu.idInTest;
  const iter = exec.scenario.iterationInTest;
  const dates = futureIso(3 + (iter % 20));

  const created = req('POST', '/events', {
    token: t,
    body: {
      title: '[LOADTEST] perf ' + vu + '-' + iter,
      description: '[LOADTEST] generated by k6',
      location: 'Online',
      startDate: dates.start,
      endDate: dates.end,
      category: 'CONFERENCE',
      faculty: 'SCIENCES',
      capacity: 100,
    },
    tags: E('/events', 'event', { bucket: 'events.create' }),
    expect: [201, 429],
  });
  if (created.status !== 201) return; // 429 → rate-limit ceiling (recorded); skip rest
  let id = null;
  try { id = created.json('id'); } catch (e) {}
  if (!id) return;

  req('PATCH', '/events/' + id + '/publish', { token: t, tags: E('/events/{id}/publish', 'event'), expect: [200, 409, 422] });
  req('POST', '/events/' + id + '/favorite', { token: t, tags: E('/events/{id}/favorite', 'event'), expect: [200, 404] });
  req('POST', '/events/' + id + '/view', { token: t, tags: E('/events/{id}/view', 'event'), expect: [204, 404] });
  req('POST', '/events/' + id + '/attend', { token: t, tags: E('/events/{id}/attend', 'engagement'), expect: [200, 400, 404, 409] });
  req('POST', '/events/' + id + '/comments', {
    token: t,
    body: { content: '[LOADTEST] comment ' + vu + '-' + iter, parentCommentId: null },
    tags: E('/events/{id}/comments', 'engagement', { bucket: 'comments.post' }),
    expect: [201, 404, 422, 429],
  });
  req('GET', '/events/' + id + '/stats', { token: t, tags: E('/events/{id}/stats', 'event'), expect: [200, 403, 404] });

  // test-to-test follow only (never a real user)
  const target = otherPoolUuid(data);
  if (target)
    req('POST', '/users/' + target + '/follow', { token: t, tags: E('/users/{id}/follow', 'user', { bucket: 'follows.follow' }), expect: [201, 409, 422, 429, 404] });

  if (Math.random() < 0.3) probeNotification(t);
}

function unread(t) {
  const r = req('GET', '/users/me/notifications', { token: t, tags: E('/users/me/notifications', 'notification'), expect: [200] });
  const h = r.headers['X-Unread-Count'] || r.headers['x-unread-count'];
  return h ? Number(h) : 0;
}

function probeNotification(t) {
  const before = unread(t);
  const t0 = Date.now();
  for (let i = 0; i < 3; i++) {
    sleep(2);
    if (unread(t) > before) { notificationLag.add(Date.now() - t0); return; }
  }
}

// ===================== teardown: best-effort cleanup of test data =====================
export function teardown(data) {
  for (let i = 0; i < data.tokens.length; i++) {
    const t = data.tokens[i];

    // delete [LOADTEST] events owned by this test user (cancel -> delete)
    const r = req('GET', '/users/me/events', { token: t, tags: E('/users/me/events', 'event'), expect: [200, 401, 404] });
    const evs = asArray(r);
    for (let k = 0; k < evs.length; k++) {
      const ev = evs[k];
      if (!ev || ev.id == null || !ev.title || ev.title.indexOf('[LOADTEST]') !== 0) continue;
      req('PATCH', '/events/' + ev.id + '/cancel', { token: t, tags: E('/events/{id}/cancel', 'event'), expect: [200, 403, 404, 409] });
      req('DELETE', '/events/' + ev.id, { token: t, tags: E('/events/{id}', 'event'), expect: [204, 401, 403, 404, 409] });
    }

    // undo test-to-test follows (targeted via my own following list)
    const myId = data.poolUuids[i];
    if (myId) {
      const fr = req('GET', '/users/' + myId + '/following?page=0&size=100', { token: t, tags: E('/users/{id}/following', 'user'), expect: [200, 401, 404] });
      const fl = asArray(fr);
      for (let f = 0; f < fl.length; f++) {
        const fid = fl[f].id || fl[f].followedId;
        if (fid) req('DELETE', '/users/' + fid + '/follow', { token: t, tags: E('/users/{id}/follow', 'user'), expect: [204, 401] });
      }
    }
  }
}
