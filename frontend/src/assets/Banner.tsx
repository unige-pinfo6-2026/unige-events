import { Link } from "react-router-dom"

export const Banner = ({className}: {className: string}) => {
    return (
        <Link to="/">
            <img className={className} src="/banner.svg" alt="Bannière UNIGE" />
        </Link>
    )
}