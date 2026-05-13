export default class FaqCategory {
  constructor(
    public id: number | null,
    public name: string,
    public sortOrder: number,
    public description?: string
  ) {}
}
