import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * The app keeps the CRA-era REACT_APP_* contract because the Docker build args,
 * CI and deployment docs already use it. Vite normally exposes only VITE_* to
 * client code, so these two variables are injected explicitly. An unset value
 * becomes the `undefined` literal, preserving config.ts's default-URL branch.
 */
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const configured = (name: string) => env[name] ?? process.env[name];
  const injected = (name: string) => {
    const value = configured(name);
    return value === undefined ? 'undefined' : JSON.stringify(value);
  };

  return {
    plugins: [react()],
    define: {
      'process.env.REACT_APP_API_URL': injected('REACT_APP_API_URL'),
      'process.env.REACT_APP_WS_URL': injected('REACT_APP_WS_URL'),
    },
  };
});
