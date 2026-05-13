export default class Price {
  constructor(
    public name: string,
    public pricePerUnit: number,
    public currency: string,
    public description: string
  ) {}
}
