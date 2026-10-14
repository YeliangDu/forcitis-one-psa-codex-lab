// Stand-in for @tarojs/taro in unit tests: records navigation and serves the route parameters a test sets.
export const navigation = {
  opened: [] as string[],
  replaced: [] as string[],
  params: {} as Record<string, string | undefined>,
};

export function resetNavigation(params: Record<string, string | undefined> = {}): void {
  navigation.opened = [];
  navigation.replaced = [];
  navigation.params = params;
}

export function useRouter(): { params: Record<string, string | undefined> } {
  return { params: navigation.params };
}

const Taro = {
  navigateTo: async ({ url }: { url: string }) => {
    navigation.opened.push(url);
  },
  redirectTo: async ({ url }: { url: string }) => {
    navigation.replaced.push(url);
  },
};

export default Taro;
