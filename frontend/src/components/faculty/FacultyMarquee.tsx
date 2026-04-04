import Marquee from "@/components/utils/Marquee"
import FacultyCard from "./FacultyCard";
import { FACULTIES, type Faculty } from "@/types/faculty";

export default function FacultyMarquee() {
    return (
        <Marquee>
            {Object.keys(FACULTIES).map((id) => (
                <FacultyCard key={id} faculty={id as Faculty} />
            ))}
        </Marquee>
    )
}