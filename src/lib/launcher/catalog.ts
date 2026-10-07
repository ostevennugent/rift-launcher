import type { LucideIcon } from "lucide-react";
import {
  Calculator,
  CalendarDays,
  Camera,
  Clock,
  CloudSun,
  Folder,
  Globe,
  Image,
  Mail,
  Map,
  MessageSquare,
  Music,
  NotebookPen,
  Phone,
  SlidersHorizontal,
  Terminal,
  Users,
  Wallet,
  AudioLines,
  PenLine,
  Route,
} from "lucide-react";
import type { AiId, PageId, PresetId } from "@/lib/launcher/types";

export type AppItem = {
  id: string;
  name: string;
  icon: LucideIcon;
  blurb: string;
};

export const AI_APPS: AppItem[] = [
  { id: "relay", name: "Relay", icon: AudioLines, blurb: "Reads the brief, this screen, and device vitals." },
  { id: "draft", name: "Draft", icon: PenLine, blurb: "Rewrites the screen into a shorter note." },
  { id: "trace", name: "Trace", icon: Route, blurb: "Turns the agenda and places into leave-by times." },
];

export const APPS: AppItem[] = [
  ...AI_APPS,
  { id: "phone", name: "Phone", icon: Phone, blurb: "Recent calls. Tap a name to open a line." },
  { id: "messages", name: "Messages", icon: MessageSquare, blurb: "Four threads. Replies stay on this device." },
  { id: "mail", name: "Mail", icon: Mail, blurb: "Three notes from studio, transit, and color." },
  { id: "browser", name: "Browser", icon: Globe, blurb: "Saved pages for Harbor. Not a live web view." },
  { id: "maps", name: "Maps", icon: Map, blurb: "Walking times around the Harbor stop." },
  { id: "camera", name: "Camera", icon: Camera, blurb: "Shutter writes a frame into the roll." },
  { id: "photos", name: "Photos", icon: Image, blurb: "Frames captured in this session." },
  { id: "calendar", name: "Calendar", icon: CalendarDays, blurb: "Today is three blocks, starting at 09:40." },
  { id: "music", name: "Music", icon: Music, blurb: "A short deck. Play, pause, and skip stay in the launcher." },
  { id: "notes", name: "Notes", icon: NotebookPen, blurb: "One draft. It persists on this device." },
  { id: "files", name: "Files", icon: Folder, blurb: "Recent launcher files." },
  { id: "terminal", name: "Terminal", icon: Terminal, blurb: "help, time, vitals, hud, clear, uname." },
  { id: "wallet", name: "Wallet", icon: Wallet, blurb: "Transit pass, studio card, cash." },
  { id: "weather", name: "Weather", icon: CloudSun, blurb: "Harbor is clear and cooling after 18:00." },
  { id: "clock", name: "Clock", icon: Clock, blurb: "Local time plus London, Tokyo, and New York." },
  { id: "calc", name: "Calc", icon: Calculator, blurb: "Four functions. No operator is guessed." },
  { id: "people", name: "People", icon: Users, blurb: "Call without leaving the glass." },
  { id: "settings", name: "Settings", icon: SlidersHorizontal, blurb: "Accent, modules, overlays, and the dock." },
];

export function appById(id: string) {
  return APPS.find((app) => app.id === id);
}

export function isAi(id: string): id is AiId {
  return id === "relay" || id === "draft" || id === "trace";
}

export const NOTIFICATIONS = [
  {
    id: "n1",
    appId: "messages",
    title: "Avery Chen",
    body: "Shade tiles feel fast. Keep HUD behind one switch.",
    time: "2m",
  },
  {
    id: "n2",
    appId: "mail",
    title: "North Channel",
    body: "Overlay notes are in. Scanlines should default off.",
    time: "18m",
  },
  {
    id: "n3",
    appId: "calendar",
    title: "Design review",
    body: "Room 4 at 09:40. Bring the dock layout.",
    time: "40m",
  },
  {
    id: "n4",
    appId: "maps",
    title: "Line 4",
    body: "Holding six minutes behind into Harbor.",
    time: "1h",
  },
];

export const AGENDA = [
  { time: "09:40", title: "Design review", where: "Room 4 · overlay" },
  { time: "12:15", title: "Transit window", where: "Line 4 · Harbor" },
  { time: "16:00", title: "Patch notes", where: "Async" },
];

export const HEADLINES = [
  "Harbor grid enters night cycle at 22:00.",
  "Line 4 is holding six minutes behind.",
  "Compositor patch is staged, not installed.",
];

export const TRANSIT = {
  line: "Line 4",
  status: "6 min behind",
  stop: "Harbor",
  walk: "4 min walk",
};

export const TRACKS = [
  { title: "Glass District", artist: "Low Orbit", length: "3:42" },
  { title: "Sodium Lights", artist: "Kite Museum", length: "4:05" },
  { title: "Afterimage", artist: "North Channel", length: "2:58" },
];

export const THREADS = [
  {
    id: "avery",
    name: "Avery Chen",
    lines: ["Shade tiles feel fast.", "Can the HUD stay behind one switch?"],
  },
  {
    id: "ren",
    name: "Ren Ito",
    lines: ["Amber is up.", "Quieter than magenta on the brief."],
  },
  {
    id: "line",
    name: "Line 4",
    lines: ["Delay holding at 6 min.", "Harbor stop, platform 2."],
  },
  {
    id: "mina",
    name: "Mina Park",
    lines: ["Notes should keep the draft if you leave."],
  },
];

export const MAIL = [
  {
    id: "m1",
    from: "North Channel",
    subject: "Overlay notes",
    body: "Keep the float chip inside the screen. Brackets can stay on. Scanlines should default off so the brief stays readable.",
  },
  {
    id: "m2",
    from: "Transit desk",
    subject: "Line 4",
    body: "Platform 2’s sensor is noisy after 21:00. The six-minute delay is real, not a display glitch.",
  },
  {
    id: "m3",
    from: "Studio",
    subject: "Dock review",
    body: "Four icons is the right density. Labels can be optional. Presets should not wipe the accent.",
  },
];

export const PEOPLE = [
  { name: "Avery Chen", role: "Studio", number: "555-0142" },
  { name: "Ren Ito", role: "Color", number: "555-0177" },
  { name: "Mina Park", role: "Notes", number: "555-0118" },
  { name: "Harbor Desk", role: "Transit", number: "555-0190" },
];

export const RECENTS = [
  { name: "Avery Chen", meta: "Outgoing · 4m" },
  { name: "Harbor Desk", meta: "Missed · 1h" },
  { name: "Mina Park", meta: "Incoming · yesterday" },
];

export const FILES = [
  { name: "overlay-notes.txt", meta: "2 KB · today" },
  { name: "dock-preset.json", meta: "1 KB · today" },
  { name: "brief-modules.md", meta: "4 KB · yesterday" },
  { name: "night-cycle.log", meta: "12 KB · yesterday" },
];

export const PAGES = [
  { title: "Harbor status", detail: "Wind, tide, and the grid cycle for the waterfront." },
  { title: "Line map", detail: "Four lines. Line 4 is the only delay." },
  { title: "Patch log", detail: "Compositor 4.2 is staged, not installed." },
  { title: "Studio board", detail: "Dock, shade, and overlay tasks." },
];

export const WALLET = [
  { name: "Transit pass", value: "$42.10", meta: "Line 4 · auto" },
  { name: "Studio card", value: "$128.40", meta: "Ends 09/28" },
  { name: "Cash", value: "$36.00", meta: "On hand" },
];

export const HOURS = [
  { t: "06", v: "14°" },
  { t: "09", v: "16°" },
  { t: "12", v: "18°" },
  { t: "15", v: "19°" },
  { t: "18", v: "17°" },
  { t: "21", v: "15°" },
];

export const ZONES: { label: string; timeZone?: string }[] = [
  { label: "Local" },
  { label: "London", timeZone: "Europe/London" },
  { label: "Tokyo", timeZone: "Asia/Tokyo" },
  { label: "New York", timeZone: "America/New_York" },
];

export const PLACES = [
  { name: "Harbor stop", meta: "4 min walk · Line 4" },
  { name: "Studio", meta: "12 min · indoor" },
  { name: "Room 4", meta: "Same building · design review" },
];

export const PRESETS: { id: PresetId; name: string; page: PageId; detail: string }[] = [
  { id: "briefing", name: "Briefing", page: "brief", detail: "Modules, ticker, and corner brackets." },
  { id: "classic", name: "Classic", page: "apps", detail: "Icons only. Overlays off." },
  { id: "glass", name: "Glass", page: "deck", detail: "Full HUD, compact grid, strong glow." },
];

export const DEFAULT_NOTE = `Launcher notes
- Keep the brief to the modules you actually check.
- Park the next-event chip where your thumb doesn't live.
- Shade tiles are the fastest overlays.`;

export function densityCols(density: "compact" | "regular" | "roomy") {
  if (density === "compact") return "grid-cols-5";
  if (density === "roomy") return "grid-cols-3";
  return "grid-cols-4";
}
