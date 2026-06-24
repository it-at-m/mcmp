export interface LoadbalancerListItem {
  id: number;
  name: string;
  domain: string | null;
  listen: string;
  port: number;
  appserviceName: string | null;
}
