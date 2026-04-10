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

export default function FacultyBadge({ faculty }: Readonly<{ faculty: Faculty }>) {
  const label = FACULTY_LABELS[faculty]
  return (
    <span
      className={`inline-block w-fit text-xs font-semibold px-2.5 py-1 rounded-full ${FACULTY_COLORS[faculty]}`}
      aria-label={`Faculté : ${label}`}
    >
      {label}
    </span>
  )
}
