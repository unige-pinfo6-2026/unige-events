import { LoadingSpinner } from "@/components/utils/LoadingSpinner";
import { useAuth } from "@/hooks";
import { useEffect } from "react";

export function LoginPage() {
    const { login } = useAuth();

    useEffect(() => {
        login()
    }, [login]);

    return (
        <LoadingSpinner>
            Redirection vers la page de connexion...
        </LoadingSpinner>
    )
}