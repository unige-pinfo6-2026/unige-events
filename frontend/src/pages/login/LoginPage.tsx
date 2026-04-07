import { LoadingSpinner } from "@/components/utils/LoadingSpinner";
import { useAuth } from "@/hooks";
import { useEffect } from "react";

export default function LoginPage() {
    const { login } = useAuth();

    useEffect(() => {
        login()
    }, [login]);

    return (
        <div className="flex items-center justify-center min-h-hero">
            <LoadingSpinner>
                Redirection vers la page de connexion...
            </LoadingSpinner>
        </div>
    )
}