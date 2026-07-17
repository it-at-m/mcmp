export interface UnifiedStorageItemList {
  uuid: string;
  name: string;
  path?: string;
  type: string;
  storageCategory?: string;
  protocol: string;
  appserviceNames?: string;
  isFavorite: boolean;
}
