import assert from 'node:assert/strict';
import test from 'node:test';
import {
  collectFailureMessage,
  collectFinalAiResponse,
} from './run-result.mjs';

test('returns the final AI node value from the last attempt', () => {
  assert.equal(
    collectFinalAiResponse({
      projects: [
        {
          cases: [
            {
              attempts: [
                {
                  steps: [
                    { node: 'aiAct' },
                    {
                      node: 'aiAsk',
                      output: { data: { value: '  最终回执  ' } },
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    }),
    '最终回执',
  );
});

test('serializes structured AI values and ignores non-AI node output', () => {
  assert.equal(
    collectFinalAiResponse({
      projects: [
        {
          cases: [
            {
              run: {
                steps: [
                  { node: 'custom', output: { data: { value: 'ignored' } } },
                  {
                    node: 'aiQuery',
                    output: { data: { value: { count: 2 } } },
                  },
                ],
              },
            },
          ],
        },
      ],
    }),
    '{\n  "count": 2\n}',
  );
});

test('returns the failed step message from the last attempt', () => {
  assert.equal(
    collectFailureMessage({
      projects: [
        {
          cases: [
            {
              attempts: [
                {
                  steps: [
                    { status: 'failed', error: { message: 'first failure' } },
                  ],
                },
                {
                  steps: [
                    {
                      status: 'failed',
                      error: { message: 'Screenshot failed: 1' },
                    },
                  ],
                },
              ],
            },
          ],
        },
      ],
    }),
    'Screenshot failed: 1',
  );
});

test('returns no failure for a successful run', () => {
  assert.equal(
    collectFailureMessage({
      projects: [{ cases: [{ run: { steps: [{ status: 'success' }] } }] }],
    }),
    undefined,
  );
});
