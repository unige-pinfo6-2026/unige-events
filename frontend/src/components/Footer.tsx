import { Mail, MapPin } from "lucide-react";
import { Banner } from "../assets/Banner";
import { ContactLink, IconLink, TextLink } from "./utils/Links";
import { FaInstagram, FaTwitter, FaGithub } from 'react-icons/fa'

const Footer = () => {
    const year = new Date().getFullYear();

    const Bucket = ({title, children}: {title: string, children: React.ReactNode}) => {
        return (
            <div>
                <h4 className="font-semibold mb-6 text-lg text-white">{title}</h4>
                
                {children}
            </div>
        )
    }

    const List = ({children}: {children: React.ReactNode}) => {
        return (
            <div className="flex flex-col space-y-4">
                {children}
            </div>
        )
    }

    return (
        <footer className="relative border-t border-white/10 bg-gradient-to-b from-background to-black overflow-hidden">
            <div className="flex flex-col max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-14 relative z-10 gap-8">
                <div className="flex flex-col lg:flex-row gap-24 justify-between">
                    {/* Brand */}
                    <div className="flex flex-col gap-4 max-w-100">
                        <Banner className="w-52"/>
                        
                        <p className="text-sm text-white/50 leading-relaxed mb-6">
                            La plateforme centralisée pour tous les événements universitaires. Connectons la communauté UNIGE.
                        </p>
                        
                        <div className="flex gap-3">
                            <IconLink icon={FaInstagram} href="https://instagram.com/unigeneve"/>
                            <IconLink icon={FaTwitter} href="https://x.com/UNIGEnews"/>
                            <IconLink icon={FaGithub} href="https://github.com/unige-pinfo6-2026/unige-events"/>
                        </div>
                    </div>

                    <div className="flex flex-col lg:flex-row gap-8 lg:gap-24">
                        {/* Plateforme */}
                        <Bucket title="Plateforme">
                            <List>
                                <TextLink href="#events">Évènements</TextLink>
                                <TextLink href="#features">Fonctionnalités</TextLink>
                                <TextLink href="#for-you">Pour vous</TextLink>
                                <TextLink href="#about">À propos</TextLink>
                                <TextLink href="#faq">FAQ</TextLink>
                            </List>
                        </Bucket>

                        {/* Ressources */}
                        <Bucket title="Ressources">
                            <List>
                                <TextLink href="/support">Centre d'aide</TextLink>
                                <TextLink href="/rules">Règles communauté</TextLink>
                            </List>
                        </Bucket>

                        {/* Contact */}
                        <Bucket title="Contact">
                            <List>
                                <ContactLink icon={Mail} href="mailto:contact@events.unige.ch">contact@events.unige.ch</ContactLink>
                                <ContactLink icon={MapPin} href="https://www.google.fr/maps/place/Universit%C3%A9+de+Gen%C3%A8ve+%2F+Battelle/@46.1765596,6.1375175,728m/data=!3m2!1e3!4b1!4m6!3m5!1s0x478c645147ac22df:0xe77faa44506a8d6!8m2!3d46.1765559!4d6.1400978!16s%2Fg%2F12vsg3nvh?hl=fr&entry=ttu&g_ep=EgoyMDI2MDMzMC4wIKXMDSoASAFQAw%3D%3D">Rte de Drize 7, 1227 Carouge, Genève</ContactLink>

                                <iframe 
                                        src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d3169.1949182763765!2d6.137517512111869!3d46.176559585507036!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x478c645147ac22df%3A0xe77faa44506a8d6!2sUniversit%C3%A9%20de%20Gen%C3%A8ve%20%2F%20Battelle!5e1!3m2!1sfr!2sch!4v1775139872613!5m2!1sfr!2sch" 
                                        style={{ border: 0 }} 
                                        loading="lazy"
                                        referrerPolicy="no-referrer-when-downgrade"
                                        className="max-w-[400px]"
                                    />
                            </List>
                        </Bucket>
                    </div>
                </div>

                <span className="w-full h-px bg-white/20" />

                <div className="flex flex-col md:flex-row justify-between items-center">
                    <p className="text-sm text-white/40">© {year} UNIGE Events. Tous droits réservés.</p>
                    
                    <div className="flex gap-8">
                        <TextLink href="/privacy">Politique de confidentialité</TextLink>
                        <TextLink href="/terms">Conditions générales</TextLink>
                    </div>
                </div>
            </div>
        </footer>
    )
}

export default Footer;