import Marquee from "@/components/utils/Marquee"
import FacultyCard from "./FacultyCard";
import { FACULTIES, type Faculty } from "@/types/faculty";

const FacultyMarquee = () => {
    return (
        <Marquee>
            {Object.keys(FACULTIES).map((id) => (
                <FacultyCard key={id} faculty={id as Faculty} />
            ))}
        </Marquee>
    )
}

export default FacultyMarquee;