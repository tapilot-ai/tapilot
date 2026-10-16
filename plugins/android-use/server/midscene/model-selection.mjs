/** Prefer the newest available Luna, without inventing an account entitlement. */
export function defaultChatGPTModel(models) {
  const compatible = models.filter(({ slug }) => /^gpt-/.test(slug));
  const luna = compatible.flatMap(({ slug }) => {
    const match = /^gpt-(\d+)(?:\.(\d+))?-luna(?:-(\d{4}-\d{2}-\d{2}))?$/.exec(slug);
    return match ? [{ slug, major: Number(match[1]), minor: Number(match[2] ?? 0), date: match[3] ?? "9999-99-99" }] : [];
  });
  luna.sort((a, b) => b.major - a.major || b.minor - a.minor || b.date.localeCompare(a.date));
  return luna[0]?.slug ?? compatible[0]?.slug;
}
