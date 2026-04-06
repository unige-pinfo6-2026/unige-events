import type React from "react";

export function LoadingSpinner({children}: Readonly<{children?: React.ReactNode}>) {
    return (
        <div className="flex flex-col justify-center items-center gap-4 min-h-[50vh]">
            <span className="size-8 rounded-full border-2 border-accent border-t-transparent animate-spin" />

            {children}
        </div>
    )
}