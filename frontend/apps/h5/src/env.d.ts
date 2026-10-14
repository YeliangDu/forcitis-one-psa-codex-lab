/// <reference types="@tarojs/taro" />

declare module '*.css';

declare namespace NodeJS {
  interface ProcessEnv {
    TARO_APP_WECOM_CORP_ID?: string;
  }
}
