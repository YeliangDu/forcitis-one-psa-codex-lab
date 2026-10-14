import Taro, { useRouter } from '@tarojs/taro';

export function openPage(path: string): void {
  void Taro.navigateTo({ url: path });
}

export function replacePage(path: string): void {
  void Taro.redirectTo({ url: path });
}

export function useRouteParam(name: string): string | undefined {
  return useRouter().params[name];
}
