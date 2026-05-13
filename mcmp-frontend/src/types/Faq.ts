export default class Faq {
  constructor(
    public id: number | null,
    public categoryId: number,
    public question: string,
    public answerMarkdown: string,
    public answerHtml: string,
    public sortOrder: number,
    public isPublished: boolean,
    public userId?: number
  ) {}
}
