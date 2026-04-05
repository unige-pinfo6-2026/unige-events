import { Outlet } from "react-router-dom";

export function AppLayout() {
    return (
        <div className="my-16">
            <Outlet/>
        </div>
    )
}