import { create } from "zustand";
import { persist } from "zustand/middleware";
import { briefContext, contextFor, reply, type AssistSnap } from "@/lib/launcher/assist";
import { APPS, DEFAULT_NOTE, PRESETS, TRACKS, isAi } from "@/lib/launcher/catalog";
import type {
  Accent,
  AiId,
  AiTurn,
  AskContext,
  CallState,
  Density,
  Glow,
  Layer,
  MusicState,
  PageId,
  PresetId,
  Pulse,
  QuickKey,
  Settings,
  Task,
} from "@/lib/launcher/types";
import { PAGE_ORDER } from "@/lib/launcher/types";

export type LauncherState = {
  settings: Settings;
  dismissed: string[];
  note: string;
  tasks: Task[];
  music: MusicState;
  shots: string[];
  pageId: PageId;
  layer: Layer;
  appId: string | null;
  peekId: string | null;
  call: CallState;
  dockNote: string;
  beat: number;
  pulse: Pulse;
  focus: AskContext | null;
  ask: { context: AskContext; returnApp: string | null } | null;
  handoff: AskContext | null;
  threads: Partial<Record<AiId, AiTurn[]>>;
  patch: (partial: Partial<Settings>) => void;
  patchWidgets: (partial: Partial<Settings["widgets"]>) => void;
  patchOverlays: (partial: Partial<Settings["overlays"]>) => void;
  setPageEnabled: (id: PageId, on: boolean) => void;
  toggleQuick: (key: QuickKey) => void;
  toggleOverlay: () => void;
  setChip: (x: number, y: number) => void;
  toggleDock: (id: string) => void;
  moveDock: (index: number, dir: -1 | 1) => void;
  applyPreset: (id: PresetId) => void;
  dismissHint: () => void;
  showHint: () => void;
  reset: () => void;
  dismiss: (id: string) => void;
  clearAlerts: () => void;
  setNote: (note: string) => void;
  toggleTask: (id: string) => void;
  togglePlay: () => void;
  skip: (dir: 1 | -1) => void;
  playTrack: (track: number) => void;
  addShot: (label: string) => void;
  startCall: (name: string) => void;
  endCall: () => void;
  setLayer: (layer: Layer) => void;
  openApp: (id: string) => void;
  openAskFromApp: () => void;
  openAskAbout: (context: AskContext, returnApp: string | null) => void;
  chooseAssistant: (id: AiId) => void;
  sendToAssistant: (id: AiId, prompt: string) => void;
  setFocus: (focus: AskContext | null) => void;
  setPeek: (id: string | null) => void;
  closeTop: () => void;
  setPage: (pageId: PageId) => void;
  cycle: (dir: 1 | -1) => void;
  tickPulse: () => void;
};

const ACCENTS: Accent[] = ["ion", "signal", "amber", "acid"];
const DENSITIES: Density[] = ["compact", "regular", "roomy"];
const GLOWS: Glow[] = ["off", "low", "high"];

function defaultTasks(): Task[] {
  return [
    { id: "t1", title: "Tune overlay opacity", done: true },
    { id: "t2", title: "Pin transit on the brief", done: false },
    { id: "t3", title: "Review shade tile order", done: false },
    { id: "t4", title: "Keep the dock at four icons", done: false },
  ];
}

export function defaultSettings(): Settings {
  return {
    deviceName: "rift-01",
    accent: "ion",
    density: "regular",
    clockFormat: "24",
    showSeconds: true,
    statusStyle: "full",
    glow: "low",
    showLabels: true,
    motion: true,
    hintSeen: false,
    pages: { brief: true, apps: true, deck: true },
    widgets: {
      weather: true,
      agenda: true,
      comms: true,
      transit: true,
      vitals: true,
      headlines: true,
    },
    overlays: {
      enabled: true,
      brackets: true,
      scanline: false,
      grid: false,
      ticker: true,
      chip: true,
    },
    chip: { x: 58, y: 15 },
    dock: ["phone", "messages", "browser", "camera"],
    defaultAi: "relay",
    quick: {
      wifi: true,
      bluetooth: true,
      dnd: false,
      flashlight: false,
      rotation: false,
      hotspot: false,
      airplane: false,
    },
  };
}

function layoutFromPreset(id: PresetId, base: Settings): Settings {
  if (id === "classic") {
    return {
      ...base,
      density: "regular",
      glow: "off",
      showLabels: true,
      statusStyle: "full",
      pages: { brief: false, apps: true, deck: false },
      widgets: {
        weather: false,
        agenda: false,
        comms: false,
        transit: false,
        vitals: false,
        headlines: false,
      },
      overlays: {
        enabled: false,
        brackets: false,
        scanline: false,
        grid: false,
        ticker: false,
        chip: false,
      },
      dock: ["phone", "messages", "browser", "camera"],
    };
  }
  if (id === "glass") {
    return {
      ...base,
      density: "compact",
      glow: "high",
      showLabels: false,
      statusStyle: "compact",
      pages: { brief: true, apps: true, deck: true },
      widgets: {
        weather: true,
        agenda: true,
        comms: true,
        transit: true,
        vitals: true,
        headlines: true,
      },
      overlays: {
        enabled: true,
        brackets: true,
        scanline: true,
        grid: true,
        ticker: true,
        chip: true,
      },
      dock: ["relay", "maps", "music", "calendar"],
    };
  }
  return {
    ...base,
    density: "regular",
    glow: "low",
    showLabels: true,
    statusStyle: "full",
    pages: { brief: true, apps: true, deck: true },
    widgets: {
      weather: true,
      agenda: true,
      comms: true,
      transit: true,
      vitals: true,
      headlines: true,
    },
    overlays: {
      enabled: true,
      brackets: true,
      scanline: false,
      grid: false,
      ticker: true,
      chip: true,
    },
    dock: ["phone", "messages", "browser", "camera"],
  };
}

export function visiblePages(settings: Settings): PageId[] {
  const on = PAGE_ORDER.filter((id) => settings.pages[id]);
  return on.length > 0 ? on : ["brief"];
}

const zeroPulse = (): Pulse => ({ cpu: 22, mem: 48, temp: 34, up: 86 });

function snapOf(s: { note: string; pulse: Pulse; focus: AskContext | null; music: MusicState; shots: string[] }): AssistSnap {
  return { note: s.note, pulse: s.pulse, focus: s.focus, musicTrack: s.music.track, shots: s.shots };
}

function cleanThreads(value: unknown): Partial<Record<AiId, AiTurn[]>> {
  if (!value || typeof value !== "object") return {};
  const out: Partial<Record<AiId, AiTurn[]>> = {};
  for (const id of ["relay", "draft", "trace"] as const) {
    const turns = (value as Record<string, unknown>)[id];
    if (!Array.isArray(turns)) continue;
    const clean = turns
      .filter((turn): turn is AiTurn => {
        if (!turn || typeof turn !== "object") return false;
        const row = turn as AiTurn;
        return (row.role === "user" || row.role === "ai") && typeof row.text === "string";
      })
      .slice(-20);
    if (clean.length) out[id] = clean;
  }
  return out;
}

export const useLauncher = create<LauncherState>()(
  persist(
    (set, get) => ({
      settings: defaultSettings(),
      dismissed: [],
      note: DEFAULT_NOTE,
      tasks: defaultTasks(),
      music: { playing: false, track: 0 },
      shots: [],
      pageId: "brief",
      layer: null,
      appId: null,
      peekId: null,
      call: null,
      dockNote: "",
      beat: 0,
      pulse: zeroPulse(),
      focus: null,
      ask: null,
      handoff: null,
      threads: {},
      patch: (partial) => set((s) => ({ settings: { ...s.settings, ...partial } })),
      patchWidgets: (partial) =>
        set((s) => ({
          settings: { ...s.settings, widgets: { ...s.settings.widgets, ...partial } },
        })),
      patchOverlays: (partial) =>
        set((s) => ({
          settings: { ...s.settings, overlays: { ...s.settings.overlays, ...partial } },
        })),
      setPageEnabled: (id, on) =>
        set((s) => {
          const pages = { ...s.settings.pages, [id]: on };
          if (!pages.brief && !pages.apps && !pages.deck) return s;
          const pageId = pages[s.pageId] ? s.pageId : (PAGE_ORDER.find((p) => pages[p]) ?? "brief");
          return { settings: { ...s.settings, pages }, pageId };
        }),
      toggleQuick: (key) =>
        set((s) => {
          const quick = { ...s.settings.quick, [key]: !s.settings.quick[key] };
          if (key === "airplane" && quick.airplane) {
            quick.wifi = false;
            quick.hotspot = false;
          }
          if ((key === "wifi" || key === "hotspot") && quick[key]) quick.airplane = false;
          return { settings: { ...s.settings, quick } };
        }),
      toggleOverlay: () =>
        set((s) => ({
          settings: {
            ...s.settings,
            overlays: { ...s.settings.overlays, enabled: !s.settings.overlays.enabled },
          },
        })),
      setChip: (x, y) =>
        set((s) => ({ settings: { ...s.settings, chip: { x, y } } })),
      toggleDock: (id) =>
        set((s) => {
          const has = s.settings.dock.includes(id);
          if (has) {
            return {
              settings: { ...s.settings, dock: s.settings.dock.filter((d) => d !== id) },
              dockNote: "",
            };
          }
          if (s.settings.dock.length >= 4) {
            return { dockNote: "Dock holds four. Remove one first." };
          }
          return {
            settings: { ...s.settings, dock: [...s.settings.dock, id] },
            dockNote: "",
          };
        }),
      moveDock: (index, dir) =>
        set((s) => {
          const dock = [...s.settings.dock];
          const j = index + dir;
          if (j < 0 || j >= dock.length) return s;
          const current = dock[index];
          const swap = dock[j];
          if (current === undefined || swap === undefined) return s;
          dock[index] = swap;
          dock[j] = current;
          return { settings: { ...s.settings, dock } };
        }),
      applyPreset: (id) =>
        set((s) => ({
          settings: layoutFromPreset(id, s.settings),
          pageId: PRESETS.find((p) => p.id === id)?.page ?? "brief",
          dockNote: "",
          peekId: null,
        })),
      dismissHint: () => set((s) => ({ settings: { ...s.settings, hintSeen: true } })),
      showHint: () => set((s) => ({ settings: { ...s.settings, hintSeen: false } })),
      reset: () =>
        set({
          settings: defaultSettings(),
          dismissed: [],
          note: DEFAULT_NOTE,
          tasks: defaultTasks(),
          music: { playing: false, track: 0 },
          shots: [],
          pageId: "brief",
          dockNote: "",
          layer: null,
          call: null,
          peekId: null,
          appId: null,
          focus: null,
          ask: null,
          handoff: null,
          threads: {},
        }),
      dismiss: (id) =>
        set((s) => ({
          dismissed: s.dismissed.includes(id) ? s.dismissed : [...s.dismissed, id],
        })),
      clearAlerts: () => set({ dismissed: ["n1", "n2", "n3", "n4"] }),
      setNote: (note) => set({ note }),
      toggleTask: (id) =>
        set((s) => ({
          tasks: s.tasks.map((task) => (task.id === id ? { ...task, done: !task.done } : task)),
        })),
      togglePlay: () => set((s) => ({ music: { ...s.music, playing: !s.music.playing } })),
      skip: (dir) =>
        set((s) => ({
          music: {
            playing: true,
            track: (s.music.track + dir + TRACKS.length) % TRACKS.length,
          },
        })),
      playTrack: (track) => set({ music: { playing: true, track } }),
      addShot: (label) => set((s) => ({ shots: [label, ...s.shots].slice(0, 12) })),
      startCall: (name) =>
        set({ call: { name, startedAt: Date.now() }, layer: null, peekId: null }),
      endCall: () => set({ call: null }),
      setLayer: (layer) => set({ layer, peekId: null }),
      openApp: (id) => set({ appId: id, layer: "app", peekId: null, handoff: null, focus: null }),
      openAskFromApp: () => {
        const s = get();
        const context = s.appId ? contextFor(s.appId, snapOf(s)) : briefContext(snapOf(s));
        set({ ask: { context, returnApp: s.appId }, layer: "ask", peekId: null });
      },
      openAskAbout: (context, returnApp) => set({ ask: { context, returnApp }, layer: "ask", peekId: null }),
      chooseAssistant: (id) =>
        set((s) => ({
          appId: id,
          layer: "app",
          peekId: null,
          handoff: s.ask?.context ?? null,
          ask: null,
        })),
      sendToAssistant: (id, prompt) => {
        const text = prompt.trim();
        if (!text) return;
        const s = get();
        const answer = reply(id, text, s.handoff, snapOf(s));
        const prev = s.threads[id] ?? [];
        const next = [...prev, { role: "user" as const, text }, { role: "ai" as const, text: answer }].slice(-20);
        set({ threads: { ...s.threads, [id]: next } });
      },
      setFocus: (focus) => set({ focus }),
      setPeek: (id) => set({ peekId: id }),
      closeTop: () =>
        set((s) => {
          if (s.peekId) return { peekId: null };
          if (s.layer === "ask") {
            const back = s.ask?.returnApp;
            return { layer: back ? "app" : null, appId: back ?? s.appId, ask: null };
          }
          if (s.layer) return { layer: null };
          return s;
        }),
      setPage: (pageId) => set({ pageId, peekId: null }),
      cycle: (dir) => {
        const s = get();
        if (s.layer) return;
        const pages = visiblePages(s.settings);
        const index = Math.max(0, pages.indexOf(s.pageId));
        const next = pages[(index + dir + pages.length) % pages.length];
        if (!next) return;
        set({ pageId: next, peekId: null });
      },
      tickPulse: () =>
        set((s) => {
          const n = s.beat + 1;
          return {
            beat: n,
            pulse: {
              cpu: 22 + ((n * 9) % 28),
              mem: 48 + ((n * 5) % 14),
              temp: 34 + ((n * 3) % 7),
              up: 86 + ((n * 11) % 40),
            },
          };
        }),
    }),
    {
      name: "rift-launcher-v1",
      skipHydration: true,
      partialize: (s) => ({
        settings: s.settings,
        dismissed: s.dismissed,
        note: s.note,
        tasks: s.tasks,
        music: s.music,
        shots: s.shots,
        pageId: s.pageId,
        threads: s.threads,
      }),
      merge: (persisted, current) => {
        const p = (persisted ?? {}) as Partial<LauncherState>;
        const settings: Settings = {
          ...current.settings,
          ...(p.settings ?? {}),
          widgets: { ...current.settings.widgets, ...(p.settings?.widgets ?? {}) },
          overlays: { ...current.settings.overlays, ...(p.settings?.overlays ?? {}) },
          pages: { ...current.settings.pages, ...(p.settings?.pages ?? {}) },
          quick: { ...current.settings.quick, ...(p.settings?.quick ?? {}) },
          chip: { ...current.settings.chip, ...(p.settings?.chip ?? {}) },
        };
        if (!ACCENTS.includes(settings.accent)) settings.accent = "ion";
        if (!DENSITIES.includes(settings.density)) settings.density = "regular";
        if (!GLOWS.includes(settings.glow)) settings.glow = "low";
        if (settings.clockFormat !== "12" && settings.clockFormat !== "24") settings.clockFormat = "24";
        if (settings.statusStyle !== "full" && settings.statusStyle !== "compact") {
          settings.statusStyle = "full";
        }
        if (typeof settings.deviceName !== "string" || !settings.deviceName.trim()) {
          settings.deviceName = current.settings.deviceName;
        }
        settings.deviceName = settings.deviceName.slice(0, 18);
        settings.dock = (Array.isArray(settings.dock) ? settings.dock : [])
          .filter((id) => APPS.some((app) => app.id === id))
          .slice(0, 4);
        if (!isAi(settings.defaultAi)) settings.defaultAi = "relay";
        const pageId: PageId =
          p.pageId === "brief" || p.pageId === "apps" || p.pageId === "deck" ? p.pageId : current.pageId;
        return {
          ...current,
          settings,
          dismissed: Array.isArray(p.dismissed)
            ? p.dismissed.filter((id) => typeof id === "string")
            : current.dismissed,
          note: typeof p.note === "string" ? p.note : current.note,
          tasks: Array.isArray(p.tasks) ? p.tasks : current.tasks,
          music:
            p.music && typeof p.music.track === "number" && typeof p.music.playing === "boolean"
              ? p.music
              : current.music,
          shots: Array.isArray(p.shots)
            ? p.shots.filter((shot) => typeof shot === "string").slice(0, 12)
            : current.shots,
          threads: cleanThreads(p.threads),
          pageId,
        };
      },
    },
  ),
);
