interface InfoMessageProps {
  type: keyof typeof variants,
  message: string
}

const variants = {
  "success": "text-success",
  "info": "text-overlay",
  "error": "text-error"
}

export function InfoMessage({ type, message }: Readonly<InfoMessageProps>) {
  return (
    <div className="flex justify-center items-center px-4">
      <p className={variants[type]}>{message}</p>
    </div>
  )
}
