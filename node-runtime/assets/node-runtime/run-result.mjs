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
          const value = step?.output?.data?.value;
          if (!step?.node?.startsWith('ai') || value === undefined) continue;
          const text =
            typeof value === 'string'
              ? value.trim()
              : JSON.stringify(value, null, 2);
          if (text) finalResponse = text;
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
