import { useEffect, useState } from "react";

export function useNow() {
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);
  return now;
}

export function formatClock(date: Date, format: "12" | "24", seconds: boolean) {
  const formatted = new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    second: seconds ? "2-digit" : undefined,
    hour12: format === "12",
  }).format(date);
  return format === "12" ? formatted.replace(/^0/, "") : formatted;
}

export function formatDate(date: Date) {
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    day: "2-digit",
    month: "short",
  })
    .format(date)
    .replace(/,/g, "")
    .toUpperCase();
}

export function formatZone(date: Date, hour12: boolean, timeZone?: string) {
  return new Intl.DateTimeFormat("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    hour12,
    timeZone,
  }).format(date);
}

export function formatDuration(totalSeconds: number) {
  const secs = Math.max(0, totalSeconds);
  const mm = String(Math.floor(secs / 60)).padStart(2, "0");
  const ss = String(secs % 60).padStart(2, "0");
  return `${mm}:${ss}`;
}
