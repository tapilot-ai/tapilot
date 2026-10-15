/** JSON is valid YAML and keeps model supplied strings out of YAML syntax. */
export function verifiedPhoneTask(instruction: string, successCondition: string): string {
  return JSON.stringify({
    cases: [{
      name: 'Tapilot verified phone operation',
      steps: [
        { aiAct: { prompt: instruction } },
        { aiAssert: { prompt: successCondition } },
      ],
    }],
  });
}
