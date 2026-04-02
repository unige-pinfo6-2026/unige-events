const Marquee = ({ children }: { children: React.ReactNode }) => {
  return (
    <div className="overflow-clip">
      <div className="flex items-center shrink-0 w-max animate-marquee gap-16">
        {children}
        {children}
      </div>
    </div>
  );
};

export default Marquee;