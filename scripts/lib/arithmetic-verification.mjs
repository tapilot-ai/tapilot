// Diagnostic prototype: exact rational arithmetic, never JavaScript evaluation.
const gcd = (a, b) => { while (b) [a, b] = [b, a % b]; return a < 0n ? -a : a; };
function fraction(n, d = 1n) {
  if (!d) throw Error('Division by zero');
  if (d < 0n) { n = -n; d = -d; }
  const divisor = gcd(n, d);
  return { n: n / divisor, d: d / divisor };
}
const equal = (a, b) => a.n === b.n && a.d === b.d;
function value(node) {
  if (node.op === 'number') return node.value;
  const a = value(node.left), b = value(node.right);
  switch (node.op) {
    case '+': return fraction(a.n * b.d + b.n * a.d, a.d * b.d);
    case '-': return fraction(a.n * b.d - b.n * a.d, a.d * b.d);
    case '*': return fraction(a.n * b.n, a.d * b.d);
    case '/': return fraction(a.n * b.d, a.d * b.n);
  }
}

export function parseArithmetic(input) {
  if (typeof input !== 'string' || !input.trim() || input.length > 512) throw Error('Invalid arithmetic');
  const source = input.replaceAll('×', '*').replaceAll('÷', '/').replaceAll('−', '-');
  const tokens = source.match(/(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?|[()+*/-]|\S/g) ?? [];
  if (tokens.length > 96) throw Error('Arithmetic too complex');
  let index = 0;
  function primary(depth) {
    if (depth > 24) throw Error('Arithmetic too deep');
    const token = tokens[index++];
    if (token === '(') {
      const node = expression(0, depth + 1);
      if (tokens[index++] !== ')') throw Error('Missing closing parenthesis');
      return node;
    }
    if (token === '-' || token === '+') {
      const node = primary(depth + 1);
      if (token === '+') return node;
      if (node.op === 'number') return { op: 'number', value: fraction(-node.value.n, node.value.d) };
      return { op: '-', left: { op: 'number', value: fraction(0n) }, right: node };
    }
    if (!/^(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?$/.test(token ?? '')) throw Error('Unsupported arithmetic token');
    const normalized = token.replaceAll(',', '');
    if (normalized.length > 40) throw Error('Number too large');
    const [integer, decimal = ''] = normalized.split('.');
    return { op: 'number', value: fraction(BigInt(integer + decimal), 10n ** BigInt(decimal.length)) };
  }
  const precedence = { '+': 1, '-': 1, '*': 2, '/': 2 };
  function expression(minimum, depth) {
    let left = primary(depth);
    while (precedence[tokens[index]] >= minimum) {
      const op = tokens[index++];
      left = { op, left, right: expression(precedence[op] + 1, depth + 1) };
    }
    return left;
  }
  const tree = expression(0, 0);
  if (index !== tokens.length) throw Error('Unconsumed arithmetic');
  value(tree); // Reject division by zero even before comparison.
  return tree;
}

// Only proper subexpressions may be replaced by their committed value. The
// final operator stays visible: 5192/4 cannot validate a requested division by 8.
function matches(actual, expected, fold, root = true) {
  if (fold && !root && actual.op === 'number') return equal(actual.value, value(expected));
  if (actual.op !== expected.op) return false;
  if (actual.op === 'number') return equal(actual.value, expected.value);
  return matches(actual.left, expected.left, fold, false) && matches(actual.right, expected.right, fold, false);
}

export function checkArithmetic({ expression, requireExpandedExpression }, facts) {
  if (facts?.isCalculator !== true || facts?.evaluated !== true) return { success: false, reason: 'Missing evaluated Calculator evidence' };
  try {
    const expected = parseArithmetic(expression);
    const actual = parseArithmetic(facts.expression);
    const result = parseArithmetic(facts.result);
    if (result.op !== 'number') return { success: false, reason: 'The result is not a displayed number' };
    if (!matches(actual, expected, !requireExpandedExpression)) return { success: false, reason: 'Visible expression does not match the requested operation' };
    if (!equal(value(actual), result.value) || !equal(value(expected), result.value)) return { success: false, reason: 'Displayed result does not match the arithmetic' };
    return { success: true, reason: 'Visible evaluated expression and result match' };
  } catch {
    return { success: false, reason: 'Unsupported or ambiguous arithmetic evidence' };
  }
}
