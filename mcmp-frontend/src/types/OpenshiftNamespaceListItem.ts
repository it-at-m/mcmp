export interface OpenshiftNamespaceListItem {
  id: number;
  name: string;
  isFavorite: boolean;
}

export interface OpenshiftNamespaceRef {
  id: number;
  name: string;
  clusterName: string | null;
}
