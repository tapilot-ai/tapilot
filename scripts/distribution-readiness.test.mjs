import test from 'node:test';
import assert from 'node:assert/strict';
import { distributionBlockers } from './distribution-readiness.mjs';
test('binary publication fails closed for missing inventories, unresolved flags or collected-only sources', () => {
  const good = Object.fromEntries(['npm', 'native', 'android'].map(k => [k, {packages:[{id:k,reviewRequired:false}]}]));
  assert.deepEqual(distributionBlockers(good, {redistributionApproved:true}), []);
  assert.equal(distributionBlockers({}, {}).length, 4);
  assert.equal(distributionBlockers(good, {materialsVerified:124,redistributionApproved:false}).length, 1);
  good.native.packages[0].reviewRequired = true;
  assert.equal(distributionBlockers(good, {redistributionApproved:true}).length, 1);
  delete good.native.packages[0].reviewRequired;
  assert.equal(distributionBlockers(good, {redistributionApproved:true}).length, 1);
});
