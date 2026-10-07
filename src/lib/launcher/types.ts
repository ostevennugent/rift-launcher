export type Accent = "ion" | "signal" | "amber" | "acid";
export type Density = "compact" | "regular" | "roomy";
export type PageId = "brief" | "apps" | "deck";
export type Glow = "off" | "low" | "high";
export type PresetId = "briefing" | "classic" | "glass";
export type QuickKey =
  | "wifi"
  | "bluetooth"
  | "dnd"
  | "flashlight"
  | "rotation"
  | "hotspot"
  | "airplane";

export type Layer = null | "shade" | "drawer" | "settings" | "app" | "ask";
export type AiId = "relay" | "draft" | "trace";

export type AskContext = {
  appId: string;
  title: string;
  body: string;
};

export type AiTurn = { role: "user" | "ai"; text: string };

export type Settings = {
  deviceName: string;
  accent: Accent;
  density: Density;
  clockFormat: "12" | "24";
  showSeconds: boolean;
  statusStyle: "full" | "compact";
  glow: Glow;
  showLabels: boolean;
  motion: boolean;
  hintSeen: boolean;
  pages: Record<PageId, boolean>;
  widgets: {
    weather: boolean;
    agenda: boolean;
    comms: boolean;
    transit: boolean;
    vitals: boolean;
    headlines: boolean;
  };
  overlays: {
    enabled: boolean;
    brackets: boolean;
    scanline: boolean;
    grid: boolean;
    ticker: boolean;
    chip: boolean;
  };
  chip: { x: number; y: number };
  dock: string[];
  defaultAi: AiId;
  quick: Record<QuickKey, boolean>;
};

export type Task = { id: string; title: string; done: boolean };
export type MusicState = { playing: boolean; track: number };
export type CallState = { name: string; startedAt: number } | null;
export type Pulse = { cpu: number; mem: number; temp: number; up: number };

export const PAGE_ORDER: PageId[] = ["brief", "apps", "deck"];

export const PAGE_LABEL: Record<PageId, string> = {
  brief: "Brief",
  apps: "Apps",
  deck: "Deck",
};
