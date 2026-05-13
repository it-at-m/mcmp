export default class Backup {
  constructor(
    public serverId: number,
    public backupType: string,
    public backupServer: string,
    public clientServer: string,
    public saveSetName: string,
    public saveTimeString: string,
    public saveTime: string,
    public ssretentString: string,
    public ssretent: string,
    public ssid: string,
    public cloneId: string,
    public pool: string,
    public totalsize: number,
    public runtime: string
  ) {}
}
