import { LoadingState } from '@forcitis/ui';

export const Default = () => <div style={{ width: 360 }}><LoadingState /></div>;

export const CustomLabel = () => <div style={{ width: 360 }}><LoadingState label="正在同步组织架构" /></div>;
