import Navbar from "./Navbar";

const Header = () => {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-background/10 backdrop-blur-sm border-b border-white/20">
        <Navbar/>
    </header>
  )
}

export default Header;