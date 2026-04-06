import EventCards from "@/components/event/EventCards";
import { SectionHeader, SectionWrapper } from "@/components/utils/Section";

export default function EventsPage() {
    // Search page de Daniel
    return (
        <SectionWrapper padding="sm">
            <SectionHeader 
                title="Évènements à venir"
                subtitle="Tous les prochains évènements à ne pas manquer."/>

            <EventCards/>
        </SectionWrapper>
    )
}