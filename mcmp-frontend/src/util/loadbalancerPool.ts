import type { LoadbalancerPool } from "@/types/LoadbalancerDetail";

const CAP_PORT_RANGES: [number, number][] = [
  [32201, 32207],
  [32301, 32307],
  [32401, 32407],
];

function isCapPort(port: number): boolean {
  return CAP_PORT_RANGES.some(([from, to]) => port >= from && port <= to);
}

export function isCapPool(pool: LoadbalancerPool): boolean {
  return (
    pool.members.length > 0 &&
    pool.members.every((m) => !m.serverId) &&
    pool.members.every((m) => isCapPort(m.port))
  );
}
