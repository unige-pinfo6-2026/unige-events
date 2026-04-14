import { Link } from "react-router-dom"

const sizes = {
  md: 'size-36',
  lg: 'size-52',
}

export const Banner = ({ size = 'md' }: { size?: keyof typeof sizes }) => {
  return (
    <Link to="/">
      <img className={sizes[size]} src="/banner.svg" alt="Bannière UNIGE" />
    </Link>
  )
}