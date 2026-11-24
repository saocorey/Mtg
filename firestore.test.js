const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_commander_123";
const BOB_UID = "bob_commander_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validDeckPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Atraxa Superfriends",
    commander: "Atraxa, Praetors' Voice",
    colorIdentity: ["W", "U", "B", "G"],
    bracket: 4,
    archived: false,
    elo: 1200,
    peakElo: 1200,
    gamesPlayed: 0,
    wins: 0,
    losses: 0,
    draws: 0,
    lastEloDelta: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validOpponentPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    name: "Marcus",
    avatarColorHex: "#5EA8E6",
    elo: 1200,
    peakElo: 1200,
    gamesPlayed: 0,
    wins: 0,
    winsAgainstUser: 0,
    lossesToUser: 0,
    draws: 0,
    lastEloDelta: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMatchPayload(uid, deckId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    deckId,
    deckName: "Atraxa Superfriends",
    deckCommander: "Atraxa, Praetors' Voice",
    deckColorIdentity: ["W", "U", "B", "G"],
    opponentIds: ["opp_1", "opp_2", "opp_3"],
    opponentNames: ["Marcus", "Elena", "Devon"],
    opponentCommanders: ["Krenko, Mob Boss", "Yuriko", "Muldrotha"],
    outcome: "WIN",
    winnerType: "USER",
    podSize: 4,
    deckEloBefore: 1200,
    deckEloAfter: 1220,
    deckEloDelta: 20,
    opponentEloDeltas: { opp_1: -7, opp_2: -7, opp_3: -7 },
    playedAtMillis: Date.now() - 5000,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied read and write on all user collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/decks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/opponents`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/matches`).get());
  await assertFails(
    unauthDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_1")
      .set(validDeckPayload(ALICE_UID))
  );
});

test("Owner: empty subcollection list queries succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/decks`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/opponents`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/matches`).get());
});

test("Owner: valid Kotlin create, partial update, and delete succeed for Deck, Opponent, and Match", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_1")
      .set(validDeckPayload(ALICE_UID, { partnerCommander: "Ikra Shidiqi" }))
  );

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/opponents`)
      .doc("opp_1")
      .set(validOpponentPayload(ALICE_UID, { favoriteCommander: "Krenko, Mob Boss" }))
  );

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("match_1")
      .set(validMatchPayload(ALICE_UID, "deck_1", { turnCount: 9, notes: "Close pod!" }))
  );

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_1")
      .update({
        elo: 1220,
        peakElo: 1220,
        gamesPlayed: 1,
        wins: 1,
        lastEloDelta: 20,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/opponents`)
      .doc("opp_1")
      .update({
        elo: 1193,
        gamesPlayed: 1,
        lossesToUser: 1,
        lastEloDelta: -7,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/matches`).doc("match_1").delete()
  );
});

test("Cross-user isolation: Alice cannot read, list, update, or delete Bob's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection(`users/${BOB_UID}/decks`)
      .doc("bob_deck")
      .set(validDeckPayload(BOB_UID));
    await adminDb
      .collection(`users/${BOB_UID}/opponents`)
      .doc("bob_opp")
      .set(validOpponentPayload(BOB_UID));
    await adminDb
      .collection(`users/${BOB_UID}/matches`)
      .doc("bob_match")
      .set(validMatchPayload(BOB_UID, "bob_deck"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/decks`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/decks`).doc("bob_deck").get());
  await assertFails(
    aliceDb
      .collection(`users/${BOB_UID}/decks`)
      .doc("bob_deck")
      .update({ name: "Hijacked", updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/opponents`).doc("bob_opp").delete()
  );
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/matches`).doc("bob_match").get()
  );
});

test("Hardened validation: rejects ghost fields, id field, immutable mutations, future timestamps, and orphan matches", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Reject orphan match (deck does not exist)
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("orphan_match")
      .set(validMatchPayload(ALICE_UID, "missing_deck"))
  );

  // Create valid deck first
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_1")
      .set(validDeckPayload(ALICE_UID))
  );

  // Reject ghost field / shadow write
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_ghost")
      .set(validDeckPayload(ALICE_UID, { isVerified: true }))
  );

  // Reject `@DocumentId` 'id' field in payload
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_with_id")
      .set(validDeckPayload(ALICE_UID, { id: "deck_with_id" }))
  );

  // Reject future timestamp
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/opponents`)
      .doc("opp_future")
      .set(validOpponentPayload(ALICE_UID, { createdAt: new Date(Date.now() + 60000) }))
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/decks`)
      .doc("deck_1")
      .update({
        createdAt: new Date(Date.now() - 500),
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Reject OPPONENT winner without winningOpponentId
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("bad_winner")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "LOSS",
          winnerType: "OPPONENT",
        })
      )
  );

  // Valid OPPONENT winner succeeds
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("opp_win_match")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "LOSS",
          winnerType: "OPPONENT",
          winningOpponentId: "opp_1",
          winningOpponentName: "Marcus",
        })
      )
  );

  // Valid TEAM WIN (user + teammate) succeeds
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("team_win_match")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "WIN",
          winnerType: "TEAM",
          winningOpponentIds: ["opp_1"],
          winningOpponentNames: ["Marcus"],
          deckEloAfter: 1213,
          deckEloDelta: 13,
          opponentEloDeltas: { opp_1: 13, opp_2: -13, opp_3: -13 },
        })
      )
  );

  // Valid TEAM LOSS (two opposing teammates) succeeds
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("team_loss_match")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "LOSS",
          winnerType: "TEAM",
          winningOpponentIds: ["opp_1", "opp_2"],
          winningOpponentNames: ["Marcus", "Elena"],
          deckEloAfter: 1187,
          deckEloDelta: -13,
          opponentEloDeltas: { opp_1: 13, opp_2: 13, opp_3: -13 },
        })
      )
  );

  // Reject TEAM LOSS with only 1 winning opponent
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("bad_team_loss")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "LOSS",
          winnerType: "TEAM",
          winningOpponentIds: ["opp_1"],
          winningOpponentNames: ["Marcus"],
        })
      )
  );

  // Reject TEAM with mismatched winningOpponentNames length
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("bad_team_names")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "LOSS",
          winnerType: "TEAM",
          winningOpponentIds: ["opp_1", "opp_2"],
          winningOpponentNames: ["Marcus"],
        })
      )
  );

  // Reject TEAM missing winningOpponentIds
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/matches`)
      .doc("bad_team_missing")
      .set(
        validMatchPayload(ALICE_UID, "deck_1", {
          outcome: "WIN",
          winnerType: "TEAM",
        })
      )
  );
});

