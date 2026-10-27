import { parseArithmetic } from './arithmetic-verification.mjs';

export function resolutionMatches(actual, expected) {
  if (!expected) return undefined;
  if (actual?.kind !== expected.kind) return false;
  if (expected.kind === 'calculation') {
    try {
      const stringify = input => JSON.stringify(parseArithmetic(input), (_, value) => typeof value === 'bigint' ? String(value) : value);
      if (stringify(actual.calculation?.expression) !== stringify(expected.expression) ||
          actual.calculation.requireExpandedExpression !== expected.requireExpandedExpression) return false;
    } catch { return false; }
  }
  return (expected.constraintsContain ?? []).every(word => actual.executionConstraints?.some(value => value.includes(word)));
}

export function locatingMatches(result, expected, screenshotShrinkFactor = 1) {
  const center = result?.center;
  return Array.isArray(center) && center.length === 2 && center.every(Number.isFinite) &&
    Number.isFinite(screenshotShrinkFactor) && screenshotShrinkFactor >= 1 &&
    Math.hypot(center[0] * screenshotShrinkFactor - expected.center[0], center[1] * screenshotShrinkFactor - expected.center[1]) <= expected.radius;
}
