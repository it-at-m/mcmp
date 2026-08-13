export interface OpenshiftNamespaceListItem {
  id: number;
  name: string;
  clusterEnvironment: string;
  isFavorite: boolean;
}

export interface OpenshiftNamespaceRef {
  id: number;
  name: string;
  clusterName: string | null;
  clusterEnvironment: string | null;
}
