interface SectionWrapperProps {
  children: React.ReactNode
  id?: string
  padding?: keyof typeof paddings
  size?: keyof typeof sizes
  tint?: boolean
  background?: React.ReactNode
  footer?: React.ReactNode
}

interface SectionHeaderProps {
  title: React.ReactNode
  subtitle?: React.ReactNode
  heading?: keyof typeof headings
  align?: keyof typeof aligns
}

const paddings = {
  hero: 'flex flex-col h-hero py-6',
  md: 'py-20 lg:py-32',
  sm: 'py-12 lg:py-16',
  bottom: 'pb-20',
}

const aligns = {
  center: 'text-center mx-auto',
  left: 'text-left',
  right: 'text-right'
}

const sizes = {
  xl: 'max-w-7xl',
  lg: 'max-w-5xl',
  md: 'max-w-3xl',
}

const headings = {
  xl: {
    title: 'text-5xl lg:text-8xl',
    subtitle: 'text-xl lg:text-2xl',
  },
  lg: {
    title: 'text-4xl lg:text-7xl',
    subtitle: 'text-lg lg:text-xl',
  },
  md: {
    title: 'text-3xl lg:text-6xl',
    subtitle: 'text-base lg:text-lg',
  },
}

export function SectionWrapper({
  children,
  id,
  padding = 'md',
  size = 'xl',
  background,
  footer,
}: Readonly<SectionWrapperProps>) {
  return (
    <section id={id} className={`relative overflow-hidden ${paddings[padding]}`}>
      {background}
      
      <div className={`flex flex-col m-auto ${sizes[size]} px-4 sm:px-6 lg:px-8 gap-12`}>
        {children}
      </div>
      
      {footer}
    </section>
  )
}

export function SectionHeader({ 
  title, 
  subtitle, 
  heading = 'md',
  align = 'center' 
}: Readonly<SectionHeaderProps>) {
  return (
    <div className={`flex flex-col gap-6 ${aligns[align]}`}>
      <h2 className={`${headings[heading].title} font-bold tracking-tight ${aligns[align]}`}>{title}</h2>
      {subtitle && <p className={`${headings[heading].subtitle} text-foreground/60 font-light max-w-3xl ${aligns[align]}`}>{subtitle}</p>}
    </div>
  )
}