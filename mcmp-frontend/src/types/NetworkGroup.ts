import type Appservice from "@/types/Appservice.ts";

import { EnvironmentType } from "@/types/EnvironmentType";

export default class NetworkGroup {
  constructor(
    public id: number | undefined,
    public name: string,
    public application: boolean,
    public database: boolean,
    public storage: boolean,
    public restrict: boolean,
    public appservices: Appservice[] = [],
    public environment: EnvironmentType,
  ) {}
}
