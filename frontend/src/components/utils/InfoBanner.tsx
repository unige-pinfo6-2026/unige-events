const variants = {
  warning: 'bg-yellow-300 border-b border-yellow-500 text-yellow-900'
} as const

interface InfoBannerProps {
  type: keyof typeof variants,
  message: string
}

export default function InfoBanner({ type, message }: Readonly<InfoBannerProps>) {
    return (
        <div className={`flex flex-col justify-center items-center px-2 py-2 text-sm font-medium ${variants[type]}`}>
            <p>{message}</p>
        </div>
    )
}