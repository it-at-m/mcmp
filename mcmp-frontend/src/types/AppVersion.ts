export type AppVersion = {
  version: string | null;
  buildTime: string | null;
  javaVersion: string | null;

  gitBranch: string | null;
  gitCommitId: string | null;
  gitCommitIdFull: string | null;
  gitCommitTime: string | null;
  gitDirty: boolean | null;
};
