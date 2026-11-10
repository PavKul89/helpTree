import React from 'react';
import { render, screen } from '@testing-library/react';

jest.mock('axios', () => {
  const response = { data: { content: [] } };
  const client = {
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
    get: jest.fn().mockResolvedValue(response),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  };

  return {
    __esModule: true,
    default: {
      create: jest.fn(() => client),
      get: jest.fn().mockResolvedValue(response),
      post: jest.fn().mockResolvedValue(response),
      isAxiosError: jest.fn(() => false),
    },
  };
});

jest.mock('./api/postsApi', () => ({
  postsApi: {
    getAll: jest.fn().mockResolvedValue({ content: [] }),
  },
}));

jest.mock('./api/statsApi', () => ({
  statsApi: {
    getStats: jest.fn().mockResolvedValue({
      totalUsers: 0,
      totalHelps: 0,
      helpRecords: 0,
      activeHelps: 0,
      confirmedHelps: 0,
      cancelledHelps: 0,
      successfulReceipts: 0,
      activePosts: 0,
    }),
  },
}));

jest.mock('./api/authApi', () => ({
  authApi: {
    getAllUsers: jest.fn().mockResolvedValue({ content: [] }),
  },
}));

const App = require('./App').default;

beforeEach(() => {
  localStorage.clear();
  const emptyPage = { content: [] };
  jest.requireMock('./api/postsApi').postsApi.getAll.mockResolvedValue(emptyPage);
  jest.requireMock('./api/statsApi').statsApi.getStats.mockResolvedValue({
    totalUsers: 0,
    totalHelps: 0,
    helpRecords: 0,
    activeHelps: 0,
    confirmedHelps: 0,
    cancelledHelps: 0,
    successfulReceipts: 0,
    activePosts: 0,
  });
  jest.requireMock('./api/authApi').authApi.getAllUsers.mockResolvedValue(emptyPage);
});

test('renders the home screen for an unauthenticated user', async () => {
  render(<App />);

  expect(
    await screen.findByRole('heading', { name: /добрые дела начинаются здесь/i })
  ).toBeInTheDocument();
});
