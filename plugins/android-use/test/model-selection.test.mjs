import assert from "node:assert/strict";
import test from "node:test";
import { defaultChatGPTModel } from "../server/midscene/model-selection.mjs";
const choose = (...slugs) => defaultChatGPTModel(slugs.map(slug => ({ slug })));
test("SIWC prefers newest available Luna regardless of catalog order", () => {
  assert.equal(choose("gpt-6.1-sol", "gpt-6-luna", "gpt-6-astra"), "gpt-6-luna");
  assert.equal(choose("gpt-6.2-luna", "gpt-6.10-luna", "gpt-6-luna"), "gpt-6.10-luna");
  assert.equal(choose("gpt-6.10-luna", "gpt-7-luna"), "gpt-7-luna");
  assert.equal(choose("gpt-6-luna-2026-10-01", "gpt-6-luna"), "gpt-6-luna");
});
test("SIWC fallback only uses an available compatible model", () => {
  assert.equal(choose("unrelated", "gpt-6.1-sol"), "gpt-6.1-sol");
  assert.equal(choose(), undefined);
  assert.equal(choose("unrelated"), undefined);
});
