import { Outlet } from "react-router-dom";

export function AppLayout() {
    return (
        <div className="max-w-7xl mx-auto my-20">
            <Outlet/>
        </div>
    )
}