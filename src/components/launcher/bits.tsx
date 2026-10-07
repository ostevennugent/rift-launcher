import { useEffect, useId, useRef, type ReactNode } from "react";
import * as Switch from "@radix-ui/react-switch";
import { X } from "lucide-react";
import { NOTIFICATIONS } from "@/lib/launcher/catalog";
import type { AppItem } from "@/lib/launcher/catalog";
import { useLauncher } from "@/lib/launcher/store";
import { cn } from "@/lib/cn";

export const press = "transition-transform duration-150 ease-out active:scale-[0.96]";

export function useLongPress(onLong: () => void, onTap: () => void, enabled = true) {
  const timer = useRef<number | null>(null);
  const fired = useRef(false);
  const start = useRef({ x: 0, y: 0 });
  const longRef = useRef(onLong);
  const tapRef = useRef(onTap);
  const enabledRef = useRef(enabled);
  longRef.current = onLong;
  tapRef.current = onTap;
  enabledRef.current = enabled;

  const clear = () => {
    if (timer.current != null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  };

  useEffect(() => clear, []);

  return {
    onPointerDown(e: React.PointerEvent) {
      if (e.button !== 0) return;
      fired.current = false;
      start.current = { x: e.clientX, y: e.clientY };
      clear();
      timer.current = window.setTimeout(() => {
        if (!enabledRef.current) return;
        fired.current = true;
        longRef.current();
      }, 450);
    },
    onPointerMove(e: React.PointerEvent) {
      if (Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 12) clear();
    },
    onPointerUp() {
      clear();
    },
    onPointerCancel() {
      clear();
    },
    onContextMenu(e: React.MouseEvent) {
      e.preventDefault();
    },
    onClick(e: React.MouseEvent) {
      if (fired.current) {
        e.preventDefault();
        fired.current = false;
        return;
      }
      tapRef.current();
    },
  };
}

export function AppIcon({
  app,
  labeled,
  allowPeek = true,
}: {
  app: AppItem;
  labeled: boolean;
  allowPeek?: boolean;
}) {
  const openApp = useLauncher((s) => s.openApp);
  const setPeek = useLauncher((s) => s.setPeek);
  const dismissed = useLauncher((s) => s.dismissed);
  const playing = useLauncher((s) => s.music.playing);
  const badge = NOTIFICATIONS.some((n) => n.appId === app.id && !dismissed.includes(n.id));
  const handlers = useLongPress(
    () => setPeek(app.id),
    () => openApp(app.id),
    allowPeek,
  );
  const Icon = app.icon;
  return (
    <button
      type="button"
      className={cn("flex min-h-11 min-w-0 flex-col items-center justify-center gap-1", press)}
      aria-label={app.name}
      {...handlers}
    >
      <span className="relative flex size-11 items-center justify-center border border-border bg-surface">
        <Icon className="size-5 text-fg" strokeWidth={1.75} />
        {badge ? <span className="absolute top-1 right-1 size-1.5 rounded-full bg-hot" /> : null}
        {app.id === "music" && playing ? (
          <span className="absolute bottom-1 left-1 h-1 w-2 bg-primary" />
        ) : null}
      </span>
      {labeled ? (
        <span className="w-full truncate text-center text-xs text-muted">{app.name}</span>
      ) : null}
    </button>
  );
}

export function Kicker({ children }: { children: string }) {
  return <div className="font-mono text-xs tracking-widest text-primary uppercase">{children}</div>;
}

export function Meter({ label, value, pct }: { label: string; value: string; pct: number }) {
  return (
    <div>
      <div className="flex items-baseline justify-between gap-2">
        <span className="font-mono text-xs tracking-wider text-muted uppercase">{label}</span>
        <span className="font-mono text-xs text-fg tabular-nums">{value}</span>
      </div>
      <div className="mt-1 h-1.5 bg-bg">
        <div className="h-full bg-primary" style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
      </div>
    </div>
  );
}

export function Group({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-6">
      <h3 className="mb-2 font-mono text-xs tracking-widest text-primary uppercase">{title}</h3>
      <div className="plate plate-mark px-3">{children}</div>
    </section>
  );
}

export function ToggleRow({
  label,
  hint,
  checked,
  onChange,
}: {
  label: string;
  hint?: string;
  checked: boolean;
  onChange: (value: boolean) => void;
}) {
  const id = useId();
  return (
    <div className="flex items-center justify-between gap-4 border-b border-border py-3 last:border-b-0">
      <label htmlFor={id} className="min-w-0">
        <span className="block text-sm">{label}</span>
        {hint ? <span className="block text-pretty font-mono text-xs text-muted">{hint}</span> : null}
      </label>
      <Switch.Root
        id={id}
        checked={checked}
        onCheckedChange={onChange}
        className="relative h-7 w-12 shrink-0 rounded-full border border-border bg-surface-2 transition-colors data-[state=checked]:border-primary data-[state=checked]:bg-primary"
      >
        <Switch.Thumb className="block size-5 translate-x-1 rounded-full bg-fg transition-transform duration-150 data-[state=checked]:translate-x-6 data-[state=checked]:bg-bg" />
      </Switch.Root>
    </div>
  );
}

export function Segment<T extends string>({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: T;
  options: { id: T; label: string }[];
  onChange: (id: T) => void;
}) {
  return (
    <div className="border-b border-border py-3 last:border-b-0">
      <div className="mb-2 font-mono text-xs tracking-widest text-muted uppercase">{label}</div>
      <div
        role="radiogroup"
        aria-label={label}
        className="grid gap-1 border border-border bg-bg p-1"
        style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
      >
        {options.map((option) => {
          const on = option.id === value;
          return (
            <button
              key={option.id}
              type="button"
              role="radio"
              aria-checked={on}
              onClick={() => onChange(option.id)}
              className={cn(
                "h-11 px-1 font-mono text-xs tracking-wide uppercase",
                on ? "bg-primary text-bg" : "text-muted",
              )}
            >
              {option.label}
            </button>
          );
        })}
      </div>
    </div>
  );
}

export function PanelFrame({
  open,
  label,
  title,
  onClose,
  origin,
  scroll = true,
  onAsk,
  children,
}: {
  open: boolean;
  label: string;
  title: string;
  onClose: () => void;
  origin: "top" | "bottom" | "right";
  scroll?: boolean;
  onAsk?: () => void;
  children: ReactNode;
}) {
  const closeRef = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (open) closeRef.current?.focus();
  }, [open]);
  const hidden =
    origin === "top" ? "-translate-y-full" : origin === "bottom" ? "translate-y-full" : "translate-x-full";
  return (
    <div
      role="dialog"
      aria-label={label}
      aria-hidden={!open}
      inert={!open}
      className={cn(
        "absolute inset-0 z-30 flex flex-col bg-bg motion-shift transition-transform duration-200 ease-out",
        open ? "translate-x-0 translate-y-0" : cn("pointer-events-none", hidden),
      )}
    >
      <div className="grid h-14 shrink-0 grid-cols-[2.75rem_minmax(0,1fr)_auto] items-center border-b border-border px-2">
        <button
          ref={closeRef}
          type="button"
          onClick={onClose}
          aria-label="Close"
          className={cn("flex size-11 items-center justify-center", press)}
        >
          <X className="size-5" />
        </button>
        <h2 className="truncate text-center text-base">{title}</h2>
        {onAsk ? (
          <button
            type="button"
            onClick={onAsk}
            className={cn("flex h-11 items-center px-2 font-mono text-xs tracking-widest text-primary uppercase", press)}
          >
            Ask
          </button>
        ) : (
          <span className="w-11" />
        )}
      </div>
      {scroll ? (
        <div className="scroll min-h-0 flex-1 overflow-y-auto">{children}</div>
      ) : (
        <div className="flex min-h-0 flex-1 flex-col">{children}</div>
      )}
    </div>
  );
}
