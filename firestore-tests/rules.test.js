// Emulator tests for ../firestore.rules.
//
// `V12Client` replays the exact Firestore calls the shipped v1.2 Android
// client makes (FirebaseOnlineClient / FirebaseOnlineSession), so the
// "compatibility" suite proves the rules don't break existing installs. The
// "attacks" suite checks that everything else is refused.

import { readFileSync } from 'node:fs';
import { after, afterEach, before, beforeEach, describe, it } from 'mocha';
import assert from 'node:assert/strict';
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from '@firebase/rules-unit-testing';
import {
  Timestamp,
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  serverTimestamp,
  setDoc,
  setLogLevel,
  updateDoc,
  writeBatch,
} from 'firebase/firestore';

const CODE = 'ABC234';

/** FS.ttlFromNow() */
const ttl = (hours = 24) => Timestamp.fromMillis(Date.now() + hours * 3600 * 1000);
/** FS.moveDocId() */
const moveId = (round, seq) => `${String(round).padStart(3, '0')}-${String(seq).padStart(6, '0')}`;

/** The v1.2 client's Firestore traffic, call for call. */
class V12Client {
  constructor(env, uid) {
    this.uid = uid;
    this.db = env.authenticatedContext(uid).firestore();
  }

  room(code = CODE) { return doc(this.db, 'rooms', code); }
  moves(code = CODE) { return collection(this.db, 'rooms', code, 'moves'); }

  async createRoom(code = CODE, hostColor = 'BLACK') {
    // pickUnusedCode()
    if ((await getDoc(this.room(code))).exists()) throw new Error('code taken');
    await setDoc(this.room(code), {
      status: 'waiting',
      createdAt: serverTimestamp(),
      hostUid: this.uid,
      hostColor,
      hostName: null,
      guestUid: null,
      guestName: null,
      moveCount: 0,
      round: 0,
      rematchHost: false,
      rematchGuest: false,
      expiresAt: ttl(),
    });
  }

  async joinRoom(code = CODE) {
    const ref = this.room(code);
    await runTransaction(this.db, async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists()) throw new Error('RoomNotFound');
      const d = snap.data();
      if (d.hostUid === this.uid) return;
      if (d.guestUid != null && d.guestUid !== this.uid) throw new Error('RoomFull');
      if (d.status !== 'waiting' && d.status !== 'active') throw new Error(`Room is ${d.status}`);
      tx.update(ref, { guestUid: this.uid, guestName: null, status: 'active', expiresAt: ttl() });
    });
  }

  async sendMove(round, seq, by, x, y, isPass = false, code = CODE) {
    const t = ttl();
    const batch = writeBatch(this.db);
    batch.set(doc(this.moves(code), moveId(round, seq)), {
      round, seq, by, x, y, isPass, ts: serverTimestamp(), expiresAt: t,
    });
    batch.update(this.room(code), { moveCount: seq, expiresAt: t });
    await batch.commit();
  }

  async pass(round, seq, by) { return this.sendMove(round, seq, by, -1, -1, true); }

  async requestRematch(round, code = CODE) {
    const ref = this.room(code);
    const t = ttl();
    const started = await runTransaction(this.db, async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists()) throw new Error('RoomNotFound');
      const d = snap.data();
      const current = d.round ?? 0;
      if (current !== round) return null;
      const isHost = d.hostUid === this.uid;
      const otherField = isHost ? 'rematchGuest' : 'rematchHost';
      if (d[otherField] === true) {
        const hostColor = d.hostColor ?? 'BLACK';
        tx.update(ref, {
          round: current + 1,
          hostColor: hostColor === 'BLACK' ? 'WHITE' : 'BLACK',
          rematchHost: false,
          rematchGuest: false,
          moveCount: 0,
          expiresAt: t,
        });
        return current + 1;
      }
      tx.update(ref, { [isHost ? 'rematchHost' : 'rematchGuest']: true, expiresAt: t });
      return null;
    });
    // v1.2 swallows failures here; the tests want to see them.
    if (started != null) {
      const stale = (await getDocs(this.moves(code))).docs
        .filter((d) => (d.get('round') ?? 0) < started);
      if (stale.length) {
        const batch = writeBatch(this.db);
        stale.forEach((d) => batch.delete(d.ref));
        await batch.commit();
      }
    }
    return started;
  }

  /** finish() / deleteRoomAndMoves(), without v1.2's error swallowing. */
  async finish(code = CODE) {
    const moves = (await getDocs(this.moves(code))).docs;
    if (moves.length) {
      const batch = writeBatch(this.db);
      moves.forEach((d) => batch.delete(d.ref));
      await batch.commit();
    }
    await deleteDoc(this.room(code));
  }

  /** The session's moves listener query, as a one-shot read. */
  async readMoves(code = CODE) {
    return (await getDocs(query(this.moves(code), orderBy('seq')))).docs.map((d) => d.data());
  }
}

/** v1.6 adds resigning on top of the v1.2 traffic. */
class V16Client extends V12Client {
  async resign(round, code = CODE) {
    const ref = this.room(code);
    await runTransaction(this.db, async (tx) => {
      const snap = await tx.get(ref);
      if (!snap.exists()) throw new Error('RoomNotFound');
      const d = snap.data();
      const current = d.round ?? 0;
      // Already moved on, or someone resigned this round first.
      if (current !== round || d.resignedRound === current) return;
      const hostColor = d.hostColor ?? 'BLACK';
      const mine = d.hostUid === this.uid ? hostColor : (hostColor === 'BLACK' ? 'WHITE' : 'BLACK');
      tx.update(ref, { resignedBy: mine, resignedRound: current, expiresAt: ttl() });
    });
  }
}

let env;

before(async () => {
  setLogLevel('error');
  env = await initializeTestEnvironment({
    projectId: 'demo-reversi',
    firestore: { rules: readFileSync(process.env.RULES_FILE ?? new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => { await env?.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

/** Host (BLACK) + guest seated in an active room. */
async function activeRoom() {
  const host = new V12Client(env, 'host');
  const guest = new V12Client(env, 'guest');
  await host.createRoom();
  await guest.joinRoom();
  return { host, guest };
}

/** activeRoom(), with v1.6 clients. */
async function activeRoom16() {
  const host = new V16Client(env, 'host');
  const guest = new V16Client(env, 'guest');
  await host.createRoom();
  await guest.joinRoom();
  return { host, guest };
}

async function roomData() {
  let data;
  await env.withSecurityRulesDisabled(async (ctx) => {
    data = (await getDoc(doc(ctx.firestore(), 'rooms', CODE))).data();
  });
  return data;
}

async function seed(path, data) {
  await env.withSecurityRulesDisabled(async (ctx) => { await setDoc(doc(ctx.firestore(), path), data); });
}

const validMove = (over = {}) => ({
  round: 0, seq: 1, by: 'BLACK', x: 2, y: 3, isPass: false,
  ts: serverTimestamp(), expiresAt: ttl(), ...over,
});

describe('v1.2 client compatibility', () => {
  it('plays a full game: create, join, moves + passes, rematch with colour swap, finish', async () => {
    const { host, guest } = await activeRoom();

    // Alternate moves, host BLACK first, with a pass from each side mixed in.
    let seq = 0;
    for (let i = 0; i < 30; i++) {
      await host.sendMove(0, ++seq, 'BLACK', i % 8, (i * 3) % 8);
      if (i === 10) await guest.pass(0, ++seq, 'WHITE');
      else await guest.sendMove(0, ++seq, 'WHITE', (i + 1) % 8, (i * 5) % 8);
    }
    await host.pass(0, ++seq, 'BLACK');
    const seen = await guest.readMoves();
    assert.equal(seen.length, seq);
    assert.deepEqual(seen.map((m) => m.seq), [...Array(seq).keys()].map((i) => i + 1));
    assert.equal((await host.readMoves()).length, seq);

    // Rematch: guest asks first, host's request starts round 1.
    assert.equal(await guest.requestRematch(0), null);
    assert.equal((await roomData()).rematchGuest, true);
    assert.equal(await host.requestRematch(0), 1);
    const r = await roomData();
    assert.equal(r.round, 1);
    assert.equal(r.hostColor, 'WHITE');
    assert.equal(r.moveCount, 0);
    assert.equal((await host.readMoves()).length, 0, 'stale round-0 moves deleted');
    // A late duplicate request for round 0 is a no-op.
    assert.equal(await guest.requestRematch(0), null);

    // Round 1: guest is BLACK now and moves first.
    await guest.sendMove(1, 1, 'BLACK', 2, 3);
    await host.sendMove(1, 2, 'WHITE', 2, 2);
    assert.equal((await host.readMoves()).length, 2);

    // Host requests first this time; guest starts round 2.
    assert.equal(await host.requestRematch(1), null);
    assert.equal(await guest.requestRematch(1), 2);
    assert.equal((await roomData()).hostColor, 'BLACK');

    await guest.finish();
    assert.equal(await roomData(), undefined);
  });

  it('lets either player leave and delete the room mid-game', async () => {
    const { host, guest } = await activeRoom();
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    await host.finish();
    // The other player's own finish() then finds nothing to delete.
    await assert.rejects(guest.readMoves());
  });

  it('allows the guest and the host to re-enter the code', async () => {
    const { host, guest } = await activeRoom();
    await guest.joinRoom();
    await host.joinRoom();
    assert.equal((await roomData()).guestUid, 'guest');
  });

  it('lets the host sweep a room nobody joined', async () => {
    const host = new V12Client(env, 'host');
    await host.createRoom();
    await host.finish();
    assert.equal(await roomData(), undefined);
  });

  it('serves live move snapshots to both players', async () => {
    const { host, guest } = await activeRoom();
    const got = await new Promise((resolve, reject) => {
      const unsub = onSnapshot(query(guest.moves(), orderBy('seq')), (snap) => {
        if (snap.size === 1) { unsub(); resolve(snap.docs[0].data()); }
      }, reject);
      host.sendMove(0, 1, 'BLACK', 4, 5).catch(reject);
    });
    assert.equal(got.x, 4);
  });

  it('surfaces a code collision as a failed create (v1.2 picks unused codes first)', async () => {
    await new V12Client(env, 'host').createRoom();
    const other = new V12Client(env, 'other');
    await assertFails(setDoc(other.room(), {
      status: 'waiting', createdAt: serverTimestamp(), hostUid: 'other', hostColor: 'BLACK',
      hostName: null, guestUid: null, guestName: null, moveCount: 0, round: 0,
      rematchHost: false, rematchGuest: false, expiresAt: ttl(),
    }));
  });
});

describe('resigning (v1.6)', () => {
  it('records the resigning colour for the round and stops further moves', async () => {
    const { host, guest } = await activeRoom16();
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    await guest.resign(0);
    const r = await roomData();
    assert.equal(r.resignedBy, 'WHITE');
    assert.equal(r.resignedRound, 0);
    await assertFails(guest.sendMove(0, 2, 'WHITE', 2, 2));
    await assertFails(host.pass(0, 2, 'BLACK'));
    // A second resignation of the same round is a no-op, not an overwrite.
    await host.resign(0);
    assert.equal((await roomData()).resignedBy, 'WHITE');
  });

  it('lets the next round play on after a rematch, with either client starting it', async () => {
    const { host, guest } = await activeRoom16();
    await host.resign(0);
    assert.equal((await roomData()).resignedBy, 'BLACK');
    // The v1.2 rematch start doesn't know about resigning, and needn't.
    const oldGuest = new V12Client(env, 'guest');
    assert.equal(await host.requestRematch(0), null);
    assert.equal(await oldGuest.requestRematch(0), 1);
    // Round 1: guest is BLACK now. The round-0 resignation no longer applies.
    await guest.sendMove(1, 1, 'BLACK', 2, 3);
    await host.sendMove(1, 2, 'WHITE', 2, 2);
    await guest.resign(1);
    const r = await roomData();
    assert.equal(r.resignedBy, 'BLACK');
    assert.equal(r.resignedRound, 1);
  });

  it('works against a v1.2 opponent, who simply sees moves refused', async () => {
    const host = new V16Client(env, 'host');
    const guest = new V12Client(env, 'guest');
    await host.createRoom();
    await guest.joinRoom();
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    await host.resign(0);
    await assertFails(guest.sendMove(0, 2, 'WHITE', 2, 2));
    await guest.finish();
    assert.equal(await roomData(), undefined);
  });
});

describe('attacks: strangers', () => {
  it('cannot do anything without signing in', async () => {
    await new V12Client(env, 'host').createRoom();
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(doc(anon, 'rooms', CODE)));
    await assertFails(updateDoc(doc(anon, 'rooms', CODE), { guestUid: 'x' }));
  });

  it('cannot list rooms to harvest codes', async () => {
    await new V12Client(env, 'host').createRoom();
    const eve = new V12Client(env, 'eve');
    await assertFails(getDocs(collection(eve.db, 'rooms')));
    await assertSucceeds(getDoc(eve.room())); // fetching a known code is still how joining works
  });

  it('cannot hijack a waiting room while joining', async () => {
    await new V12Client(env, 'host').createRoom();
    const eve = new V12Client(env, 'eve');
    const ref = eve.room();
    const join = { guestUid: 'eve', guestName: null, status: 'active', expiresAt: ttl() };
    await assertFails(updateDoc(ref, { ...join, hostUid: 'eve' }));
    await assertFails(updateDoc(ref, { ...join, hostColor: 'WHITE' }));
    await assertFails(updateDoc(ref, { ...join, round: 7 }));
    await assertFails(updateDoc(ref, { ...join, guestUid: 'someone-else' }));
    await assertFails(updateDoc(ref, { ...join, status: 'waiting' }));
    await assertFails(updateDoc(ref, { ...join, guestName: 'x'.repeat(5000) }));
    await assertFails(updateDoc(ref, { ...join, expiresAt: Timestamp.fromDate(new Date('9999-01-01')) }));
    await assertFails(updateDoc(ref, { hostUid: 'eve' }));
    await assertFails(deleteDoc(ref));
  });

  it('cannot take or clear a seat in a full room', async () => {
    await activeRoom();
    const eve = new V12Client(env, 'eve');
    await assert.rejects(eve.joinRoom(), /RoomFull/);
    await assertFails(updateDoc(eve.room(), { guestUid: 'eve' }));
    await assertFails(updateDoc(eve.room(), { guestUid: null, status: 'waiting' }));
  });

  it('cannot read, write or delete moves', async () => {
    const { host } = await activeRoom();
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    const eve = new V12Client(env, 'eve');
    await assertFails(getDocs(eve.moves()));
    await assertFails(getDoc(doc(eve.moves(), moveId(0, 1))));
    await assertFails(eve.sendMove(0, 2, 'WHITE', 2, 2));
    await assertFails(deleteDoc(doc(eve.moves(), moveId(0, 1))));
  });

  it('cannot host as someone else or create malformed rooms', async () => {
    const eve = new V12Client(env, 'eve');
    const good = {
      status: 'waiting', createdAt: serverTimestamp(), hostUid: 'eve', hostColor: 'BLACK',
      hostName: null, guestUid: null, guestName: null, moveCount: 0, round: 0,
      rematchHost: false, rematchGuest: false, expiresAt: ttl(),
    };
    const ref = eve.room();
    await assertFails(setDoc(ref, { ...good, hostUid: 'victim' }));
    await assertFails(setDoc(ref, { ...good, guestUid: 'victim' }));
    await assertFails(setDoc(ref, { ...good, status: 'active' }));
    await assertFails(setDoc(ref, { ...good, round: 5 }));
    await assertFails(setDoc(ref, { ...good, moveCount: 3 }));
    await assertFails(setDoc(ref, { ...good, hostColor: 'RED' }));
    await assertFails(setDoc(ref, { ...good, hostName: 'x'.repeat(100_000) }));
    await assertFails(setDoc(ref, { ...good, junk: 'x'.repeat(100_000) }));
    await assertFails(setDoc(ref, { ...good, expiresAt: Timestamp.fromDate(new Date('9999-01-01')) }));
    await assertFails(setDoc(ref, { ...good, createdAt: Timestamp.fromDate(new Date('2000-01-01')) }));
    const { status, ...missing } = good;
    await assertFails(setDoc(ref, missing));
    await assertFails(setDoc(doc(eve.db, 'rooms', 'abc234'), good));
    await assertFails(setDoc(doc(eve.db, 'rooms', 'ABCDEFG'), good));
    await assertFails(setDoc(doc(eve.db, 'rooms', 'ABC10O'), good));
    await assertSucceeds(setDoc(ref, good));
  });

  it('cannot write to other collections', async () => {
    const eve = new V12Client(env, 'eve');
    await assertFails(setDoc(doc(eve.db, 'junk', 'x'), { a: 1 }));
    await assertFails(getDoc(doc(eve.db, 'junk', 'x')));
  });
});

describe('attacks: a malicious opponent', () => {
  it('cannot move as the other colour', async () => {
    const { host, guest } = await activeRoom();
    // Guest (WHITE) tries to play BLACK's first move.
    await assertFails(guest.sendMove(0, 1, 'BLACK', 2, 3));
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    // Host (BLACK) tries to play WHITE's reply.
    await assertFails(host.sendMove(0, 2, 'WHITE', 2, 2));
    await assertFails(host.pass(0, 2, 'WHITE'));
  });

  it('cannot skip, repeat or overwrite move numbers', async () => {
    const { host, guest } = await activeRoom();
    await host.sendMove(0, 1, 'BLACK', 2, 3);
    await assertFails(guest.sendMove(0, 3, 'WHITE', 2, 2)); // skips seq 2
    await assertFails(guest.sendMove(0, 1, 'WHITE', 2, 2)); // seq already used
    await assertFails(host.sendMove(0, 1, 'BLACK', 5, 5));  // rewrite own move
    await assertFails(updateDoc(doc(host.moves(), moveId(0, 1)), { x: 7 }));
    await assertFails(guest.sendMove(1, 2, 'WHITE', 2, 2)); // future round
  });

  it('cannot write a move without advancing the room, or advance it without a move', async () => {
    const { host } = await activeRoom();
    await assertFails(setDoc(doc(host.moves(), moveId(0, 1)), validMove()));
    await assertFails(updateDoc(host.room(), { moveCount: 1, expiresAt: ttl() }));
    await assertFails(updateDoc(host.room(), { moveCount: 5, expiresAt: ttl() }));
  });

  it('cannot send malformed or oversized moves', async () => {
    const { host } = await activeRoom();
    const send = (id, data) => {
      const b = writeBatch(host.db);
      b.set(doc(host.moves(), id), data);
      b.update(host.room(), { moveCount: 1, expiresAt: ttl() });
      return b.commit();
    };
    const id = moveId(0, 1);
    await assertFails(send(id, validMove({ x: 8 })));
    await assertFails(send(id, validMove({ y: -1 })));
    await assertFails(send(id, validMove({ x: 2.5 })));
    await assertFails(send(id, validMove({ x: '2' })));
    await assertFails(send(id, validMove({ isPass: true })));       // a pass with coordinates
    await assertFails(send(id, validMove({ isPass: 'yes' })));
    await assertFails(send(id, validMove({ by: 'RED' })));
    await assertFails(send(id, validMove({ junk: 'x'.repeat(100_000) })));
    await assertFails(send(id, validMove({ ts: Timestamp.fromDate(new Date('2000-01-01')) })));
    await assertFails(send(id, validMove({ expiresAt: Timestamp.fromDate(new Date('9999-01-01')) })));
    await assertFails(send('0-1', validMove()));                    // wrong doc id
    await assertFails(send(moveId(0, 2), validMove()));             // id doesn't match seq
    await assertSucceeds(send(id, validMove()));
  });

  it('cannot flood a room past the move cap', async () => {
    const { host } = await activeRoom();
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), 'rooms', CODE), { moveCount: 200 });
    });
    await assertFails(host.sendMove(0, 201, 'BLACK', 2, 3));
  });

  it('cannot tamper with seats, colours or game state', async () => {
    const { host, guest } = await activeRoom();
    const t = ttl();
    await assertFails(updateDoc(guest.room(), { hostColor: 'WHITE', expiresAt: t }));
    await assertFails(updateDoc(host.room(), { guestUid: null, expiresAt: t }));
    await assertFails(updateDoc(host.room(), { guestUid: 'friend', expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { hostUid: 'guest', expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { status: 'waiting', expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { round: 5, expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { hostName: 'x'.repeat(50), expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { expiresAt: Timestamp.fromDate(new Date('9999-01-01')) }));
    await assertFails(updateDoc(guest.room(), { junk: true }));
  });

  it("cannot raise the other player's rematch flag or force a rematch alone", async () => {
    const { host, guest } = await activeRoom();
    await assertFails(updateDoc(guest.room(), { rematchHost: true, expiresAt: ttl() }));
    const forced = {
      round: 1, hostColor: 'WHITE', rematchHost: false, rematchGuest: false, moveCount: 0, expiresAt: ttl(),
    };
    await assertFails(updateDoc(guest.room(), forced));
    await guest.requestRematch(0);
    // Even with a request in, the requester can't start the round themselves.
    await assertFails(updateDoc(guest.room(), forced));
    // Starting it must swap colours and advance by exactly one round.
    await assertFails(updateDoc(host.room(), { ...forced, hostColor: 'BLACK' }));
    await assertFails(updateDoc(host.room(), { ...forced, round: 2 }));
    await assertSucceeds(updateDoc(host.room(), forced));
  });

  it('cannot resign for the other player, for another round, or twice', async () => {
    const { host, guest } = await activeRoom();
    const t = ttl();
    await assertFails(updateDoc(guest.room(), { resignedBy: 'BLACK', resignedRound: 0, expiresAt: t }));
    await assertFails(updateDoc(host.room(), { resignedBy: 'WHITE', resignedRound: 0, expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { resignedBy: 'WHITE', resignedRound: 1, expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { resignedBy: 'WHITE', expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { resignedBy: 'WHITE', resignedRound: 0, moveCount: 3, expiresAt: t }));
    await assertFails(updateDoc(guest.room(), { resignedBy: 'WHITE', resignedRound: 0, round: 1, expiresAt: t }));
    await assertSucceeds(updateDoc(guest.room(), { resignedBy: 'WHITE', resignedRound: 0, expiresAt: t }));
    // Nor take it back, or pin it on the winner afterwards.
    await assertFails(updateDoc(guest.room(), { resignedRound: -1, expiresAt: t }));
    await assertFails(updateDoc(host.room(), { resignedBy: 'BLACK', resignedRound: 0, expiresAt: t }));
  });

  it("cannot resign in a room that's still waiting for a guest", async () => {
    const host = new V16Client(env, 'host');
    await host.createRoom();
    await assertFails(host.resign(0));
  });

  it("cannot play in a room that's still waiting for a guest", async () => {
    const host = new V12Client(env, 'host');
    await host.createRoom();
    await assertFails(host.sendMove(0, 1, 'BLACK', 2, 3));
    await assertFails(host.requestRematch(0));
  });

  it('cannot use the room as free storage via a seeded finished room', async () => {
    await seed(`rooms/${CODE}`, {
      status: 'finished', hostUid: 'host', guestUid: 'guest', hostColor: 'BLACK',
      moveCount: 0, round: 0, rematchHost: false, rematchGuest: false, expiresAt: ttl(),
    });
    const host = new V12Client(env, 'host');
    await assertFails(host.sendMove(0, 1, 'BLACK', 2, 3));
    await assertSucceeds(deleteDoc(host.room()));
  });
});
