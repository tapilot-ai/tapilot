import assert from 'node:assert/strict';
import test from 'node:test';
import {
  collectFailureMessage,
  collectFinalAiResponse,
} from './run-result.mjs';

test('independent screenshot reading replaces an unsupported action summary', () => {
  const steps = [
    { node: 'aiAct', output: { summary: 'Android 14' } },
    { node: 'aiAssert', output: { data: { pass: true } } },
    { node: 'aiAsk', output: { data: { value: 'ColorOS 14.0; Android version unknown' } } },
  ];
  assert.equal(collectFinalAiResponse({ projects: [{ cases: [{ run: { steps } }] }] }),
    'ColorOS 14.0; Android version unknown');
});

test('empty explicit observation never falls back to an earlier action claim', () => {
  for (const value of [undefined, null, '', '  ']) {
    const steps = [
      { node: 'aiAct', output: { summary: 'Android 14' } },
      { node: 'aiAsk', output: { data: { value } } },
    ];
    assert.equal(collectFinalAiResponse({ projects: [{ cases: [{ run: { steps } }] }] }), undefined);
  }
});

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
                    output: { data: { count: 2 } },
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


test('final assertion does not overwrite observed phone facts', () => {
  const result = { projects: [{ cases: [{ run: { steps: [
    { node: 'aiAct', output: { data: { value: '自动调节已开启；存储 54.2 GB / 256 GB' } } },
    { node: 'aiAssert', output: { data: { value: true } } },
    { node: 'aiWaitFor', output: { data: { value: true } } },
  ] } }] }] };
  assert.equal(collectFinalAiResponse(result), '自动调节已开启；存储 54.2 GB / 256 GB');
});

test('verification alone cannot supply an observed answer', () => {
  assert.equal(collectFinalAiResponse({ projects: [{ cases: [{ run: { steps: [
    { node: 'aiAssert', output: { data: { value: true } } },
  ] } }] }] }), undefined);
});


test('preserves real SDK aiAct summary when the following assertion has no insight value', () => {
  assert.equal(collectFinalAiResponse({ projects: [{ cases: [{ run: { steps: [
    { node: 'aiAct', output: { summary: '自动调节已开启' } },
    { node: 'aiAssert', output: { summary: 'Assertion passed', data: { pass: true } } },
  ] } }] }] }), '自动调节已开启');
});
