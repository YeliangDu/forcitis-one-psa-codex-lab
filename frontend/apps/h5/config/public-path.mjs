export const PUBLIC_PATH = '/h5/';

/** H5 is served under /h5/ (user decision). H5_PUBLIC_PATH may only repeat that value; anything else fails the build. */
export function publicPathOf(env) {
  const value = env.H5_PUBLIC_PATH ?? PUBLIC_PATH;
  if (value !== PUBLIC_PATH) {
    throw new Error(`H5_PUBLIC_PATH must be "${PUBLIC_PATH}" (the H5 app is served under ${PUBLIC_PATH}), got "${value}".`);
  }
  return value;
}
