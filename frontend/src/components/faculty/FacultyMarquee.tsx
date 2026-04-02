import Marquee from "../utils/Marquee"
import FacultyCard from "./FacultyCard";
import { FACULTIES } from "../../types/faculty";

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