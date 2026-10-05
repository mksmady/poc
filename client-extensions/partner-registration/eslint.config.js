import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

export default [
	js.configs.recommended,
	{
		files: ['src/**/*.{js,jsx}'],
		languageOptions: {
			globals: {...globals.browser, Liferay: 'readonly'},
			parserOptions: {ecmaFeatures: {jsx: true}},
		},
		plugins: {'react-hooks': reactHooks},
		rules: {
			...reactHooks.configs.recommended.rules,
			'no-unused-vars': ['error', {varsIgnorePattern: '^[A-Z_]'}],
		},
	},
	{
		files: ['src/**/*.test.{js,jsx}', 'src/test/**'],
		languageOptions: {globals: {...globals.node, ...globals.vitest}},
	},
];
