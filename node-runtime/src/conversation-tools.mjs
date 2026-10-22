import { Type } from 'typebox';

export const planSchema = Type.Object({
  steps: Type.Array(Type.String({ minLength: 1 }), { minItems: 1, maxItems: 20 }),
  currentStep: Type.Integer({ minimum: 0 }),
});

export const phoneOperationSchema = Type.Object({
  effort: Type.Optional(Type.Union([
    Type.Literal('fast'), Type.Literal('balance'), Type.Literal('deepThink'),
  ], { description: 'Use deepThink for multi-page discovery, organizing or editing multiple items, and recovery requiring memory of prior visual states. It enables subgoals and visual memory. Use fast for a short, known route or a single visible control. balance is for moderate navigation. Do not use fast for an entire multi-page organization task.' })),
  instruction: Type.String({ minLength: 1 }),
  success_condition: Type.String({
    minLength: 1,
    description: 'Requested result observable in the final screenshot. Keep action-history constraints such as "do not change settings" in instruction, not this screenshot predicate. Do not omit requested visible facts.',
  }),
  mode: Type.Union([Type.Literal('act'), Type.Literal('observe')], {
    description: 'Use observe ONLY for the current screen or an app landing screen after launch. Use act for navigation to a subpage, searching, scrolling, clicking or typing, even for read-only goals that forbid changing settings. Do not prepend an observe call when navigation is requested.',
  }),
  finishWhenVerified: Type.Boolean({
    description: 'True only when this single operation fulfills the ENTIRE current user goal and the final visual observation alone is a sufficient answer. False for intermediate steps, multi-page facts, synthesis or explanations. Success returns the actual observed result, never a predicted summary.',
  }),
  app: Type.String({ description: 'Exact installed app label, package or component to launch first; empty string to keep the current app. Reuse find_apps results here.' }),
  plan: Type.Optional(Type.Object(planSchema.properties, {
    description: 'For multi-step work, save or update the plan together with this phone operation instead of a separate set_plan call. currentStep is the zero-based step about to execute, not a claim that it has succeeded.',
  })),
});

/** Decode only the native operation fields; validate before any phone side effect. */
export function parsePhoneOperation(value) {
  const text = (key, fallback) => {
    const candidate = Object.hasOwn(value, key) ? value[key] : fallback;
    if (typeof candidate !== 'string') throw new Error(`Expected string: ${key}`);
    return candidate;
  };
  const operation = { instruction: text('instruction'), success_condition: text('success_condition'),
    mode: text('mode', 'act'), app: text('app', ''), effort: text('effort', 'fast') };
  if (!operation.instruction.trim() || !operation.success_condition.trim()) throw new Error('Phone instruction and success condition must not be blank');
  if (!['act', 'observe'].includes(operation.mode)) throw new Error('Unsupported phone operation mode');
  if (!['fast', 'balance', 'deepThink'].includes(operation.effort)) throw new Error('Unsupported planning effort');
  return operation;
}
