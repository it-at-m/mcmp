export function toFirstLetterUppercase(text: string): string {
  return text ? text.charAt(0).toUpperCase() + text.slice(1).toLowerCase() : "";
}

export function ifEmptyReturnDash(value: unknown): string {
  if (value === undefined || value === null || value === "") {
    return "-";
  }
  return String(value);
}

export function toDateAndTimeString(date: Date | string): string {
  if (!date) return "";
  const d = typeof date === "string" ? new Date(date) : date;
  if (isNaN(d.getTime())) return "-";
  return d.toLocaleString("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function formatToBerlinDateTime(
  isoString: Date | string | null | undefined
): string {
  if (!isoString) return "-";

  const date = typeof isoString === "string" ? new Date(isoString) : isoString;
  if (Number.isNaN(date.getTime())) return "-";

  return new Intl.DateTimeFormat("de-DE", {
    timeZone: "Europe/Berlin",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(date);
}

export function formatBytesSmart(value: number): string {
  if (value === undefined || value === null) {
    return "-";
  }
  if (value < 1024) {
    return value + " B";
  } else if (value < 1024 * 1024) {
    const kbValue = value / 1024;
    return (
      kbValue.toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }) + " KB"
    );
  } else if (value < 1024 * 1024 * 1024) {
    const mbValue = value / (1024 * 1024);
    return (
      mbValue.toLocaleString("de-DE", {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1,
      }) + " MB"
    );
  } else if (value < 1024 * 1024 * 1024 * 1024) {
    const gbValue = value / (1024 * 1024 * 1024);
    return (
      gbValue.toLocaleString("de-DE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }) + " GB"
    );
  } else {
    const tbValue = value / (1024 * 1024 * 1024 * 1024);
    return (
      tbValue.toLocaleString("de-DE", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }) + " TB"
    );
  }
}

export function formatToGermanLocalTime(isoString: string): string {
  if (!isoString) return "";
  const date = new Date(isoString);
  return date.toLocaleString("de-DE", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

export function formatToBerlinDate(
  isoString: Date | string | null | undefined
): string {
  if (!isoString) return "-";

  const date = typeof isoString === "string" ? new Date(isoString) : isoString;
  if (Number.isNaN(date.getTime())) return "-";

  return new Intl.DateTimeFormat("de-DE", {
    timeZone: "Europe/Berlin",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date);
}

export function formatMBtoGB(value: number): string {
  if (value === undefined || value === null) return "-";
  return (value / 1024).toFixed(1);
}

export function formatBtoGB(value: number): string {
  if (value === undefined || value === null) {
    return "-";
  }
  const gbValue = value / (1024 * 1024 * 1024);
  return gbValue.toFixed(0);
}
export function calculateBtoGB(value: number): number {
  if (value === undefined || value === null) {
    return 0;
  }
  return value / (1024 * 1024 * 1024);
}
export function calculateGBtoB(value: number): number {
  if (value === undefined || value === null) {
    return 0;
  }
  return value * (1024 * 1024 * 1024);
}
export function calculateMBtoGB(value: number): number {
  if (value === undefined || value === null) {
    return 0;
  }
  return value / 1024;
}

export function formatCurrency(value: number): string {
  if (value === undefined || value === null) return "-";
  return value.toLocaleString("de-DE", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function formatBooleanToGerman(
  value: boolean | null | undefined
): string {
  if (value === undefined || value === null) return "-";
  return value ? "Ja" : "Nein";
}

export function formatBooleanToJaNein(value: boolean | null | undefined): string {
  if (value === undefined || value === null) return "-";
  return value ? "Ja" : "Nein";
}

export function formatDuration(
  duration: string | number | null | undefined
): string {
  if (duration === null || duration === undefined || duration === "") {
    return "";
  }

  let hours = 0;
  let minutes = 0;
  let seconds = 0;

  // Convert string numbers to actual numbers
  const numericDuration =
    typeof duration === "string" ? parseFloat(duration) : duration;

  if (
    !isNaN(numericDuration as number) &&
    typeof numericDuration === "number"
  ) {
    const totalSeconds = Math.floor(numericDuration);
    hours = Math.floor(totalSeconds / 3600);
    minutes = Math.floor((totalSeconds % 3600) / 60);
    seconds = totalSeconds % 60;
  } else if (typeof duration === "string" && duration.startsWith("PT")) {
    const hoursMatch = duration.match(/(\d+)H/);
    const minutesMatch = duration.match(/(\d+)M/);
    const secondsMatch = duration.match(/(\d+(\.\d+)?)S/);

    hours = hoursMatch?.[1] ? parseInt(hoursMatch[1]) : 0;
    minutes = minutesMatch?.[1] ? parseInt(minutesMatch[1]) : 0;
    seconds = secondsMatch?.[1] ? Math.floor(parseFloat(secondsMatch[1])) : 0;
  } else {
    return String(duration);
  }

  const h = hours.toString().padStart(2, "0");
  const m = minutes.toString().padStart(2, "0");
  const s = seconds.toString().padStart(2, "0");

  return `${h}:${m}:${s}`;
}
