import { FACULTIES, type Faculty } from "@/types/faculty"

interface FacultyBadgeProps {
  id: Faculty
}

export default function FacultyBadge({ id }: Readonly<FacultyBadgeProps>) {
  const faculty = FACULTIES[id]
  const label = id == null ? "Toutes facultés" : faculty.abbr
  const className = id == null
    ? "inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full bg-foreground/10 text-foreground/70"
    : "inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full text-white"
  const style = id == null ? undefined : { backgroundColor: faculty.color }

  return (
    <span
      className={className}
      style={style}
      aria-label={id == null ? 'Toutes facultés' : faculty.name}
    >
      {label}
    </span>
  )
}
