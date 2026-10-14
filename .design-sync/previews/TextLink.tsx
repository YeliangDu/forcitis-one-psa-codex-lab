import { TextLink } from '@forcitis/ui';

export const Internal = () => (
  <p style={{ margin: 0 }}>查看 <TextLink href="#" onNavigate={() => {}}>报价单 QT-FST-202610-0012</TextLink> 的审批记录</p>
);

export const External = () => (
  <p style={{ margin: 0 }}>该待办在老系统中处理：<TextLink href="#" external>打开老系统待办</TextLink></p>
);
