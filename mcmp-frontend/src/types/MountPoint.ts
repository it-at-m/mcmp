export default class MountPoint {
  constructor(
    public serverId: number,
    public diskPath: string,
    public capacityInBytes: number,
    public freeSpaceInBytes: number,
    public source: string,
    public editable: boolean
  ) {}
}
