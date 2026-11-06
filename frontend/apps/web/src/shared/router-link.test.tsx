import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { RouterLink } from './router-link';

function app() {
  return (
    <MemoryRouter initialEntries={['/start']}>
      <Routes>
        <Route path="/start" element={<RouterLink to="/target">去目标</RouterLink>} />
        <Route path="/target" element={<p>目标页</p>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('RouterLink', () => {
  it('points its href at the path', () => {
    render(app());
    expect(screen.getByText('去目标').getAttribute('href')).toBe('/target');
  });

  it('moves inside the app on a plain click', () => {
    render(app());
    fireEvent.click(screen.getByText('去目标'));
    expect(screen.getByText('目标页')).toBeTruthy();
  });

  it('leaves a ctrl-click to the browser', () => {
    render(app());
    fireEvent.click(screen.getByText('去目标'), { ctrlKey: true });
    expect(screen.queryByText('目标页')).toBeNull();
  });
});
