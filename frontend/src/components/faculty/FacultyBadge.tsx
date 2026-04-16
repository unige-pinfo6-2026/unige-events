import { FACULTIES, type Faculty } from "@/types/faculty"

interface FacultyBadgeProps {
  id: Faculty
}

const ALL_FACULTIES = "Toutes facultés"

export default function FacultyBadge({ id }: Readonly<FacultyBadgeProps>) {
  const faculty = FACULTIES[id]

  const label = id == null ? ALL_FACULTIES : faculty.abbr
  const ariaLabel = id == null ? ALL_FACULTIES : faculty.name
  const style = id == null ? undefined : { backgroundColor: faculty.color }

  return (
    <span
      className={`inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full text-white ${!id && "bg-overlay"}`}
      style={style}
      aria-label={ariaLabel}
    >
      {label}
    </span>
  )
}
