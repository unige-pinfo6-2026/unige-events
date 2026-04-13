import { Economy } from "@/assets/faculty/Economy"
import { Law } from "@/assets/faculty/Law"
import { Letters } from "@/assets/faculty/Letters"
import { Medicine } from "@/assets/faculty/Medicine"
import { Psychology } from "@/assets/faculty/Psychology"
import { Sciences } from "@/assets/faculty/Sciences"
import { Theology } from "@/assets/faculty/Theology"
import { Translation } from "@/assets/faculty/Translation"

export const FACULTIES = {
  SCIENCES: { name: 'Faculté des Sciences', abbr: 'Sciences', logo: Sciences, color: '#318063' },
  MEDICINE: { name: 'Faculté de Médecine', abbr: 'Médecine', logo: Medicine, color: '#9a0050' },
  LETTERS: { name: 'Faculté des Lettres', abbr: 'Lettres', logo: Letters, color: '#046fcb' },
  SOCIAL_SCIENCES: { name: 'Faculté des Sciences de la Société', abbr: 'SdS', logo: Economy, color: '#fcb000' }, // TODO: logo dédié manquant
  GSEM: { name: "Faculté d'Économie et de Management", abbr: 'GSEM', logo: Economy, color: '#425878' },
  LAW: { name: 'Faculté de Droit', abbr: 'Droit', logo: Law, color: '#ba0c2f' },
  THEOLOGY: { name: 'Faculté Autonome de Théologie Protestante', abbr: 'Théologie', logo: Theology, color: '#490674' },
  PSYCHOLOGY: { name: "Faculté de Psychologie et des Sciences de l'Éducation", abbr: 'Psychologie', logo: Psychology, color: '#00b1ae' },
  FTI: { name: "Faculté de Traduction et d'Interprétation", abbr: 'FTI', logo: Translation, color: '#fe5900' },
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