import { Faculty, FACULTY_LABELS } from '@/types/event'

const FACULTY_COLORS: Record<Faculty, string> = {
  SCIENCES:    'bg-[#007E64] text-white',
  LETTRES:     'bg-[#0067C5] text-white',
  DROIT:       'bg-[#F42941] text-white',
  MEDECINE:    'bg-[#96004B] text-white',
  SES:         'bg-[#F1AB00] text-gray-900',
  PSYCHOLOGIE: 'bg-[#C69200] text-gray-900',
  THEOLOGIE:   'bg-[#4B0B71] text-white',
  FTI:         'bg-[#FF5C00] text-white',
  GSI:         'bg-[#003580] text-white',
}

const ALL_FACULTIES_LABEL = 'Toutes facultés'
const ALL_FACULTIES_CLASSES = 'bg-foreground/10 text-foreground/70'

interface FacultyBadgeProps {
  faculty: Faculty | null | undefined
}

export default function FacultyBadge({ faculty }: Readonly<FacultyBadgeProps>) {
  const label = faculty == null ? ALL_FACULTIES_LABEL : FACULTY_LABELS[faculty]
  const classes = faculty == null ? ALL_FACULTIES_CLASSES : FACULTY_COLORS[faculty]
  return (
    <span
      className={`inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full ${classes}`}
      aria-label={`Faculté : ${label}`}
    >
      {label}
    </span>
  )
}
