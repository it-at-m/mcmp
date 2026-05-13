export default class Snapshot {
  constructor(
    public serverId: number,
    public snapshotId: number,
    public name: string,
    public description: string,
    public createTime: string,
    public retentionPeriod: string,
    public quiesced: boolean,
    public state: string,
    public replaySupported: boolean
  ) {}
}
