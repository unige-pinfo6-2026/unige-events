import type { Faculty } from "./faculty.types"

const FacultyCard = ({faculty}: {faculty: Faculty}) => {
    return (
        <faculty.logo className="w-auto h-24"/>
    )
}

export default FacultyCard;