import { FACULTIES, type Faculty } from "@/types/faculty";

export default function FacultyCard({faculty}: Readonly<{faculty: Faculty}>) {
    const { logo: Logo } = FACULTIES[faculty];
    
    return (
        <Logo className="w-auto h-24"/>
    )
}