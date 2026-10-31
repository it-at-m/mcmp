export const taggingActionOptions = [
  { key: "SERVER", text: "Server" },
  { key: "REPOSITORY", text: "Repository" },
  { key: "LOADBALANCER", text: "Loadbalancer" },
  { key: "KUBERNETES_NAMESPACE", text: "Kubernetes Namespace" },
] as const;

export type TaggingAction = (typeof taggingActionOptions)[number]["key"];

/**
 * Checks if the discovery and tagging process is enabled by verifying the provided tagging action.
 *
 * @param {TaggingAction | null} [taggingAction] - The tagging action to evaluate. If null or undefined, tagging is considered disabled.
 * @return {boolean} - Returns `true` if the tagging action is provided (not null or undefined), otherwise `false`.
 */
export function isDiscoveryAndTaggingEnabled(
  taggingAction?: TaggingAction | null
): boolean {
  return taggingAction != null;
}

/**
 * Retrieves a descriptive text for discovery and tagging based on the provided tagging action.
 *
 * @param {TaggingAction | null | undefined} taggingAction - The tagging action which determines the text to be returned.
 * @return {string} A string describing the discovery and tagging status, or a default message if disabled.
 */
export function getDiscoveryAndTaggingText(
  taggingAction?: TaggingAction | null
): string {
  if (!isDiscoveryAndTaggingEnabled(taggingAction)) {
    return "Keine Erkennung / kein Tagging";
  }
  const option = taggingActionOptions.find((o) => o.key === taggingAction);
  return `Discovery & Tagging (${option?.text ?? taggingAction})`;
}
