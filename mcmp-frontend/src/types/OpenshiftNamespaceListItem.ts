export interface OpenshiftNamespaceListItem {
  id: number;
  name: string;
  environment: string;
  isFavorite: boolean;
}

export interface OpenshiftNamespaceRef {
  id: number;
  name: string;
  clusterName: string | null;
}
