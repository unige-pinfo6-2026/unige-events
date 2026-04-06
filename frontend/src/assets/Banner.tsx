export const Banner = ({className}: {className: string}) => {
    return (
        <a href="/">
            <img className={className} src="/banner.svg" alt="Bannière UNIGE" />
        </a>
    )
}