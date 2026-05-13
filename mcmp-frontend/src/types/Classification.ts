export default class Classification {
  constructor(
    public kenner: string,
    public vCenter: string
  ) {}
}

export const classifications: { [key: string]: Classification } = {
  vCenterC: { kenner: "c", vCenter: "c" },
  vCenterK: { kenner: "k", vCenter: "k" },
};
