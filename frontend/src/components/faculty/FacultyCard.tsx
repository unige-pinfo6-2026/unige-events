import { FACULTIES, type Faculty } from "@/types/faculty";

const FacultyCard = ({faculty}: {faculty: Faculty}) => {
    const { logo: Logo } = FACULTIES[faculty]
    return (
        <Logo className="w-auto h-24"/>
    )
}

export default FacultyCard;