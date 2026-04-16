export function EventGridFixture() {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
      {[0, 1, 2, 3].map(i => (
        <div key={i} className="h-[360px] rounded-3xl" />
      ))}
    </div>
  )
}
