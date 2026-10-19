/**
 * Small helpers to build CSV files that Excel (German locale) opens
 * directly and correctly:
 * - semicolon as delimiter
 * - UTF-8 with BOM so umlauts are rendered correctly
 * - quoting of fields containing the delimiter, quotes or line breaks
 */

export type CsvValue = string | number | boolean | Date | null | undefined;

export interface CsvColumn<T> {
  header: string;
  value: (item: T) => CsvValue;
}

const DEFAULT_DELIMITER = ";";

function escapeValue(value: CsvValue, delimiter: string): string {
  if (value === null || value === undefined) {
    return "";
  }

  const text = value instanceof Date ? value.toISOString() : String(value);

  const needsQuoting =
    text.includes(delimiter) ||
    text.includes('"') ||
    text.includes("\n") ||
    text.includes("\r");

  if (!needsQuoting) {
    return text;
  }

  return `"${text.replace(/"/g, '""')}"`;
}

export function buildCsv<T>(
  items: T[],
  columns: CsvColumn<T>[],
  delimiter = DEFAULT_DELIMITER
): string {
  const headerLine = columns
    .map((column) => escapeValue(column.header, delimiter))
    .join(delimiter);

  const rows = items.map((item) =>
    columns
      .map((column) => escapeValue(column.value(item), delimiter))
      .join(delimiter)
  );

  return [headerLine, ...rows].join("\r\n");
}

export function downloadCsv(filename: string, csv: string): void {
  // Prepend BOM so Excel detects the file as UTF-8.
  const blob = new Blob(["\uFEFF", csv], {
    type: "text/csv;charset=utf-8;",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");

  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);

  // Release the URL on the next tick so the download can start.
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
