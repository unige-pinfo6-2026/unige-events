import { Economy } from "../../assets/faculty/Economy"
import { Law } from "../../assets/faculty/Law"
import { Letters } from "../../assets/faculty/Letters"
import { Medicine,  } from "../../assets/faculty/Medicine"
import { Psychology } from "../../assets/faculty/Psychology"
import { Sciences } from "../../assets/faculty/Sciences"
import { Theology } from "../../assets/faculty/Theology"
import { Translation } from "../../assets/faculty/Translation"

export type Faculty = {
    id: string
    name: string
    logo: React.FC<React.SVGProps<SVGSVGElement>>
}

export const FACULTIES = [
  { id: 'sciences', name: 'Faculté des Sciences', logo: Sciences },
  { id: 'medecine', name: 'Faculté de Médecine', logo: Medicine },
  { id: 'letters', name: 'Faculté des Lettres', logo: Letters },
  { id: 'economy', name: 'Faculté des Sciences Économiques et Sociales', logo: Economy },
  { id: 'law', name: 'Faculté de Droit', logo: Law },
  { id: 'theology', name: 'Faculté Autonome de Théologie Protestante', logo: Theology },
  { id: 'psychology', name: 'Faculté de Psychologie et des Sciences de l\'Éducation', logo: Psychology },
  { id: 'translation', name: 'Faculté de Traduction et d\'Interprétation', logo: Translation },
] as const satisfies Faculty[]