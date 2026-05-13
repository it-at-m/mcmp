import type AppserviceList from "@/types/AppserviceList.ts";





type Listener = {
  port: number;
  server_pool: "default";
  listener_type: "http" | "tcp" | "udp" | "fast-tcp";
  clientside_tls: boolean;
  serverside_tls: boolean;
  x_forwarded_for: boolean;
  persistence: "cookie" | "source-address" | "none";
  wss: boolean;
};

type ServerPools = {
  member: { name: string; ip: string; ports: number[] }[];
  monitors: ["tcp"] | MonitorType[];
  loadbalancing_mode: "round-robin" | "least-connections";
};

export type MonitorType = {
  type: "http" | "https";
  method: "GET" | "HEAD" | "OPTIONS";
  path: string;
  headers: { Host: string; [key: string]: string } | { Host: string };
  receive_string: string;
};

export default class LoadbalancerOrder {
  constructor(
    // General
    public appservice: AppserviceList | null,
    public dns: string | null,

    // Virtual-IP / Listener
    public listener: Listener[],

    // Server-Pool
    public server_pools: ServerPools[]
  ) {}
}
