export default class Changelog {
    constructor(
        public id: number | null,
        public appVersion: string,
        public contentMarkdown: string,
        public contentHtml?: string,
        public authorName?: string,
        public createdAt?: string,
        public isPublished: boolean = false
    ) {}
}