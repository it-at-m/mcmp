export default class JobNodeHierarchy {
  constructor(
    public jobDepth: number | null,
    public nodeAlias: string | null,
    public jobAwxLink: string | null,
    public templateType: string | null,
    public jobOrg: string | null,
    public jobStatus: string | null,
    public jobFailed: boolean | null,
    public jobReturnCompleted: boolean | null,
    public jobReturnMessage: string | null,
    public jobReturnData: string | null,
    public jobStarted: string | null,
    public jobFinished: string | null,
    public jobDuration: number | null,
    public jobExtraVars: string | null,
    public jobArtifacts: string | null,
    public jobIsRootCause: boolean | null,
    public jobErrorMessage: string | null,
    public awxDescription: string | null,
    public jobId: number | null,
    public templateId: number | null,
    public templateLink: string | null,
    public awxStatus: string | null,
    public awxLaunchRequest: string | null
  ) {}
}
