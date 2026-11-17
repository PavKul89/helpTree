/** @type {import('jest').Config} */
module.exports = {
  testEnvironment: 'jsdom',
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.ts'],
  transform: {
    '^.+\\.tsx?$': ['ts-jest', { tsconfig: '<rootDir>/tsconfig.test.json' }],
  },
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json', 'node'],
  moduleNameMapper: {
    '\\.(css|less|scss|sass)$': '<rootDir>/src/__mocks__/styleMock.js',
    '^axios$': '<rootDir>/node_modules/axios/dist/node/axios.cjs',
    '^react-router-dom$': '<rootDir>/node_modules/react-router-dom/dist/index.js',
    '^react-router/dom$': '<rootDir>/node_modules/react-router/dist/development/dom-export.js',
    '^react-router$': '<rootDir>/node_modules/react-router/dist/development/index.js',
  },
  testMatch: ['<rootDir>/src/**/*.test.ts', '<rootDir>/src/**/*.test.tsx'],
};
