// Emulator tests for cleanup.js. Run with `npm test`.

import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { before, beforeEach, describe, it } from 'mocha';
import { initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';
import { cleanup } from './cleanup.js';

const PROJECT = 'demo-reversi';
const HOUR = 3600 * 1000;
const NOW = Date.now();
const at = (hoursFromNow) => Timestamp.fromMillis(NOW + hoursFromNow * HOUR);
const moveId = (round, seq) => `${String(round).padStart(3, '0')}-${String(seq).padStart(6, '0')}`;

let db;

before(() => {
  initializeApp({ projectId: PROJECT });
  db = getFirestore();
});

beforeEach(async () => {
  const res = await fetch(
    `http://${process.env.FIRESTORE_EMULATOR_HOST}/emulator/v1/projects/${PROJECT}/databases/(default)/documents`,
    { method: 'DELETE' },
  );
  assert.ok(res.ok);
});

/** A room expiring at [roomHours] from now, with moves expiring at [moveHours]. */
async function room(code, roomHours, moveCount = 0, moveHours = roomHours) {
  const ref = db.doc(`rooms/${code}`);
  await ref.set({ status: 'active', hostUid: 'h', guestUid: 'g', round: 0, moveCount, expiresAt: at(roomHours) });
  for (let seq = 1; seq <= moveCount; seq++) {
    await ref.collection('moves').doc(moveId(0, seq)).set({ round: 0, seq, expiresAt: at(moveHours) });
  }
}

/** Moves under a room code with no room document. */
async function orphanMoves(code, count, hours) {
  for (let seq = 1; seq <= count; seq++) {
    await db.doc(`rooms/${code}/moves/${moveId(0, seq)}`).set({ round: 0, seq, expiresAt: at(hours) });
  }
}

const exists = async (path) => (await db.doc(path).get()).exists;
const moveTotal = async (code) => (await db.collection(`rooms/${code}/moves`).count().get()).data().count;
const quiet = { now: NOW, log: () => {} };

/** One of every case. Default grace is 24 h, so "expired" means > 24 h ago. */
async function seedMix() {
  await room('OLD234', -48, 3);           // expired: goes, with its moves
  await room('EMPTY2', -30);              // expired, never played: goes
  await room('FRESH2', 20, 2);            // active: stays
  await room('GRACE2', -2, 1);            // expired 2 h ago, inside the grace period: stays
  await room('SLOW23', 10, 3, -72);       // slow game, room still active, old moves: all stay
  await orphanMoves('GONE23', 2, -40);    // room deleted, moves left: go
  await orphanMoves('GONE34', 1, 5);      // room deleted, moves not expired yet: stay
}

describe('cleanup', () => {
  it('only reports on a dry run', async () => {
    await seedMix();
    const stats = await cleanup(db, quiet);
    assert.deepEqual(stats, { rooms: 2, roomMoves: 3, orphanMoves: 2, keptLiveMoves: 3, budgetHit: false });
    assert.ok(await exists('rooms/OLD234'));
    assert.equal(await moveTotal('OLD234'), 3);
    assert.equal(await moveTotal('GONE23'), 2);
  });

  it('deletes expired rooms with their moves, and orphaned expired moves, and nothing else', async () => {
    await seedMix();
    const stats = await cleanup(db, { ...quiet, dryRun: false });
    assert.deepEqual(stats, { rooms: 2, roomMoves: 3, orphanMoves: 2, keptLiveMoves: 3, budgetHit: false });

    assert.equal(await exists('rooms/OLD234'), false);
    assert.equal(await moveTotal('OLD234'), 0);
    assert.equal(await exists('rooms/EMPTY2'), false);
    assert.equal(await moveTotal('GONE23'), 0);

    assert.ok(await exists('rooms/FRESH2'));
    assert.equal(await moveTotal('FRESH2'), 2);
    assert.ok(await exists('rooms/GRACE2'));
    assert.equal(await moveTotal('GRACE2'), 1);
    assert.ok(await exists('rooms/SLOW23'));
    assert.equal(await moveTotal('SLOW23'), 3);
    assert.equal(await moveTotal('GONE34'), 1);

    // Nothing left to do on the next run.
    const again = await cleanup(db, { ...quiet, dryRun: false });
    assert.equal(again.rooms + again.orphanMoves, 0);
  });

  it('honours a custom grace period', async () => {
    await seedMix();
    const stats = await cleanup(db, { ...quiet, dryRun: false, graceHours: 1 });
    assert.equal(stats.rooms, 3);
    assert.equal(await exists('rooms/GRACE2'), false);
  });

  it('stops at the delete budget without splitting a room', async () => {
    for (const code of ['AAA222', 'BBB222', 'CCC222']) await room(code, -48, 4); // 5 docs each
    await orphanMoves('GONE23', 2, -40);
    const stats = await cleanup(db, { ...quiet, dryRun: false, maxDeletes: 12 });
    assert.equal(stats.rooms, 2);
    assert.equal(stats.orphanMoves, 0);
    assert.equal(stats.budgetHit, true);
    assert.ok(await exists('rooms/CCC222'), 'oldest-first; the third room waits for the next run');
    assert.equal(await moveTotal('CCC222'), 4);

    const rest = await cleanup(db, { ...quiet, dryRun: false, maxDeletes: 12 });
    assert.equal(rest.rooms, 1);
    assert.equal(rest.orphanMoves, 2);
    assert.equal(rest.budgetHit, false);
  });

  it('runs end to end from the command line', async () => {
    await seedMix();
    const run = (...args) => promisify(execFile)('node', ['cleanup.js', '--project', PROJECT, ...args]);
    const dry = await run();
    assert.match(dry.stdout, /DRY RUN/);
    assert.match(dry.stdout, /Would delete 2 expired rooms \(3 moves\) and 2 orphaned moves/);
    assert.ok(await exists('rooms/OLD234'));

    const real = await run('--delete');
    assert.match(real.stdout, /Deleted 2 expired rooms \(3 moves\) and 2 orphaned moves/);
    assert.match(real.stdout, /Kept 3 old moves/);
    assert.equal(await exists('rooms/OLD234'), false);

    await assert.rejects(run('--max-deletes', '0'), /Bad --grace-hours or --max-deletes/);
  });
});
