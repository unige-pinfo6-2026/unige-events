// Global test setup — mocks browser APIs absent from jsdom

// Guard all mocks: this setup runs in every environment, including Node (non-jsdom tests).
if (typeof window !== 'undefined') {
  // boneyard-js/react calls window.matchMedia for dark-mode detection.
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  })

  // boneyard-js/react uses ResizeObserver to track container dimensions.
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
