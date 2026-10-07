import { useEffect, useRef, useState } from "react";
import {
  Battery,
  Bell,
  BellOff,
  Bluetooth,
  Flashlight,
  LayoutGrid,
  Phone,
  PhoneOff,
  Plane,
  Radio,
  RotateCw,
  Signal,
  Wifi,
  WifiOff,
  X,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { AGENDA, APPS, AI_APPS, NOTIFICATIONS, appById, densityCols, isAi } from "@/lib/launcher/catalog";
import { briefContext } from "@/lib/launcher/assist";
import { formatClock, formatDuration, useNow } from "@/lib/launcher/clock";
import { PAGE_LABEL, type PageId, type QuickKey } from "@/lib/launcher/types";
import { useLauncher, visiblePages } from "@/lib/launcher/store";
import { AppIcon, Kicker, PanelFrame, press } from "@/components/launcher/bits";
import { AppsPage, BriefPage, DeckPage } from "@/components/launcher/HomePages";
import { AppView } from "@/components/launcher/AppView";
import { AskSheet } from "@/components/launcher/Assist";
import { SettingsPanel } from "@/components/launcher/SettingsPanel";
import { cn } from "@/lib/cn";

const TILES: { key: QuickKey; label: string; icon: LucideIcon }[] = [
  { key: "airplane", label: "Airplane", icon: Plane },
  { key: "wifi", label: "Wi-Fi", icon: Wifi },
  { key: "bluetooth", label: "Bluetooth", icon: Bluetooth },
  { key: "dnd", label: "Silent", icon: BellOff },
  { key: "flashlight", label: "Light", icon: Flashlight },
  { key: "rotation", label: "Rotate", icon: RotateCw },
  { key: "hotspot", label: "Hotspot", icon: Radio },
];

function StatusBar() {
  const settings = useLauncher((s) => s.settings);
  const layer = useLauncher((s) => s.layer);
  const setLayer = useLauncher((s) => s.setLayer);
  const dismissed = useLauncher((s) => s.dismissed);
  const now = useNow();
  const time = now ? formatClock(now, settings.clockFormat, false) : "––:––";
  const quick = settings.quick;
  const open = layer === "shade";
  const count = NOTIFICATIONS.filter((n) => !dismissed.includes(n.id)).length;
  return (
    <button
      type="button"
      aria-expanded={open}
      aria-label="Open controls"
      onClick={() => setLayer(open ? null : "shade")}
      className={cn("flex h-12 shrink-0 items-center gap-2 border-b border-border px-3", open && "border-primary")}
    >
      <span className="font-mono text-xs tabular-nums">{time}</span>
      {settings.statusStyle === "full" ? (
        <span className="flex-1 text-center font-mono text-xs tracking-widest text-primary">RIFT</span>
      ) : (
        <span className="flex-1" />
      )}
      <span className="flex items-center gap-1.5">
        {!quick.dnd && count > 0 ? <span className="font-mono text-xs text-hot tabular-nums">{count}</span> : null}
        {quick.dnd ? <BellOff className="size-3.5 text-muted" /> : null}
        {quick.flashlight ? <Flashlight className="size-3.5 text-primary" /> : null}
        {quick.airplane ? <Plane className="size-3.5" /> : quick.wifi ? <Wifi className="size-3.5" /> : <WifiOff className="size-3.5 text-muted" />}
        {settings.statusStyle === "full" && !quick.airplane ? <Signal className="size-3.5" /> : null}
        <Battery className="size-3.5" />
        <span className="font-mono text-xs tabular-nums">76</span>
      </span>
    </button>
  );
}

function CallBar() {
  const call = useLauncher((s) => s.call);
  const endCall = useLauncher((s) => s.endCall);
  const now = useNow();
  if (!call) return null;
  const elapsed = now ? Math.floor((now.getTime() - call.startedAt) / 1000) : 0;
  return (
    <div className="flex h-12 shrink-0 items-center gap-2 border-b border-border bg-surface px-3">
      <Phone className="size-4 text-primary" />
      <span className="min-w-0 flex-1 truncate text-sm">{call.name}</span>
      <span className="font-mono text-xs text-muted tabular-nums">{formatDuration(elapsed)}</span>
      <button type="button" onClick={endCall} aria-label="End call" className={cn("flex h-11 items-center gap-1 bg-hot px-3 text-bg", press)}>
        <PhoneOff className="size-4" />
        <span className="font-mono text-xs uppercase">End</span>
      </button>
    </div>
  );
}

function Dock() {
  const settings = useLauncher((s) => s.settings);
  const layer = useLauncher((s) => s.layer);
  const setLayer = useLauncher((s) => s.setLayer);
  const apps = settings.dock.map((id) => appById(id)).filter((app) => app != null);
  const open = layer === "drawer";
  return (
    <nav aria-label="Dock" className="shrink-0 border-t border-border px-1 py-1">
      <div
        className="grid gap-1"
        style={{ gridTemplateColumns: `repeat(${apps.length + 1}, minmax(0, 1fr))` }}
      >
        {apps.map((app) => (
          <AppIcon key={app.id} app={app} labeled={settings.showLabels} />
        ))}
        <button
          type="button"
          aria-label="All apps"
          aria-expanded={open}
          onClick={() => setLayer(open ? null : "drawer")}
          className={cn("flex min-h-11 flex-col items-center justify-center gap-1", press)}
        >
          <span className="flex size-11 items-center justify-center border border-primary bg-surface text-primary">
            <LayoutGrid className="size-5" />
          </span>
          {settings.showLabels ? <span className="text-xs text-muted">All</span> : null}
        </button>
      </div>
    </nav>
  );
}

function Ticker() {
  const settings = useLauncher((s) => s.settings);
  const pulse = useLauncher((s) => s.pulse);
  if (!settings.overlays.enabled || !settings.overlays.ticker) return null;
  const line = [
    settings.deviceName || "rift",
    "Harbor clear 18°",
    `cpu ${pulse.cpu}%`,
    `mem ${pulse.mem}%`,
    `temp ${pulse.temp}°`,
    `uplink ${String(pulse.up).padStart(3, "0")} mb/s`,
    settings.quick.dnd ? "silent" : "alerts on",
    settings.quick.airplane ? "airplane" : settings.quick.wifi ? "wifi" : "wifi off",
  ].join("   ·   ");
  return (
    <div className={cn("overflow-hidden border-t border-border", !settings.motion && "overflow-x-auto")}>
      <div className="ticker-track">
        <span className="px-3 py-1 font-mono text-xs whitespace-nowrap text-muted">{line}</span>
        {settings.motion ? (
          <span aria-hidden className="px-3 py-1 font-mono text-xs whitespace-nowrap text-muted">
            {line}
          </span>
        ) : null}
      </div>
    </div>
  );
}

function PageDots({ pages, active }: { pages: PageId[]; active: PageId }) {
  const setPage = useLauncher((s) => s.setPage);
  const peek = useLauncher((s) => s.peekId);
  if (peek || pages.length < 2) return null;
  return (
    <div className="flex shrink-0 items-center justify-center border-t border-border">
      {pages.map((id) => (
        <button
          key={id}
          type="button"
          aria-label={`Show ${PAGE_LABEL[id]}`}
          aria-current={id === active ? "page" : undefined}
          onClick={() => setPage(id)}
          className="flex h-11 items-center gap-2 px-3"
        >
          <span className={cn("h-1.5", id === active ? "w-6 bg-primary" : "w-1.5 bg-border")} />
          <span className={cn("font-mono text-xs tracking-widest uppercase", id === active ? "text-primary" : "text-muted")}>
            {PAGE_LABEL[id]}
          </span>
        </button>
      ))}
    </div>
  );
}

function FloatChip() {
  const settings = useLauncher((s) => s.settings);
  const setChip = useLauncher((s) => s.setChip);
  const openApp = useLauncher((s) => s.openApp);
  const ref = useRef<HTMLButtonElement>(null);
  const drag = useRef<{ px: number; py: number; x: number; y: number; moved: boolean } | null>(null);
  if (!settings.overlays.enabled || !settings.overlays.chip) return null;
  const event = AGENDA[0];
  return (
    <button
      ref={ref}
      type="button"
      className="plate plate-mark absolute z-20 w-32 touch-none p-2 text-left"
      style={{ left: `${settings.chip.x}%`, top: `${settings.chip.y}%` }}
      aria-label={`Next event, ${event?.title ?? "event"} at ${event?.time ?? ""}. Drag to move.`}
      onPointerDown={(e) => {
        e.currentTarget.setPointerCapture(e.pointerId);
        drag.current = { px: e.clientX, py: e.clientY, x: settings.chip.x, y: settings.chip.y, moved: false };
      }}
      onPointerMove={(e) => {
        const d = drag.current;
        const parent = ref.current?.parentElement;
        if (!d || !parent || !ref.current) return;
        const rect = parent.getBoundingClientRect();
        const chip = ref.current.getBoundingClientRect();
        if (Math.hypot(e.clientX - d.px, e.clientY - d.py) > 6) d.moved = true;
        const nx = d.x + ((e.clientX - d.px) / rect.width) * 100;
        const ny = d.y + ((e.clientY - d.py) / rect.height) * 100;
        const maxX = Math.max(0, ((rect.width - chip.width) / rect.width) * 100);
        const maxY = Math.max(0, ((rect.height - chip.height) / rect.height) * 100);
        setChip(Math.min(maxX, Math.max(0, nx)), Math.min(maxY, Math.max(0, ny)));
      }}
      onPointerUp={() => {
        const moved = drag.current?.moved;
        drag.current = null;
        if (!moved) openApp("calendar");
      }}
    >
      <div className="font-mono text-xs tracking-widest text-primary uppercase">Next</div>
      <div className="truncate text-sm">{event?.title}</div>
      <div className="font-mono text-xs text-muted">{event?.time} · Room 4</div>
    </button>
  );
}

function PeekCard() {
  const peekId = useLauncher((s) => s.peekId);
  const setPeek = useLauncher((s) => s.setPeek);
  const openApp = useLauncher((s) => s.openApp);
  const app = peekId ? appById(peekId) : undefined;
  if (!app) return null;
  const Icon = app.icon;
  return (
    <div className="absolute inset-x-3 bottom-2 z-30">
      <div className="plate plate-mark p-3">
        <div className="flex items-start gap-3">
          <span className="flex size-11 shrink-0 items-center justify-center border border-border">
            <Icon className="size-5" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="text-sm">{app.name}</div>
            <p className="text-xs text-pretty text-muted">{app.blurb}</p>
          </div>
        </div>
        <div className="mt-3 flex gap-2">
          <button type="button" onClick={() => openApp(app.id)} className={cn("h-11 flex-1 bg-primary text-sm text-bg", press)}>
            Open
          </button>
          <button
            type="button"
            onClick={() =>
              useLauncher.getState().openAskAbout(
                { appId: app.id, title: app.name, body: app.blurb },
                null,
              )
            }
            className={cn("h-11 border border-border px-3 text-sm", press)}
          >
            Ask
          </button>
          <button type="button" onClick={() => setPeek(null)} className={cn("h-11 border border-border px-3 text-sm", press)}>
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

function Hud() {
  const overlays = useLauncher((s) => s.settings.overlays);
  return (
    <>
      {overlays.enabled && overlays.brackets ? (
        <div className="pointer-events-none absolute inset-0 z-20" aria-hidden>
          <span className="absolute top-0 left-0 size-2 border-t border-l border-primary" />
          <span className="absolute top-0 right-0 size-2 border-t border-r border-primary" />
          <span className="absolute bottom-0 left-0 size-2 border-b border-l border-primary" />
          <span className="absolute right-0 bottom-0 size-2 border-r border-b border-primary" />
        </div>
      ) : null}
      {overlays.enabled && overlays.scanline ? <div className="scanline pointer-events-none absolute inset-0 z-20" aria-hidden /> : null}
    </>
  );
}

function tileIcon(key: QuickKey, on: boolean, Icon: LucideIcon) {
  if (key === "wifi" && !on) return WifiOff;
  if (key === "dnd" && !on) return Bell;
  return Icon;
}

function Shade() {
  const open = useLauncher((s) => s.layer === "shade");
  const setLayer = useLauncher((s) => s.setLayer);
  const quick = useLauncher((s) => s.settings.quick);
  const overlaysOn = useLauncher((s) => s.settings.overlays.enabled);
  const toggleQuick = useLauncher((s) => s.toggleQuick);
  const toggleOverlay = useLauncher((s) => s.toggleOverlay);
  const dismissed = useLauncher((s) => s.dismissed);
  const dismiss = useLauncher((s) => s.dismiss);
  const clearAlerts = useLauncher((s) => s.clearAlerts);
  const openApp = useLauncher((s) => s.openApp);
  const alerts = NOTIFICATIONS.filter((n) => !dismissed.includes(n.id));
  return (
    <PanelFrame open={open} label="Controls" title="Controls" onClose={() => setLayer(null)} origin="top">
      <div className="px-3 pt-4 pb-8">
        <div className="grid grid-cols-4 gap-2">
          {TILES.map((tile) => {
            const on = quick[tile.key];
            const Icon = tileIcon(tile.key, on, tile.icon);
            return (
              <button
                key={tile.key}
                type="button"
                aria-pressed={on}
                onClick={() => toggleQuick(tile.key)}
                className={cn(
                  "flex h-16 flex-col items-center justify-center gap-1 border text-xs",
                  press,
                  on ? "border-primary bg-primary text-bg" : "border-border bg-bg text-muted",
                )}
              >
                <Icon className="size-4" />
                {tile.label}
              </button>
            );
          })}
          <button
            type="button"
            aria-pressed={overlaysOn}
            onClick={toggleOverlay}
            className={cn(
              "flex h-16 flex-col items-center justify-center gap-1 border text-xs",
              press,
              overlaysOn ? "border-primary bg-primary text-bg" : "border-border bg-bg text-muted",
            )}
          >
            <span className="size-3 border border-current" />
            HUD
          </button>
        </div>
        <button
          type="button"
          onClick={() => {
            const state = useLauncher.getState();
            state.openAskAbout(
              briefContext({
                note: state.note,
                pulse: state.pulse,
                focus: null,
                musicTrack: state.music.track,
                shots: state.shots,
              }),
              null,
            );
          }}
          className={cn("mt-4 h-11 w-full bg-primary text-sm text-bg", press)}
        >
          Ask about this screen
        </button>
        <div className="mt-6 flex items-center justify-between">
          <Kicker>Alerts</Kicker>
          {alerts.length > 0 ? (
            <button type="button" onClick={clearAlerts} className="h-11 px-2 font-mono text-xs text-muted">
              Clear
            </button>
          ) : null}
        </div>
        {quick.dnd ? <p className="mt-2 font-mono text-xs text-muted">Silent is on. Alerts stay listed.</p> : null}
        {alerts.length === 0 ? <p className="mt-3 text-sm text-muted">Nothing waiting.</p> : null}
        <ul>
          {alerts.map((alert) => {
            const app = appById(alert.appId);
            return (
              <li key={alert.id} className="flex gap-2 border-b border-border py-3">
                <button type="button" onClick={() => openApp(alert.appId)} className="min-w-0 flex-1 text-left">
                  <span className="flex items-baseline justify-between gap-2">
                    <span className="font-mono text-xs tracking-widest text-muted uppercase">{app?.name ?? "App"}</span>
                    <span className="font-mono text-xs text-muted">{alert.time}</span>
                  </span>
                  <span className="mt-1 block text-sm">{alert.title}</span>
                  <span className="block text-sm text-pretty text-muted">{alert.body}</span>
                </button>
                <button type="button" aria-label={`Dismiss ${alert.title}`} onClick={() => dismiss(alert.id)} className="flex size-11 items-center justify-center">
                  <X className="size-4" />
                </button>
              </li>
            );
          })}
        </ul>
        <button
          type="button"
          onClick={() => setLayer("settings")}
          className={cn("mt-4 h-11 w-full border border-border text-sm", press)}
        >
          Launcher settings
        </button>
      </div>
    </PanelFrame>
  );
}

function Drawer() {
  const open = useLauncher((s) => s.layer === "drawer");
  const setLayer = useLauncher((s) => s.setLayer);
  const density = useLauncher((s) => s.settings.density);
  const labeled = useLauncher((s) => s.settings.showLabels);
  const [query, setQuery] = useState("");
  useEffect(() => {
    if (!open) setQuery("");
  }, [open]);
  const q = query.trim().toLowerCase();
  const list = APPS.filter((app) => app.name.toLowerCase().includes(q));
  return (
    <PanelFrame open={open} label="All apps" title="Apps" onClose={() => setLayer(null)} origin="bottom" scroll={false}>
      <div className="scroll min-h-0 flex-1 overflow-y-auto px-3 pt-3 pb-8">
        <label className="sr-only" htmlFor="rift-search">
          Search apps
        </label>
        <input
          id="rift-search"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search apps"
          className="mb-3 h-11 w-full border border-border bg-surface px-3 text-base outline-none placeholder:text-muted focus-visible:border-primary"
        />
        {list.length === 0 ? <p className="text-sm text-muted">No apps match.</p> : null}
        {q ? (
          <div className={cn("grid gap-1", densityCols(density))}>
            {list.map((app) => (
              <AppIcon key={app.id} app={app} labeled={labeled} allowPeek={false} />
            ))}
          </div>
        ) : (
          <>
            <div className="mb-2">
              <Kicker>Assistants</Kicker>
            </div>
            <div className={cn("grid gap-1", densityCols(density))}>
              {AI_APPS.map((app) => (
                <AppIcon key={app.id} app={app} labeled={labeled} allowPeek={false} />
              ))}
            </div>
            <div className="mt-4 mb-2">
              <Kicker>Device</Kicker>
            </div>
            <div className={cn("grid gap-1", densityCols(density))}>
              {list
                .filter((app) => !isAi(app.id))
                .map((app) => (
                  <AppIcon key={app.id} app={app} labeled={labeled} allowPeek={false} />
                ))}
            </div>
          </>
        )}
      </div>
    </PanelFrame>
  );
}

function GridLayer() {
  const on = useLauncher((s) => s.settings.overlays.enabled && s.settings.overlays.grid);
  if (!on) return null;
  return (
    <div
      aria-hidden
      className="pointer-events-none absolute inset-0"
      style={{
        backgroundImage:
          "linear-gradient(var(--color-border) 1px, transparent 1px), linear-gradient(90deg, var(--color-border) 1px, transparent 1px)",
        backgroundSize: "24px 24px",
      }}
    />
  );
}

export function Launcher() {
  const settings = useLauncher((s) => s.settings);
  const pageId = useLauncher((s) => s.pageId);
  const cycle = useLauncher((s) => s.cycle);
  const [ready, setReady] = useState(false);
  const start = useRef<{ x: number; y: number } | null>(null);

  useEffect(() => {
    void Promise.resolve(useLauncher.persist.rehydrate()).finally(() => setReady(true));
  }, []);

  useEffect(() => {
    if (!ready) return;
    const root = document.documentElement;
    root.dataset.accent = settings.accent;
    root.dataset.glow = settings.glow;
    root.dataset.motion = settings.motion ? "on" : "off";
  }, [ready, settings.accent, settings.glow, settings.motion]);

  useEffect(() => {
    const id = window.setInterval(() => useLauncher.getState().tickPulse(), 1800);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement | null)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA") {
        if (e.key === "Escape") (e.target as HTMLElement).blur();
        return;
      }
      const state = useLauncher.getState();
      if (e.key === "Escape") {
        state.closeTop();
        return;
      }
      if (state.layer || state.peekId) return;
      if (e.key === "ArrowRight") state.cycle(1);
      if (e.key === "ArrowLeft") state.cycle(-1);
      if (e.key === "ArrowDown") state.setLayer("shade");
      if (e.key === "ArrowUp") state.setLayer("drawer");
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const pages = visiblePages(settings);
  const active = pages.includes(pageId) ? pageId : (pages[0] ?? "brief");
  const index = Math.max(0, pages.indexOf(active));

  return (
    <div className="stage flex h-dvh justify-center text-fg">
      <main aria-label="RIFT launcher" className="shell relative flex h-full w-full max-w-md flex-col overflow-hidden border-x border-border bg-bg">
        <StatusBar />
        <CallBar />
        <div
          className="relative min-h-0 flex-1 overflow-hidden"
          onPointerDown={(e) => {
            const target = e.target as HTMLElement;
            if (target.closest("button, a, input, textarea, label")) return;
            start.current = { x: e.clientX, y: e.clientY };
          }}
          onPointerUp={(e) => {
            if (!start.current) return;
            const dx = e.clientX - start.current.x;
            const dy = e.clientY - start.current.y;
            start.current = null;
            if (Math.abs(dx) < 64 || Math.abs(dx) < Math.abs(dy) * 1.25) return;
            cycle(dx < 0 ? 1 : -1);
          }}
        >
          <GridLayer />
          <div
            className="motion-shift relative z-10 flex h-full"
            style={{
              width: `${pages.length * 100}%`,
              transform: `translateX(-${(100 / pages.length) * index}%)`,
            }}
          >
            {pages.map((id) => (
              <section
                key={id}
                aria-label={PAGE_LABEL[id]}
                className="scroll h-full min-h-0 overflow-x-hidden overflow-y-auto"
                style={{ width: `${100 / pages.length}%` }}
                inert={id !== active}
              >
                {id === "brief" ? <BriefPage /> : null}
                {id === "apps" ? <AppsPage /> : null}
                {id === "deck" ? <DeckPage /> : null}
              </section>
            ))}
          </div>
          <FloatChip />
          <PeekCard />
        </div>
        <PageDots pages={pages} active={active} />
        <Ticker />
        <Dock />
        <Hud />
        <Shade />
        <Drawer />
        <AppView />
        <AskSheet />
        <SettingsPanel />
      </main>
    </div>
  );
}
