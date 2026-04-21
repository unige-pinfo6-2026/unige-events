// Global test setup — mocks browser APIs absent or incomplete in happy-dom.
// Also registers boneyard bone definitions so <Skeleton name="..."> resolves
// to real bones in tests and fixtures actually render under the overlay.
import '../bones/registry'

if (globalThis.document !== undefined) {
  // boneyard-js/react calls matchMedia for dark-mode detection.
  globalThis.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof globalThis.matchMedia

  // happy-dom returns 0×0 for getBoundingClientRect, which makes boneyard-js skip
  // rendering its fixtures (width guard). Report a realistic container size so
  // <Skeleton> actually renders its children and fixture code is exercised.
  Element.prototype.getBoundingClientRect = function () {
    return { x: 0, y: 0, top: 0, left: 0, right: 1200, bottom: 800, width: 1200, height: 800, toJSON: () => ({}) }
  }

  // boneyard-js/react uses ResizeObserver to track container dimensions.
  globalThis.ResizeObserver = class {
    observe() { /* noop — dimensions come from getBoundingClientRect */ }
    unobserve() { /* noop */ }
    disconnect() { /* noop */ }
  } as unknown as typeof ResizeObserver
}
