import { Link } from "react-router-dom"

const sizes = {
  md: 'w-36',
  lg: 'w-52',
}

export const Banner = ({ size = 'md' }: { size?: keyof typeof sizes }) => {
  return (
    <Link to="/" className="shrink-0">
      <img className={sizes[size]} src="/banner.svg" alt="Bannière UNIGE" />
    </Link>
  )
}