import { Button, PageTitle } from '@forcitis/ui';

export const TitleOnly = () => <PageTitle text="编号规则" />;

export const WithActions = () => (
  <PageTitle
    text="组织同步记录"
    actions={<><Button variant="default">导出</Button><Button>立即同步</Button></>}
  />
);
