export interface OpenshiftAppserviceRef {
  id: number;
  name: string;
}

export interface OpenshiftNamespaceDetail {
  id: number;
  name: string;
  sysId: string | null;
  sysClass: string | null;
  lastDiscovered: string | null;
  k8sUid: string | null;
  environment: string | null;
  clusterName: string | null;
  appservices: OpenshiftAppserviceRef[];
}
