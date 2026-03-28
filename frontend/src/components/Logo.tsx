interface LogoProps {
  size?: number
  className?: string
}

function Logo({ size = 40, className }: Readonly<LogoProps>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
      aria-label="UNIGE Events logo"
    >
      <style>{`
        @keyframes logo-fL { 0%,18%{opacity:0;transform:translateX(-12px)} 45%,100%{opacity:1;transform:translateX(0)} }
        @keyframes logo-fR { 0%,24%{opacity:0;transform:translateX(12px)} 52%,100%{opacity:1;transform:translateX(0)} }
        @keyframes logo-bnc { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-2px)} }
        @keyframes logo-wv1 { 0%,8%{opacity:0;transform:scaleX(0.4)} 22%{opacity:1} 40%{opacity:0;transform:scaleX(1.4)} 100%{opacity:0} }
        @keyframes logo-wv2 { 0%,14%{opacity:0;transform:scaleX(0.4)} 28%{opacity:.7} 46%{opacity:0;transform:scaleX(1.4)} 100%{opacity:0} }
        @keyframes logo-sp { 0%,58%{opacity:0;transform:scale(0)} 72%,88%{opacity:1;transform:scale(1)} 96%,100%{opacity:0} }
        .logo-fL{animation:logo-fL 3s ease-out infinite;transform-origin:9px 22px}
        .logo-fR{animation:logo-fR 3s ease-out infinite;transform-origin:42px 22px}
        .logo-bL{animation:logo-bnc 1.6s ease-in-out 0.2s infinite;transform-origin:9px 22px}
        .logo-bC{animation:logo-bnc 1.6s ease-in-out infinite;transform-origin:21px 18px}
        .logo-bR{animation:logo-bnc 1.6s ease-in-out 0.35s infinite;transform-origin:42px 22px}
        .logo-wv1{animation:logo-wv1 3s ease-out infinite;transform-origin:35px 18px}
        .logo-wv2{animation:logo-wv2 3s ease-out infinite;transform-origin:35px 18px}
        .logo-sp{animation:logo-sp 3s ease-out infinite}
      `}</style>

      {/* Ground arc */}
      <path d="M3 43 Q24 37 45 43" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" opacity="0.2" />

      {/* Left figure — slides in, arms up */}
      <g className="logo-fL"><g className="logo-bL">
        <circle cx="9" cy="22" r="3.5" fill="currentColor" opacity="0.7" />
        <line x1="9" y1="25.5" x2="9" y2="34" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
        <line x1="9" y1="28" x2="5" y2="22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" opacity="0.7" />
        <line x1="9" y1="28" x2="13" y2="22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" opacity="0.7" />
        <line x1="9" y1="34" x2="6" y2="43" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
        <line x1="9" y1="34" x2="12" y2="43" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
      </g></g>

      {/* Center caller — elevated, megaphone right */}
      <g className="logo-bC">
        <circle cx="21" cy="17" r="4.5" fill="currentColor" />
        <line x1="21" y1="21.5" x2="21" y2="31" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <line x1="21" y1="24" x2="27" y2="20" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <line x1="21" y1="24" x2="15" y2="21" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        <line x1="21" y1="31" x2="17" y2="43" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <line x1="21" y1="31" x2="25" y2="43" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
        <rect x="27" y="17" width="4" height="7" fill="currentColor" opacity="0.9" />
        <path d="M31 15 L36 12 L36 26 L31 24 Z" fill="currentColor" />
      </g>

      {/* Waves */}
      <path className="logo-wv1" d="M37 14 Q41 18 37 22" stroke="currentColor" strokeWidth="1.6" fill="none" strokeLinecap="round" />
      <path className="logo-wv2" d="M39 11 Q44 18 39 25" stroke="currentColor" strokeWidth="1.2" fill="none" strokeLinecap="round" opacity="0.55" />

      {/* Right figure — slides in, arms up */}
      <g className="logo-fR"><g className="logo-bR">
        <circle cx="42" cy="22" r="3.5" fill="currentColor" opacity="0.7" />
        <line x1="42" y1="25.5" x2="42" y2="34" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
        <line x1="42" y1="28" x2="38" y2="22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" opacity="0.7" />
        <line x1="42" y1="28" x2="46" y2="22" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" opacity="0.7" />
        <line x1="42" y1="34" x2="39" y2="43" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
        <line x1="42" y1="34" x2="45" y2="43" stroke="currentColor" strokeWidth="2" strokeLinecap="round" opacity="0.7" />
      </g></g>

      {/* Sparkles when everyone's together */}
      <g className="logo-sp">
        <line x1="9" y1="14" x2="9" y2="11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.8" />
        <line x1="12" y1="15" x2="14" y2="13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.8" />
        <line x1="6" y1="15" x2="4" y2="13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.8" />
        <line x1="42" y1="14" x2="42" y2="11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.6" />
        <line x1="45" y1="15" x2="47" y2="13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.6" />
        <line x1="39" y1="15" x2="37" y2="13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" opacity="0.6" />
      </g>
    </svg>
  )
}

export default Logo
