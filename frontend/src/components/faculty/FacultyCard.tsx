import type { Faculty } from "../../types/faculty"

const FacultyCard = ({faculty}: {faculty: Faculty}) => {
    return (
        <faculty.logo className="w-auto h-24"/>
    )
}

export default FacultyCard;