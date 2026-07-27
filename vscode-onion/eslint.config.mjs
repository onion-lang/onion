// ESLint flat config. The project had no config file at all, so `npm run lint` had
// always failed with "ESLint couldn't find a configuration file" — the script existed
// but never linted anything (found while clearing the dependabot alerts, #440).
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    ignores: ['out/**', 'node_modules/**'],
  },
  ...tseslint.configs.recommended,
  {
    files: ['src/**/*.ts'],
    rules: {
      '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    },
  },
);
