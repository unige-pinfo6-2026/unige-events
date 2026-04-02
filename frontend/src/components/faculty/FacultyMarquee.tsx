import Marquee from "../utils/Marquee"
import FacultyCard from "./FacultyCard";
import { FACULTIES } from "./faculty.types";

const FacultyMarquee = () => {
    return (
        <Marquee>
            {FACULTIES.map(faculty => {
                return (
                    <FacultyCard faculty={faculty}/>
                )
            })}
        </Marquee>
    )
}

export default FacultyMarquee;