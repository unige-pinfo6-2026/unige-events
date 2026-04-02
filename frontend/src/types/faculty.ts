import { Economy } from "@/assets/faculty/Economy"
import { Law } from "@/assets/faculty/Law"
import { Letters } from "@/assets/faculty/Letters"
import { Medicine } from "@/assets/faculty/Medicine"
import { Psychology } from "@/assets/faculty/Psychology"
import { Sciences } from "@/assets/faculty/Sciences"
import { Theology } from "@/assets/faculty/Theology"
import { Translation } from "@/assets/faculty/Translation"

export const FACULTIES = {
  SCIENCES: { name: 'Faculté des Sciences', logo: Sciences },
  MEDECINE: { name: 'Faculté de Médecine', logo: Medicine },
  LETTERS: { name: 'Faculté des Lettres', logo: Letters },
  ECONOMY: { name: 'Faculté des Sciences Économiques et Sociales', logo: Economy },
  LAW: { name: 'Faculté de Droit', logo: Law },
  THEOLOGY: { name: 'Faculté Autonome de Théologie Protestante', logo: Theology },
  PSYCHOLOGY: { name: "Faculté de Psychologie et des Sciences de l'Éducation", logo: Psychology },
  TRANSLATION: { name: "Faculté de Traduction et d'Interprétation", logo: Translation },
} as const

export type Faculty = keyof typeof FACULTIES