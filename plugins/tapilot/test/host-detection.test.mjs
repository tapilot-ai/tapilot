import test from 'node:test';
import assert from 'node:assert/strict';
import { summarize } from '../scripts/probe-client-host.mjs';

test('DSH session outranks inherited Codex markers without claiming a remote UI', () => {
  const result = summarize({env:{DSH_SESSION_ID:'private-dsh-id',CODEX_THREAD_ID:'inherited-private-id'},ancestors:[{depth:0,name:'zsh',host:null},{depth:1,name:'node',host:null}]});
  assert.equal(result.host,'dsh');
  assert.equal(result.surface,'unknown');
  assert.deepEqual(result.candidates,['dsh','codex']);
  assert.ok(!JSON.stringify(result).includes('private'));
});
test('Codex running inside a DSH terminal retains both agent and container evidence', () => {
  const result = summarize({env:{DSH_SESSION_ID:'session',CODEX_THREAD_ID:'thread'},ancestors:[{depth:0,name:'codex',host:null}]});
  assert.equal(result.host,'codex');
  assert.equal(result.containerHost,'dsh');
  assert.equal(result.surface,'unknown');
});
test('an app sandbox overrides inherited markers; adapter hints are not host identity', () => {
  const result = summarize({env:{CODEX_THREAD_ID:'thread',TAPILOT_HOST:'workbuddy',DOUBAO_OFFICE_LOG_ID:'log',DOUBAO_OFFICE_PLATFORM_APP_ID:'id'},executable:'/sandbox/DoubaoWork/bin/node'});
  assert.equal(result.host,'doubao');
  assert.equal(result.adapterHint,'workbuddy');
  assert.equal(result.panelSupport,'unverified');
});
test('install adapter alone and unsupported terminal values do not identify the host', () => {
  const result = summarize({env:{TAPILOT_HOST:'codex',TERM_PROGRAM:'private-value'}});
  assert.equal(result.host,'unknown');
  assert.equal(result.terminal,'other');
  assert.ok(!JSON.stringify(result).includes('private-value'));
});

test('Cherry process evidence identifies its container without leaking inherited values', () => {
  const result = summarize({env:{CODEX_THREAD_ID:'inherited-secret'}, executable:'/Applications/Cherry Studio.app/Contents/Frameworks/Cherry Studio Helper.app/Contents/MacOS/Cherry Studio Helper'});
  assert.equal(result.host, 'cherry');
  assert.equal(result.confidence, 'process-evidence');
  assert.equal(result.panelSupport, 'unverified');
  assert.ok(!JSON.stringify(result).includes('inherited-secret'));
});
