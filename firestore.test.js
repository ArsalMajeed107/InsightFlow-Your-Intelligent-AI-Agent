const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read documents", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("documents").get());
});

test("Authenticated user: cannot read another user's documents", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("documents").doc("doc1").set({
      userId: BOB_UID,
      title: "Bob's Doc",
      originalFileName: "bob.pdf",
      fileSizeBytes: 1024,
      pageCount: 2,
      extractedText: "Text",
      summary: "Summary",
      language: "en",
      tags: ["legal"],
      createdAt: new Date(),
      updatedAt: new Date()
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("documents").doc("doc1").get());
});

test("Authenticated user: can create and read their own document", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice");
  
  await assertSucceeds(docRef.set({
    userId: ALICE_UID,
    title: "Alice AI Notes",
    originalFileName: "notes.pdf",
    fileSizeBytes: 2048,
    pageCount: 5,
    extractedText: "Detailed notes",
    summary: "Key findings",
    language: "en",
    tags: ["tech", "ai"],
    createdAt: new Date(),
    updatedAt: new Date()
  }));

  await assertSucceeds(docRef.get());
});

test("Authenticated user: can add document message and read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const msgRef = aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").collection("messages").doc("msg1");

  await assertSucceeds(msgRef.set({
    userId: ALICE_UID,
    documentId: "doc_alice",
    role: "user",
    content: "Summarize section 3",
    pageReferences: [3],
    createdAt: new Date()
  }));

  await assertSucceeds(msgRef.get());
});

test("Cross-user cannot write to another user's document chat", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const msgRef = bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").collection("messages").doc("msg_bob");

  await assertFails(msgRef.set({
    userId: BOB_UID,
    documentId: "doc_alice",
    role: "user",
    content: "Attempting hijack",
    pageReferences: [],
    createdAt: new Date()
  }));
});
