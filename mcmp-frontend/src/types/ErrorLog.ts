export interface ErrorLog {
  id: number;
  referenceId: string;
  exceptionClass: string;
  message: string | null;
  stacktrace: string | null;
  requestMethod: string | null;
  requestPath: string | null;
  requestQuery: string | null;
  requestBody: string | null;
  username: string | null;
  createdAt: string;
}
