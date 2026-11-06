import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MutationNotice, QueryBoundary } from './query-boundary';

const pending = { isPending: true, isError: false, error: null, data: undefined };
const ready = { isPending: false, isError: false, error: null, data: { name: '张三' } };

describe('QueryBoundary', () => {
  it('shows the loading state while the query is pending and never calls the render function', () => {
    render(<QueryBoundary query={pending} errorTitle="员工加载失败">{() => <p>内容</p>}</QueryBoundary>);
    expect(screen.queryByText('内容')).toBeNull();
    expect(screen.queryByText('员工加载失败')).toBeNull();
  });

  it('shows the error title and the described failure when the query failed', () => {
    const failed = { isPending: false, isError: true, error: new Error('boom'), data: undefined };
    render(<QueryBoundary query={failed} errorTitle="员工加载失败">{() => <p>内容</p>}</QueryBoundary>);
    expect(screen.getByText('员工加载失败')).toBeTruthy();
    expect(screen.queryByText('内容')).toBeNull();
    expect(screen.queryByText('boom')).toBeNull();
  });

  it('renders the content with the loaded data', () => {
    render(<QueryBoundary query={ready} errorTitle="员工加载失败">{(data) => <p>{data.name}</p>}</QueryBoundary>);
    expect(screen.getByText('张三')).toBeTruthy();
  });
});

describe('MutationNotice', () => {
  it('shows nothing before the action fails', () => {
    const { container } = render(<MutationNotice mutation={{ isError: false, error: null }} title="撤回失败" />);
    expect(container.textContent).toBe('');
  });

  it('shows the title once the action failed and never the raw exception message', () => {
    render(<MutationNotice mutation={{ isError: true, error: new Error('boom') }} title="撤回失败" />);
    expect(screen.getByText('撤回失败')).toBeTruthy();
    expect(screen.queryByText('boom')).toBeNull();
  });
});
