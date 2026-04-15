import EconomyLogo from "@/assets/faculty/EconomyLogo"
import LawLogo from "@/assets/faculty/LawLogo"
import LettersLogo from "@/assets/faculty/LettersLogo"
import MedicineLogo from "@/assets/faculty/MedicineLogo"
import PsychologyLogo from "@/assets/faculty/PsychologyLogo"
import SciencesLogo from "@/assets/faculty/SciencesLogo"
import SocialSciencesLogo from "@/assets/faculty/SocialSciencesLogo"
import TheologyLogo from "@/assets/faculty/TheologyLogo"
import TranslationLogo from "@/assets/faculty/TranslationLogo"

export const FACULTIES = {
  SCIENCES: { name: 'Faculté des Sciences', abbr: 'Sciences', logo: SciencesLogo, color: '#318063' },
  MEDICINE: { name: 'Faculté de Médecine', abbr: 'Médecine', logo: MedicineLogo, color: '#9a0050' },
  LETTERS: { name: 'Faculté des Lettres', abbr: 'Lettres', logo: LettersLogo, color: '#046fcb' },
  SOCIAL_SCIENCES: { name: 'Faculté des Sciences de la Société', abbr: 'SdS', logo: SocialSciencesLogo, color: '#fcb000' },
  GSEM: { name: "Faculté d'Économie et de Management", abbr: 'GSEM', logo: EconomyLogo, color: '#425878' },
  LAW: { name: 'Faculté de Droit', abbr: 'Droit', logo: LawLogo, color: '#ba0c2f' },
  THEOLOGY: { name: 'Faculté Autonome de Théologie Protestante', abbr: 'Théologie', logo: TheologyLogo, color: '#490674' },
  PSYCHOLOGY: { name: "Faculté de Psychologie et des Sciences de l'Éducation", abbr: 'Psychologie', logo: PsychologyLogo, color: '#00b1ae' },
  FTI: { name: "Faculté de Traduction et d'Interprétation", abbr: 'FTI', logo: TranslationLogo, color: '#fe5900' },
} as const

export const Faculty = {
  SCIENCES: 'SCIENCES',
  MEDICINE: 'MEDICINE',
  LETTERS: 'LETTERS',
  SOCIAL_SCIENCES: 'SOCIAL_SCIENCES',
  GSEM: 'GSEM',
  LAW: 'LAW',
  THEOLOGY: 'THEOLOGY',
  PSYCHOLOGY: 'PSYCHOLOGY',
  FTI: 'FTI',
} as const satisfies Record<keyof typeof FACULTIES, keyof typeof FACULTIES>

export type Faculty = keyof typeof FACULTIES