export type ExecutionFailureCategory =
  | 'network'
  | 'modelTimeout'
  | 'stepTimeout'
  | 'unknown';

export interface ClassifiedExecutionFailure {
  category: ExecutionFailureCategory;
  hostname?: string;
}

const NETWORK_ERROR_PATTERNS = [
  /\bENOTFOUND\b/i,
  /\bEAI_AGAIN\b/i,
  /\bNetwork is unreachable\b/i,
  /\bgetaddrinfo\b/i,
];

const HOSTNAME_AFTER_DNS_ERROR =
  /(?:ENOTFOUND|EAI_AGAIN|getaddrinfo(?:\s+ENOTFOUND)?)\s+([a-z0-9.-]+\.[a-z]{2,})/i;

export function classifyExecutionFailure(
  failure: string,
  output: readonly string[],
): ClassifiedExecutionFailure {
  const diagnostic = [failure, ...output].join('\n');
  if (NETWORK_ERROR_PATTERNS.some((pattern) => pattern.test(diagnostic))) {
    return {
      category: 'network',
      hostname: diagnostic.match(HOSTNAME_AFTER_DNS_ERROR)?.[1],
    };
  }
  if (
    /AI call hard timeout|APIConnectionTimeoutError|model request.*timed out/i.test(
      diagnostic,
    )
  ) {
    return { category: 'modelTimeout' };
  }
  if (/Node .+ timed out after \d+ms/i.test(diagnostic)) {
    return { category: 'stepTimeout' };
  }
  return { category: 'unknown' };
}
