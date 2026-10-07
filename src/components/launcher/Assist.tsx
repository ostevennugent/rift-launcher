import { useEffect, useRef, useState } from "react";
import { AI_APPS, appById, isAi } from "@/lib/launcher/catalog";
import { suggestions } from "@/lib/launcher/assist";
import type { AiId, AiTurn } from "@/lib/launcher/types";
import { useLauncher } from "@/lib/launcher/store";
import { Kicker, PanelFrame, press } from "@/components/launcher/bits";
import { cn } from "@/lib/cn";

export function AskSheet() {
  const open = useLauncher((s) => s.layer === "ask");
  const ask = useLauncher((s) => s.ask);
  const closeTop = useLauncher((s) => s.closeTop);
  const choose = useLauncher((s) => s.chooseAssistant);
  const defaultAi = useLauncher((s) => s.settings.defaultAi);
  const context = ask?.context;
  const apps = [...AI_APPS].sort((a, b) => (a.id === defaultAi ? -1 : b.id === defaultAi ? 1 : 0));
  return (
    <PanelFrame open={open} label="Ask an assistant" title="Ask" onClose={closeTop} origin="bottom">
      <div className="px-3 pt-4 pb-8">
        {context ? (
          <div className="border border-border bg-surface p-3">
            <Kicker>{appById(context.appId)?.name ?? context.title}</Kicker>
            <p className="mt-2 line-clamp-4 text-sm text-pretty">{context.body}</p>
          </div>
        ) : null}
        <p className="mt-4 text-sm text-pretty text-muted">
          Pick an assistant. It only reads this launcher. Nothing is sent off the device.
        </p>
        <ul className="mt-3">
          {apps.map((app) => {
            const Icon = app.icon;
            const chosen = app.id === defaultAi;
            return (
              <li key={app.id}>
                <button
                  type="button"
                  onClick={() => {
                    if (isAi(app.id)) choose(app.id);
                  }}
                  className={cn("flex min-h-16 w-full items-center gap-3 border-b border-border py-2 text-left", press)}
                >
                  <span className="flex size-11 shrink-0 items-center justify-center border border-border">
                    <Icon className="size-5" />
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex items-baseline gap-2">
                      <span className="text-sm">{app.name}</span>
                      {chosen ? <span className="font-mono text-xs text-primary">Default</span> : null}
                    </span>
                    <span className="block text-xs text-pretty text-muted">{app.blurb}</span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </PanelFrame>
  );
}

const EMPTY_TURNS: AiTurn[] = [];

export function AssistApp({ id }: { id: AiId }) {
  const turns = useLauncher((s) => s.threads[id] ?? EMPTY_TURNS);
  const handoff = useLauncher((s) => s.handoff);
  const send = useLauncher((s) => s.sendToAssistant);
  const note = useLauncher((s) => s.note);
  const setNote = useLauncher((s) => s.setNote);
  const openApp = useLauncher((s) => s.openApp);
  const setLayer = useLauncher((s) => s.setLayer);
  const [draft, setDraft] = useState("");
  const endRef = useRef<HTMLDivElement>(null);
  const app = appById(id);
  const lastAi = [...turns].reverse().find((turn) => turn.role === "ai")?.text;
  const source = handoff ? appById(handoff.appId) : undefined;

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "nearest" });
  }, [turns.length]);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="scroll min-h-0 flex-1 overflow-y-auto px-3 pt-3 pb-4">
        <p className="text-sm text-pretty text-muted">{app?.blurb}</p>
        {handoff ? (
          <div className="mt-3 border border-border bg-surface p-3">
            <div className="font-mono text-xs tracking-widest text-primary uppercase">
              From {source?.name ?? handoff.title}
            </div>
            <p className="mt-2 line-clamp-4 text-sm text-pretty">{handoff.body}</p>
            {source && handoff.appId !== "brief" ? (
              <button
                type="button"
                onClick={() => openApp(handoff.appId)}
                className={cn("mt-3 h-11 border border-border px-3 text-sm", press)}
              >
                Return to {source.name}
              </button>
            ) : (
              <button type="button" onClick={() => setLayer(null)} className={cn("mt-3 h-11 border border-border px-3 text-sm", press)}>
                Return to brief
              </button>
            )}
          </div>
        ) : (
          <p className="mt-3 font-mono text-xs text-muted">No screen attached. Ask from an app to hand one over.</p>
        )}
        <div className="mt-4 flex flex-wrap gap-2">
          {suggestions(id, handoff).map((prompt) => (
            <button
              key={prompt}
              type="button"
              onClick={() => send(id, prompt)}
              className={cn("h-11 border border-border px-3 text-sm", press)}
            >
              {prompt}
            </button>
          ))}
        </div>
        <ul className="mt-4">
          {turns.map((turn, index) => (
            <li
              key={`${turn.role}-${index}`}
              className={cn("mb-2 border px-3 py-2 text-sm text-pretty", turn.role === "ai" ? "border-primary" : "border-border bg-surface")}
            >
              <div className="mb-1 font-mono text-xs tracking-widest text-muted uppercase">
                {turn.role === "ai" ? app?.name : "You"}
              </div>
              {turn.text}
            </li>
          ))}
        </ul>
        {id === "draft" && lastAi ? (
          <button
            type="button"
            onClick={() => setNote(handoff?.appId === "notes" ? lastAi : note.trim() ? `${note.trim()}\n\n${lastAi}` : lastAi)}
            className={cn("mt-2 h-11 bg-primary px-3 text-sm text-bg", press)}
          >
            {handoff?.appId === "notes" ? "Replace note" : "Add to notes"}
          </button>
        ) : null}
        <div ref={endRef} />
      </div>
      <form
        className="flex border-t border-border"
        onSubmit={(e) => {
          e.preventDefault();
          send(id, draft);
          setDraft("");
        }}
      >
        <input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={`Ask ${app?.name ?? "assistant"}`}
          aria-label={`Ask ${app?.name ?? "assistant"}`}
          className="h-12 min-w-0 flex-1 bg-transparent px-3 text-base outline-none placeholder:text-muted"
        />
        <button type="submit" className="h-12 bg-primary px-4 text-sm text-bg">
          Send
        </button>
      </form>
    </div>
  );
}
