import { useEffect, useState } from "react";
import { Check, X } from "lucide-react";
import {
  AGENDA,
  AI_APPS,
  APPS,
  HEADLINES,
  NOTIFICATIONS,
  TRACKS,
  TRANSIT,
  densityCols,
} from "@/lib/launcher/catalog";
import { formatClock, formatDate, useNow } from "@/lib/launcher/clock";
import { useLauncher } from "@/lib/launcher/store";
import { cn } from "@/lib/cn";
import { AppIcon, Kicker, Meter, press } from "@/components/launcher/bits";

function Hint() {
  const seen = useLauncher((s) => s.settings.hintSeen);
  const dismiss = useLauncher((s) => s.dismissHint);
  if (seen) return null;
  return (
    <div className="flex items-center gap-2 border border-border bg-surface px-3">
      <p className="min-w-0 flex-1 py-2 text-pretty font-mono text-xs text-muted">
        Swipe for pages. Tap the status bar for controls. Hold an icon to preview.
      </p>
      <button
        type="button"
        aria-label="Dismiss hint"
        onClick={dismiss}
        className={cn("flex size-11 shrink-0 items-center justify-center", press)}
      >
        <X className="size-4" />
      </button>
    </div>
  );
}

function ClockBlock({ showWeather }: { showWeather: boolean }) {
  const settings = useLauncher((s) => s.settings);
  const openApp = useLauncher((s) => s.openApp);
  const now = useNow();
  const long = settings.showSeconds && settings.clockFormat === "12";
  return (
    <div className="min-w-0">
      <div
        className={cn(
          "clock-glow leading-none font-medium tracking-tight tabular-nums",
          long ? "text-4xl" : "text-5xl",
        )}
      >
        {now ? formatClock(now, settings.clockFormat, settings.showSeconds) : "––:––"}
      </div>
      <div className="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="font-mono text-xs tracking-widest text-muted uppercase">
          {now ? formatDate(now) : "––"}
        </span>
        {showWeather ? (
          <button type="button" onClick={() => openApp("weather")} className={cn("font-mono text-xs text-primary", press)}>
            18° clear · Harbor
          </button>
        ) : null}
      </div>
    </div>
  );
}

function Headlines() {
  const motion = useLauncher((s) => s.settings.motion);
  const [index, setIndex] = useState(0);
  useEffect(() => {
    if (!motion) return;
    const id = window.setInterval(() => setIndex((n) => (n + 1) % HEADLINES.length), 5000);
    return () => window.clearInterval(id);
  }, [motion]);
  const line = HEADLINES[index] ?? HEADLINES[0];
  return (
    <button
      type="button"
      onClick={() => setIndex((n) => (n + 1) % HEADLINES.length)}
      className={cn("plate plate-mark w-full p-3 text-left", press)}
    >
      <Kicker>Wire</Kicker>
      <p className="mt-1 text-sm text-pretty">{line}</p>
    </button>
  );
}

export function BriefPage() {
  const settings = useLauncher((s) => s.settings);
  const openApp = useLauncher((s) => s.openApp);
  const setPage = useLauncher((s) => s.setPage);
  const setLayer = useLauncher((s) => s.setLayer);
  const pulse = useLauncher((s) => s.pulse);
  const w = settings.widgets;
  const any = Object.values(w).some(Boolean);
  const pair = w.comms && w.transit;
  const cols = settings.density === "roomy" ? "grid-cols-1" : "grid-cols-2";
  return (
    <div className="flex min-h-full flex-col gap-3 p-3 pb-4">
      <Hint />
      <ClockBlock showWeather={w.weather} />
      {w.headlines ? <Headlines /> : null}
      {w.agenda || w.comms || w.transit || w.vitals ? (
        <div className={cn("grid gap-2", cols)}>
          {w.agenda ? (
            <button
              type="button"
              onClick={() => openApp("calendar")}
              className={cn("plate plate-mark col-span-full p-3 text-left", press)}
            >
              <Kicker>Agenda</Kicker>
              <ul className="mt-2 space-y-2">
                {AGENDA.map((item) => (
                  <li key={item.time} className="flex gap-3">
                    <span className="w-12 shrink-0 font-mono text-xs text-muted tabular-nums">{item.time}</span>
                    <span className="min-w-0">
                      <span className="block text-sm">{item.title}</span>
                      <span className="block truncate font-mono text-xs text-muted">{item.where}</span>
                    </span>
                  </li>
                ))}
              </ul>
            </button>
          ) : null}
          {w.comms ? (
            <button
              type="button"
              onClick={() => openApp("messages")}
              className={cn("plate plate-mark p-3 text-left", pair ? "" : "col-span-full", press)}
            >
              <Kicker>Comms</Kicker>
              <ul className="mt-2 space-y-2">
                {NOTIFICATIONS.filter((n) => n.appId === "messages" || n.appId === "mail")
                  .slice(0, 2)
                  .map((n) => (
                    <li key={n.id}>
                      <div className="text-sm">{n.title}</div>
                      <div className="truncate font-mono text-xs text-muted">{n.body}</div>
                    </li>
                  ))}
              </ul>
            </button>
          ) : null}
          {w.transit ? (
            <button
              type="button"
              onClick={() => openApp("maps")}
              className={cn("plate plate-mark p-3 text-left", pair ? "" : "col-span-full", press)}
            >
              <Kicker>Transit</Kicker>
              <div className="mt-2 text-sm">{TRANSIT.line}</div>
              <div className="font-mono text-xs text-hot">{TRANSIT.status}</div>
              <div className="mt-2 font-mono text-xs text-muted">
                {TRANSIT.stop} · {TRANSIT.walk}
              </div>
            </button>
          ) : null}
          {w.vitals ? (
            <button
              type="button"
              onClick={() => setPage("deck")}
              className={cn("plate plate-mark col-span-full p-3 text-left", press)}
            >
              <Kicker>Vitals</Kicker>
              <div className="mt-3 grid grid-cols-2 gap-3">
                <Meter label="CPU" value={`${pulse.cpu}%`} pct={pulse.cpu} />
                <Meter label="Mem" value={`${pulse.mem}%`} pct={pulse.mem} />
                <Meter label="Temp" value={`${pulse.temp}°`} pct={(pulse.temp - 30) * 8} />
                <Meter label="Uplink" value={`${pulse.up}`} pct={pulse.up / 1.6} />
              </div>
            </button>
          ) : null}
        </div>
      ) : null}
      {!any ? (
        <div className="plate plate-mark p-3">
          <Kicker>Brief</Kicker>
          <p className="mt-2 text-sm text-pretty text-muted">No modules are on. Add them in settings.</p>
          <button
            type="button"
            onClick={() => setLayer("settings")}
            className={cn("mt-3 h-11 bg-primary px-4 text-sm text-bg", press)}
          >
            Open settings
          </button>
        </div>
      ) : null}
    </div>
  );
}

export function AppsPage() {
  const labeled = useLauncher((s) => s.settings.showLabels);
  const density = useLauncher((s) => s.settings.density);
  const rest = APPS.filter((app) => !AI_APPS.some((ai) => ai.id === app.id));
  return (
    <div className="p-3 pb-4">
      <div className="mb-2 flex items-baseline justify-between px-1">
        <h2 className="text-lg">Apps</h2>
        <span className="font-mono text-xs text-muted">{APPS.length}</span>
      </div>
      <div className="mb-2 px-1">
        <Kicker>Assistants</Kicker>
      </div>
      <div className={cn("grid gap-1", densityCols(density))}>
        {AI_APPS.map((app) => (
          <AppIcon key={app.id} app={app} labeled={labeled} />
        ))}
      </div>
      <div className="mt-4 mb-2 px-1">
        <Kicker>Device</Kicker>
      </div>
      <div className={cn("grid gap-1", densityCols(density))}>
        {rest.map((app) => (
          <AppIcon key={app.id} app={app} labeled={labeled} />
        ))}
      </div>
    </div>
  );
}

function MusicPlate() {
  const music = useLauncher((s) => s.music);
  const toggle = useLauncher((s) => s.togglePlay);
  const skip = useLauncher((s) => s.skip);
  const track = TRACKS[music.track] ?? TRACKS[0];
  return (
    <div className="plate plate-mark p-3">
      <Kicker>Now</Kicker>
      <div className="mt-1 text-lg">{track?.title}</div>
      <div className="font-mono text-xs text-muted">
        {track?.artist} · {track?.length}
      </div>
      <div className="mt-3 flex items-center gap-2">
        <button
          type="button"
          aria-label="Previous track"
          onClick={() => skip(-1)}
          className={cn("flex size-11 items-center justify-center border border-border", press)}
        >
          <span className="font-mono text-xs">Prev</span>
        </button>
        <button
          type="button"
          aria-label={music.playing ? "Pause" : "Play"}
          onClick={toggle}
          className={cn("flex h-11 items-center bg-primary px-4 text-sm text-bg", press)}
        >
          {music.playing ? "Pause" : "Play"}
        </button>
        <button
          type="button"
          aria-label="Next track"
          onClick={() => skip(1)}
          className={cn("flex size-11 items-center justify-center border border-border", press)}
        >
          <span className="font-mono text-xs">Next</span>
        </button>
      </div>
    </div>
  );
}

export function DeckPage() {
  const pulse = useLauncher((s) => s.pulse);
  const tasks = useLauncher((s) => s.tasks);
  const toggleTask = useLauncher((s) => s.toggleTask);
  return (
    <div className="flex flex-col gap-3 p-3 pb-4">
      <div className="flex items-baseline justify-between px-1">
        <h2 className="text-lg">Deck</h2>
        <span className="font-mono text-xs text-muted">Session</span>
      </div>
      <MusicPlate />
      <div className="plate plate-mark p-3">
        <Kicker>Uplink</Kicker>
        <div className="mt-3 grid grid-cols-2 gap-3">
          <Meter label="CPU" value={`${pulse.cpu}%`} pct={pulse.cpu} />
          <Meter label="Mem" value={`${pulse.mem}%`} pct={pulse.mem} />
          <Meter label="Temp" value={`${pulse.temp}°`} pct={(pulse.temp - 30) * 8} />
          <Meter label="Link" value={`${pulse.up} mb/s`} pct={pulse.up / 1.6} />
        </div>
      </div>
      <div className="plate plate-mark p-3">
        <Kicker>Today</Kicker>
        <ul className="mt-2 space-y-2">
          {AGENDA.map((item) => (
            <li key={item.time} className="flex gap-3">
              <span className="w-12 shrink-0 font-mono text-xs text-muted tabular-nums">{item.time}</span>
              <span className="min-w-0">
                <span className="block text-sm">{item.title}</span>
                <span className="block truncate font-mono text-xs text-muted">{item.where}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
      <div className="plate plate-mark px-3">
        <div className="pt-3">
          <Kicker>Tasks</Kicker>
        </div>
        <ul>
          {tasks.map((task) => (
            <li key={task.id}>
              <button
                type="button"
                aria-pressed={task.done}
                onClick={() => toggleTask(task.id)}
                className="flex min-h-11 w-full items-center gap-3 text-left"
              >
                <span
                  className={cn(
                    "flex size-5 shrink-0 items-center justify-center border",
                    task.done ? "border-primary bg-primary text-bg" : "border-border",
                  )}
                >
                  {task.done ? <Check className="size-3" /> : null}
                </span>
                <span className={cn("text-sm", task.done && "text-muted line-through")}>{task.title}</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}
