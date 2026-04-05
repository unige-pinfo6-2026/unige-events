export function ErrorMessage({ message }: Readonly<{ message: string }>) {
  return (
    <div className="flex justify-center items-center px-4">
      <p className="text-error">{message}</p>
    </div>
  )
}
