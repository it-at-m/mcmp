import { useRules } from "@/composables/rules.ts";

// Mirrors the backend validation in JobController.requireNewRepositoryName
export function useRepoRules() {
  const rules = useRules();

  const newRepoNameRules = [
    rules.notEmptyRule("Es muss ein Name angegeben werden."),
    rules.regexRule(
      /^[A-Za-z0-9._/!-]+$/,
      "Erlaubt sind nur Buchstaben, Zahlen und . _ / ! -"
    ),
    rules.regexRule(
      /-(test|prod)$/,
      'Der Name muss auf "-test" oder "-prod" enden.'
    ),
  ];

  const upstreamUrlRules = [
    (value: string | null | undefined) =>
      !value ||
      /^https?:\/\/\S+$/.test(value) ||
      "Die URL muss mit http:// oder https:// beginnen.",
  ];

  return { newRepoNameRules, upstreamUrlRules };
}
