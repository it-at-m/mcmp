export default class Disk {
  constructor(
    public serverId: number,
    public vdiskKey: number,
    public unitNumber: number,
    public capacityInBytes: number,
    public vdiskId: string,
    public device: string
  ) {}
}
