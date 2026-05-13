export interface ServerList {
  id: number;
  name: string;
  powerState: string;
  os?: string;
  serverKind?: string;
  serverType?: string;
  hasRightsizingRecommendations?: boolean;
}
