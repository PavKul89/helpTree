const DEFAULT_API_HOST = 'http://localhost';
const DEFAULT_API_PORT = 8080;
const DEFAULT_API_URL = `${DEFAULT_API_HOST}:${DEFAULT_API_PORT}`;

const configuredApiUrl = process.env.REACT_APP_API_URL;
export const API_URL =
  configuredApiUrl === undefined ? DEFAULT_API_URL : configuredApiUrl;

export const WS_URL =
  process.env.REACT_APP_WS_URL ||
  `${(API_URL ? API_URL.replace(/^http/, 'ws') : window.location.origin.replace(/^http/, 'ws'))}/ws/chat`;
