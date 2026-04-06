import type { LucideIcon } from "lucide-react"

interface InfoMessageProps {
  icon?: LucideIcon,
  type: keyof typeof variants,
  message: string
}

const variants = {
  "success": "text-success border-success/30",
  "info": "text-foreground/50 border-foreground/30",
  "error": "text-error border-error/30"
}

export function InfoMessage({ icon: Icon, type, message }: Readonly<InfoMessageProps>) {
  return (
    <div className={`flex flex-col justify-center items-center p-8 rounded-3xl bg-background/40 backdrop-blur-xl border border-error/30 ${variants[type]}`}>
      {Icon && <Icon className="size-10 text-foreground/20 mx-auto mb-4" />}

      <p>{message}</p>
    </div>
  )
}

{/* <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-12 border border-border text-center">
                <Search className="w-10 h-10 text-foreground/20 mx-auto mb-4" />
                <p className="text-foreground/50 text-sm">
                  Aucun résultat. Essayez de modifier vos filtres ou votre recherche
                </p>
              </div> */}
