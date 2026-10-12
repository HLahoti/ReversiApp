#!/usr/bin/env node
// Deletes online-play rooms, and their moves, whose `expiresAt` has passed.
//
// The app pushes a room's expiresAt to now + 24 h on every write, and the
// players' devices delete the room when they leave. Rooms whose players never
// come back would otherwise stay forever: the project is on the Spark plan,
// which has no TTL policies. Run this every so often instead.
//
//   node cleanup.js --key ~/secrets/reversi-service-account.json            (dry run)
//   node cleanup.js --key ~/secrets/reversi-service-account.json --delete
//
// Uses the Admin SDK, which bypasses firestore.rules. Needs the collection-group
// index on moves.expiresAt from firestore.indexes.json to be deployed.

import { readFileSync } from 'node:fs';
import { parseArgs } from 'node:util';
import { pathToFileURL } from 'node:url';
import { cert, initializeApp } from 'firebase-admin/app';
import { Timestamp, getFirestore } from 'firebase-admin/firestore';

const USAGE = `Usage: node cleanup.js [--key <service-account.json>] [--delete] [options]

Without --delete this is a dry run: it lists what would go and deletes nothing.

Options:
  --key <file>          Service-account key (or set GOOGLE_APPLICATION_CREDENTIALS).
  --delete              Actually delete.
  --grace-hours <n>     Only touch things that expired at least this long ago,
                        allowing for players' clocks being wrong (default 24).
  --max-deletes <n>     Stop after this many document deletes, to stay inside
                        the Spark plan's 20k deletes/day (default 10000).
  --project <id>        Override the project id taken from the key file.
  -h, --help            Show this help.`;

/**
 * Finds, and unless [dryRun] deletes, everything that expired before
 * now − [graceHours]:
 *   1. rooms, with every move under them;
 *   2. moves whose room is already gone (a device deleted the room but
 *      not all of its moves).
 * A move is never deleted while its room still exists, however old the move
 * is: a slow game keeps its room alive with early moves past their expiresAt.
 * Deletes at most [maxDeletes] documents; whole rooms only.
 */
export async function cleanup(db, {
  now = Date.now(),
  graceHours = 24,
  maxDeletes = 10_000,
  dryRun = true,
  log = console.log,
} = {}) {
  const cutoff = Timestamp.fromMillis(now - graceHours * 3600 * 1000);
  const verb = dryRun ? 'would delete' : 'deleted';
  const stats = { rooms: 0, roomMoves: 0, orphanMoves: 0, keptLiveMoves: 0, budgetHit: false };
  let budget = maxDeletes;
  const writer = dryRun ? null : db.bulkWriter();

  // 1. Expired rooms, oldest first, each with all of its moves.
  const rooms = await db.collection('rooms')
    .where('expiresAt', '<', cutoff)
    .orderBy('expiresAt')
    .limit(maxDeletes)
    .get();
  const handled = new Set();
  for (const room of rooms.docs) {
    const moveCount = (await room.ref.collection('moves').count().get()).data().count;
    if (moveCount + 1 > budget) {
      stats.budgetHit = true;
      break;
    }
    if (writer) await db.recursiveDelete(room.ref, writer);
    log(`${verb} room ${room.id} (expired ${room.get('expiresAt').toDate().toISOString()}, ${moveCount} moves)`);
    handled.add(room.ref.path);
    budget -= moveCount + 1;
    stats.rooms++;
    stats.roomMoves += moveCount;
  }
  if (writer) await writer.flush();

  // 2. Expired moves left behind without a room.
  if (!stats.budgetHit && budget > 0) {
    const moves = (await db.collectionGroup('moves')
      .where('expiresAt', '<', cutoff)
      .limit(maxDeletes)
      .get()).docs.filter((m) => !handled.has(m.ref.parent.parent.path));

    const parents = [...new Map(moves.map((m) => [m.ref.parent.parent.path, m.ref.parent.parent])).values()];
    const existing = new Set();
    for (let i = 0; i < parents.length; i += 100) {
      for (const snap of await db.getAll(...parents.slice(i, i + 100))) {
        if (snap.exists) existing.add(snap.ref.path);
      }
    }

    for (const move of moves) {
      const room = move.ref.parent.parent;
      if (existing.has(room.path)) {
        stats.keptLiveMoves++;
        continue;
      }
      if (budget === 0) {
        stats.budgetHit = true;
        break;
      }
      if (writer) writer.delete(move.ref);
      log(`${verb} orphaned move ${room.id}/${move.id}`);
      budget--;
      stats.orphanMoves++;
    }
  }

  if (writer) await writer.close();
  return stats;
}

async function main() {
  const { values } = parseArgs({
    options: {
      key: { type: 'string' },
      delete: { type: 'boolean', default: false },
      'grace-hours': { type: 'string', default: '24' },
      'max-deletes': { type: 'string', default: '10000' },
      project: { type: 'string' },
      help: { type: 'boolean', short: 'h', default: false },
    },
  });
  if (values.help) {
    console.log(USAGE);
    return;
  }

  const graceHours = Number(values['grace-hours']);
  const maxDeletes = Number(values['max-deletes']);
  if (!(graceHours >= 0) || !Number.isInteger(maxDeletes) || maxDeletes < 1) {
    throw new Error(`Bad --grace-hours or --max-deletes.\n\n${USAGE}`);
  }

  const keyPath = values.key ?? process.env.GOOGLE_APPLICATION_CREDENTIALS;
  const emulator = process.env.FIRESTORE_EMULATOR_HOST;
  if (!keyPath && !emulator) {
    throw new Error(
      'No credentials. Download a service-account key from the Firebase console ' +
      '(Project settings → Service accounts → Generate new private key), keep it ' +
      `outside the repo, and pass it with --key.\n\n${USAGE}`,
    );
  }
  const key = keyPath ? JSON.parse(readFileSync(keyPath, 'utf8')) : null;
  const projectId = values.project ?? key?.project_id ?? process.env.GCLOUD_PROJECT;
  initializeApp(key ? { credential: cert(key), projectId } : { projectId });

  const dryRun = !values.delete;
  console.log(`Project ${projectId}${emulator ? ` (emulator at ${emulator})` : ''}: ` +
    `${dryRun ? 'DRY RUN, nothing will be deleted' : 'DELETING'}; ` +
    `grace ${graceHours} h, at most ${maxDeletes} deletes.`);

  const stats = await cleanup(getFirestore(), { graceHours, maxDeletes, dryRun });

  const verb = dryRun ? 'Would delete' : 'Deleted';
  console.log(`\n${verb} ${stats.rooms} expired rooms (${stats.roomMoves} moves) ` +
    `and ${stats.orphanMoves} orphaned moves.`);
  if (stats.keptLiveMoves) console.log(`Kept ${stats.keptLiveMoves} old moves in rooms that are still active.`);
  if (stats.budgetHit) console.log('Stopped at --max-deletes; run again (tomorrow, on the free plan) to continue.');
  if (dryRun && stats.rooms + stats.orphanMoves > 0) console.log('Re-run with --delete to delete them.');
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((err) => {
    if (err.code === 9 /* FAILED_PRECONDITION */) {
      console.error('Missing index: deploy it first with `firebase deploy --only firestore:indexes`.');
    }
    console.error(err.message ?? err);
    process.exit(1);
  });
}
