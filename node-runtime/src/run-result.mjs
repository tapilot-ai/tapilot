export function collectFinalAiResponse(runResult) {
  let finalResponse;
  for (const project of runResult.projects ?? []) {
    for (const testCase of project.cases ?? []) {
      const attempts = testCase.attempts?.length
        ? testCase.attempts
        : testCase.run
          ? [testCase.run]
          : [];
      for (const attempt of attempts) {
        for (const step of attempt.steps ?? []) {
          const value = step?.node === 'aiAct'
            ? step?.output?.summary ?? step?.output?.data?.value
            : ['aiQuery', 'verifyPhone'].includes(step?.node)
              ? step?.output?.data
              : step?.output?.data?.value;
          // Assertions/waits establish success; their booleans are not observed facts.
          if (!['aiAct', 'aiAsk', 'aiQuery', 'verifyPhone', 'aiString', 'aiNumber', 'aiBoolean'].includes(step?.node)) continue;
          // An empty explicit observation must not resurrect an earlier action summary.
          if (value === undefined || value === null) { finalResponse = undefined; continue; }
          const text =
            typeof value === 'string'
              ? value.trim()
              : JSON.stringify(value, null, 2);
          finalResponse = text || undefined;
        }
      }
    }
  }
  return finalResponse;
}

export function collectFailureMessage(runResult) {
  let failure;
  for (const project of runResult.projects ?? []) {
    for (const testCase of project.cases ?? []) {
      const attempts = testCase.attempts?.length
        ? testCase.attempts
        : testCase.run
          ? [testCase.run]
          : [];
      for (const attempt of attempts) {
        for (const step of attempt.steps ?? []) {
          if (step.status === 'failed' && step.error?.message) {
            failure = step.error.message;
          }
        }
      }
    }
  }
  return failure;
}
