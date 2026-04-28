import { LoadingSpinner } from "@/components/utils/LoadingSpinner";
import { useAuth } from "@/hooks";
import { useEffect } from "react";
import { useLocation } from "react-router-dom";

export default function LoginPage() {
    const { login } = useAuth();
    const location = useLocation();

    useEffect(() => {
        const returnTo = (location.state as { returnTo?: string } | null)?.returnTo
        login(returnTo)
    }, [login, location.state]);

    return (
        <div className="flex items-center justify-center min-h-hero">
            <LoadingSpinner>
                Redirection vers la page de connexion...
            </LoadingSpinner>
        </div>
    )
}