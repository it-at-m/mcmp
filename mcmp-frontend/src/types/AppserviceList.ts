import type { EnvironmentType } from "@/types/EnvironmentType.ts";

export default class AppserviceList {
  constructor(
    public id: number,
    public name: string,
    public hasServers: boolean,
    public environment: EnvironmentType,
    public isFavorite = false
  ) {}
}
