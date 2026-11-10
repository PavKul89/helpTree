export {};

describe('frontend URL configuration', () => {
  beforeEach(() => {
    jest.resetModules();
    delete process.env.REACT_APP_API_URL;
    delete process.env.REACT_APP_WS_URL;
  });

  test('derives a secure WebSocket URL from the configured API URL', () => {
    process.env.REACT_APP_API_URL = 'https://api.example.com';

    const config = require('./config') as typeof import('./config');

    expect(config.API_URL).toBe('https://api.example.com');
    expect(config.WS_URL).toBe('wss://api.example.com/ws/chat');
  });

  test('uses the current page origin when the API URL is empty', () => {
    process.env.REACT_APP_API_URL = '';

    const config = require('./config') as typeof import('./config');

    expect(config.API_URL).toBe('');
    expect(config.WS_URL).toBe(
      `${window.location.origin.replace(/^http/, 'ws')}/ws/chat`
    );
  });

  test('defaults to the local backend when no API URL is configured', () => {
    const config = require('./config') as typeof import('./config');

    expect(config.API_URL).toBe('http://localhost:8080');
    expect(config.WS_URL).toBe('ws://localhost:8080/ws/chat');
  });

  test('uses an explicitly configured WebSocket URL as-is', () => {
    process.env.REACT_APP_API_URL = 'https://api.example.com';
    process.env.REACT_APP_WS_URL = 'wss://socket.example.com/custom';

    const config = require('./config') as typeof import('./config');

    expect(config.WS_URL).toBe('wss://socket.example.com/custom');
  });

  test('derives the WebSocket URL when the optional override is empty', () => {
    process.env.REACT_APP_API_URL = 'https://api.example.com';
    process.env.REACT_APP_WS_URL = '';

    const config = require('./config') as typeof import('./config');

    expect(config.WS_URL).toBe('wss://api.example.com/ws/chat');
  });
});
