"use strict";

const path = require("path");
const { spawnSync } = require("child_process");

const tests = [
  "storage-test.js",
  "schema-migration-test.js",
  "restart-e2e-test.js",
  "auth-test.js",
  "authorization-e2e-test.js",
  "session-token-storage-test.js",
  "session-pruning-test.js",
  "login-rate-limit-test.js",
  "config-test.js",
  "backup-test.js",
  "push-test.js"
];

for (const test of tests) {
  console.log(`\n=== ${test} ===`);

  const result = spawnSync(
    process.execPath,
    [path.join(__dirname, test)],
    {
      cwd: __dirname,
      env: process.env,
      stdio: "inherit"
    }
  );

  if (result.error) {
    console.error(`Failed to start ${test}: ${result.error.message}`);
    process.exit(1);
  }

  if (result.status !== 0) {
    console.error(`${test} failed with exit code ${result.status}.`);
    process.exit(result.status ?? 1);
  }
}

console.log("\nRELAY BACKEND TEST SUITE GREEN");
