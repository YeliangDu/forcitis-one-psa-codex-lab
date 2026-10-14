export const DEFAULT_PUBLIC_PATH = '/h5/';

const VALID = /^\/([A-Za-z0-9._-]+\/)+$/;

/** The sub-path the H5 app is served under (build-time H5_PUBLIC_PATH). Anything that is not "/segment/.../" fails the build instead of producing broken asset URLs. */
export function publicPathOf(env) {
  const value = env.H5_PUBLIC_PATH ?? DEFAULT_PUBLIC_PATH;
  if (!VALID.test(value) || value.split('/').some((segment) => segment === '.' || segment === '..')) {
    throw new Error(`H5_PUBLIC_PATH must start and end with "/" and consist of plain path segments (for example "/h5/"), got "${value}".`);
  }
  return value;
}
