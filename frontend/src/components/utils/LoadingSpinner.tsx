import type React from "react";

export function LoadingSpinner({children}: Readonly<{children?: React.ReactNode}>) {
    return (
        <div className="flex flex-col items-center justify-center gap-4">
            <span className="size-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />

            {children}
        </div>
    )
}