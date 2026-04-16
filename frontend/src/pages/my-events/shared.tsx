export function EventGridFixture() {
  return (
    <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
      {Array.from({ length: 6 }).map((_, i) => (
        <article key={i} className="relative bg-background border border-border rounded-3xl overflow-hidden">
          <div className="relative h-52 bg-foreground/10">
            <span className="absolute top-4 left-4 h-6 w-24 rounded-full bg-foreground/20" />
            <div className="absolute bottom-0 left-0 right-0 px-5 pb-5 flex flex-col gap-2">
              <div className="h-6 w-4/5 rounded-md bg-foreground/25" />
              <div className="h-4 w-1/2 rounded-md bg-foreground/20" />
            </div>
          </div>
          <div className="p-5 flex flex-col gap-3">
            <div className="flex flex-col gap-2.5">
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-40 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-32 rounded bg-foreground/10" />
              </div>
              <div className="flex items-center gap-2.5">
                <div className="w-4 h-4 rounded bg-foreground/15 shrink-0" />
                <div className="h-4 w-24 rounded bg-foreground/10" />
              </div>
            </div>
            <div className="border-t border-border" />
            <div className="flex flex-col gap-1.5">
              <div className="h-3.5 w-full rounded bg-foreground/10" />
              <div className="h-3.5 w-5/6 rounded bg-foreground/10" />
            </div>
          </div>
        </article>
      ))}
    </div>
  )
}

export function PublicationGridFixture() {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-5">
      {Array.from({ length: 6 }).map((_, i) => (
        <article key={i} className="flex flex-col rounded-2xl bg-background border border-border overflow-hidden">
          <div className="relative h-36 bg-foreground/10">
            <span className="absolute top-3 left-3 h-[22px] w-20 rounded-full bg-foreground/20" />
          </div>
          <div className="flex flex-col gap-2 p-4 flex-1">
            <div className="flex items-start gap-3">
              <div className="flex-1 h-5 rounded bg-foreground/15" />
              <div className="shrink-0 h-[18px] w-16 rounded-full bg-foreground/10" />
            </div>
            <div className="h-[22px] w-20 rounded-full bg-foreground/10" />
            <div className="flex items-center gap-2">
              <div className="w-3.5 h-3.5 rounded bg-foreground/15 shrink-0" />
              <div className="h-3.5 w-36 rounded bg-foreground/10" />
            </div>
            <div className="flex items-center gap-2">
              <div className="w-3.5 h-3.5 rounded bg-foreground/15 shrink-0" />
              <div className="h-3.5 w-28 rounded bg-foreground/10" />
            </div>
          </div>
          <div className="flex gap-2 p-3 border-t border-border">
            <div className="h-7 w-20 rounded-lg bg-foreground/10" />
            <div className="h-7 w-[76px] rounded-lg bg-foreground/10 ml-auto" />
          </div>
        </article>
      ))}
    </div>
  )
}
