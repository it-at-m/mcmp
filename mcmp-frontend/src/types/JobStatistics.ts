export default class JobStatistics {
  constructor(
    public action: string,
    public changeRequired: boolean,
    public changeStatusRejected: number,
    public changeStatusCanceled: number,
    public changeStatusSkipped: number,
    public changeStatusApproved: number,
    public changeStatusFailed: number,
    public totalJobs: number,
    public awxStatusFailed: number,
    public awxStatusSuccessful: number,
    public awxDurationMin: number,
    public awxDurationMax: number,
    public awxDurationMittelwert: number,
    public awxDurationTrimmedAvg: number,
    public sortOrder: number
  ) {}
}
