export function currentUrl(): string {
  return window.location.href;
}

/** Changes the address bar without a navigation, so a one-time value can leave it at once. */
export function replaceUrl(url: string): void {
  window.history.replaceState(null, '', url);
}

export function leaveTo(url: string): void {
  window.location.assign(url);
}
