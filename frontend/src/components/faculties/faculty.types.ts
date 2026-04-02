import { Economy } from "../../assets/faculties/Economy"
import { Law } from "../../assets/faculties/Law"
import { Letters } from "../../assets/faculties/Letters"
import { Medicine,  } from "../../assets/faculties/Medicine"
import { Psychology } from "../../assets/faculties/Psychology"
import { Sciences } from "../../assets/faculties/Sciences"
import { Theology } from "../../assets/faculties/Theology"
import { Translation } from "../../assets/faculties/Translation"

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