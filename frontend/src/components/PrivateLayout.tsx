import { Outlet } from "react-router-dom";

export function PrivateLayout() {
    return (
        <div className="my-16">
            <Outlet/>
        </div>
    )
}